import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.GestureDetector;
import android.view.MotionEvent;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.LinkedHashMap;
import java.util.Map;

/** Runs against a Debug APK via app_process; no application installation is needed. */
public final class GestureDetectorSmoke {
    private static final Map<String, Integer> callbacks = new LinkedHashMap<>();

    public static void main(String[] args) throws Exception {
        Looper.prepare();
        boolean tv = args.length > 0 && args[0].equals("tv");
        if (tv) {
            Class<?> app = Class.forName("com.fongmi.android.tv.App");
            Object instance = allocate(app);
            set(app, instance, "handler", new Handler(Looper.myLooper()));
            set(app, null, "instance", instance);
        }
        String[] names = tv ? new String[]{"CustomKeyDownVod", "CustomKeyDownLive", "CustomKeyDownCast"}
                : new String[]{"CustomKeyDownVod", "CustomKeyDownLive"};
        for (String name : names) {
            queueThenRelease(name, false);
            queueThenRelease(name, true);
        }
        if (tv) {
            releaseFeedback("CustomKeyDownCast", new String[]{"changeSpeed"}, new String[]{"onSpeedEnd"});
            releaseFeedback("CustomKeyDownVod", new String[]{"changeBright", "changeVolume"}, new String[]{"onBrightEnd", "onVolumeEnd"});
        }
        new Handler(Looper.myLooper()).postDelayed(() -> {
            int failures = 0;
            for (Map.Entry<String, Integer> entry : callbacks.entrySet()) {
                boolean passed = entry.getValue() == 0;
                System.out.println((passed ? "PASS " : "FAIL ") + entry.getKey() + " delta=" + entry.getValue());
                if (!passed) failures++;
            }
            System.out.println("RESULT tests=" + callbacks.size() + " failures=" + failures);
            System.exit(failures == 0 ? 0 : 1);
        }, Math.max(1200, android.view.ViewConfiguration.getLongPressTimeout() + 500));
        Looper.loop();
    }

    private static void queueThenRelease(String name, boolean tap) throws Exception {
        String key = name + (tap ? " pending tap" : " pending long press");
        callbacks.put(key, 0);
        GestureDetector detector = new GestureDetector(new GestureDetector.SimpleOnGestureListener() {
            @Override public boolean onDown(MotionEvent event) { return true; }
            @Override public void onLongPress(MotionEvent event) { callbacks.put(key, callbacks.get(key) + 1); }
            @Override public boolean onSingleTapConfirmed(MotionEvent event) {
                callbacks.put(key, callbacks.get(key) + 1);
                return true;
            }
        });
        long now = SystemClock.uptimeMillis();
        MotionEvent down = MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, 200, 200, 0);
        detector.onTouchEvent(down);
        down.recycle();
        if (tap) {
            MotionEvent up = MotionEvent.obtain(now, now + 10, MotionEvent.ACTION_UP, 200, 200, 0);
            detector.onTouchEvent(up);
            up.recycle();
        }
        Class<?> type = Class.forName("com.fongmi.android.tv.ui.custom." + name);
        Object gesture = allocate(type);
        set(type, gesture, "detector", detector);
        for (String field : new String[]{"seekRunnable", "runnable"}) {
            try { set(type, gesture, field, (Runnable) () -> {}); }
            catch (NoSuchFieldException ignored) {}
        }
        try { set(type, gesture, "text", new StringBuilder()); }
        catch (NoSuchFieldException ignored) {}
        try {
            Class<?> seek = Class.forName("com.fongmi.android.tv.player.RemoteSeekAccumulator");
            set(type, gesture, "seek", seek.getConstructor().newInstance());
        } catch (NoSuchFieldException ignored) {}
        type.getMethod("release").invoke(gesture);
    }

    private static Object allocate(Class<?> type) throws Exception {
        Class<?> unsafe = Class.forName("sun.misc.Unsafe");
        Field instance = unsafe.getDeclaredField("theUnsafe");
        instance.setAccessible(true);
        return unsafe.getMethod("allocateInstance", Class.class).invoke(instance.get(null), type);
    }

    private static void releaseFeedback(String name, String[] flags, String[] methods) throws Exception {
        Class<?> type = Class.forName("com.fongmi.android.tv.ui.custom." + name);
        Object gesture = allocate(type);
        Class<?> listener = Class.forName(type.getName() + "$Listener");
        for (String method : methods) callbacks.put(name + " " + method + " exactly once", 1);
        set(type, gesture, "listener", Proxy.newProxyInstance(listener.getClassLoader(), new Class<?>[]{listener}, (p,m,a) -> {
            String key = name + " " + m.getName() + " exactly once";
            if (callbacks.containsKey(key)) callbacks.put(key, callbacks.get(key) - 1);
            return null;
        }));
        set(type, gesture, "seekRunnable", (Runnable) () -> {});
        if (name.equals("CustomKeyDownVod")) {
            Class<?> seek = Class.forName("com.fongmi.android.tv.player.RemoteSeekAccumulator");
            set(type, gesture, "seek", seek.getConstructor().newInstance());
        }
        for (String flag : flags) set(type, gesture, flag, true);
        type.getMethod("release").invoke(gesture);
        type.getMethod("release").invoke(gesture);
    }

    private static void set(Class<?> type, Object object, String name, Object value) throws Exception {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        field.set(object, value);
    }
}
