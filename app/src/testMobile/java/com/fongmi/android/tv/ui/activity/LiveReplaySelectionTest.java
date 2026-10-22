package com.fongmi.android.tv.ui.activity;

import com.fongmi.android.tv.bean.EpgData;
import com.fongmi.android.tv.player.DirectPlaybackFlow;
import com.fongmi.android.tv.player.PlaybackInput;
import com.fongmi.android.tv.player.session.Subscription;
import com.fongmi.android.tv.player.live.LiveDataConnection;
import com.fongmi.android.tv.bean.Channel;
import com.fongmi.android.tv.bean.Live;
import org.junit.Test;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import static org.junit.Assert.assertFalse;

public class LiveReplaySelectionTest {
    @Test public void rejectedFutureProgramDoesNotDiscardUserPause() throws Exception {
        DirectPlaybackFlow.Engine engine = (DirectPlaybackFlow.Engine) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class[]{DirectPlaybackFlow.Engine.class}, (p, m, a) -> {
                    if (m.getName().equals("isCurrent")) return true;
                    if (m.getName().equals("observe")) return (Subscription) () -> {};
                    return null;
                });
        DirectPlaybackFlow.Port port = (DirectPlaybackFlow.Port) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class[]{DirectPlaybackFlow.Port.class}, (p, m, a) -> null);
        DirectPlaybackFlow flow = new DirectPlaybackFlow(engine, port, Runnable::run);
        flow.dispatch(PlaybackInput.action(PlaybackInput.Type.PAUSE));
        Class<?> unsafe = Class.forName("sun.misc.Unsafe");
        Field instance = unsafe.getDeclaredField("theUnsafe");
        instance.setAccessible(true);
        LiveActivity activity = (LiveActivity) unsafe.getMethod("allocateInstance", Class.class).invoke(instance.get(null), LiveActivity.class);
        Field field = LiveActivity.class.getDeclaredField("mPlaybackFlow");
        field.setAccessible(true);
        field.set(activity, flow);
        LiveDataConnection connection = new LiveDataConnection(null, null, false, new LiveDataConnection.Port() {
            @Override public Channel channel() { return new Channel(); }
            @Override public void play(Channel channel) { throw new AssertionError("future program must not play"); }
            @Override public void renderEpg() {}
            @Override public void renderLive(Live live) {}
        });
        Field data = LiveActivity.class.getDeclaredField("mLiveData");
        data.setAccessible(true);
        data.set(activity, connection);
        EpgData future = new EpgData();
        future.setStartTime(Long.MAX_VALUE);
        activity.onItemClick(future);
        assertFalse(flow.playWhenReady(true));
    }
}
