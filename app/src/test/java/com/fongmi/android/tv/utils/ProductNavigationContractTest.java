package com.fongmi.android.tv.utils;

import com.fongmi.android.tv.player.EpisodeNavigator;
import com.fongmi.android.tv.player.PlaybackMarkers;
import com.fongmi.android.tv.player.PlaybackSeek;
import org.junit.Test;
import static org.junit.Assert.*;

public class ProductNavigationContractTest {
    @Test public void categoryAndSearchWithUnknownTotalPagesContinueUntilAnEmptyPage() {
        for (int total : new int[]{-1, 0}) {
            for (int requested = 1; requested <= 5; requested++) {
                PaginationState populated = PaginationState.complete(requested, total, 20, false);
                assertEquals(requested, populated.getPage());
                assertTrue("Unknown count must not hide the next page: " + total, populated.canLoadMore());
                PaginationState empty = PaginationState.complete(requested, total, 0, false);
                assertFalse(empty.canLoadMore());
                assertEquals(Math.max(1, requested - 1), empty.getPage());
            }
        }
    }

    @Test public void failedRequestsRemainRetryableRegardlessOfReportedPageCountOrPartialItems() {
        for (int requested = 1; requested <= 5; requested++) {
            for (int total : new int[]{-1, 0, 1, 5}) {
                for (int count : new int[]{0, 20}) {
                    PaginationState failed = PaginationState.complete(requested, total, count, true);
                    assertTrue(failed.canLoadMore());
                    assertEquals(Math.max(1, requested - 1), failed.getPage());
                }
            }
        }
    }

    @Test public void knownCategoryAndSearchLastPagesStopWithoutDroppingTheLastPage() {
        for (int total = 1; total <= 5; total++) {
            for (int requested = 1; requested <= total + 1; requested++) {
                PaginationState loaded = PaginationState.complete(requested, total, 20, false);
                assertEquals(requested, loaded.getPage());
                assertEquals(requested < total, loaded.canLoadMore());
            }
        }
    }

    @Test public void episodeButtonsStayAtSeriesBoundariesRatherThanJumpToAnotherEpisode() {
        for (int size = 0; size <= 5; size++) {
            if (size == 0) {
                assertEquals(-1, EpisodeNavigator.next(0, size));
                assertEquals(-1, EpisodeNavigator.previous(0, size));
                continue;
            }
            for (int current = 0; current < size; current++) {
                assertEquals(Math.min(current + 1, size - 1), EpisodeNavigator.next(current, size));
                assertEquals(Math.max(current - 1, 0), EpisodeNavigator.previous(current, size));
            }
        }
    }

    @Test public void seekGesturesClampToVideoBoundsButAllowUnknownLengthStreams() {
        for (long duration : new long[]{-1, 0, 60_000, 900_000}) {
            for (long current : new long[]{0, 30_000, 60_000}) {
                for (long offset : new long[]{-120_000, -10_000, 0, 10_000, 120_000}) {
                    long expected = Math.max(0, current + offset);
                    if (duration > 0) expected = Math.min(duration, expected);
                    assertEquals(expected, PlaybackSeek.target(current, duration, offset));
                }
            }
        }
    }

    @Test public void introAndOutroCannotBeSetWithoutAValidTimelineOrToTheWrongHalf() {
        for (long duration : new long[]{-1, 0, 60_000}) {
            for (long current : new long[]{-1, 0, 1, 30_000, 30_001, 60_000, 60_001}) {
                boolean valid = duration > 0 && current >= 0 && current <= duration;
                assertEquals(valid && current <= duration / 2 ? current : PlaybackMarkers.INVALID,
                        PlaybackMarkers.openingAt(current, duration));
                assertEquals(valid && current >= duration / 2 ? duration - current : PlaybackMarkers.INVALID,
                        PlaybackMarkers.endingAt(current, duration));
            }
        }
    }
}
