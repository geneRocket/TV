package com.fongmi.android.tv.player.vod;

import static org.junit.Assert.assertSame;

import com.fongmi.android.tv.bean.History;

import org.junit.Test;

public class VodHistoryControllerTest {

    @Test
    public void progressUpdateDuringLoadDoesNotDiscardLoadedHistory() {
        VodHistoryController controller = new VodHistoryController();
        History provisional = new History();
        History loaded = new History();

        controller.beginLoad();
        controller.updateProgress(provisional, true, 120_000, 1_800_000);

        assertSame(loaded, controller.applyLoaded(provisional, loaded));
    }
}
