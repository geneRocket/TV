package com.fongmi.android.tv.utils;

import org.junit.Test;
import java.lang.reflect.Method;
import static org.junit.Assert.*;

public class TimerCallbackTest {
    @Test public void destroyedDialogStopsReceivingTicks() throws Exception {
        Timer timer = new Timer();
        Callback dialog = new Callback();
        timer.setCallback(dialog);
        tick(timer); assertEquals(1, dialog.ticks);
        timer.clearCallback(dialog);
        tick(timer); assertEquals(1, dialog.ticks);
    }

    @Test public void oldDialogCleanupDoesNotUnsubscribeTheReplacement() throws Exception {
        Timer timer = new Timer();
        Callback old = new Callback(), current = new Callback();
        timer.setCallback(old); timer.setCallback(current);
        timer.clearCallback(old); tick(timer);
        assertEquals(0, old.ticks); assertEquals(1, current.ticks);
    }

    private static void tick(Timer timer) throws Exception {
        Method method = Timer.class.getDeclaredMethod("onTick", long.class);
        method.setAccessible(true); method.invoke(timer, 1000L);
    }

    private static class Callback implements Timer.Callback {
        int ticks;
        public void onTick(long tick) { ticks++; }
        public void onFinish() { }
    }
}
