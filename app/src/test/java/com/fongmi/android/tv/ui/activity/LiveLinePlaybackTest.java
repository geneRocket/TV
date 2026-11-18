package com.fongmi.android.tv.ui.activity;

import com.fongmi.android.tv.bean.Channel;
import com.fongmi.android.tv.player.DirectPlaybackFlow;
import com.fongmi.android.tv.player.PlaybackInput;
import com.fongmi.android.tv.player.session.Subscription;

import org.junit.Test;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class LiveLinePlaybackTest {
    @Test public void selectingAnotherLineStartsPlaybackAgain() throws Exception {
        assertStartsPlayback("onLine");
        assertStartsPlayback("prevLine");
    }

    private void assertStartsPlayback(String methodName) throws Exception {
        ClassLoader loader = getClass().getClassLoader();
        DirectPlaybackFlow.Engine engine = (DirectPlaybackFlow.Engine) Proxy.newProxyInstance(loader,
                new Class[]{DirectPlaybackFlow.Engine.class}, (proxy, method, args) -> {
                    if (method.getName().equals("isCurrent")) return true;
                    if (method.getName().equals("observe")) return (Subscription) () -> {};
                    return null;
                });
        DirectPlaybackFlow.Port port = (DirectPlaybackFlow.Port) Proxy.newProxyInstance(loader,
                new Class[]{DirectPlaybackFlow.Port.class}, (proxy, method, args) -> null);
        try (DirectPlaybackFlow flow = new DirectPlaybackFlow(engine, port, Runnable::run)) {
            flow.dispatch(PlaybackInput.action(PlaybackInput.Type.PAUSE));
            assertFalse(flow.playWhenReady(true));
            LiveActivity activity = allocateActivity();
            setField(activity, "mPlaybackFlow", flow);
            RuntimeException stopBeforeRendering = new RuntimeException();
            Channel channel = new Channel() {
                @Override public void setLine(int line) {
                    assertTrue("The new stream needs its own autoplay intent", flow.playWhenReady(true));
                    throw stopBeforeRendering;
                }
            };
            channel.addUrls("https://example.com/a", "https://example.com/b");
            setField(activity, "mChannel", channel);
            Method select = LiveActivity.class.getDeclaredMethod(methodName);
            select.setAccessible(true);
            try {
                select.invoke(activity);
                fail("The selected line did not change");
            } catch (InvocationTargetException error) {
                if (error.getCause() instanceof AssertionError) throw (AssertionError) error.getCause();
                assertSame(stopBeforeRendering, error.getCause());
            }
        }
    }

    private static LiveActivity allocateActivity() throws Exception {
        Class<?> unsafe = Class.forName("sun.misc.Unsafe");
        Field instance = unsafe.getDeclaredField("theUnsafe");
        instance.setAccessible(true);
        return (LiveActivity) unsafe.getMethod("allocateInstance", Class.class).invoke(instance.get(null), LiveActivity.class);
    }

    private static void setField(LiveActivity activity, String name, Object value) throws Exception {
        Field field = LiveActivity.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(activity, value);
    }
}
