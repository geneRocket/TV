package com.fongmi.android.tv.player.extractor;

import org.junit.Test;
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeStreamLinkHandlerFactory;

import static org.junit.Assert.*;

public class YoutubePlaybackUrlTest {
    private static final String VIDEO = "dQw4w9WgXcQ";
    private static final String LIST = "PLrEnWoR732-DtKgaDdnPkezM_nDidBU9H";

    @Test public void watchLinkFromPlaylistStillSelectsItsVideo() throws Exception {
        assertVideo("https://www.youtube.com/watch?v=" + VIDEO + "&list=" + LIST + "&index=2");
    }

    @Test public void sharedShortLinkCanCarryAPlaylist() throws Exception {
        assertVideo("https://youtu.be/" + VIDEO + "?list=" + LIST);
    }

    @Test public void ordinaryWatchLinkKeepsItsStartPosition() throws Exception {
        String url = "https://www.youtube.com/watch?v=" + VIDEO + "&t=42";
        assertEquals(url, Youtube.playbackUrl(url));
        assertVideo(url);
    }

    private void assertVideo(String url) throws Exception {
        assertEquals(VIDEO, YoutubeStreamLinkHandlerFactory.getInstance().fromUrl(Youtube.playbackUrl(url)).getId());
    }
}
