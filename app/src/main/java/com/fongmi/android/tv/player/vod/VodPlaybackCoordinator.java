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
    private boolean closed;
    private String resumeEpisodeId = "";
    private long resumePosition;
    private String preservedEpisodeId = "";
    private long preservedPosition;
    private boolean hasPreservedPosition;

    public VodPlaybackCoordinator(PlaybackResolver resolver, PlaybackItemFactory itemFactory, PlayerSession session) {
        if (resolver == null) throw new IllegalArgumentException("resolver must not be null");
        if (itemFactory == null) throw new IllegalArgumentException("itemFactory must not be null");
        if (session == null) throw new IllegalArgumentException("session must not be null");
        this.resolver = resolver;
        this.itemFactory = itemFactory;
        this.session = session;
        Subscription subscription = session.observe(this::onSessionState);
        if (subscription == null) throw new IllegalStateException("session returned no subscription");
        this.sessionSubscription = subscription;
    }

    public synchronized void setResumePosition(String episodeId, long position) {
        requireOpen();
        this.resumeEpisodeId = episodeId == null ? "" : episodeId;
        this.resumePosition = Math.max(position, 0);
    }

    public synchronized void preservePosition(String episodeId, long position) {
        requireOpen();
        this.preservedEpisodeId = episodeId == null ? "" : episodeId;
        this.preservedPosition = Math.max(position, 0);
        this.hasPreservedPosition = !this.preservedEpisodeId.isEmpty();
    }

    public synchronized void setEpisodes(List<VodEpisode> episodes) {
        requireOpen();
        List<VodEpisode> copy = new ArrayList<>(episodes == null ? new ArrayList<>() : episodes);
        if (copy.contains(null)) throw new IllegalArgumentException("episodes must not contain null");
        this.episodes = copy;
    }

    public synchronized void setOpening(long opening) {
        requireOpen();
        this.opening = Math.max(opening, 0);
    }

    public synchronized void play(int index, StartPolicy policy) {
        requireOpen();
        if (policy == null) throw new IllegalArgumentException("policy must not be null");
        if (index < 0 || index >= episodes.size()) throw new IndexOutOfBoundsException("episode index=" + index);
        cancelResolve();
        VodEpisode episode = episodes.get(index);
        long preserved = preservedPosition(episode);
        long startPosition = startPosition(policy, resumePosition(episode), state.getPosition(), preserved);
        clearPreservedPosition();
        long currentRequest = ++requestId;
        publish(new VodPlaybackState(VodPlaybackPhase.RESOLVING, episode, index, startPosition, -1, null));
        try {
            ResolveHandle handle = resolver.resolve(new ResolveRequest(currentRequest, episode.getSiteKey(), episode.getFlag(), episode.getResolverUrl()), new PlaybackResolver.Callback() {
                @Override public void onResolved(ResolveResult result) { applyResolved(currentRequest, episode, index, startPosition, result); }
                @Override public void onFailure(ResolveFailure failure) { applyFailure(currentRequest, episode, index, failure); }
            });
            if (handle == null) throw new IllegalStateException("resolver returned no handle");
            if (handle.getRequestId() != currentRequest) {
                handle.cancel();
                throw new IllegalStateException("resolver returned a handle for another request");
            }
            if (requestId != currentRequest) handle.cancel();
            else if (state.getPhase() == VodPlaybackPhase.RESOLVING) resolveHandle = handle;
        } catch (Throwable error) {
            if (requestId == currentRequest && state.getPhase() == VodPlaybackPhase.RESOLVING) {
                publish(new VodPlaybackState(VodPlaybackPhase.ERROR, episode, index, startPosition, -1, error));
            }
        }
    }

    public synchronized boolean next() {
        int target = state.getEpisodeIndex() + 1;
        if (target < 0 || target >= episodes.size()) return false;
        play(target, StartPolicy.RESUME_SAVED);
        return true;
    }

    public synchronized boolean previous() {
        int target = state.getEpisodeIndex() - 1;
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

    public synchronized void play() { requireOpen(); session.play(); }

    public synchronized void pause() { requireOpen(); session.pause(); }

    public synchronized void seekTo(long position) { requireOpen(); session.seekTo(position); }

    public synchronized void stop() {
        requireOpen();
        cancelResolve();
        requestId++;
        clearPreservedPosition();
        try {
            session.stop();
        } catch (RuntimeException ignored) {
        }
        if (state.getPhase() != VodPlaybackPhase.STOPPED) {
            publish(new VodPlaybackState(VodPlaybackPhase.STOPPED, state.getEpisode(), state.getEpisodeIndex(), state.getPosition(), state.getDuration(), null));
        }
    }

    public synchronized VodPlaybackState state() {
        return state;
    }

    public synchronized Subscription observe(VodPlaybackObserver observer) {
        requireOpen();
        if (observer == null) throw new IllegalArgumentException("observer must not be null");
        observers.add(observer);
        try {
            observer.onVodPlaybackState(state);
        } catch (RuntimeException ignored) {
        }
        return () -> removeObserver(observer);
    }

    private synchronized void removeObserver(VodPlaybackObserver observer) {
        observers.remove(observer);
    }

    private synchronized void applyResolved(long expectedRequest, VodEpisode episode, int index, long startPosition, ResolveResult resolved) {
        if (closed || expectedRequest != requestId) return;
        resolveHandle = null;
        if (resolved == null || resolved.getRequestId() != requestId) {
            publish(new VodPlaybackState(VodPlaybackPhase.ERROR, episode, index, startPosition, -1, new IllegalStateException("resolver returned an invalid result")));
            return;
        }
        try {
            session.prepare(itemFactory.create(episode, resolved.getResult(), startPosition));
        } catch (Throwable error) {
            publishPrepareErrorIfNeeded(episode, index, startPosition, error);
        }
    }

    private synchronized void applyFailure(long expectedRequest, VodEpisode episode, int index, ResolveFailure failure) {
        if (closed || expectedRequest != requestId) return;
        resolveHandle = null;
        if (failure == null || failure.getRequestId() != requestId) {
            publish(new VodPlaybackState(VodPlaybackPhase.ERROR, episode, index, state.getPosition(), state.getDuration(), new IllegalStateException("resolver returned an invalid failure")));
            return;
        }
        publish(new VodPlaybackState(VodPlaybackPhase.ERROR, episode, index, state.getPosition(), state.getDuration(), failure.getCause()));
    }

    private void publishPrepareErrorIfNeeded(VodEpisode episode, int index, long position, Throwable error) {
        if (state.getPhase() == VodPlaybackPhase.ERROR && state.getError() == error) return;
        publish(new VodPlaybackState(VodPlaybackPhase.ERROR, episode, index, position, state.getDuration(), error));
    }

    private synchronized void onSessionState(PlaybackState playback) {
        if (closed || playback.getItem() == null || state.getEpisode() == null) return;
        if (!playback.getItem().getIdentity().equals(state.getEpisode().getIdentity())) return;
        if (state.getPhase() == VodPlaybackPhase.RESOLVING && playback.getPhase() != PlaybackPhase.PREPARING) return;
        publish(new VodPlaybackState(map(playback.getPhase()), state.getEpisode(), state.getEpisodeIndex(), playback.getPosition(), playback.getDuration(), playback.getError()));
    }

    private VodPlaybackPhase map(PlaybackPhase phase) {
        switch (phase) {
            case IDLE: return VodPlaybackPhase.IDLE;
            case PREPARING: return VodPlaybackPhase.PREPARING;
            case BUFFERING: return VodPlaybackPhase.BUFFERING;
            case READY: return VodPlaybackPhase.READY;
            case PAUSED: return VodPlaybackPhase.PAUSED;
            case ENDED: return VodPlaybackPhase.ENDED;
            case ERROR: return VodPlaybackPhase.ERROR;
            case STOPPED: return VodPlaybackPhase.STOPPED;
            case RELEASED: return VodPlaybackPhase.RELEASED;
            default: throw new IllegalStateException("Unsupported playback phase: " + phase);
        }
    }

    private long startPosition(StartPolicy policy, long saved, long current, long preserved) {
        switch (policy) {
            case KEEP_CURRENT_POSITION: return Math.max(opening, preserved >= 0 ? preserved : Math.max(current, 0));
            case RESUME_SAVED: return Math.max(opening, Math.max(saved, 0));
            default: return opening;
        }
    }

    private long resumePosition(VodEpisode episode) {
        return episode.getIdentity().equals(resumeEpisodeId) ? resumePosition : 0;
    }

    private long preservedPosition(VodEpisode episode) {
        return hasPreservedPosition && episode.getIdentity().equals(preservedEpisodeId) ? preservedPosition : -1;
    }

    private void clearPreservedPosition() {
        hasPreservedPosition = false;
        preservedEpisodeId = "";
        preservedPosition = 0;
    }

    private void cancelResolve() {
        try {
            if (resolveHandle != null) resolveHandle.cancel();
        } catch (RuntimeException ignored) {
        }
        resolveHandle = null;
    }

    private void publish(VodPlaybackState next) {
        state = next;
        for (VodPlaybackObserver observer : new LinkedHashSet<>(observers)) {
            try {
                observer.onVodPlaybackState(next);
            } catch (RuntimeException ignored) {
                // State remains authoritative; one presentation observer cannot
                // interrupt resolver/session callbacks or other observers.
            }
        }
    }

    private void requireOpen() {
        if (closed) throw new IllegalStateException("coordinator is closed");
    }

    @Override
    public synchronized void close() {
        if (closed) return;
        closed = true;
        cancelResolve();
        try {
            sessionSubscription.cancel();
        } catch (RuntimeException ignored) {
        }
        try {
            session.release();
        } catch (RuntimeException ignored) {
        }
        publish(new VodPlaybackState(VodPlaybackPhase.RELEASED, state.getEpisode(), state.getEpisodeIndex(), state.getPosition(), state.getDuration(), null));
        observers.clear();
    }
}
