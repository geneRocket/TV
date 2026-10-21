package com.fongmi.android.tv.player.vod;

import com.fongmi.android.tv.player.resolver.PlaybackResolver;
import com.fongmi.android.tv.player.resolver.ResolveFailure;
import com.fongmi.android.tv.player.resolver.ResolveHandle;
import com.fongmi.android.tv.player.resolver.ResolveRequest;
import com.fongmi.android.tv.player.resolver.ResolveResult;
import com.fongmi.android.tv.player.engine.EngineSnapshot;
import com.fongmi.android.tv.player.session.PlaybackPhase;
import com.fongmi.android.tv.player.session.PlaybackItem;
import com.fongmi.android.tv.player.session.PlaybackState;
import com.fongmi.android.tv.player.session.PlayerSession;
import com.fongmi.android.tv.player.session.Subscription;
import com.fongmi.android.tv.utils.ThreadPools;

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
    // Only one terminal resolver callback may claim a request.
    private long pendingRequestId;
    // Old engine events must not leave RESOLVING, even when reloading the same episode.
    private boolean acceptingSession;
    private long opening;
    private boolean closed;
    private com.fongmi.android.tv.utils.TaskScheduler timeoutScheduler;
    private long timeoutMillis;
    private Runnable timeoutTask;
    private long timeoutGeneration;
    private boolean playWhenReady = true;
    private boolean playbackAllowed = true;
    private boolean preserveControls;
    private boolean overrideFormat;
    private String recoveryFormat;
    private float resumeSpeed = 1;
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

    public synchronized void configureTimeout(com.fongmi.android.tv.utils.TaskScheduler scheduler, long millis) {
        clearTimeout();
        timeoutScheduler = scheduler;
        timeoutMillis = Math.max(1, millis);
    }

    public synchronized void clearTimeout() {
        timeoutGeneration++;
        if (timeoutTask != null && timeoutScheduler != null) timeoutScheduler.remove(timeoutTask);
        timeoutTask = null;
    }

    public synchronized void preserveControls(boolean paused, float speed) {
        if (preserveControls) return;
        preserveControls = true;
        playWhenReady = !paused;
        resumeSpeed = Float.isFinite(speed) && speed > 0 ? speed : 1;
    }

    private void updateTimeout(VodPlaybackPhase phase) {
        boolean waiting = phase == VodPlaybackPhase.RESOLVING || phase == VodPlaybackPhase.PREPARING
                || phase == VodPlaybackPhase.BUFFERING;
        if (!waiting) { clearTimeout(); return; }
        if (timeoutScheduler == null || timeoutTask != null) return;
        long expected = requestId;
        long timer = timeoutGeneration;
        timeoutTask = () -> {
            synchronized (VodPlaybackCoordinator.this) {
                if (closed || expected != requestId || timer != timeoutGeneration || timeoutTask == null) return;
                long position = state.getPosition();
                try { position = currentPosition(); }
                catch (RuntimeException ignored) { }
                clearTimeout();
                long timedOutRequest = ++requestId;
                pendingRequestId = 0;
                acceptingSession = false;
                cancelResolve();
                try { session.stop(); }
                catch (RuntimeException error) { ThreadPools.log(error, "Timed out playback stop failed."); }
                if (closed || timedOutRequest != requestId) return;
                publish(new VodPlaybackState(VodPlaybackPhase.ERROR, state.getEpisode(), state.getEpisodeIndex(),
                        position, state.getDuration(), new java.util.concurrent.TimeoutException("Playback timed out")));
            }
        };
        timeoutScheduler.post(timeoutTask, timeoutMillis);
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
        // Updating list order is not a playback transition: do not replay READY
        // or ENDED side effects, or let list refreshes steal presentation focus.
        state = withCurrentEpisodeIndex(state);
    }

    public synchronized void setOpening(long opening) {
        requireOpen();
        this.opening = Math.max(opening, 0);
    }

    public synchronized StartPolicy normalizeStartPolicy(int index, StartPolicy policy) {
        requireOpen();
        if (policy == null) throw new IllegalArgumentException("policy must not be null");
        if (index < 0 || index >= episodes.size()) throw new IndexOutOfBoundsException("episode index=" + index);
        VodEpisode target = episodes.get(index);
        if (policy == StartPolicy.RESUME_SAVED && state.getEpisode() != null
                && target.getIdentity().equals(state.getEpisode().getIdentity())
                && target.getResolverUrl().equals(state.getEpisode().getResolverUrl())) {
            policy = StartPolicy.KEEP_CURRENT_POSITION;
        }
        return policy;
    }

    public synchronized void play(int index, StartPolicy policy) {
        requireOpen();
        if (policy == null) throw new IllegalArgumentException("policy must not be null");
        if (index < 0 || index >= episodes.size()) throw new IndexOutOfBoundsException("episode index=" + index);
        clearTimeout();
        VodEpisode target = episodes.get(index);
        if (policy == StartPolicy.FRESH || state.getEpisode() == null
                || !target.getIdentity().equals(state.getEpisode().getIdentity())
                || !target.getResolverUrl().equals(state.getEpisode().getResolverUrl())) overrideFormat = false;
        policy = normalizeStartPolicy(index, policy);
        if (policy == StartPolicy.KEEP_CURRENT_POSITION && !preserveControls) {
            EngineSnapshot snapshot = session.snapshot();
            preserveControls(!playWhenReady, snapshot.getSpeed());
        }
        if (policy != StartPolicy.KEEP_CURRENT_POSITION) {
            preserveControls = false;
            playWhenReady = true;
        }
        long currentPosition = state.getEpisode() == null ? -1 : currentPosition();
        long currentRequest = ++requestId;
        pendingRequestId = currentRequest;
        acceptingSession = false;
        cancelResolve();
        // Stop only after capturing progress and invalidating old callbacks. A
        // caller-side stop would discard live progress too early.
        try { session.stop(); }
        catch (RuntimeException error) { ThreadPools.log(error, "Previous playback stop failed."); }
        if (closed || currentRequest != requestId) return;
        VodEpisode episode = episodes.get(index);
        long preserved = preservedPosition(episode, policy);
        long startPosition = startPosition(policy, resumePosition(episode), currentPosition, preserved);
        clearPreservedPosition();
        publish(new VodPlaybackState(VodPlaybackPhase.RESOLVING, episode, index, startPosition, -1, null));
        if (closed || currentRequest != requestId) return;
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
            if (closed || requestId != currentRequest) handle.cancel();
            else if (state.getPhase() == VodPlaybackPhase.RESOLVING) resolveHandle = handle;
        } catch (Throwable error) {
            if (!closed && requestId == currentRequest && state.getPhase() == VodPlaybackPhase.RESOLVING) {
                pendingRequestId = 0;
                publish(new VodPlaybackState(VodPlaybackPhase.ERROR, episode, index, startPosition, -1, error));
            }
        }
    }

    public synchronized boolean next() {
        if (state.getEpisodeIndex() < 0) return false;
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

    public synchronized boolean replay() {
        if (state.getEpisodeIndex() < 0) return false;
        play(state.getEpisodeIndex(), StartPolicy.FRESH);
        return true;
    }

    public synchronized boolean reload() {
        if (state.getEpisodeIndex() < 0) return false;
        play(state.getEpisodeIndex(), StartPolicy.KEEP_CURRENT_POSITION);
        return true;
    }

    public synchronized boolean reload(String format) {
        if (state.getEpisodeIndex() < 0) return false;
        overrideFormat = true;
        recoveryFormat = format;
        return reload();
    }

    public synchronized void play() {
        requireOpen();
        playWhenReady = true;
        if (state.getPhase() == VodPlaybackPhase.ENDED) { replay(); return; }
        if (acceptingSession && playbackAllowed) session.play();
    }

    public synchronized boolean wantsPlayback() { return playWhenReady; }

    public synchronized void setPlaybackAllowed(boolean allowed) {
        requireOpen();
        if (playbackAllowed == allowed) return;
        playbackAllowed = allowed;
        // Another playback owner can release the delegate before Activity teardown/recreation.
        if (session.state().getPhase() == PlaybackPhase.RELEASED) return;
        if (!allowed) session.pause();
        else if (acceptingSession && playWhenReady) session.play();
    }

    /** Starts a new film without retaining the previous film's resume state. */
    public synchronized void reset() {
        stop();
        episodes.clear();
        overrideFormat = false;
        recoveryFormat = null;
        resumeEpisodeId = "";
        resumePosition = opening = 0;
        preserveControls = false;
        playWhenReady = true;
        publish(VodPlaybackState.IDLE);
    }

    public synchronized void pause() { requireOpen(); playWhenReady = false; if (acceptingSession) session.pause(); }

    public synchronized void seekTo(long position) {
        requireOpen();
        if (acceptingSession) session.seekTo(position);
        else if (state.getPhase() == VodPlaybackPhase.RESOLVING) {
            publish(new VodPlaybackState(VodPlaybackPhase.RESOLVING, state.getEpisode(), state.getEpisodeIndex(),
                    Math.max(position, 0), state.getDuration(), null));
        }
    }

    public synchronized void stop() {
        requireOpen();
        long stoppedPosition = state.getPosition();
        try { stoppedPosition = currentPosition(); }
        catch (RuntimeException ignored) { /* A failed engine must still be stopped. */ }
        long stoppingRequest = ++requestId;
        pendingRequestId = 0;
        acceptingSession = false;
        cancelResolve();
        clearPreservedPosition();
        try {
            session.stop();
        } catch (RuntimeException error) {
            ThreadPools.log(error, "Vod playback session stop failed.");
        }
        if (!closed && requestId == stoppingRequest && state.getPhase() != VodPlaybackPhase.STOPPED) {
            publish(new VodPlaybackState(VodPlaybackPhase.STOPPED, state.getEpisode(), state.getEpisodeIndex(), stoppedPosition, state.getDuration(), null));
        }
    }

    public synchronized VodPlaybackState state() {
        return state;
    }

    public synchronized long generation() { return requestId; }

    public synchronized long currentPosition() {
        if (closed) return state.getPosition();
        long snapshot = sessionSnapshotPosition();
        return snapshot >= 0 ? snapshot : state.getPosition();
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
        if (closed || expectedRequest != requestId || pendingRequestId != expectedRequest) return;
        pendingRequestId = 0;
        resolveHandle = null;
        // A seek can arrive while resolution is pending, before an engine exists.
        startPosition = state.getPosition();
        if (resolved == null || resolved.getRequestId() != requestId) {
            publish(new VodPlaybackState(VodPlaybackPhase.ERROR, episode, index, startPosition, -1, new IllegalStateException("resolver returned an invalid result")));
            return;
        }
        boolean restore = preserveControls;
        float speed = resumeSpeed;
        preserveControls = false;
        try {
            PlaybackItem item = itemFactory.create(episode, resolved.getResult(), startPosition)
                    .withPlayWhenReady(playWhenReady && playbackAllowed);
            if (overrideFormat) item = item.withFormat(recoveryFormat);
            if (closed || expectedRequest != requestId) return;
            acceptingSession = true;
            session.prepare(item);
            if (!closed && expectedRequest == requestId && restore) {
                session.setSpeed(speed);
                if (!playWhenReady || !playbackAllowed) session.pause();
            }
        } catch (Throwable error) {
            if (!closed && expectedRequest == requestId) publishPrepareErrorIfNeeded(episode, index, startPosition, error);
        }
    }

    private synchronized void applyFailure(long expectedRequest, VodEpisode episode, int index, ResolveFailure failure) {
        if (closed || expectedRequest != requestId || pendingRequestId != expectedRequest) return;
        pendingRequestId = 0;
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
        if (closed || !acceptingSession || playback == null || playback.getItem() == null || state.getEpisode() == null) return;
        if (!playback.getItem().getIdentity().equals(state.getEpisode().getIdentity())) return;
        if (state.getPhase() == VodPlaybackPhase.RESOLVING && playback.getPhase() != PlaybackPhase.PREPARING) return;
        if (playback.getPhase() == PlaybackPhase.RELEASED) acceptingSession = false;
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
            case KEEP_CURRENT_POSITION: return Math.max(opening, preserved >= 0 ? preserved : current >= 0 ? current : saved);
            case RESUME_SAVED: return Math.max(opening, Math.max(saved, 0));
            default: return opening;
        }
    }

    private long sessionSnapshotPosition() {
        if (!acceptingSession) return -1;
        PlaybackState playback = session.state();
        if (playback == null || playback.getItem() == null || state.getEpisode() == null) return -1;
        if (!playback.getItem().getIdentity().equals(state.getEpisode().getIdentity())) return -1;
        EngineSnapshot snapshot = session.snapshot();
        if (snapshot == null || snapshot.getSessionId() != playback.getSessionId()) return -1;
        return snapshot.getPosition();
    }

    private long resumePosition(VodEpisode episode) {
        return episode.getIdentity().equals(resumeEpisodeId) ? resumePosition : 0;
    }

    private long preservedPosition(VodEpisode episode, StartPolicy policy) {
        if (!hasPreservedPosition) return -1;
        if (policy == StartPolicy.KEEP_CURRENT_POSITION) return preservedPosition;
        return episode.getIdentity().equals(preservedEpisodeId) ? preservedPosition : -1;
    }

    private void clearPreservedPosition() {
        hasPreservedPosition = false;
        preservedEpisodeId = "";
        preservedPosition = 0;
    }

    private void cancelResolve() {
        ResolveHandle previous = resolveHandle;
        resolveHandle = null;
        try {
            if (previous != null) previous.cancel();
        } catch (RuntimeException error) {
            ThreadPools.log(error, "Vod playback resolve cancel failed.");
        }
    }

    private int indexOfEpisode(VodEpisode episode, int previousIndex) {
        if (episode == null) return -1;
        if (previousIndex >= 0 && previousIndex < episodes.size()
                && sameEpisodeRoute(episodes.get(previousIndex), episode)) return previousIndex;
        int identityMatch = -1;
        boolean ambiguous = false;
        for (int i = 0; i < episodes.size(); i++) {
            VodEpisode candidate = episodes.get(i);
            if (!candidate.getIdentity().equals(episode.getIdentity())) continue;
            if (sameEpisodeRoute(candidate, episode)) return i;
            if (identityMatch >= 0) ambiguous = true;
            identityMatch = i;
        }
        // A refreshed address can retain its episode identity. Duplicate labels
        // require a matching route so that reload cannot silently pick another cut.
        return ambiguous ? -1 : identityMatch;
    }

    private boolean sameEpisodeRoute(VodEpisode first, VodEpisode second) {
        return first.getIdentity().equals(second.getIdentity())
                && first.getResolverUrl().equals(second.getResolverUrl());
    }

    private VodPlaybackState withCurrentEpisodeIndex(VodPlaybackState value) {
        int index = indexOfEpisode(value.getEpisode(), value.getEpisodeIndex());
        if (index == value.getEpisodeIndex()) return value;
        return new VodPlaybackState(value.getPhase(), value.getEpisode(), index,
                value.getPosition(), value.getDuration(), value.getError());
    }

    private void publish(VodPlaybackState next) {
        next = withCurrentEpisodeIndex(next);
        state = next;
        updateTimeout(next.getPhase());
        for (VodPlaybackObserver observer : new LinkedHashSet<>(observers)) {
            if (state != next) break;
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
        clearTimeout();
        cancelResolve();
        try {
            sessionSubscription.cancel();
        } catch (RuntimeException error) {
            ThreadPools.log(error, "Vod playback session subscription cancel failed.");
        }
        try {
            session.release();
        } catch (RuntimeException error) {
            ThreadPools.log(error, "Vod playback session release failed.");
        }
        publish(new VodPlaybackState(VodPlaybackPhase.RELEASED, state.getEpisode(), state.getEpisodeIndex(), state.getPosition(), state.getDuration(), null));
        observers.clear();
    }
}
