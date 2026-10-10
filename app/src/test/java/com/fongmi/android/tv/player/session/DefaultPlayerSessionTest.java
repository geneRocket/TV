package com.fongmi.android.tv.player.session;

import com.fongmi.android.tv.bean.Track;
import com.fongmi.android.tv.player.engine.EngineEvent;
import com.fongmi.android.tv.player.engine.EnginePhase;
import com.fongmi.android.tv.player.engine.EngineRequest;
import com.fongmi.android.tv.player.engine.EngineSnapshot;
import com.fongmi.android.tv.player.engine.PlayerEngine;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class DefaultPlayerSessionTest {

    @Test
    public void preparePublishesStateAndForwardsImmutableRequest() {
        FakeEngine engine = new FakeEngine();
        DefaultPlayerSession session = new DefaultPlayerSession(engine);
        List<PlaybackPhase> phases = new ArrayList<>();
        session.observe(state -> phases.add(state.getPhase()));

        long id = session.prepare(item("episode-1", 120_000));

        assertEquals(1, id);
        assertEquals(id, engine.request.getSessionId());
        assertEquals(120_000, engine.request.getStartPosition());
        assertEquals(PlaybackPhase.PREPARING, session.state().getPhase());
        assertEquals(List.of(PlaybackPhase.IDLE, PlaybackPhase.PREPARING), phases);
    }

    @Test
    public void staleEngineEventsCannotMutateCurrentSession() {
        FakeEngine engine = new FakeEngine();
        DefaultPlayerSession session = new DefaultPlayerSession(engine);
        long old = session.prepare(item("episode-1", 0));
        long current = session.prepare(item("episode-2", 0));

        engine.emit(EngineEvent.state(old, EnginePhase.ENDED, 900_000, 900_000));
        assertEquals(current, session.state().getSessionId());
        assertEquals(PlaybackPhase.PREPARING, session.state().getPhase());

        engine.emit(EngineEvent.state(current, EnginePhase.READY, 0, 1_000_000));
        assertEquals(PlaybackPhase.READY, session.state().getPhase());
    }

    @Test
    public void seekUpdatesAuthoritativePositionWithoutWaitingForEngineCallback() {
        FakeEngine engine = new FakeEngine();
        DefaultPlayerSession session = new DefaultPlayerSession(engine);
        long id = session.prepare(item("episode-1", 0));
        engine.emit(EngineEvent.state(id, EnginePhase.READY, 120_000, 900_000));

        session.seekTo(480_000);

        assertEquals(480_000, engine.seekPosition);
        assertEquals(480_000, session.state().getPosition());
        assertEquals(PlaybackPhase.READY, session.state().getPhase());
    }

    @Test
    public void releaseIsTerminal() {
        FakeEngine engine = new FakeEngine();
        DefaultPlayerSession session = new DefaultPlayerSession(engine);
        session.prepare(item("episode-1", 0));
        session.release();

        assertEquals(PlaybackPhase.RELEASED, session.state().getPhase());
        assertEquals(1, engine.releaseCount);
    }

    @Test
    public void prepareFailurePublishesErrorInsteadOfLeavingPreparing() {
        FakeEngine engine = new FakeEngine();
        engine.prepareError = new IllegalStateException("prepare failed");
        DefaultPlayerSession session = new DefaultPlayerSession(engine);

        try {
            session.prepare(item("episode-1", 0));
            fail("Expected prepare failure");
        } catch (IllegalStateException expected) {
            assertEquals("prepare failed", expected.getMessage());
        }

        assertEquals(PlaybackPhase.ERROR, session.state().getPhase());
        assertEquals("prepare failed", session.state().getError().getMessage());
    }

    @Test
    public void engineStopEventIsNotPublishedTwice() {
        FakeEngine engine = new FakeEngine();
        DefaultPlayerSession session = new DefaultPlayerSession(engine);
        List<PlaybackPhase> phases = new ArrayList<>();
        session.observe(state -> phases.add(state.getPhase()));
        long id = session.prepare(item("episode-1", 0));
        engine.emit(EngineEvent.state(id, EnginePhase.READY, 10, 100));
        engine.emitStop = true;

        session.stop();

        assertEquals(1, phases.stream().filter(phase -> phase == PlaybackPhase.STOPPED).count());
    }

    @Test
    public void stopFailureStillPublishesStopped() {
        FakeEngine engine = new FakeEngine();
        engine.stopError = new IllegalStateException("already idle");
        DefaultPlayerSession session = new DefaultPlayerSession(engine);

        session.stop();

        assertEquals(PlaybackPhase.STOPPED, session.state().getPhase());
    }

    @Test
    public void releaseFailureStillPublishesTerminalState() {
        FakeEngine engine = new FakeEngine();
        engine.releaseError = new IllegalStateException("release failed");
        DefaultPlayerSession session = new DefaultPlayerSession(engine);

        session.release();

        assertEquals(PlaybackPhase.RELEASED, session.state().getPhase());
    }

    @Test
    public void playbackItemDropsNullSubtitlesAtBoundary() {
        PlaybackItem item = new PlaybackItem("episode-1", "https://example.com/video.m3u8", Collections.emptyMap(),
                "m3u8", null, Arrays.asList(null, null), 0, false);

        assertEquals(Collections.emptyList(), item.getSubtitles());
    }

    @Test
    public void stoppedSessionIgnoresLateEngineEvents() {
        FakeEngine engine = new FakeEngine();
        DefaultPlayerSession session = new DefaultPlayerSession(engine);
        long id = session.prepare(item("episode-1", 0));
        session.stop();
        engine.emit(EngineEvent.state(id, EnginePhase.READY, 10, 100));
        engine.emit(EngineEvent.state(id, EnginePhase.ENDED, 100, 100));
        assertEquals(PlaybackPhase.STOPPED, session.state().getPhase());
    }

    @Test
    public void observerReleasePreventsPreparingReleasedEngine() {
        FakeEngine engine = new FakeEngine();
        DefaultPlayerSession session = new DefaultPlayerSession(engine);
        session.observe(state -> {
            if (state.getPhase() == PlaybackPhase.PREPARING) session.release();
        });
        List<PlaybackPhase> phases = new ArrayList<>();
        session.observe(state -> phases.add(state.getPhase()));
        session.prepare(item("episode-1", 0));
        assertEquals(null, engine.request);
        assertEquals(List.of(PlaybackPhase.IDLE, PlaybackPhase.RELEASED), phases);
    }

    @Test
    public void observerSwitchDoesNotPrepareOldItemAfterNewItem() {
        FakeEngine engine = new FakeEngine();
        DefaultPlayerSession session = new DefaultPlayerSession(engine);
        session.observe(state -> {
            if (state.getItem() != null && state.getItem().getIdentity().equals("episode-1")) {
                session.prepare(item("episode-2", 0));
            }
        });
        session.prepare(item("episode-1", 0));
        assertEquals(session.state().getSessionId(), engine.request.getSessionId());
        assertEquals("episode-2", session.state().getItem().getIdentity());
    }

    private PlaybackItem item(String identity, long position) {
        return new PlaybackItem(identity, "https://example.com/video.m3u8", Collections.emptyMap(), "m3u8", null, Collections.emptyList(), position, false);
    }

    private static final class FakeEngine implements PlayerEngine {

        private EngineRequest request;
        private Listener listener;
        private int releaseCount;
        private long seekPosition;
        private RuntimeException prepareError;
        private RuntimeException releaseError;
        private RuntimeException stopError;
        private boolean emitStop;

        @Override
        public void prepare(EngineRequest request, Listener listener) {
            this.request = request;
            this.listener = listener;
            if (prepareError != null) throw prepareError;
        }

        void emit(EngineEvent event) {
            listener.onEngineEvent(event);
        }

        @Override public void play() {}
        @Override public void pause() {}
        @Override public void seekTo(long position) { seekPosition = position; }
        @Override public void stop() {
            if (stopError != null) throw stopError;
            if (emitStop) listener.onEngineEvent(EngineEvent.state(request.getSessionId(), EnginePhase.STOPPED, 0, -1));
        }
        @Override public void setSpeed(float speed) {}
        @Override public void setTrack(Track track) {}
        @Override public EngineSnapshot snapshot() { return EngineSnapshot.EMPTY; }
        @Override public void release() { releaseCount++; if (releaseError != null) throw releaseError; }
    }

    @Test public void pausedPrepareDoesNotRequestAutoplayFromTheEngine() {
        FakeEngine engine = new FakeEngine();
        DefaultPlayerSession session = new DefaultPlayerSession(engine);
        session.prepare(item("episode-1", 120_000).withPlayWhenReady(false));
        org.junit.Assert.assertFalse(engine.request.isPlayWhenReady());
        assertEquals(120_000, engine.request.getStartPosition());
    }

}
