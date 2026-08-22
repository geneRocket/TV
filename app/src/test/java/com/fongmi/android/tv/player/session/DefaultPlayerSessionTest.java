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
import java.util.List;

import static org.junit.Assert.assertEquals;

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
    public void releaseIsTerminal() {
        FakeEngine engine = new FakeEngine();
        DefaultPlayerSession session = new DefaultPlayerSession(engine);
        session.prepare(item("episode-1", 0));
        session.release();

        assertEquals(PlaybackPhase.RELEASED, session.state().getPhase());
        assertEquals(1, engine.releaseCount);
    }

    private PlaybackItem item(String identity, long position) {
        return new PlaybackItem(identity, "https://example.com/video.m3u8", Collections.emptyMap(), "m3u8", null, Collections.emptyList(), position, false);
    }

    private static final class FakeEngine implements PlayerEngine {

        private EngineRequest request;
        private Listener listener;
        private int releaseCount;

        @Override
        public void prepare(EngineRequest request, Listener listener) {
            this.request = request;
            this.listener = listener;
        }

        void emit(EngineEvent event) {
            listener.onEngineEvent(event);
        }

        @Override public void play() {}
        @Override public void pause() {}
        @Override public void seekTo(long position) {}
        @Override public void stop() {}
        @Override public void setSpeed(float speed) {}
        @Override public void setTrack(Track track) {}
        @Override public EngineSnapshot snapshot() { return EngineSnapshot.EMPTY; }
        @Override public void release() { releaseCount++; }
    }
}
