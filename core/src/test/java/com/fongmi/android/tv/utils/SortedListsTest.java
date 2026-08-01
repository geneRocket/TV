package com.fongmi.android.tv.utils;

import org.junit.Test;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class SortedListsTest {

    @Test
    public void mergesAlreadySortedInputsInLinearOrder() {
        assertEquals(List.of(1, 2, 3, 4, 5, 6), SortedLists.merge(List.of(1, 3, 5), List.of(2, 4, 6), Comparator.naturalOrder()));
    }

    @Test
    public void keepsLeftSideStableWhenValuesCompareEqual() {
        Item left = new Item(1, "left");
        Item right = new Item(1, "right");
        assertEquals(List.of(left, right), SortedLists.merge(List.of(left), List.of(right), Comparator.comparingInt(item -> item.rank)));
    }

    @Test
    public void handlesEmptyInput() {
        assertEquals(List.of(1, 2), SortedLists.merge(Collections.emptyList(), List.of(1, 2), Comparator.naturalOrder()));
    }

    private static final class Item {
        private final int rank;
        private final String name;

        private Item(int rank, String name) {
            this.rank = rank;
            this.name = name;
        }
    }
}
