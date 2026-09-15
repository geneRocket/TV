package com.fongmi.android.tv.ui.player;

import static org.junit.Assert.assertSame;

import com.fongmi.android.tv.bean.History;

import org.junit.Test;

public class TvVodHistoryControllerTest {

    @Test
    public void progressUpdateDuringLoadDoesNotDiscardLoadedHistory() {
        TvVodHistoryController controller = new TvVodHistoryController();
        History provisional = new History();
        History loaded = new History();

        controller.beginLoad();
        controller.updateProgress(provisional, true, 120_000, 1_800_000);

        assertSame(loaded, controller.applyLoaded(provisional, loaded));
    }
}
