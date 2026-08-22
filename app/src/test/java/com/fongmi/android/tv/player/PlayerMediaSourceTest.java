package com.fongmi.android.tv.player;

import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;

public class PlayerMediaSourceTest {

    @Test
    public void sourceCopiesMutableInputsAndNormalizesPosition() {
        Map<String, String> headers = new HashMap<>();
        headers.put("User-Agent", "test");
        PlayerMediaSource source = new PlayerMediaSource(headers, "url", null, null, new ArrayList<>(), -1, false);

        headers.put("Referer", "changed-later");
        assertEquals(1, source.getHeaders().size());
        assertEquals(0, source.getStartPosition());
    }
}
