package com.fongmi.android.tv.utils;

import org.junit.Test;

import static org.junit.Assert.*;

public class PaginationStateTest {

    @Test
    public void emptyFirstPageRemainsFirstAndStops() {
        PaginationState state = PaginationState.complete(1, 0, 0, false);
        assertEquals(1, state.getPage());
        assertFalse(state.canLoadMore());
    }

    @Test
    public void failedPageCanRetryEvenWithoutPageCount() {
        PaginationState state = PaginationState.complete(3, 0, 0, true);
        assertEquals(2, state.getPage());
        assertTrue(state.canLoadMore());
    }

    @Test
    public void unknownPageCountEndsOnlyWhenResponseIsEmpty() {
        assertTrue(PaginationState.complete(2, 0, 20, false).canLoadMore());
        PaginationState state = PaginationState.complete(3, 0, 0, false);
        assertEquals(2, state.getPage());
        assertFalse(state.canLoadMore());
    }

    @Test
    public void knownLastPageStopsAfterNonemptyResponse() {
        assertTrue(PaginationState.complete(1, 2, 20, false).canLoadMore());
        assertFalse(PaginationState.complete(2, 2, 20, false).canLoadMore());
    }
}
