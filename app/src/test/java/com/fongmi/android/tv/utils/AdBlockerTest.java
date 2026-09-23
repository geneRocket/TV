package com.fongmi.android.tv.utils;

import org.junit.Test;
import static org.junit.Assert.*;

public class AdBlockerTest {
    @Test public void normalPlaybackParametersAreNotAdResources() {
        assertFalse(AdBlocker.hasDefaultAdSignal("https://cdn.example/movie.m3u8?ad=0"));
        assertFalse(AdBlocker.hasDefaultAdSignal("https://cdn.example/movie.m3u8?ads=false"));
        assertFalse(AdBlocker.hasDefaultAdSignal("https://cdn.example/movie.m3u8?token=opaque-ad-signature"));
        assertFalse(AdBlocker.hasDefaultAdSignal("https://cdn.example/movie.m3u8?title=advertisement"));
        assertFalse(AdBlocker.hasDefaultAdSignal("https://cdn.example/movie.m3u8#label=ad"));
        assertFalse(AdBlocker.hasDefaultAdSignal("https://cdn.example/movie.m3u8#?ad=1"));
    }

    @Test public void explicitAndEncodedAdResourcesAreStillBlocked() {
        assertTrue(AdBlocker.hasDefaultAdSignal("https://cdn.example/ads/clip.m3u8"));
        assertTrue(AdBlocker.hasDefaultAdSignal("https://cdn.example/%61ds/clip.m3u8"));
        assertTrue(AdBlocker.hasDefaultAdSignal("https://cdn.example/clip.m3u8?ad=1"));
        assertTrue(AdBlocker.hasDefaultAdSignal("https://cdn.example/clip.m3u8?placement=pre%72oll"));
    }
}
