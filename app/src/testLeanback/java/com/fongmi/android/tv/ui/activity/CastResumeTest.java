package com.fongmi.android.tv.ui.activity;

import android.content.Intent;
import com.fongmi.android.tv.utils.CallbackScope;
import com.fongmi.android.tv.utils.TaskScheduler;

import org.junit.Test;

import java.lang.reflect.Field;

public class CastResumeTest {
    @Test public void foregroundIntentWithoutCastActionDoesNotRestartSource() throws Exception {
        Class<?> unsafe = Class.forName("sun.misc.Unsafe");
        Field field = unsafe.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        TestCastActivity activity = (TestCastActivity) unsafe.getMethod("allocateInstance", Class.class)
                .invoke(field.get(null), TestCastActivity.class);
        Field callbacks = CastActivity.class.getDeclaredField("mUiCallbacks");
        callbacks.setAccessible(true);
        callbacks.set(activity, new CallbackScope(new TaskScheduler() {
            @Override public void post(Runnable task, long delay) {}
            @Override public void remove(Runnable task) {}
        }));
        java.lang.reflect.Method apply = CastActivity.class.getDeclaredMethod("applyCastIntent", Intent.class);
        apply.setAccessible(true);
        apply.invoke(activity, new Intent() {
            @Override public boolean hasExtra(String key) { return false; }
        });
    }

    public static final class TestCastActivity extends CastActivity {
        @Override public Intent getIntent() { throw new AssertionError("foreground-only intent must not reload the old URI"); }
    }
}
