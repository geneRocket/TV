package com.fongmi.android.tv.player.engine;

import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;

public class EngineRequestTest {

    @Test
    public void requestCopiesInputsAndNormalizesStartPosition() {
        Map<String, String> headers = new HashMap<>();
        headers.put("User-Agent", "test");
        EngineRequest request = new EngineRequest(1, "https://example.com/video", headers, null, null, new ArrayList<>(), -1, false);
        headers.put("Referer", "late-change");

        assertEquals(1, request.getHeaders().size());
        assertEquals(0, request.getStartPosition());
    }
}
