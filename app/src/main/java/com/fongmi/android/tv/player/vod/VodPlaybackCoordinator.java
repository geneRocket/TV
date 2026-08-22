package com.fongmi.android.tv.player.vod;

import com.fongmi.android.tv.player.resolver.PlaybackResolver;
import com.fongmi.android.tv.player.resolver.ResolveFailure;
import com.fongmi.android.tv.player.resolver.ResolveHandle;
import com.fongmi.android.tv.player.resolver.ResolveRequest;
import com.fongmi.android.tv.player.resolver.ResolveResult;
import com.fongmi.android.tv.player.session.PlaybackPhase;
import com.fongmi.android.tv.player.session.PlaybackState;
import com.fongmi.android.tv.player.session.PlayerSession;
import com.fongmi.android.tv.player.session.Subscription;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class VodPlaybackCoordinator implements AutoCloseable {

    private final Set<VodPlaybackObserver> observers = new LinkedHashSet<>();
    private final PlaybackItemFactory itemFactory;
    private final PlaybackResolver resolver;
    private final PlayerSession session;
    private final Subscription sessionSubscription;
    private List<VodEpisode> episodes = new ArrayList<>();
    private ResolveHandle resolveHandle;
    private VodPlaybackState state = VodPlaybackState.IDLE;
    private long requestId;
    private long opening;
    private boolean reverse;
    private boolean closed;
    private String resumeEpisodeId = "";
    private long resumePosition;

    public VodPlaybackCoordinator(PlaybackResolver resolver, PlaybackItemFactory itemFactory, PlayerSession session) {
        this.resolver = resolver;
        this.itemFactory = itemFactory;
        this.session = session;
        this.sessionSubscription = session.observe(this::onSessionState);
    }

    public synchronized void setResumePosition(String episodeId, long position) {
        requireOpen();
        this.resumeEpisodeId = episodeId == null ? "" : episodeId;
        this.resumePosition = Math.max(position, 0);
    }

    public synchronized void setEpisodes(List<VodEpisode> episodes, boolean reverse) {
        requireOpen();
        this.episodes = new ArrayList<>(episodes == null ? new ArrayList<>() : episodes);
        this.reverse = reverse;
    }

    public synchronized void setOpening(long opening) {
        this.opening = Math.max(opening, 0);
    }

    public synchronized void play(int index, StartPolicy policy) {
        requireOpen();
        if (index < 0 || index >= episodes.size()) throw new IndexOutOfBoundsException("episode index=" + index);
        cancelResolve();
        VodEpisode episode = episodes.get(index);
        long startPosition = startPosition(policy, resumePosition(episode), state.getPosition());
        long currentRequest = ++requestId;
        publish(new VodPlaybackState(VodPlaybackPhase.RESOLVING, episode, index, startPosition, -1, null));
        resolveHandle = resolver.resolve(new ResolveRequest(currentRequest, episode.getSiteKey(), episode.getFlag(), episode.getResolverUrl()), new PlaybackResolver.Callback() {
            @Override public void onResolved(ResolveResult result) { applyResolved(currentRequest, episode, index, startPosition, result); }
            @Override public void onFailure(ResolveFailure failure) { applyFailure(currentRequest, episode, index, failure); }
        });
    }

    public synchronized boolean next() {
        int target = state.getEpisodeIndex() + (reverse ? -1 : 1);
        if (target < 0 || target >= episodes.size()) return false;
        play(target, StartPolicy.RESUME_SAVED);
        return true;
    }

    public synchronized boolean previous() {
        int target = state.getEpisodeIndex() + (reverse ? 1 : -1);
        if (target < 0 || target >= episodes.size()) return false;
        play(target, StartPolicy.RESUME_SAVED);
        return true;
    }

    public synchronized void replay() {
        if (state.getEpisodeIndex() >= 0) play(state.getEpisodeIndex(), StartPolicy.FRESH);
    }

    public synchronized void reload() {
        if (state.getEpisodeIndex() >= 0) play(state.getEpisodeIndex(), StartPolicy.KEEP_CURRENT_POSITION);
    }

    public synchronized void replaceMedia(com.fongmi.android.tv.bean.Result result) {
        requireOpen();
        if (state.getEpisode() == null || result == null) return;
        cancelResolve();
        requestId++;
        long position = Math.max(state.getPosition(), 0);
        try {
            session.prepare(itemFactory.create(state.getEpisode(), result, position));
        } catch (Throwable error) {
            publish(new VodPlaybackState(VodPlaybackPhase.ERROR, state.getEpisode(), state.getEpisodeIndex(), position, state.getDuration(), error));
        }
    }

    public synchronized void play() { session.play(); }

    public synchronized void pause() { session.pause(); }

    public synchronized void seekTo(long position) { session.seekTo(position); }

    public synchronized void stop() {
        cancelResolve();
        requestId++;
        session.stop();
    }

    public synchronized VodPlaybackState state() {
        return state;
    }

    public synchronized Subscription observe(VodPlaybackObserver observer) {
        requireOpen();
        observers.add(observer);
        observer.onVodPlaybackState(state);
        return () -> removeObserver(observer);
    }

    private synchronized void removeObserver(VodPlaybackObserver observer) {
        observers.remove(observer);
    }

    private synchronized void applyResolved(long expectedRequest, VodEpisode episode, int index, long startPosition, ResolveResult resolved) {
        if (closed || expectedRequest != requestId || resolved.getRequestId() != requestId) return;
        try {
            session.prepare(itemFactory.create(episode, resolved.getResult(), startPosition));
        } catch (Throwable error) {
            publish(new VodPlaybackState(VodPlaybackPhase.ERROR, episode, index, startPosition, -1, error));
        }
    }

    private synchronized void applyFailure(long expectedRequest, VodEpisode episode, int index, ResolveFailure failure) {
        if (closed || expectedRequest != requestId || failure.getRequestId() != requestId) return;
        publish(new VodPlaybackState(VodPlaybackPhase.ERROR, episode, index, state.getPosition(), state.getDuration(), failure.getCause()));
    }

    private synchronized void onSessionState(PlaybackState playback) {
        if (closed || playback.getItem() == null || state.getEpisode() == null) return;
        if (!playback.getItem().getIdentity().equals(state.getEpisode().getIdentity())) return;
        publish(new VodPlaybackState(map(playback.getPhase()), state.getEpisode(), state.getEpisodeIndex(), playback.getPosition(), playback.getDuration(), playback.getError()));
    }

    private VodPlaybackPhase map(PlaybackPhase phase) {
        return VodPlaybackPhase.valueOf(phase.name());
    }

    private long startPosition(StartPolicy policy, long saved, long current) {
        switch (policy) {
            case KEEP_CURRENT_POSITION: return Math.max(opening, Math.max(Math.max(current, 0), Math.max(saved, 0)));
            case RESUME_SAVED: return Math.max(opening, Math.max(saved, 0));
            default: return opening;
        }
    }

    private long resumePosition(VodEpisode episode) {
        return episode.getIdentity().equals(resumeEpisodeId) ? resumePosition : 0;
    }

    private void cancelResolve() {
        if (resolveHandle != null) resolveHandle.cancel();
        resolveHandle = null;
    }

    private void publish(VodPlaybackState next) {
        state = next;
        for (VodPlaybackObserver observer : new LinkedHashSet<>(observers)) observer.onVodPlaybackState(next);
    }

    private void requireOpen() {
        if (closed) throw new IllegalStateException("coordinator is closed");
    }

    @Override
    public synchronized void close() {
        if (closed) return;
        closed = true;
        cancelResolve();
        sessionSubscription.cancel();
        session.release();
        observers.clear();
    }
}
