package com.fongmi.android.tv.player;

import com.fongmi.android.tv.event.ErrorEvent;
import com.fongmi.android.tv.player.session.*;

import org.junit.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

public class DirectPlaybackFlowTest {
    @Test public void userPauseSurvivesBackgroundAndPrepare() {
        Fixture f = new Fixture();
        f.flow.dispatch(PlaybackInput.action(PlaybackInput.Type.PAUSE));
        f.flow.setPlaybackAllowed(false);
        f.flow.setPlaybackAllowed(true);
        assertEquals(PlaybackInput.Type.PAUSE, f.last());
        assertFalse(f.flow.playWhenReady(true));
    }

    @Test public void lifecyclePauseResumesPlayingIntent() {
        Fixture f = new Fixture();
        f.flow.setPlaybackAllowed(false);
        assertFalse(f.flow.playWhenReady(true));
        f.flow.setPlaybackAllowed(true);
        assertEquals(PlaybackInput.Type.PLAY, f.last());
        assertTrue(f.flow.playWhenReady(true));
    }

    @Test public void playWhileBackgroundDoesNotStartUntilAllowed() {
        Fixture f = new Fixture();
        f.flow.setPlaybackAllowed(false);
        f.flow.dispatch(PlaybackInput.action(PlaybackInput.Type.PLAY));
        assertEquals(PlaybackInput.Type.PAUSE, f.last());
        f.flow.setPlaybackAllowed(true);
        assertEquals(PlaybackInput.Type.PLAY, f.last());
    }

    @Test public void explicitPausedPrepareRemainsPaused() {
        Fixture f = new Fixture();
        assertFalse(f.flow.playWhenReady(false));
    }

    @Test public void subtitleSeekAndSystemStopUseSameOwner() {
        Fixture f = new Fixture();
        f.flow.dispatch(PlaybackInput.subtitle(null));
        assertEquals(PlaybackInput.Type.SUBTITLE, f.last());
        f.flow.dispatch(PlaybackInput.seek(40_000));
        assertEquals(40_000, f.inputs.get(1).getPosition());
        f.flow.dispatch(PlaybackInput.action(PlaybackInput.Type.STOP));
        assertEquals(PlaybackInput.Type.STOP, f.last());
        assertEquals(1, f.finishes);
        assertFalse(f.flow.playWhenReady(true));
    }

    @Test public void endedIsDeliveredOncePerSession() {
        Fixture f = new Fixture();
        f.emit(1, PlaybackPhase.ENDED);
        f.emit(1, PlaybackPhase.ENDED);
        assertEquals(1, f.ends);
        f.flow.beginSource();
        f.emit(2, PlaybackPhase.ENDED);
        assertEquals(2, f.ends);
    }

    @Test public void newSourceRejectsQueuedOldEndAndError() {
        Fixture f = new Fixture(true);
        f.emit(1, PlaybackPhase.ENDED);
        f.flow.onError(ErrorEvent.url(1));
        f.flow.beginSource();
        f.drain();
        assertEquals(0, f.ends);
        assertEquals(0, f.retries);
    }

    @Test public void closedOrReplacedOwnerCannotAct() {
        Fixture f = new Fixture();
        f.current = false;
        f.flow.dispatch(PlaybackInput.seek(3));
        f.flow.onError(ErrorEvent.url(1));
        assertTrue(f.inputs.isEmpty());
        assertEquals(0, f.retries);
        f.current = true;
        f.flow.close();
        f.emit(1, PlaybackPhase.READY);
        f.flow.dispatch(PlaybackInput.action(PlaybackInput.Type.PLAY));
        assertTrue(f.inputs.isEmpty());
        assertTrue(f.cancelled);
    }

    @Test public void readyResetsRecoveryButPausedDoesNot() {
        Fixture f = new Fixture();
        f.flow.onError(ErrorEvent.url(1));
        f.emit(1, PlaybackPhase.PAUSED);
        f.flow.onError(ErrorEvent.url(1));
        assertEquals(1, f.retries);
        assertEquals(1, f.switches);
        f.emit(2, PlaybackPhase.READY);
        f.flow.onError(ErrorEvent.url(1));
        assertEquals(2, f.retries);
    }

    @Test public void automaticSourceAdvanceDoesNotResetGlobalErrorBudget() {
        Fixture f = new Fixture();
        for (int i = 0; i < 21; i++) f.flow.onError(ErrorEvent.url(0));
        assertEquals(20, f.advances);
        assertEquals(1, f.errors);
    }

    @Test public void manualSourceSelectionStartsNewBudgetAndPlaybackIntent() {
        Fixture f = new Fixture();
        f.flow.onError(ErrorEvent.url(1));
        f.flow.dispatch(PlaybackInput.action(PlaybackInput.Type.PAUSE));
        f.flow.beginSource();
        f.flow.onError(ErrorEvent.url(1));
        assertEquals(2, f.retries);
        assertTrue(f.flow.playWhenReady(true));
    }

    @Test public void reentrantErrorsAreSerialized() {
        Fixture f = new Fixture();
        f.reentrant = true;
        f.flow.onError(ErrorEvent.url(1));
        assertEquals(1, f.retries);
        assertEquals(1, f.switches);
        assertEquals(1, f.maxDepth);
    }

    private static final class Fixture implements DirectPlaybackFlow.Engine, DirectPlaybackFlow.Port {
        final ArrayDeque<Runnable> tasks = new ArrayDeque<>();
        final List<PlaybackInput> inputs = new ArrayList<>();
        final DirectPlaybackFlow flow;
        PlaybackObserver observer;
        boolean current = true, cancelled, reentrant;
        int ends, finishes, retries, switches, advances, errors, depth, maxDepth;
        Fixture() { this(false); }
        Fixture(boolean queued) { flow = new DirectPlaybackFlow(this, this, queued ? tasks::add : Runnable::run); }
        void emit(long id, PlaybackPhase phase) { observer.onPlaybackState(new PlaybackState(id, phase, null, 0, 100000, null)); }
        void drain() { while (!tasks.isEmpty()) tasks.remove().run(); }
        PlaybackInput.Type last() { return inputs.get(inputs.size() - 1).getType(); }
        @Override public boolean isCurrent() { return current; }
        @Override public int getPlayer() { return PlayerType.EXO; }
        @Override public void executeInput(PlaybackInput input) { inputs.add(input); }
        @Override public Subscription observe(PlaybackObserver observer) { this.observer = observer; return () -> cancelled = true; }
        @Override public void render(PlaybackState state) {}
        @Override public void navigate(boolean next) {}
        @Override public void finish() { finishes++; }
        @Override public void selectPlayer(int player) {}
        @Override public void decode(boolean persist) {}
        @Override public boolean hasPlayableItem() { return true; }
        @Override public void retry() {
            depth++; maxDepth = Math.max(depth, maxDepth); retries++;
            if (reentrant) flow.onError(ErrorEvent.url(1));
            depth--;
        }
        @Override public void reloadExo(ErrorEvent error) {}
        @Override public void switchPlayer() { depth++; maxDepth = Math.max(depth, maxDepth); switches++; depth--; }
        @Override public void advance(ErrorEvent error) { advances++; flow.beginSource(); }
        @Override public void error(ErrorEvent error) { errors++; }
        @Override public void ended() { ends++; }
    }
}
