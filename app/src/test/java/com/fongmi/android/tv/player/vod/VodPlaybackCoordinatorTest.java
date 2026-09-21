package com.fongmi.android.tv.player.vod;

import com.fongmi.android.tv.bean.Result;
import com.fongmi.android.tv.bean.Track;
import com.fongmi.android.tv.player.engine.EngineEvent;
import com.fongmi.android.tv.player.engine.EnginePhase;
import com.fongmi.android.tv.player.engine.EngineRequest;
import com.fongmi.android.tv.player.engine.EngineSnapshot;
import com.fongmi.android.tv.player.engine.PlayerEngine;
import com.fongmi.android.tv.player.resolver.PlaybackResolver;
import com.fongmi.android.tv.player.resolver.ResolveFailure;
import com.fongmi.android.tv.player.resolver.ResolveHandle;
import com.fongmi.android.tv.player.resolver.ResolveRequest;
import com.fongmi.android.tv.player.resolver.ResolveResult;
import com.fongmi.android.tv.player.session.DefaultPlayerSession;
import com.fongmi.android.tv.player.session.PlaybackItem;

import org.junit.Test;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class VodPlaybackCoordinatorTest {

    @Test
    public void lateResolveFromPreviousEpisodeIsIgnored() {
        ManualResolver resolver = new ManualResolver();
        FakeEngine engine = new FakeEngine();
        VodPlaybackCoordinator coordinator = coordinator(resolver, engine);

        coordinator.play(0, StartPolicy.FRESH);
        long old = resolver.lastRequestId;
        coordinator.play(1, StartPolicy.FRESH);
        long current = resolver.lastRequestId;

        resolver.resolve(old);
        assertEquals(VodPlaybackPhase.RESOLVING, coordinator.state().getPhase());
        assertEquals("episode-2", coordinator.state().getEpisode().getIdentity());

        resolver.resolve(current);
        assertEquals("episode-2", engine.request.getUrl());
    }

    @Test
    public void brokenObserverDoesNotInterruptPlaybackOrOtherObservers() {
        ManualResolver resolver = new ManualResolver();
        FakeEngine engine = new FakeEngine();
        VodPlaybackCoordinator coordinator = coordinator(resolver, engine);
        java.util.List<VodPlaybackPhase> phases = new ArrayList<>();
        coordinator.observe(state -> { if (state.getPhase() != VodPlaybackPhase.IDLE) throw new IllegalStateException("broken UI"); });
        coordinator.observe(state -> phases.add(state.getPhase()));

        coordinator.play(0, StartPolicy.FRESH);
        resolver.resolve(resolver.lastRequestId);

        assertEquals(3, phases.size());
        assertEquals(VodPlaybackPhase.PREPARING, phases.get(2));
        assertEquals("episode-1", engine.request.getUrl());
    }

    @Test
    public void synchronousResolverFailureBecomesErrorState() {
        FakeEngine engine = new FakeEngine();
        VodPlaybackCoordinator coordinator = coordinator((request, callback) -> {
            throw new IllegalStateException("resolver unavailable");
        }, engine);

        coordinator.play(0, StartPolicy.FRESH);

        assertEquals(VodPlaybackPhase.ERROR, coordinator.state().getPhase());
        assertEquals("resolver unavailable", coordinator.state().getError().getMessage());
    }

    @Test
    public void nullResolveCallbackValueBecomesErrorState() {
        FakeEngine engine = new FakeEngine();
        VodPlaybackCoordinator coordinator = coordinator((request, callback) -> {
            callback.onResolved(null);
            return new ResolveHandle() {
                @Override public long getRequestId() { return request.getRequestId(); }
                @Override public void cancel() {}
            };
        }, engine);

        coordinator.play(0, StartPolicy.FRESH);

        assertEquals(VodPlaybackPhase.ERROR, coordinator.state().getPhase());
    }

    @Test
    public void closePublishesTerminalState() {
        ManualResolver resolver = new ManualResolver();
        FakeEngine engine = new FakeEngine();
        VodPlaybackCoordinator coordinator = coordinator(resolver, engine);
        java.util.List<VodPlaybackPhase> phases = new ArrayList<>();
        coordinator.observe(state -> phases.add(state.getPhase()));

        coordinator.close();

        assertEquals(VodPlaybackPhase.RELEASED, coordinator.state().getPhase());
        assertEquals(VodPlaybackPhase.RELEASED, phases.get(phases.size() - 1));
    }

    @Test
    public void nextEpisodeStartsAtOpeningInsteadOfPreviousEpisodeProgress() {
        ManualResolver resolver = new ManualResolver();
        FakeEngine engine = new FakeEngine();
        VodPlaybackCoordinator coordinator = coordinator(resolver, engine);
        coordinator.setOpening(60_000);

        coordinator.setResumePosition("episode-1", 600_000);
        coordinator.play(0, StartPolicy.RESUME_SAVED);
        resolver.resolve(resolver.lastRequestId);
        engine.emit(EngineEvent.state(engine.request.getSessionId(), EnginePhase.READY, 600_000, 1_000_000));
        assertTrue(coordinator.next());
        resolver.resolve(resolver.lastRequestId);

        assertEquals("episode-2", engine.request.getUrl());
        assertEquals(60_000, engine.request.getStartPosition());
    }

    @Test
    public void historyPositionResumesOnlyItsMatchingEpisode() {
        ManualResolver resolver = new ManualResolver();
        FakeEngine engine = new FakeEngine();
        VodPlaybackCoordinator coordinator = coordinator(resolver, engine);
        coordinator.setResumePosition("episode-2", 480_000);

        coordinator.play(0, StartPolicy.RESUME_SAVED);
        resolver.resolve(resolver.lastRequestId);
        assertEquals(0, engine.request.getStartPosition());

        coordinator.play(1, StartPolicy.RESUME_SAVED);
        resolver.resolve(resolver.lastRequestId);
        assertEquals(480_000, engine.request.getStartPosition());
    }

    @Test
    public void boundaryDoesNotWrapToAnotherEpisode() {
        ManualResolver resolver = new ManualResolver();
        FakeEngine engine = new FakeEngine();
        VodPlaybackCoordinator coordinator = coordinator(resolver, engine);
        coordinator.play(1, StartPolicy.FRESH);
        assertFalse(coordinator.next());
    }

    @Test
    public void stopWhileResolvingPublishesStoppedAndRejectsLateResult() {
        ManualResolver resolver = new ManualResolver();
        FakeEngine engine = new FakeEngine();
        VodPlaybackCoordinator coordinator = coordinator(resolver, engine);
        coordinator.play(0, StartPolicy.FRESH);
        long request = resolver.lastRequestId;

        coordinator.stop();
        resolver.resolve(request);

        assertEquals(VodPlaybackPhase.STOPPED, coordinator.state().getPhase());
        assertTrue(engine.request == null);
    }

    @Test
    public void reloadWithoutCurrentEpisodeDoesNotPublishLoadingState() {
        ManualResolver resolver = new ManualResolver();
        FakeEngine engine = new FakeEngine();
        VodPlaybackCoordinator coordinator = coordinator(resolver, engine);

        assertFalse(coordinator.reload());
        assertEquals(VodPlaybackPhase.IDLE, coordinator.state().getPhase());
        assertEquals(0, resolver.lastRequestId);
    }

    @Test
    public void reloadWithCurrentEpisodePublishesResolvingState() {
        ManualResolver resolver = new ManualResolver();
        FakeEngine engine = new FakeEngine();
        VodPlaybackCoordinator coordinator = coordinator(resolver, engine);
        coordinator.play(0, StartPolicy.FRESH);
        resolver.resolve(resolver.lastRequestId);

        assertTrue(coordinator.reload());

        assertEquals(VodPlaybackPhase.RESOLVING, coordinator.state().getPhase());
        assertEquals("episode-1", coordinator.state().getEpisode().getIdentity());
    }

    @Test
    public void replayWithoutCurrentEpisodeDoesNotPublishLoadingState() {
        ManualResolver resolver = new ManualResolver();
        FakeEngine engine = new FakeEngine();
        VodPlaybackCoordinator coordinator = coordinator(resolver, engine);

        assertFalse(coordinator.replay());
        assertEquals(VodPlaybackPhase.IDLE, coordinator.state().getPhase());
        assertEquals(0, resolver.lastRequestId);
    }

    @Test
    public void oldSessionTerminalEventDoesNotReplaceResolvingState() {
        ManualResolver resolver = new ManualResolver();
        FakeEngine engine = new FakeEngine();
        VodPlaybackCoordinator coordinator = coordinator(resolver, engine);
        coordinator.play(0, StartPolicy.FRESH);
        resolver.resolve(resolver.lastRequestId);
        long oldSession = engine.request.getSessionId();
        engine.emit(EngineEvent.state(oldSession, EnginePhase.READY, 120_000, 900_000));

        coordinator.reload();
        engine.emit(EngineEvent.state(oldSession, EnginePhase.STOPPED, 120_000, 900_000));

        assertEquals(VodPlaybackPhase.RESOLVING, coordinator.state().getPhase());
    }

    @Test
    public void updatedResumePositionSurvivesPlayerReplacement() {
        ManualResolver resolver = new ManualResolver();
        FakeEngine engine = new FakeEngine();
        VodPlaybackCoordinator coordinator = coordinator(resolver, engine);
        coordinator.play(0, StartPolicy.FRESH);
        resolver.resolve(resolver.lastRequestId);
        engine.emit(EngineEvent.state(engine.request.getSessionId(), EnginePhase.READY, 321_000, 900_000));

        coordinator.preservePosition("episode-1", 321_000);
        engine.emit(EngineEvent.state(engine.request.getSessionId(), EnginePhase.STOPPED, 0, -1));
        coordinator.reload();
        resolver.resolve(resolver.lastRequestId);

        assertEquals(321_000, engine.request.getStartPosition());
    }

    @Test
    public void keepCurrentPositionDoesNotJumpForwardToOlderHistory() {
        ManualResolver resolver = new ManualResolver();
        FakeEngine engine = new FakeEngine();
        VodPlaybackCoordinator coordinator = coordinator(resolver, engine);
        coordinator.setResumePosition("episode-1", 600_000);
        coordinator.play(0, StartPolicy.RESUME_SAVED);
        resolver.resolve(resolver.lastRequestId);
        engine.emit(EngineEvent.state(engine.request.getSessionId(), EnginePhase.READY, 120_000, 900_000));

        coordinator.reload();
        resolver.resolve(resolver.lastRequestId);

        assertEquals(120_000, engine.request.getStartPosition());
    }

    @Test
    public void keepCurrentPositionFallsBackToSavedHistoryBeforePlaybackStarts() {
        ManualResolver resolver = new ManualResolver();
        FakeEngine engine = new FakeEngine();
        VodPlaybackCoordinator coordinator = coordinator(resolver, engine);
        coordinator.setResumePosition("episode-1", 2_718_382);

        coordinator.play(0, StartPolicy.KEEP_CURRENT_POSITION);
        resolver.resolve(resolver.lastRequestId);

        assertEquals(2_718_382, engine.request.getStartPosition());
    }

    @Test
    public void keepCurrentPositionUsesPreservedPositionAcrossRouteIdentityChange() {
        ManualResolver resolver = new ManualResolver();
        FakeEngine engine = new FakeEngine();
        VodPlaybackCoordinator coordinator = coordinator(resolver, engine);
        coordinator.play(0, StartPolicy.FRESH);
        resolver.resolve(resolver.lastRequestId);
        engine.emit(EngineEvent.state(engine.request.getSessionId(), EnginePhase.READY, 321_000, 900_000));

        coordinator.preservePosition("site\u001fflag-a\u001f第1集", 321_000);
        coordinator.setEpisodes(Arrays.asList(
                new VodEpisode("site\u001fflag-b\u001f第1集", "site", "flag-b", "第1集", "episode-1-route-b"),
                new VodEpisode("site\u001fflag-b\u001f第2集", "site", "flag-b", "第2集", "episode-2-route-b")));
        coordinator.play(0, StartPolicy.KEEP_CURRENT_POSITION);
        resolver.resolve(resolver.lastRequestId);

        assertEquals(321_000, engine.request.getStartPosition());
    }

    @Test
    public void resumePositionIsDeliveredToEngineRequest() {
        ManualResolver resolver = new ManualResolver();
        FakeEngine engine = new FakeEngine();
        VodPlaybackCoordinator coordinator = coordinator(resolver, engine);
        coordinator.setResumePosition("episode-1", 42_680);
        coordinator.play(0, StartPolicy.RESUME_SAVED);
        resolver.resolve(resolver.lastRequestId);

        assertEquals(42_680, engine.request.getStartPosition());
    }

    @Test
    public void currentPositionUsesCoordinatorStateAndMatchingEngineSnapshot() {
        ManualResolver resolver = new ManualResolver();
        FakeEngine engine = new FakeEngine();
        VodPlaybackCoordinator coordinator = coordinator(resolver, engine);
        coordinator.setResumePosition("episode-1", 42_680);
        coordinator.play(0, StartPolicy.RESUME_SAVED);

        assertEquals(42_680, coordinator.currentPosition());

        resolver.resolve(resolver.lastRequestId);
        engine.emit(EngineEvent.state(engine.request.getSessionId(), EnginePhase.READY, 43_000, 7_174_333));

        assertEquals(43_000, coordinator.currentPosition());
    }

    @Test
    public void currentPositionUsesFreshEngineSnapshotBeforeStateEventArrives() {
        ManualResolver resolver = new ManualResolver();
        FakeEngine engine = new FakeEngine();
        VodPlaybackCoordinator coordinator = coordinator(resolver, engine);
        coordinator.play(0, StartPolicy.FRESH);
        resolver.resolve(resolver.lastRequestId);

        engine.snapshot = new EngineSnapshot(engine.request.getSessionId(), EnginePhase.READY, 88_000, 900_000, 120_000, true, 1.0f, 1920, 1080);

        assertEquals(88_000, coordinator.currentPosition());
    }

    @Test
    public void currentPositionIgnoresSnapshotFromDifferentSession() {
        ManualResolver resolver = new ManualResolver();
        FakeEngine engine = new FakeEngine();
        VodPlaybackCoordinator coordinator = coordinator(resolver, engine);
        coordinator.setResumePosition("episode-1", 42_000);
        coordinator.play(0, StartPolicy.RESUME_SAVED);
        resolver.resolve(resolver.lastRequestId);

        engine.snapshot = new EngineSnapshot(engine.request.getSessionId() + 1, EnginePhase.READY, 88_000, 900_000, 120_000, true, 1.0f, 1920, 1080);

        assertEquals(42_000, coordinator.currentPosition());
    }

    @Test
    public void mismatchedResolveHandleBecomesError() {
        FakeEngine engine = new FakeEngine();
        VodPlaybackCoordinator coordinator = coordinator((request, callback) -> new ResolveHandle() {
            @Override public long getRequestId() { return request.getRequestId() + 1; }
            @Override public void cancel() {}
        }, engine);

        coordinator.play(0, StartPolicy.FRESH);

        assertEquals(VodPlaybackPhase.ERROR, coordinator.state().getPhase());
    }

    @Test
    public void synchronousResolveDoesNotCancelCompletedHandle() {
        FakeEngine engine = new FakeEngine();
        AtomicBoolean cancelled = new AtomicBoolean();
        VodPlaybackCoordinator coordinator = coordinator((request, callback) -> {
            callback.onResolved(new ResolveResult(request.getRequestId(), Result.empty()));
            return new ResolveHandle() {
                @Override public long getRequestId() { return request.getRequestId(); }
                @Override public void cancel() { cancelled.set(true); }
            };
        }, engine);

        coordinator.play(0, StartPolicy.FRESH);

        assertFalse(cancelled.get());
        assertEquals(VodPlaybackPhase.PREPARING, coordinator.state().getPhase());
    }

    @Test
    public void cancellationCallbackCannotPreparePreviousRequest() {
        FakeEngine engine = new FakeEngine();
        VodPlaybackCoordinator coordinator = coordinator((request, callback) -> new ResolveHandle() {
            @Override public long getRequestId() { return request.getRequestId(); }
            @Override public void cancel() {
                callback.onResolved(new ResolveResult(request.getRequestId(), Result.empty()));
            }
        }, engine);
        coordinator.play(0, StartPolicy.FRESH);
        coordinator.play(1, StartPolicy.FRESH);
        assertTrue(engine.request == null);
        coordinator.stop();
        assertTrue(engine.request == null);
        assertEquals(VodPlaybackPhase.STOPPED, coordinator.state().getPhase());
    }

    @Test
    public void duplicateResolverCompletionCannotRestartPlayback() {
        ManualResolver resolver = new ManualResolver();
        FakeEngine engine = new FakeEngine();
        VodPlaybackCoordinator coordinator = coordinator(resolver, engine);
        coordinator.play(0, StartPolicy.FRESH);
        long request = resolver.lastRequestId;
        resolver.resolve(request);
        long session = engine.request.getSessionId();
        resolver.resolve(request);
        resolver.callbacks.get(request).onFailure(new ResolveFailure(request, new IllegalStateException("late")));
        assertEquals(session, engine.request.getSessionId());
        assertEquals(VodPlaybackPhase.PREPARING, coordinator.state().getPhase());
    }

    @Test
    public void resolvingObserverCanStopBeforeResolverStarts() {
        ManualResolver resolver = new ManualResolver();
        VodPlaybackCoordinator coordinator = coordinator(resolver, new FakeEngine());
        coordinator.observe(state -> {
            if (state.getPhase() == VodPlaybackPhase.RESOLVING) coordinator.stop();
        });
        coordinator.play(0, StartPolicy.FRESH);
        assertEquals(0, resolver.lastRequestId);
        assertEquals(VodPlaybackPhase.STOPPED, coordinator.state().getPhase());
    }

    @Test
    public void reloadUsesFreshSnapshotAndPreservesZero() {
        ManualResolver resolver = new ManualResolver();
        FakeEngine engine = new FakeEngine();
        VodPlaybackCoordinator coordinator = coordinator(resolver, engine);
        coordinator.setResumePosition("episode-1", 600_000);
        coordinator.play(0, StartPolicy.RESUME_SAVED);
        resolver.resolve(resolver.lastRequestId);
        engine.snapshot = new EngineSnapshot(engine.request.getSessionId(), EnginePhase.READY, 88_000, 900_000, 0, true, 1, 0, 0);
        coordinator.reload();
        resolver.resolve(resolver.lastRequestId);
        assertEquals(88_000, engine.request.getStartPosition());
        engine.snapshot = new EngineSnapshot(engine.request.getSessionId(), EnginePhase.READY, 0, 900_000, 0, true, 1, 0, 0);
        assertEquals(0, coordinator.currentPosition());
        coordinator.reload();
        resolver.resolve(resolver.lastRequestId);
        assertEquals(0, engine.request.getStartPosition());
    }

    @Test
    public void explicitPreservedZeroDoesNotFallBackToHistory() {
        ManualResolver resolver = new ManualResolver();
        FakeEngine engine = new FakeEngine();
        VodPlaybackCoordinator coordinator = coordinator(resolver, engine);
        coordinator.setResumePosition("episode-1", 600_000);
        coordinator.play(0, StartPolicy.RESUME_SAVED);
        resolver.resolve(resolver.lastRequestId);
        coordinator.preservePosition("episode-1", 0);
        coordinator.reload();
        resolver.resolve(resolver.lastRequestId);
        assertEquals(0, engine.request.getStartPosition());
    }

    @Test
    public void oldPreparingEventCannotEndNewResolve() {
        ManualResolver resolver = new ManualResolver();
        FakeEngine engine = new FakeEngine();
        VodPlaybackCoordinator coordinator = coordinator(resolver, engine);
        coordinator.play(0, StartPolicy.FRESH);
        resolver.resolve(resolver.lastRequestId);
        long old = engine.request.getSessionId();
        coordinator.reload();
        engine.emit(EngineEvent.state(old, EnginePhase.PREPARING, 0, -1));
        engine.emit(EngineEvent.state(old, EnginePhase.ENDED, 100, 100));
        assertEquals(VodPlaybackPhase.RESOLVING, coordinator.state().getPhase());
    }

    private VodPlaybackCoordinator coordinator(ManualResolver resolver, FakeEngine engine) {
        return coordinator((PlaybackResolver) resolver, engine);
    }

    private VodPlaybackCoordinator coordinator(PlaybackResolver resolver, FakeEngine engine) {
        VodPlaybackCoordinator coordinator = new VodPlaybackCoordinator(resolver,
                (episode, result, position) -> new PlaybackItem(episode.getIdentity(), episode.getResolverUrl(), Collections.emptyMap(), null, null, Collections.emptyList(), position, false),
                new DefaultPlayerSession(engine));
        coordinator.setEpisodes(Arrays.asList(
                new VodEpisode("episode-1", "site", "flag", "第1集", "episode-1"),
                new VodEpisode("episode-2", "site", "flag", "第2集", "episode-2")));
        return coordinator;
    }

    private static final class ManualResolver implements PlaybackResolver {

        private final Map<Long, Callback> callbacks = new LinkedHashMap<>();
        private long lastRequestId;

        @Override
        public ResolveHandle resolve(ResolveRequest request, Callback callback) {
            lastRequestId = request.getRequestId();
            callbacks.put(lastRequestId, callback);
            return new ResolveHandle() {
                @Override public long getRequestId() { return request.getRequestId(); }
                @Override public void cancel() {}
            };
        }

        void resolve(long requestId) {
            callbacks.get(requestId).onResolved(new ResolveResult(requestId, Result.empty()));
        }
    }

    private static final class FakeEngine implements PlayerEngine {

        private EngineRequest request;
        private Listener listener;
        private EngineSnapshot snapshot = EngineSnapshot.EMPTY;

        @Override public void prepare(EngineRequest request, Listener listener) { this.request = request; this.listener = listener; }
        void emit(EngineEvent event) {
            snapshot = new EngineSnapshot(event.getSessionId(), event.getPhase(), event.getPosition(), event.getDuration(), 0, event.getPhase() == EnginePhase.READY, 1.0f, 0, 0);
            listener.onEngineEvent(event);
        }
        @Override public void play() {}
        @Override public void pause() {}
        @Override public void seekTo(long position) {}
        @Override public void stop() {}
        @Override public void setSpeed(float speed) {}
        @Override public void setTrack(Track track) {}
        @Override public EngineSnapshot snapshot() { return snapshot; }
        @Override public void release() {}
    }
}
