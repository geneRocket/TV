package com.fongmi.android.tv.ui.custom;

import com.fongmi.android.tv.impl.ParseCallback;

import org.junit.Test;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

public class CustomWebViewStopTest {
    private CustomWebView create(AtomicInteger failures) throws Exception {
        Class<?> unsafeClass = Class.forName("sun.misc.Unsafe");
        Field field = unsafeClass.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        CustomWebView view = (CustomWebView) unsafeClass.getMethod("allocateInstance", Class.class)
                .invoke(field.get(null), CustomWebView.class);
        field = CustomWebView.class.getDeclaredField("callback");
        field.setAccessible(true);
        field.set(view, new ParseCallback() {
            @Override public void onParseSuccess(Map<String, String> headers, String url, String from) {
                fail("Stopping must not report success");
            }
            @Override public void onParseError() { failures.incrementAndGet(); }
        });
        return view;
    }

    private Object callback(CustomWebView view) throws Exception {
        Field field = CustomWebView.class.getDeclaredField("callback");
        field.setAccessible(true);
        return field.get(view);
    }

    @Test public void timeoutReportsFailureOnceAndReleasesCallback() throws Exception {
        AtomicInteger failures = new AtomicInteger();
        AtomicInteger cleanups = new AtomicInteger();
        CustomWebView view = create(failures);
        view.stop(true, cleanups::incrementAndGet);
        view.stop(true, cleanups::incrementAndGet);
        assertEquals(1, failures.get());
        assertEquals(1, cleanups.get());
        assertNull(callback(view));
    }

    @Test public void explicitCancellationDoesNotReportFailure() throws Exception {
        AtomicInteger failures = new AtomicInteger();
        CustomWebView view = create(failures);
        view.stop(false, () -> {});
        view.stop(true, () -> fail("Cancellation is terminal"));
        assertEquals(0, failures.get());
        assertNull(callback(view));
    }

    @Test public void cleanupReentryCannotConsumeTimeoutNotification() throws Exception {
        AtomicInteger failures = new AtomicInteger();
        CustomWebView view = create(failures);
        view.stop(true, () -> view.stop(false, () -> fail("Cleanup must run only once")));
        assertEquals(1, failures.get());
        assertNull(callback(view));
    }

    @Test public void cleanupFailureStillNotifiesAndReleasesCallback() throws Exception {
        AtomicInteger failures = new AtomicInteger();
        CustomWebView view = create(failures);
        RuntimeException error = new IllegalStateException("cleanup failed");
        try {
            view.stop(true, () -> { throw error; });
            fail("Cleanup error must propagate");
        } catch (RuntimeException caught) {
            assertSame(error, caught);
        }
        assertEquals(1, failures.get());
        assertNull(callback(view));
    }
}
