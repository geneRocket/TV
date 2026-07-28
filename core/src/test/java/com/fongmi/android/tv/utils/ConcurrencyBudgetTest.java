package com.fongmi.android.tv.utils;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class ConcurrencyBudgetTest {

    @Test
    public void lowCoreDevicesKeepMinimumResponsiveness() {
        ConcurrencyBudget budget = ConcurrencyBudget.forProcessors(1);
        assertEquals(4, budget.general);
        assertEquals(4, budget.config);
        assertEquals(2, budget.loader);
        assertEquals(4, budget.search);
        assertEquals(4, budget.parse);
        assertEquals(2, budget.preload);
        assertEquals(8, budget.network);
        assertEquals(4, budget.networkPerHost);
        assertEquals(8, budget.idleConnections);
    }

    @Test
    public void commonDevicesScaleWithAvailableCores() {
        ConcurrencyBudget budget = ConcurrencyBudget.forProcessors(4);
        assertEquals(8, budget.general);
        assertEquals(4, budget.config);
        assertEquals(4, budget.loader);
        assertEquals(8, budget.search);
        assertEquals(8, budget.parse);
        assertEquals(4, budget.preload);
        assertEquals(16, budget.network);
        assertEquals(8, budget.networkPerHost);
        assertEquals(8, budget.idleConnections);
    }

    @Test
    public void highCoreDevicesRemainBounded() {
        ConcurrencyBudget budget = ConcurrencyBudget.forProcessors(64);
        assertEquals(16, budget.general);
        assertEquals(8, budget.config);
        assertEquals(8, budget.loader);
        assertEquals(16, budget.search);
        assertEquals(16, budget.parse);
        assertEquals(4, budget.preload);
        assertEquals(32, budget.network);
        assertEquals(8, budget.networkPerHost);
        assertEquals(16, budget.idleConnections);
    }
}
