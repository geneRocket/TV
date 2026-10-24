package com.fongmi.android.tv.player.vod;

import com.fongmi.android.tv.bean.Result;
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
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.*;

/** Expectations at user-visible playback ownership boundaries, using real flows and sessions. */
public class VodPlaybackProductTest {
    @Test public void handoffPersistsTheAuthoritativeCheckpointOnlyForAProvenRoute() {
        for (boolean played : new boolean[]{false, true}) {
            Fixture f = new Fixture();
            if (played) f.start(); else f.flow.playEpisode(0, StartPolicy.FRESH);
            com.fongmi.android.tv.bean.History history = new com.fongmi.android.tv.bean.History();
            history.setKey("site@@@video@@@7"); history.setPosition(120_000);
            List<com.fongmi.android.tv.bean.History> saved = new ArrayList<>();
            VodHistoryController controller = new VodHistoryController(new VodHistoryController.Persistence() {
                @Override public boolean enabled() { return true; }
                @Override public void save(com.fongmi.android.tv.bean.History snapshot, boolean async) { saved.add(snapshot); }
            }, () -> 10_000);
            new VodHistorySession(f.coordinator, f.flow, controller, () -> history);
            if (played) f.engine.advance(EnginePhase.READY, 480_000, 2);
            f.current = false;
            f.flow.onOwnershipLost();
            assertEquals(played ? 1 : 0, saved.size());
            assertEquals(played ? 480_000 : 120_000, history.getPosition());
            if (played) assertEquals(900_000, saved.get(0).getDuration());
        }
    }

    @Test public void firstDetailArrivingDuringHandoffCanSelectItsInitialEpisodeOnReturn() {
        Fixture f = new Fixture();
        f.flow.onOwnershipLost(); f.delegate.release(); f.current = false;
        int[] initialSelections = {0};
        VodPlaybackLifecycle lifecycle = new VodPlaybackLifecycle(f.flow, new VodPlaybackLifecycle.Port() {
            @Override public void acquireOwnership() { f.current = true; }
            @Override public boolean needsEngine() { return f.engine.released; }
            @Override public void rebuildAndAttachEngine() { f.attach(); }
            @Override public void restoreInitialSelection() {
                initialSelections[0]++;
                f.flow.playEpisode(0, StartPolicy.RESUME_SAVED);
            }
        });
        lifecycle.resume(); f.resolve(); lifecycle.resume();
        assertEquals(1, initialSelections[0]);
        assertEquals(0, f.engine.request.getStartPosition());
        assertTrue(f.engine.request.isPlayWhenReady());
    }
    @Test public void sharedLifecycleRestoresOneCheckpointAndDoesNotReloadOnEveryResume() {
        Fixture f = new Fixture(); f.start();
        f.input(PlaybackInput.Type.PAUSE);
        f.engine.advance(EnginePhase.PAUSED, 480_000, 2);
        int[] saves = {0}, attaches = {0};
        f.flow.setHandoff(() -> saves[0]++);
        f.current = false;
        f.flow.onOwnershipLost(); f.flow.onOwnershipLost();
        assertEquals(VodPlaybackPhase.SUSPENDED, f.flow.state().getPhase());
        assertEquals(1, saves[0]);
        VodPlaybackCheckpoint checkpoint = f.coordinator.checkpoint();
        assertEquals(480_000, checkpoint.getPosition());
        assertEquals(2, checkpoint.getSpeed(), 0);
        assertFalse(checkpoint.wantsPlayback());
        f.delegate.release();
        assertEquals(VodPlaybackPhase.SUSPENDED, f.flow.state().getPhase());
        VodPlaybackLifecycle lifecycle = new VodPlaybackLifecycle(f.flow, new VodPlaybackLifecycle.Port() {
            @Override public void acquireOwnership() { f.current = true; }
            @Override public boolean needsEngine() { return f.engine.released; }
            @Override public void rebuildAndAttachEngine() { attaches[0]++; f.attach(); }
        });
        assertTrue(lifecycle.resume()); f.resolve();
        assertEquals(480_000, f.engine.request.getStartPosition());
        assertEquals(2, f.engine.speed, 0);
        assertFalse(f.engine.request.isPlayWhenReady());
        int attempts = f.attempts.size();
        assertFalse(lifecycle.resume());
        assertEquals(attempts, f.attempts.size());
        assertEquals(1, attaches[0]);
        f.flow.close();
        f.current = false;
        assertFalse(lifecycle.resume());
        assertFalse(f.current);
    }

    @Test public void handoffCancelsBothResolveAndRecoveryDeadlinesWithoutStoppingTheNewOwner() {
        Fixture f = new Fixture(); f.start();
        java.util.Map<Runnable, Long> tasks = new java.util.LinkedHashMap<>();
        com.fongmi.android.tv.utils.TaskScheduler scheduler = new com.fongmi.android.tv.utils.TaskScheduler() {
            @Override public void post(Runnable task, long delay) { tasks.put(task, delay); }
            @Override public void remove(Runnable task) { tasks.remove(task); }
        };
        f.coordinator.configureTimeout(scheduler, 30_000);
        f.flow.installRecovery((VodRecoveryFlow.Port) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class[]{VodRecoveryFlow.Port.class}, (proxy, method, args) -> {
                    if (method.getName().equals("mayRecover")) return true;
                    if (method.getName().equals("playerType")) return 2;
                    if (method.getReturnType() == boolean.class) return false;
                    return null;
                }), scheduler);
        f.flow.onError(com.fongmi.android.tv.event.ErrorEvent.url(1));
        f.flow.reload();
        List<Runnable> obsolete = new ArrayList<>(tasks.keySet());
        assertFalse(obsolete.isEmpty());
        f.flow.onOwnershipLost();
        assertTrue(tasks.isEmpty());
        assertFalse(f.flow.recovery().isActive());
        obsolete.forEach(Runnable::run);
        assertEquals(VodPlaybackPhase.SUSPENDED, f.flow.state().getPhase());
    }

    @Test public void unavailableEngineStillProducesAUsableHandoffCheckpoint() {
        Fixture f = new Fixture(); f.start();
        f.engine.emit(EnginePhase.READY, 40_000);
        f.engine.failSnapshot = true;
        f.flow.onOwnershipLost();
        f.delegate.release();
        f.returnToVod(); f.resolve();
        assertEquals(40_000, f.engine.request.getStartPosition());
    }
    @Test public void returnFromCastRestoresPositionSpeedAndUserPauseAcrossLifecycleOrders() {
        for (boolean paused : new boolean[]{false, true}) {
            for (boolean pauseBeforeRelease : new boolean[]{false, true}) {
                for (EnginePhase phase : new EnginePhase[]{EnginePhase.READY, EnginePhase.BUFFERING, EnginePhase.PREPARING}) {
                    for (long position : new long[]{0, 480_000, 899_000}) {
                        Fixture f = new Fixture();
                        f.start();
                        if (paused) f.input(PlaybackInput.Type.PAUSE);
                        f.engine.advance(phase, position, 2.25f);
                        f.takeover(pauseBeforeRelease);
                        f.returnToVod();
                        f.resolve();

                        String scenario = paused + "/" + pauseBeforeRelease + "/" + phase + "/" + position;
                        assertEquals(scenario, position, f.engine.request.getStartPosition());
                        assertEquals(scenario, !paused, f.engine.request.isPlayWhenReady());
                        assertEquals(scenario, 2.25f, f.engine.speed, 0);
                    }
                }
            }
        }
    }

    @Test public void repeatedCastSessionsDoNotRestoreTheFirstSessionsPosition() {
        Fixture f = new Fixture();
        f.start();
        for (long position : new long[]{480_000, 720_000, 0, 60_000}) {
            f.engine.advance(EnginePhase.READY, position, 1.5f);
            f.takeover(false);
            f.returnToVod();
            f.resolve();
            assertEquals(position, f.engine.request.getStartPosition());
            assertEquals(1.5f, f.engine.speed, 0);
        }
    }

    @Test public void firstResolutionFinishingDuringCastCannotPrepareOrFailTheReleasedPlayer() {
        Fixture f = new Fixture();
        f.flow.playEpisode(0, StartPolicy.FRESH);
        Attempt old = f.attempt();
        f.takeover(false);
        old.resolve();

        assertEquals(0, f.engine.prepares);
        assertFalse(f.phases.contains(VodPlaybackPhase.ERROR));
        assertTrue(old.cancelled);
        f.returnToVod();
        f.resolve();
        assertEquals(0, f.engine.request.getStartPosition());
        assertTrue(f.engine.request.isPlayWhenReady());
    }

    @Test public void delayedReloadResultDuringCastCannotConsumeSavedSeekOrWakePlayback() {
        Fixture f = new Fixture();
        f.start();
        f.engine.advance(EnginePhase.READY, 480_000, 1.5f);
        f.flow.reload();
        f.flow.dispatch(PlaybackInput.seek(600_000));
        Attempt old = f.attempt();
        int prepares = f.engine.prepares;
        f.takeover(true);
        old.resolve();

        assertEquals(prepares, f.engine.prepares);
        assertFalse(f.phases.contains(VodPlaybackPhase.ERROR));
        f.returnToVod();
        f.resolve();
        assertEquals(600_000, f.engine.request.getStartPosition());
    }

    @Test public void changingEpisodeAfterCastUsesThatEpisodesHistoryInsteadOfPreviousProgress() {
        for (long saved : new long[]{0, 120_000}) {
            Fixture f = new Fixture();
            f.start();
            f.input(PlaybackInput.Type.PAUSE);
            f.engine.advance(EnginePhase.PAUSED, 480_000, 2);
            f.takeover(false);
            f.attach();
            f.flow.setPlaybackAllowed(true);
            f.coordinator.setResumePosition("two", saved);
            f.flow.playEpisode(1, StartPolicy.RESUME_SAVED);
            f.resolve();
            assertEquals("two", f.flow.state().getEpisode().getIdentity());
            assertEquals(saved, f.engine.request.getStartPosition());
            assertTrue(f.engine.request.isPlayWhenReady());
        }
    }

    @Test public void replayAfterCastStartsFromOpeningInsteadOfRestoringOldProgress() {
        Fixture f = new Fixture();
        f.start();
        f.coordinator.setOpening(30_000);
        f.engine.advance(EnginePhase.READY, 480_000, 2);
        f.takeover(false);
        f.attach();
        f.flow.setPlaybackAllowed(true);
        f.flow.playEpisode(0, StartPolicy.FRESH);
        f.resolve();
        assertEquals(30_000, f.engine.request.getStartPosition());
        assertTrue(f.engine.request.isPlayWhenReady());
    }

    @Test public void oldCastOwnerInputsAndEngineCallbacksCannotChangeTheNewEpisode() {
        Fixture f = new Fixture();
        f.start();
        Engine old = f.engine;
        f.takeover(false);
        f.current = false;
        f.flow.dispatch(PlaybackInput.seek(800_000));
        f.input(PlaybackInput.Type.NEXT);
        assertEquals("one", f.flow.state().getEpisode().getIdentity());
        f.current = true;
        f.returnToVod();
        f.flow.playEpisode(1, StartPolicy.FRESH);
        f.resolve();
        old.emit(EnginePhase.ENDED, 900_000);
        old.emit(EnginePhase.ERROR, 0);
        assertEquals("two", f.flow.state().getEpisode().getIdentity());
        assertEquals(0, f.coordinator.currentPosition());
        assertNotEquals(VodPlaybackPhase.ERROR, f.flow.state().getPhase());
    }

    @Test public void closingPageDuringCastRejectsLateResolutionAndReturnRequests() {
        Fixture f = new Fixture();
        f.flow.playEpisode(0, StartPolicy.FRESH);
        Attempt old = f.attempt();
        f.takeover(false);
        f.flow.close();
        old.resolve();
        f.flow.reload();
        f.flow.setPlaybackAllowed(true);
        assertEquals(0, f.engine.prepares);
        assertEquals(VodPlaybackPhase.RELEASED, f.flow.state().getPhase());
    }

    @Test public void newlyOpenedFilmDoesNotInheritCastInterruptedFilmsProgressOrPause() {
        Fixture f = new Fixture();
        f.start();
        f.input(PlaybackInput.Type.PAUSE);
        f.engine.advance(EnginePhase.PAUSED, 480_000, 2);
        f.takeover(false);
        f.attach();
        f.flow.resetFilm();
        f.coordinator.setEpisodes(Collections.singletonList(new VodEpisode("new", "site", "flag", "new", "new")));
        f.flow.setPlaybackAllowed(true);
        f.flow.playEpisode(0, StartPolicy.RESUME_SAVED);
        f.resolve();
        assertEquals(0, f.engine.request.getStartPosition());
        assertTrue(f.engine.request.isPlayWhenReady());
    }

    private static final class Attempt {
        final long id;
        final PlaybackResolver.Callback callback;
        boolean cancelled;
        Attempt(long id, PlaybackResolver.Callback callback) { this.id = id; this.callback = callback; }
        void resolve() { callback.onResolved(new ResolveResult(id, Result.empty())); }
    }

    private static final class Fixture {
        Engine engine = new Engine();
        DefaultPlayerSession delegate = new DefaultPlayerSession(engine.proxy);
        final SwitchablePlayerSession session = new SwitchablePlayerSession(delegate);
        final List<Attempt> attempts = new ArrayList<>();
        final List<VodPlaybackPhase> phases = new ArrayList<>();
        boolean current = true;
        final VodPlaybackCoordinator coordinator = new VodPlaybackCoordinator((request, callback) -> {
            Attempt attempt = new Attempt(request.getRequestId(), callback);
            attempts.add(attempt);
            return new ResolveHandle() {
                @Override public long getRequestId() { return attempt.id; }
                @Override public void cancel() { attempt.cancelled = true; }
            };
        }, (episode, result, position) -> new PlaybackItem(episode.getIdentity(), "https://example.com/" + episode.getIdentity(),
                Collections.emptyMap(), null, null, Collections.emptyList(), position, false), session);
        final VodPlaybackFlow flow = new VodPlaybackFlow(coordinator, (VodPlaybackFlow.Port) Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class[]{VodPlaybackFlow.Port.class}, (proxy, method, args) -> {
                    if (method.getName().equals("isCurrent")) return current;
                    if (method.getName().equals("speed")) return engine.speed;
                    if (method.getReturnType() == boolean.class) return false;
                    return null;
                }));

        Fixture() {
            coordinator.setEpisodes(Arrays.asList(new VodEpisode("one", "site", "flag", "one", "one"),
                    new VodEpisode("two", "site", "flag", "two", "two")));
            flow.observe(state -> phases.add(state.getPhase()));
        }
        Attempt attempt() { return attempts.get(attempts.size() - 1); }
        void start() { flow.playEpisode(0, StartPolicy.FRESH); resolve(); engine.emit(EnginePhase.READY, 0); }
        void resolve() { attempt().resolve(); }
        void input(PlaybackInput.Type type) { flow.dispatch(PlaybackInput.action(type)); }
        void takeover(boolean pauseFirst) {
            if (pauseFirst) flow.setPlaybackAllowed(false);
            delegate.release();
            if (!pauseFirst) flow.setPlaybackAllowed(false);
        }
        void attach() {
            engine = new Engine();
            delegate = new DefaultPlayerSession(engine.proxy);
            session.replace(delegate);
        }
        void returnToVod() { attach(); flow.setPlaybackAllowed(true); flow.reload(); }
    }

    private static final class Engine {
        EngineRequest request;
        PlayerEngine.Listener listener;
        EngineSnapshot snapshot = EngineSnapshot.EMPTY;
        boolean released;
        boolean failSnapshot;
        int prepares;
        float speed = 1;
        final PlayerEngine proxy = (PlayerEngine) Proxy.newProxyInstance(getClass().getClassLoader(), new Class[]{PlayerEngine.class}, (object, method, args) -> {
            switch (method.getName()) {
                case "prepare":
                    if (released) throw new IllegalStateException("released engine prepared");
                    prepares++;
                    request = (EngineRequest) args[0];
                    listener = (PlayerEngine.Listener) args[1];
                    advance(EnginePhase.PREPARING, request.getStartPosition(), speed);
                    break;
                case "snapshot": if (failSnapshot) throw new IllegalStateException("engine unavailable"); return snapshot;
                case "pause": if (request != null && !released) emit(EnginePhase.PAUSED, snapshot.getPosition()); break;
                case "play": if (request != null && !released) emit(EnginePhase.READY, snapshot.getPosition()); break;
                case "seekTo": advance(snapshot.getPhase(), (Long) args[0], speed); break;
                case "setSpeed": advance(snapshot.getPhase(), snapshot.getPosition(), (Float) args[0]); break;
                case "release": released = true; snapshot = EngineSnapshot.EMPTY; speed = 1; break;
            }
            return null;
        });
        void advance(EnginePhase phase, long position, float speed) {
            this.speed = speed;
            snapshot = new EngineSnapshot(request == null ? 0 : request.getSessionId(), phase, position,
                    900_000, position, phase == EnginePhase.READY, speed, 0, 0);
        }
        void emit(EnginePhase phase, long position) {
            advance(phase, position, speed);
            listener.onEngineEvent(EngineEvent.state(request.getSessionId(), phase, position, 900_000));
        }
    }
}
