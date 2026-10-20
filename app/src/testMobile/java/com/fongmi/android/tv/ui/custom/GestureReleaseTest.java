package com.fongmi.android.tv.ui.custom;

import org.junit.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class GestureReleaseTest {

    @Test
    public void vodReleaseEndsAllTransientGesturesOnce() throws Exception {
        verifyRelease(CustomKeyDownVod.class, false);
    }

    @Test
    public void liveReleaseCancelsSeekAndEndsAllTransientGesturesOnce() throws Exception {
        verifyRelease(CustomKeyDownLive.class, false);
    }

    @Test
    public void lockingVodEndsTransientGestures() throws Exception {
        verifyRelease(CustomKeyDownVod.class, true);
    }

    @Test
    public void lockingLiveEndsTransientGestures() throws Exception {
        verifyRelease(CustomKeyDownLive.class, true);
    }

    private void verifyRelease(Class<?> type, boolean lock) throws Exception {
        Class<?> unsafeClass = Class.forName("sun.misc.Unsafe");
        Field instance = unsafeClass.getDeclaredField("theUnsafe");
        instance.setAccessible(true);
        Object gesture = unsafeClass.getMethod("allocateInstance", Class.class).invoke(instance.get(null), type);
        Map<String, Integer> calls = new HashMap<>();
        Class<?> listener = Class.forName(type.getName() + "$Listener");
        field(type, "listener").set(gesture, Proxy.newProxyInstance(listener.getClassLoader(), new Class<?>[]{listener},
                (proxy, method, args) -> { calls.merge(method.getName(), 1, Integer::sum); return null; }));
        for (String flag : new String[]{"changeTime", "changeSpeed", "changeBright", "changeVolume"}) {
            field(type, flag).setBoolean(gesture, true);
        }

        for (int i = 0; i < 2; i++) {
            if (lock) type.getMethod("setLock", boolean.class).invoke(gesture, true);
            else type.getMethod("release").invoke(gesture);
        }

        for (String callback : new String[]{"onSeekCancel", "onSpeedEnd", "onBrightEnd", "onVolumeEnd"}) {
            assertEquals(callback, Integer.valueOf(1), calls.get(callback));
        }
        assertFalse(calls.containsKey("onSeekEnd"));
        for (String flag : new String[]{"changeTime", "changeSpeed", "changeBright", "changeVolume"}) {
            assertFalse(flag, field(type, flag).getBoolean(gesture));
        }
    }

    private Field field(Class<?> type, String name) throws Exception {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }
}
