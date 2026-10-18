package com.fongmi.android.tv.ui.adapter;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class WordAdapterLimitTest {
    @Test public void repeatedAppendsCannotGrowBeyondSuggestionLimit() {
        WordAdapter adapter = TvAdapterIdentityTest.words();
        List<String> initial = new ArrayList<>();
        for (int i = 0; i < 20; i++) initial.add("word" + i);
        adapter.addAll(initial);
        adapter.appendAll(Arrays.asList("extra1", "extra2"));
        assertEquals(20, adapter.getItemCount());
        adapter.appendAll(Arrays.asList("extra3"));
        assertEquals(20, adapter.getItemCount());
    }

    @Test public void appendSkipsDuplicatesAndUsesRemainingCapacity() {
        WordAdapter adapter = TvAdapterIdentityTest.words();
        List<String> initial = new ArrayList<>();
        for (int i = 0; i < 19; i++) initial.add("word" + i);
        adapter.addAll(initial);
        adapter.appendAll(Arrays.asList("word0", " ", null, "extra1", "extra2"));
        assertEquals(20, adapter.getItemCount());
    }
}
