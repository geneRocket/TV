package com.fongmi.android.tv.ui.custom;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class PlaybackSubtitleLauncherTest {
    @Test public void subtitleSearchNameUsesVideoThenEpisodeThenActivityTitle() {
        assertEquals("Film", PlaybackSubtitleLauncher.name("Film", "Episode", "Page"));
        assertEquals("Episode", PlaybackSubtitleLauncher.name("", "Episode", "Page"));
        assertEquals("Page", PlaybackSubtitleLauncher.name("", null, "Page"));
        assertEquals("", PlaybackSubtitleLauncher.name(null, null, null));
    }
}
