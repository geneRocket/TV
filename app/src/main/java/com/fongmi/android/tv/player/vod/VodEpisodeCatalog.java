package com.fongmi.android.tv.player.vod;

import com.fongmi.android.tv.bean.Episode;
import com.fongmi.android.tv.bean.History;

import java.util.ArrayList;
import java.util.List;

/** Converts the displayed episode order and scoped history into the playback model. */
public final class VodEpisodeCatalog {
    private VodEpisodeCatalog() {}

    public static List<VodEpisode> episodes(String site, String flag, List<Episode> items) {
        List<VodEpisode> result = new ArrayList<>(items.size());
        for (Episode item : items) result.add(new VodEpisode(VodEpisode.resumeIdentity(site, flag, item.getName()),
                site, flag, item.getName(), item.getUrl()));
        return result;
    }

    public static void bind(VodPlaybackCoordinator coordinator, String site, String flag, List<Episode> items, History history) {
        coordinator.setEpisodes(episodes(site, flag, items));
        String identity = history == null ? "" : VodEpisode.resumeIdentity(site, history.getVodFlag(), history.getVodRemarks());
        coordinator.setResumePosition(identity, history == null ? 0 : history.getPosition());
        coordinator.setOpening(history == null ? 0 : history.getOpening());
    }
}
