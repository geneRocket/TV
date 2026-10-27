package com.fongmi.android.tv.ui.presenter;

import org.junit.Test;
import java.util.Arrays;
import java.util.Collections;
import static org.junit.Assert.*;

public class EpisodePageLabelsTest {
    @Test public void reversePagesCoverExactlyTheEpisodesOnEachPage() {
        assertEquals(Arrays.asList("120 - 81", "80 - 41", "40 - 1"), EpisodePageLabels.create(120, 40, true));
        assertEquals(Arrays.asList("95 - 56", "55 - 16", "15 - 1"), EpisodePageLabels.create(95, 40, true));
        assertEquals(Arrays.asList("3 - 3", "2 - 2", "1 - 1"), EpisodePageLabels.create(3, 1, true));
    }

    @Test public void forwardPagesAndEmptyListsHaveNoPhantomEpisodes() {
        assertEquals(Arrays.asList("1 - 40", "41 - 80", "81 - 95"), EpisodePageLabels.create(95, 40, false));
        assertEquals(Collections.emptyList(), EpisodePageLabels.create(0, 40, true));
    }
}
