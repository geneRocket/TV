package com.fongmi.android.tv.bean;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class HistoryTest {

    @Test
    public void historyKeyUsesVodIdWhenAvailable() {
        assertEquals("siteA@@@123@@@7", History.historyKey("siteA", "123", "Movie Name", 7));
    }

    @Test
    public void historyKeyFallsBackToNormalizedNameWhenVodIdIsEmpty() {
        assertEquals("siteA@@@movie name@@@7", History.historyKey("siteA", "", "Movie Name", 7));
    }

    @Test
    public void historyKeyStoresRawSiteKeyAndConfigId() {
        assertEquals("siteA@@@123@@@7", History.historyKey("7@siteA", "123", "Movie Name", 7));
    }

    @Test
    public void vodIdIsEmptyWhenHistoryKeyUsesNameFallback() {
        History history = new History();
        history.setKey(History.historyKey("siteA", "", "Movie Name", 7));
        history.setVodName("Movie Name");

        assertEquals("", history.getVodId());
    }
}
