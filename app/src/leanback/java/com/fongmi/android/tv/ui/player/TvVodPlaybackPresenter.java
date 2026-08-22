package com.fongmi.android.tv.ui.player;

import com.fongmi.android.tv.player.session.Subscription;
import com.fongmi.android.tv.player.vod.VodPlaybackCoordinator;
import com.fongmi.android.tv.player.vod.VodPlaybackPhase;
import com.fongmi.android.tv.player.vod.VodPlaybackState;
import com.fongmi.android.tv.player.vod.StartPolicy;

/** Converts domain playback state into one immutable TV screen state. */
public final class TvVodPlaybackPresenter implements AutoCloseable {

    public interface View {
        void render(TvVodViewState state);
    }

    private final Subscription subscription;
    private final VodPlaybackCoordinator coordinator;

    public TvVodPlaybackPresenter(VodPlaybackCoordinator coordinator, View view) {
        this.coordinator = coordinator;
        subscription = coordinator.observe(state -> view.render(map(state)));
    }

    public void playEpisode(int index, boolean replay) {
        coordinator.play(index, replay ? StartPolicy.FRESH : StartPolicy.RESUME_SAVED);
    }

    public boolean nextEpisode() { return coordinator.next(); }

    public boolean previousEpisode() { return coordinator.previous(); }

    public void replayEpisode() { coordinator.replay(); }

    public void reload() { coordinator.reload(); }

    public void play() { coordinator.play(); }

    public void pause() { coordinator.pause(); }

    public void seekTo(long position) { coordinator.seekTo(position); }

    public void stop() { coordinator.stop(); }

    private TvVodViewState map(VodPlaybackState state) {
        VodPlaybackPhase phase = state.getPhase();
        boolean progress = phase == VodPlaybackPhase.RESOLVING || phase == VodPlaybackPhase.PREPARING || phase == VodPlaybackPhase.BUFFERING;
        boolean controls = phase == VodPlaybackPhase.READY || phase == VodPlaybackPhase.PAUSED || phase == VodPlaybackPhase.BUFFERING;
        String name = state.getEpisode() == null ? "" : state.getEpisode().getName();
        return new TvVodViewState(phase, state.getEpisodeIndex(), name, state.getPosition(), state.getDuration(), progress, controls, state.getError());
    }

    @Override public void close() {
        subscription.cancel();
    }
}
