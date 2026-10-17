package com.fongmi.android.tv.viewmodel;

import org.junit.Test;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.assertEquals;

public class SubtitleViewModelTest {
    @Test
    public void lateRetryCannotReplaceANewerUserSearch() {
        AtomicInteger sequence = new AtomicInteger();
        int old = SubtitleViewModel.nextSearchRequest(sequence, -1);
        int current = SubtitleViewModel.nextSearchRequest(sequence, -1);

        assertEquals(-1, SubtitleViewModel.nextSearchRequest(sequence, old));
        assertEquals(current, sequence.get());
    }

    @Test
    public void currentSearchCanRetryOnlyOnceWithItsOriginalRequestNumber() {
        AtomicInteger sequence = new AtomicInteger();
        int initial = SubtitleViewModel.nextSearchRequest(sequence, -1);
        assertEquals(initial + 1, SubtitleViewModel.nextSearchRequest(sequence, initial));
        assertEquals(-1, SubtitleViewModel.nextSearchRequest(sequence, initial));
        assertEquals(initial + 1, sequence.get());
    }
}
