package com.fongmi.android.tv.player.vod;

import com.fongmi.android.tv.bean.Result;
import com.fongmi.android.tv.bean.Sub;
import com.fongmi.android.tv.player.PlaybackInput;
import com.fongmi.android.tv.player.engine.EngineEvent;
import com.fongmi.android.tv.player.engine.EnginePhase;
import com.fongmi.android.tv.player.engine.EngineRequest;
import com.fongmi.android.tv.player.engine.EngineSnapshot;
import com.fongmi.android.tv.player.engine.PlayerEngine;
import com.fongmi.android.tv.player.resolver.PlaybackResolver;
import com.fongmi.android.tv.player.resolver.ResolveHandle;
import com.fongmi.android.tv.player.resolver.ResolveResult;
import com.fongmi.android.tv.player.session.DefaultPlayerSession;
import com.fongmi.android.tv.player.session.PlaybackItem;
import com.fongmi.android.tv.player.session.SwitchablePlayerSession;

import org.junit.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.*;

public class VodPlaybackFlowTest {
    @Test public void revokedPagesCannotRestartPlaybackThroughNonTransportEntryPoints() {
        Fixture f = new Fixture(); f.start();
        long request = f.coordinator.generation();
        f.current = false;
        f.flow.reload();
        f.flow.reload("hls");
        f.flow.nextPlayer();
        f.flow.playEpisode(0, StartPolicy.FRESH);
        assertEquals(request, f.coordinator.generation());
        assertFalse(f.replace);
    }
    @Test public void historyQualificationUsesBusinessStateWithoutWaitingForUiDelivery() {
        List<Runnable> delivery = new ArrayList<>();
        Fixture f = new Fixture(delivery::add);
        f.start();
        assertTrue(f.flow.canCommitHistory());
        f.coordinator.play(0, StartPolicy.FRESH);
        assertFalse(f.flow.canCommitHistory());
        while (!delivery.isEmpty()) delivery.remove(0).run();
        assertFalse(f.flow.canCommitHistory());
    }
    @Test public void castReleaseAndReturnKeepsProgressWithoutARecentStateCallback() {
        for (boolean paused : new boolean[]{false, true}) {
            Fixture f = new Fixture();
            f.start();
            f.engine.emit(EnginePhase.READY, 0);
            if (paused) f.flow.dispatch(PlaybackInput.action(PlaybackInput.Type.PAUSE));
            // Playback advances between callbacks; cast can release the old owner
            // before its Activity gets another pause/stop callback.
            f.engine.snapshot = new EngineSnapshot(f.engine.request.getSessionId(),
                    paused ? EnginePhase.PAUSED : EnginePhase.READY, 480_000, 900_000,
                    480_000, !paused, 1.75f, 0, 0);
            f.delegate.release();
            f.flow.setPlaybackAllowed(false);
            f.replace = true;
            f.attachSubtitleSession();
            f.flow.setPlaybackAllowed(true);
            f.flow.reload();
            f.resolve();

            assertEquals(480_000, f.engine.request.getStartPosition());
            assertEquals(!paused, f.engine.request.isPlayWhenReady());
        }
    }

    @Test public void pausedSubtitleSwitchAttachesBeforePrepareAndRestoresControls() {
        Fixture f = new Fixture();
        f.start();
        f.flow.dispatch(PlaybackInput.action(PlaybackInput.Type.PAUSE));
        Engine old = f.engine;
        f.replace = true;

        f.flow.dispatch(PlaybackInput.subtitle(new Sub()));

        assertEquals(VodPlaybackPhase.RESOLVING, f.coordinator.state().getPhase());
        assertFalse(f.coordinator.wantsPlayback());
        assertNull(f.engine.request);
        f.resolve();
        assertFalse(f.engine.request.isPlayWhenReady());
        assertEquals(40_000, f.engine.request.getStartPosition());
        assertEquals(1.75f, f.engine.speed, 0);
        assertEquals(VodPlaybackPhase.PAUSED, f.coordinator.state().getPhase());
        old.emit(EnginePhase.READY, 999_000);
        assertEquals(40_000, f.coordinator.currentPosition());
        f.flow.dispatch(PlaybackInput.action(PlaybackInput.Type.PLAY));
        assertEquals(VodPlaybackPhase.READY, f.coordinator.state().getPhase());
    }

    @Test public void playingSubtitleReloadKeepsPositionAndSpeed() {
        Fixture f = new Fixture();
        f.start();
        f.flow.dispatch(PlaybackInput.subtitle(new Sub()));
        f.resolve();
        assertTrue(f.engine.request.isPlayWhenReady());
        assertEquals(40_000, f.engine.request.getStartPosition());
        assertEquals(1.75f, f.engine.speed, 0);
    }

    @Test public void manualEngineAndDecodeChangesUseTheSamePausedPrepareProtocol() {
        for (PlaybackInput input : java.util.Arrays.asList(PlaybackInput.player(2), PlaybackInput.decode(true), PlaybackInput.decode(false))) {
            Fixture f = new Fixture();
            f.start();
            f.flow.dispatch(PlaybackInput.action(PlaybackInput.Type.PAUSE));
            f.flow.dispatch(input);
            assertEquals(VodPlaybackPhase.RESOLVING, f.flow.state().getPhase());
            f.resolve();
            assertFalse(f.engine.request.isPlayWhenReady());
            assertEquals(40_000, f.engine.request.getStartPosition());
            assertEquals(1.75f, f.engine.speed, 0);
        }
    }

    @Test public void automaticDecodeKeepsBudgetWhileManualDecodeBeginsANewOne() {
        Fixture f = new Fixture();
        java.util.Map<Runnable, Long> tasks = new java.util.LinkedHashMap<>();
        VodRecoveryFlow.Port recoveryPort = (VodRecoveryFlow.Port) Proxy.newProxyInstance(getClass().getClassLoader(), new Class[]{VodRecoveryFlow.Port.class}, (proxy, method, args) -> {
            if (method.getName().equals("playerType")) return 2;
            if (method.getName().equals("mayRecover")) return true;
            if (method.getName().equals("toggleDecode")) f.flow.dispatch(PlaybackInput.decode(false));
            if (method.getReturnType() == boolean.class) return false;
            return null;
        });
        f.flow.installRecovery(recoveryPort, new com.fongmi.android.tv.utils.TaskScheduler() {
            @Override public void post(Runnable task, long delay) { tasks.put(task, delay); }
            @Override public void remove(Runnable task) { tasks.remove(task); }
        });
        f.start();
        f.flow.onError(new com.fongmi.android.tv.event.ErrorEvent(com.fongmi.android.tv.event.ErrorEvent.Type.URL, 1, 4001));
        assertTrue(f.flow.recovery().isRecovering());
        assertTrue(tasks.containsValue(45_000L));
        f.flow.dispatch(PlaybackInput.decode(true));
        assertFalse(f.flow.recovery().isActive());
        assertTrue(tasks.isEmpty());
    }

    @Test public void seekDuringResolveAndBackgroundRetainsUserPause() {
        Fixture f = new Fixture();
        f.coordinator.play(0, StartPolicy.FRESH);
        f.flow.dispatch(PlaybackInput.action(PlaybackInput.Type.PAUSE));
        f.coordinator.setPlaybackAllowed(false);
        f.flow.dispatch(PlaybackInput.seek(50_000));
        f.resolve();
        assertEquals(50_000, f.engine.request.getStartPosition());
        assertFalse(f.engine.request.isPlayWhenReady());
        f.coordinator.setPlaybackAllowed(true);
        assertFalse(f.coordinator.wantsPlayback());
        assertEquals(0, f.engine.plays);
    }

    @Test public void reentrantInputsAreQueuedAndStaleOwnerCannotControlPlayback() {
        Fixture f = new Fixture();
        f.reentrant = true;
        f.flow.dispatch(PlaybackInput.action(PlaybackInput.Type.NEXT));
        assertEquals(java.util.Arrays.asList("next-start", "next-end", "previous"), f.order);
        f.current = false;
        f.flow.dispatch(PlaybackInput.action(PlaybackInput.Type.NEXT));
        assertEquals(3, f.order.size());
        f.flow.close();
        f.current = true;
        f.flow.dispatch(PlaybackInput.action(PlaybackInput.Type.PLAY));
        assertEquals(VodPlaybackPhase.RELEASED, f.coordinator.state().getPhase());
        assertEquals(VodPlaybackPhase.RELEASED, f.flow.state().getPhase());
    }

    @Test public void endedAdvancesOnceAndLoopReplaysWithoutPageSideEffects() {
        Fixture f = new Fixture();
        f.coordinator.setEpisodes(java.util.Arrays.asList(new VodEpisode("one", "site", "flag", "one", "one"),
                new VodEpisode("two", "site", "flag", "two", "two")));
        f.start();
        Engine old = f.engine;
        old.emit(EnginePhase.ENDED, 100_000);
        old.emit(EnginePhase.ENDED, 100_000);
        assertEquals("two", f.flow.state().getEpisode().getIdentity());
        assertEquals(VodPlaybackPhase.RESOLVING, f.flow.state().getPhase());
        assertEquals(Collections.singletonList("next"), f.order);

        Fixture loop = new Fixture();
        loop.flow.setLoop(true);
        loop.start();
        loop.engine.emit(EnginePhase.ENDED, 100_000);
        assertTrue(loop.order.isEmpty());
        assertEquals("one", loop.flow.state().getEpisode().getIdentity());
        assertEquals(VodPlaybackPhase.RESOLVING, loop.flow.state().getPhase());
        loop.resolve();
        assertEquals(0, loop.engine.request.getStartPosition());
    }

    @Test public void pausedOutroDoesNotAdvanceAndRepeatedTicksCannotAdvanceTwice() {
        Fixture f = new Fixture();
        f.start();
        f.flow.dispatch(PlaybackInput.action(PlaybackInput.Type.PAUSE));
        f.flow.onPosition(99_000, 100_000, 0, 5_000);
        assertTrue(f.order.isEmpty());
        f.flow.dispatch(PlaybackInput.action(PlaybackInput.Type.PLAY));
        f.flow.onPosition(99_000, 100_000, 0, 5_000);
        f.flow.onPosition(99_000, 100_000, 0, 5_000);
        assertEquals(Collections.singletonList("next"), f.order);
    }

    @Test public void delayedStatesCannotReenterNewFilmAndCloseRejectsRequestTickets() {
        List<Runnable> delivery = new ArrayList<>();
        Fixture f = new Fixture(delivery::add);
        f.coordinator.setEpisodes(java.util.Arrays.asList(new VodEpisode("one", "site", "flag", "one", "one"),
                new VodEpisode("two", "site", "flag", "two", "two")));
        List<String> episodes = new ArrayList<>();
        f.flow.observe(state -> { if (state.getEpisode() != null) episodes.add(state.getEpisode().getIdentity()); });
        f.coordinator.play(0, StartPolicy.FRESH);
        long old = f.flow.requestGeneration();
        f.coordinator.play(1, StartPolicy.FRESH);
        while (!delivery.isEmpty()) delivery.remove(0).run();
        assertEquals(Collections.singletonList("two"), episodes);
        assertFalse(f.flow.acceptsRequest(old));
        f.flow.resetFilm();
        while (!delivery.isEmpty()) delivery.remove(0).run();
        assertEquals(VodPlaybackPhase.IDLE, f.flow.state().getPhase());
        f.flow.close();
        assertFalse(f.flow.acceptsRequest(f.flow.requestGeneration()));
    }

    private static class Fixture implements VodPlaybackFlow.Port {
        Engine engine = new Engine();
        DefaultPlayerSession delegate = new DefaultPlayerSession(engine.proxy);
        final SwitchablePlayerSession session = new SwitchablePlayerSession(delegate);
        PlaybackResolver.Callback callback;
        long requestId;
        boolean current = true, replace, reentrant;
        final List<String> order = new ArrayList<>();
        final VodPlaybackCoordinator coordinator = new VodPlaybackCoordinator((request, callback) -> {
            this.callback = callback;
            requestId = request.getRequestId();
            return new ResolveHandle() {
                @Override public long getRequestId() { return request.getRequestId(); }
                @Override public void cancel() {}
            };
        }, (episode, result, position) -> new PlaybackItem(episode.getIdentity(), "https://example.com/video.mp4",
                Collections.emptyMap(), null, null, Collections.emptyList(), position, false), session);
        final VodPlaybackFlow flow;

        Fixture() { this(Runnable::run); }
        Fixture(java.util.concurrent.Executor delivery) {
            flow = new VodPlaybackFlow(coordinator, this, delivery);
            coordinator.setEpisodes(Collections.singletonList(new VodEpisode("one", "site", "flag", "one", "one")));
        }
        void start() { coordinator.play(0, StartPolicy.FRESH); resolve(); engine.emit(EnginePhase.READY, 40_000); }
        void resolve() { callback.onResolved(new ResolveResult(requestId, Result.empty())); }
        @Override public boolean isCurrent() { return current; }
        @Override public float speed() { return 1.75f; }
        @Override public boolean selectSubtitle(Sub subtitle) { return true; }
        @Override public void attachSubtitleSession() {
            if (!replace) return;
            delegate.release();
            engine = new Engine();
            delegate = new DefaultPlayerSession(engine.proxy);
            session.replace(delegate);
        }
        @Override public void seekPresentation(long position) {}
        @Override public void selectPlayer(int player) { replace = true; attachSubtitleSession(); }
        @Override public void toggleDecode(boolean persist) { replace = true; attachSubtitleSession(); }
        @Override public void nextPlayer() { replace = true; attachSubtitleSession(); }
        @Override public void navigated(boolean next, boolean moved) {
            if (!reentrant) order.add(next ? "next" : "previous");
            else if (next) {
                order.add("next-start");
                flow.dispatch(PlaybackInput.action(PlaybackInput.Type.PREVIOUS));
                order.add("next-end");
            } else order.add("previous");
        }
        @Override public void finish() {}
    }

    private static class Engine {
        EngineRequest request;
        PlayerEngine.Listener listener;
        EngineSnapshot snapshot = EngineSnapshot.EMPTY;
        float speed;
        int plays;
        final PlayerEngine proxy = (PlayerEngine) Proxy.newProxyInstance(getClass().getClassLoader(), new Class[]{PlayerEngine.class}, (object, method, args) -> {
            switch (method.getName()) {
                case "prepare":
                    request = (EngineRequest) args[0];
                    listener = (PlayerEngine.Listener) args[1];
                    snapshot = new EngineSnapshot(request.getSessionId(), EnginePhase.PREPARING, request.getStartPosition(), -1,
                            request.getStartPosition(), false, 1.75f, 0, 0);
                    break;
                case "play": plays++; emit(EnginePhase.READY, snapshot.getPosition()); break;
                case "pause": if (request != null) emit(EnginePhase.PAUSED, snapshot.getPosition()); break;
                case "snapshot": return snapshot;
                case "setSpeed": speed = (Float) args[0]; break;
            }
            return null;
        });
        void emit(EnginePhase phase, long position) {
            snapshot = new EngineSnapshot(request.getSessionId(), phase, position, 100_000, position, phase == EnginePhase.READY, 1.75f, 0, 0);
            listener.onEngineEvent(EngineEvent.state(request.getSessionId(), phase, position, 100_000));
        }
    }
}
