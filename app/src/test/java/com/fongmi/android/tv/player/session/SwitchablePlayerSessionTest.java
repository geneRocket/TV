package com.fongmi.android.tv.player.session;

import com.fongmi.android.tv.bean.Track;
import com.fongmi.android.tv.player.engine.EngineSnapshot;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class SwitchablePlayerSessionTest {

    @Test
    public void replacementKeepsObserversAndDetachesOldDelegate() {
        FakeSession first = new FakeSession();
        FakeSession second = new FakeSession();
        SwitchablePlayerSession session = new SwitchablePlayerSession(first);
        List<PlaybackPhase> phases = new ArrayList<>();
        session.observe(state -> phases.add(state.getPhase()));

        first.emit(PlaybackPhase.READY);
        session.replace(second);
        first.emit(PlaybackPhase.ERROR);
        second.emit(PlaybackPhase.PAUSED);

        assertEquals(PlaybackPhase.PAUSED, session.state().getPhase());
        assertEquals(3, phases.size());
        assertEquals(PlaybackPhase.IDLE, phases.get(0));
        assertEquals(PlaybackPhase.READY, phases.get(1));
        assertEquals(PlaybackPhase.PAUSED, phases.get(2));
    }

    @Test
    public void replacementTransfersOwnershipAndCommandsReachCurrentDelegate() {
        FakeSession first = new FakeSession();
        FakeSession second = new FakeSession();
        SwitchablePlayerSession session = new SwitchablePlayerSession(first);

        session.replace(second);
        session.play();
        session.seekTo(42);
        session.release();

        assertEquals(0, first.playCount);
        assertTrue(first.released);
        assertEquals(1, second.playCount);
        assertEquals(42, second.seekPosition);
        assertTrue(second.released);
    }

    @Test
    public void replacingWithSameDelegateIsNoOp() {
        FakeSession delegate = new FakeSession();
        SwitchablePlayerSession session = new SwitchablePlayerSession(delegate);

        session.replace(delegate);
        session.play();

        assertEquals(1, delegate.playCount);
        assertTrue(!delegate.released);
    }

    @Test
    public void releasePublishesTerminalState() {
        FakeSession delegate = new FakeSession();
        SwitchablePlayerSession session = new SwitchablePlayerSession(delegate);
        List<PlaybackPhase> phases = new ArrayList<>();
        session.observe(state -> phases.add(state.getPhase()));
        delegate.emit(PlaybackPhase.READY);

        session.release();

        assertEquals(PlaybackPhase.RELEASED, session.state().getPhase());
        assertEquals(PlaybackPhase.RELEASED, phases.get(phases.size() - 1));
    }

    @Test
    public void brokenObserverDoesNotBlockOtherObservers() {
        FakeSession delegate = new FakeSession();
        SwitchablePlayerSession session = new SwitchablePlayerSession(delegate);
        List<PlaybackPhase> phases = new ArrayList<>();
        session.observe(state -> { if (state.getPhase() != PlaybackPhase.IDLE) throw new IllegalStateException("broken UI"); });
        session.observe(state -> phases.add(state.getPhase()));

        delegate.emit(PlaybackPhase.READY);

        assertEquals(PlaybackPhase.READY, session.state().getPhase());
        assertEquals(2, phases.size());
        assertEquals(PlaybackPhase.READY, phases.get(1));
    }

    @Test
    public void lateCallbackFromReplacedDelegateIsIgnored() {
        FakeSession first = new FakeSession();
        first.keepObserverAfterCancel = true;
        FakeSession second = new FakeSession();
        SwitchablePlayerSession session = new SwitchablePlayerSession(first);

        session.replace(second);
        first.emit(PlaybackPhase.ERROR);

        assertEquals(PlaybackPhase.IDLE, session.state().getPhase());
    }

    @Test
    public void failedReplacementLeavesPreviousDelegateUsable() {
        FakeSession first = new FakeSession();
        FakeSession broken = new FakeSession();
        broken.observeError = new IllegalStateException("subscribe failed");
        SwitchablePlayerSession session = new SwitchablePlayerSession(first);

        try {
            session.replace(broken);
        } catch (IllegalStateException expected) {
            assertEquals("subscribe failed", expected.getMessage());
        }
        session.play();

        assertEquals(1, first.playCount);
        assertTrue(!first.released);
        assertTrue(broken.released);
    }

    @Test
    public void failedReplacementKeepsPreviousDelegateSubscribed() {
        FakeSession first = new FakeSession();
        FakeSession broken = new FakeSession();
        broken.observeError = new IllegalStateException("subscribe failed");
        SwitchablePlayerSession session = new SwitchablePlayerSession(first);

        try {
            session.replace(broken);
        } catch (IllegalStateException expected) {
            assertEquals("subscribe failed", expected.getMessage());
        }
        first.emit(PlaybackPhase.READY);

        assertEquals(PlaybackPhase.READY, session.state().getPhase());
    }

    @Test
    public void missingReplacementSubscriptionLeavesPreviousDelegateUsable() {
        FakeSession first = new FakeSession();
        FakeSession broken = new FakeSession();
        broken.returnNullSubscription = true;
        SwitchablePlayerSession session = new SwitchablePlayerSession(first);

        try {
            session.replace(broken);
            throw new AssertionError("Expected missing subscription to fail");
        } catch (IllegalStateException expected) {
            assertEquals("delegate returned no subscription", expected.getMessage());
        }
        session.play();

        assertEquals(1, first.playCount);
        assertTrue(!first.released);
        assertTrue(broken.released);
    }

    private static final class FakeSession implements PlayerSession {
        private PlaybackObserver observer;
        private PlaybackState state = PlaybackState.IDLE;
        private int playCount;
        private long seekPosition;
        private boolean released;
        private boolean keepObserverAfterCancel;
        private boolean returnNullSubscription;
        private RuntimeException observeError;

        void emit(PlaybackPhase phase) {
            state = new PlaybackState(1, phase, null, 0, -1, null);
            if (observer != null) observer.onPlaybackState(state);
        }

        @Override public long prepare(PlaybackItem item) { return 1; }
        @Override public void play() { playCount++; }
        @Override public void pause() {}
        @Override public void seekTo(long position) { seekPosition = position; }
        @Override public void setSpeed(float speed) {}
        @Override public void setTrack(Track track) {}
        @Override public EngineSnapshot snapshot() { return EngineSnapshot.EMPTY; }
        @Override public void stop() {}
        @Override public PlaybackState state() { return state; }
        @Override public Subscription observe(PlaybackObserver observer) {
            if (observeError != null) throw observeError;
            this.observer = observer;
            observer.onPlaybackState(state);
            if (returnNullSubscription) return null;
            return () -> { if (!keepObserverAfterCancel && this.observer == observer) this.observer = null; };
        }
        @Override public void release() { released = true; }
    }
}
