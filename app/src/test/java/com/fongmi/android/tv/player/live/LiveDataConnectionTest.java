package com.fongmi.android.tv.player.live;

import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Observer;

import com.fongmi.android.tv.bean.*;
import org.junit.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.Assert.*;

public class LiveDataConnectionTest {
    @Test public void cachedSynchronousDeliveryMayCloseConnectionWithoutLeakingRemainingObservers() {
        Fixture f = new Fixture(false);
        f.urls.cached = f.current;
        f.closeOnPlay = true;
        f.data.connect();
        assertEquals(1, f.played);
        assertTrue(f.urls.observers.isEmpty());
        assertTrue(f.epgs.observers.isEmpty());
        assertTrue(f.xmls.observers.isEmpty());
        assertTrue(f.lives.observers.isEmpty());
    }
    @Test public void backgroundSubscriptionsAreOwnedAndRemovedExactlyOnce() {
        Fixture f = new Fixture(true);
        f.data.connect(); f.data.connect();
        assertTrue(f.urls.forever);
        assertTrue(f.epgs.forever);
        assertFalse(f.xmls.forever);
        assertFalse(f.lives.forever);
        assertEquals(1, f.urls.observers.size());
        Observer<? super Channel> late = f.urls.observers.get(0);
        f.data.close(); f.data.close();
        assertTrue(f.data.isClosed());
        assertTrue(f.urls.observers.isEmpty());
        assertTrue(f.epgs.observers.isEmpty());
        assertTrue(f.xmls.observers.isEmpty());
        assertTrue(f.lives.observers.isEmpty());
        late.onChanged(f.current);
        f.data.loadChannel(); f.data.refreshEpg();
        assertEquals(0, f.played);
        assertEquals(0, f.urlRequests);
        assertEquals(0, f.epgRequests);
    }

    @Test public void foregroundSubscriptionsUseLifecycleAndClosedConnectionCannotReconnect() {
        Fixture f = new Fixture(false);
        f.data.connect();
        assertFalse(f.urls.forever);
        f.data.close(); f.data.connect();
        assertTrue(f.urls.observers.isEmpty());
    }

    @Test public void staleUrlFromPreviousGroupLineOrChannelCannotReplacePlayback() {
        Fixture f = new Fixture(false); f.data.connect();
        TestChannel result = new TestChannel();
        result.group = new Group("other"); f.urls.emit(result);
        result.group = f.current.group; result.line = 1; f.urls.emit(result);
        result.line = 0; result.url = "other"; f.urls.emit(result);
        assertEquals(0, f.played);
        result.url = "one"; f.urls.emit(result);
        assertEquals(1, f.played);
    }

    @Test public void epgMatchesTvgIdEvenWhenDisplayNameIsDifferent() {
        Fixture f = new Fixture(false); f.data.connect();
        f.epgs.emit(new Epg() { @Override public String getKey() { return "channel-id"; } });
        assertEquals(1, f.epgRenders);
        f.epgs.emit(new Epg() { @Override public String getKey() { return "display-name"; } });
        assertEquals(1, f.epgRenders);
    }

    @Test public void playlistAndXmlEventsRequestOnlyCurrentEpg() {
        Fixture f = new Fixture(false); f.data.connect();
        f.xmls.emit(false); assertEquals(0, f.epgRequests);
        f.xmls.emit(true); assertEquals(1, f.epgRequests);
        f.lives.emit(new Live());
        assertEquals(1, f.xmlRequests); assertEquals(1, f.liveRenders);
        f.current = null;
        f.xmls.emit(true); assertEquals(1, f.epgRequests);
    }

    @Test public void invalidProgramDoesNotRunUiOrResetPlaybackIntent() {
        Fixture f = new Fixture(false);
        int[] prepared = {0};
        EpgData future = new EpgData(); future.setStartTime(Long.MAX_VALUE);
        assertFalse(f.data.replay(future, () -> prepared[0]++));
        f.current.catchup = false;
        assertFalse(f.data.replay(new EpgData(), () -> prepared[0]++));
        assertFalse(f.data.replay(null, () -> prepared[0]++));
        assertEquals(0, prepared[0]); assertEquals(0, f.urlRequests);
    }

    @Test public void replayPreparesUiBeforeRequestAndStopsWhenClosedDuringUi() {
        Fixture f = new Fixture(false);
        assertTrue(f.data.replay(new EpgData(), () -> assertEquals(0, f.urlRequests)));
        assertEquals(1, f.urlRequests);
        assertNotNull(f.lastReplay);
        assertFalse(f.data.replay(new EpgData(), f.data::close));
        assertEquals(1, f.urlRequests);
    }

    static class Stream<T> extends LiveData<T> {
        final List<Observer<? super T>> observers = new ArrayList<>();
        boolean forever;
        T cached;
        @Override public void observe(LifecycleOwner owner, Observer<? super T> observer) {
            forever = false; observers.add(observer);
            if (cached != null) observer.onChanged(cached);
        }
        @Override public void observeForever(Observer<? super T> observer) { forever = true; observers.add(observer); }
        @Override public void removeObserver(Observer<? super T> observer) { observers.remove(observer); }
        void emit(T value) { for (Observer<? super T> observer : new ArrayList<>(observers)) observer.onChanged(value); }
    }
    static class TestChannel extends Channel {
        Group group = new Group("group");
        int line;
        String url = "one";
        boolean catchup = true;
        @Override public Group getGroup() { return group; }
        @Override public int getLine() { return line; }
        @Override public String getCurrent() { return url; }
        @Override public String getTvgId() { return "channel-id"; }
        @Override public String getTvgName() { return "display-name"; }
        @Override public boolean hasCatchup() { return catchup; }
    }
    static class Fixture implements LiveDataConnection.Source, LiveDataConnection.Port {
        final Stream<Channel> urls = new Stream<>(); final Stream<Epg> epgs = new Stream<>();
        final Stream<Boolean> xmls = new Stream<>(); final Stream<Live> lives = new Stream<>();
        TestChannel current = new TestChannel();
        final LiveDataConnection data;
        int played, epgRenders, liveRenders, urlRequests, epgRequests, xmlRequests;
        EpgData lastReplay;
        boolean closeOnPlay;
        Fixture(boolean background) { data = new LiveDataConnection(null, this, background, this); }
        @Override public LiveData<Channel> url() { return urls; }
        @Override public LiveData<Epg> epg() { return epgs; }
        @Override public LiveData<Boolean> xml() { return xmls; }
        @Override public LiveData<Live> live() { return lives; }
        @Override public void loadXml(Live live) { xmlRequests++; }
        @Override public void loadEpg(Channel channel) { epgRequests++; }
        @Override public void loadUrl(Channel channel, EpgData replay) { urlRequests++; lastReplay = replay; }
        @Override public Channel channel() { return current; }
        @Override public void play(Channel channel) { played++; if (closeOnPlay) data.close(); }
        @Override public void renderEpg() { epgRenders++; }
        @Override public void renderLive(Live live) { liveRenders++; }
    }
}
