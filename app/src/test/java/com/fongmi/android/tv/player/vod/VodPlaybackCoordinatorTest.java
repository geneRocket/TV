package com.fongmi.android.tv.player.vod;

import com.fongmi.android.tv.bean.Result;
import com.fongmi.android.tv.bean.Track;
import com.fongmi.android.tv.bean.PlaybackProgress;
import com.fongmi.android.tv.player.progress.EpisodeProgressStore;
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
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

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
    public void nextEpisodeUsesItsOwnSavedProgress() {
        ManualResolver resolver = new ManualResolver();
        FakeEngine engine = new FakeEngine();
        VodPlaybackCoordinator coordinator = coordinator(resolver, engine);
        coordinator.setOpening(60_000);

        coordinator.configureProgress("history", 1);
        PlaybackProgress progress = new PlaybackProgress();
        progress.setHistoryKey("history");
        progress.setEpisodeId("episode-1");
        progress.setPosition(600_000);
        ((MemoryProgressStore) coordinatorProgressStore).save(progress);
        PlaybackProgress nextProgress = new PlaybackProgress();
        nextProgress.setHistoryKey("history");
        nextProgress.setEpisodeId("episode-2");
        nextProgress.setPosition(120_000);
        ((MemoryProgressStore) coordinatorProgressStore).save(nextProgress);
        coordinator.play(0, StartPolicy.RESUME_SAVED);
        resolver.resolve(resolver.lastRequestId);
        engine.emit(EngineEvent.state(engine.request.getSessionId(), EnginePhase.READY, 600_000, 1_000_000));
        assertTrue(coordinator.next());
        resolver.resolve(resolver.lastRequestId);

        assertEquals("episode-2", engine.request.getUrl());
        assertEquals(120_000, engine.request.getStartPosition());
    }

    @Test
    public void unplayedNextEpisodeUsesOpeningInsteadOfPreviousProgress() {
        ManualResolver resolver = new ManualResolver();
        FakeEngine engine = new FakeEngine();
        VodPlaybackCoordinator coordinator = coordinator(resolver, engine);
        coordinator.setOpening(60_000);
        coordinator.configureProgress("history", 1);

        coordinator.play(0, StartPolicy.FRESH);
        resolver.resolve(resolver.lastRequestId);
        engine.emit(EngineEvent.state(engine.request.getSessionId(), EnginePhase.READY, 600_000, 1_000_000));
        assertTrue(coordinator.next());
        resolver.resolve(resolver.lastRequestId);

        assertEquals("episode-2", engine.request.getUrl());
        assertEquals(60_000, engine.request.getStartPosition());
    }

    @Test
    public void boundaryDoesNotWrapToAnotherEpisode() {
        ManualResolver resolver = new ManualResolver();
        FakeEngine engine = new FakeEngine();
        VodPlaybackCoordinator coordinator = coordinator(resolver, engine);
        coordinator.play(1, StartPolicy.FRESH);
        assertFalse(coordinator.next());
    }

    private EpisodeProgressStore coordinatorProgressStore;

    private VodPlaybackCoordinator coordinator(ManualResolver resolver, FakeEngine engine) {
        coordinatorProgressStore = new MemoryProgressStore();
        VodPlaybackCoordinator coordinator = new VodPlaybackCoordinator(resolver,
                (episode, result, position) -> new PlaybackItem(episode.getIdentity(), episode.getResolverUrl(), Collections.emptyMap(), null, null, Collections.emptyList(), position, false),
                new DefaultPlayerSession(engine), coordinatorProgressStore);
        coordinator.setEpisodes(Arrays.asList(
                new VodEpisode("episode-1", "site", "flag", "第1集", "episode-1"),
                new VodEpisode("episode-2", "site", "flag", "第2集", "episode-2")), false);
        return coordinator;
    }

    private static final class MemoryProgressStore implements EpisodeProgressStore {

        private final Map<String, PlaybackProgress> values = new LinkedHashMap<>();

        @Override public PlaybackProgress find(String historyKey, String episodeId) { return values.get(historyKey + "|" + episodeId); }
        @Override public void save(PlaybackProgress progress) { values.put(progress.getHistoryKey() + "|" + progress.getEpisodeId(), progress); }
        @Override public void delete(String historyKey) { values.keySet().removeIf(key -> key.startsWith(historyKey + "|")); }
        @Override public void deleteByConfig(int cid) { values.values().removeIf(value -> value.getCid() == cid); }
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

        @Override public void prepare(EngineRequest request, Listener listener) { this.request = request; this.listener = listener; }
        void emit(EngineEvent event) { listener.onEngineEvent(event); }
        @Override public void play() {}
        @Override public void pause() {}
        @Override public void seekTo(long position) {}
        @Override public void stop() {}
        @Override public void setSpeed(float speed) {}
        @Override public void setTrack(Track track) {}
        @Override public EngineSnapshot snapshot() { return EngineSnapshot.EMPTY; }
        @Override public void release() {}
    }
}
