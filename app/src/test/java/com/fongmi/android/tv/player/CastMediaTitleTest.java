package com.fongmi.android.tv.player;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class CastMediaTitleTest {
    @Test public void coldProcessDoesNotRequireClingDriverInitialization() {
        String previous = System.getProperty("org.xml.sax.driver");
        try {
            System.setProperty("org.xml.sax.driver", "missing.ClingDriver");
            assertEquals("media-id", CastMediaTitle.read("uri", "<DIDL-Lite xmlns='urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/'><item id='media-id'/></DIDL-Lite>"));
        } finally {
            if (previous == null) System.clearProperty("org.xml.sax.driver");
            else System.setProperty("org.xml.sax.driver", previous);
        }
    }

    @Test public void absentOrInvalidMetadataFallsBackToUri() {
        assertEquals("uri", CastMediaTitle.read("uri", ""));
        assertEquals("uri", CastMediaTitle.read("uri", null));
        assertEquals("uri", CastMediaTitle.read("uri", "not xml"));
        assertEquals("uri", CastMediaTitle.read("uri", "<DIDL-Lite/>"));
    }

    @Test public void firstItemIdAndEmptyIdMatchPreviousDisplayBehavior() {
        assertEquals("one", CastMediaTitle.read("uri", "<root><item id='one'/><item id='two'/></root>"));
        assertEquals("", CastMediaTitle.read("uri", "<root><item id=''/></root>"));
    }
}
