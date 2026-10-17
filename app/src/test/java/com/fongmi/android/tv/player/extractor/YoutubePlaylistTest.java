package com.fongmi.android.tv.player.extractor;

import com.fongmi.android.tv.bean.Episode;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import org.schabi.newpipe.extractor.stream.StreamType;
import org.junit.Test;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import static org.junit.Assert.*;

public class YoutubePlaylistTest {
    @Test public void livePlaylistEntriesDoNotNeedAKnownDuration() {
        List<Episode> result = new ArrayList<>();
        Youtube.Parser.addItems(result, Arrays.asList(item("Live", "live", StreamType.LIVE_STREAM, -1)));
        assertEquals(1, result.size());
        assertEquals("https://www.youtube.com/watch?v=live", result.get(0).getUrl());
    }

    @Test public void unknownDurationEntriesKeepTheirPositionAcrossPages() {
        List<Episode> result = new ArrayList<>();
        Youtube.Parser.addItems(result, Arrays.asList(item("First", "first", StreamType.VIDEO_STREAM, 60),
                item("Unknown", "unknown", StreamType.VIDEO_STREAM, -1)));
        Youtube.Parser.addItems(result, Arrays.asList(item("Last", "last", StreamType.VIDEO_STREAM, 120)));
        assertEquals(Arrays.asList("First", "Unknown", "Last"), result.stream().map(Episode::getName).collect(Collectors.toList()));
    }

    @Test public void ordinaryVideoMetadataStillBecomesPlayableEpisodes() {
        List<Episode> result = new ArrayList<>();
        Youtube.Parser.addItems(result, Arrays.asList(item("Movie", "movie", StreamType.VIDEO_STREAM, 60)));
        assertEquals(1, result.size()); assertEquals("Movie", result.get(0).getName());
        assertEquals("https://www.youtube.com/watch?v=movie", result.get(0).getUrl());
    }

    private StreamInfoItem item(String name, String id, StreamType type, long duration) {
        StreamInfoItem result = new StreamInfoItem(0, "https://www.youtube.com/watch?v=" + id, name, type);
        result.setDuration(duration); return result;
    }
}
