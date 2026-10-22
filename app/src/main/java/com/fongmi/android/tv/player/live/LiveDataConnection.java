package com.fongmi.android.tv.player.live;

import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Observer;

import com.fongmi.android.tv.bean.Channel;
import com.fongmi.android.tv.bean.Epg;
import com.fongmi.android.tv.bean.EpgData;
import com.fongmi.android.tv.bean.Live;
import com.fongmi.android.tv.model.LiveViewModel;

import java.util.Objects;

/** Owns live data subscriptions, channel identity checks and EPG requests for one page. */
public final class LiveDataConnection implements AutoCloseable {
    public interface Source {
        LiveData<Channel> url();
        LiveData<Epg> epg();
        LiveData<Boolean> xml();
        LiveData<Live> live();
        void loadXml(Live live);
        void loadEpg(Channel channel);
        void loadUrl(Channel channel, EpgData replay);
    }
    public interface Port {
        Channel channel();
        void play(Channel channel);
        void renderEpg();
        void renderLive(Live live);
    }

    private final LifecycleOwner owner;
    private final Source source;
    private final Port port;
    private final boolean background;
    private boolean started;
    private boolean closed;
    private final Observer<Channel> urlObserver = this::url;
    private final Observer<Epg> epgObserver = this::epg;
    private final Observer<Boolean> xmlObserver = this::xml;
    private final Observer<Live> liveObserver = this::live;

    public static Source source(LiveViewModel model) {
        return new Source() {
            @Override public LiveData<Channel> url() { return model.url(); }
            @Override public LiveData<Epg> epg() { return model.epg(); }
            @Override public LiveData<Boolean> xml() { return model.xml(); }
            @Override public LiveData<Live> live() { return model.live(); }
            @Override public void loadXml(Live live) { model.getXml(live); }
            @Override public void loadEpg(Channel channel) { model.getEpg(channel); }
            @Override public void loadUrl(Channel channel, EpgData replay) {
                if (replay == null) model.getUrl(channel);
                else model.getUrl(channel, replay);
            }
        };
    }

    public LiveDataConnection(LifecycleOwner owner, Source source, boolean background, Port port) {
        this.owner = owner;
        this.source = source;
        this.background = background;
        this.port = port;
    }

    public boolean isClosed() { return closed; }

    /** Start only after assigning the page field: LiveData can deliver cached results synchronously. */
    public void connect() {
        if (started || closed) return;
        started = true;
        observe(source.url(), urlObserver, background);
        observe(source.epg(), epgObserver, background);
        observe(source.xml(), xmlObserver, false);
        observe(source.live(), liveObserver, false);
    }

    private <T> void observe(LiveData<T> data, Observer<T> observer, boolean forever) {
        if (closed) return;
        if (forever) data.observeForever(observer);
        else data.observe(owner, observer);
    }

    private void url(Channel result) {
        if (closed) return;
        Channel current = port.channel();
        if (result == null || current == null) return;
        if (result.getGroup() == current.getGroup() && result.getLine() == current.getLine()
                && Objects.equals(result.getCurrent(), current.getCurrent())) port.play(result);
    }

    private void epg(Epg result) {
        if (closed) return;
        Channel current = port.channel();
        if (result != null && current != null && Objects.equals(current.getTvgId(), result.getKey())) port.renderEpg();
    }

    private void xml(Boolean ready) {
        if (!closed && Boolean.TRUE.equals(ready)) refreshEpg();
    }

    private void live(Live result) {
        if (closed || result == null) return;
        source.loadXml(result);
        if (!closed) port.renderLive(result);
    }

    public void refreshEpg() {
        if (closed) return;
        Channel current = port.channel();
        if (current != null) source.loadEpg(current);
    }

    public void loadChannel() {
        if (closed) return;
        Channel current = port.channel();
        if (current != null) source.loadUrl(current, null);
    }

    public boolean replay(EpgData program, Runnable beforeLoad) {
        if (closed) return false;
        Channel current = port.channel();
        if (current == null || program == null || program.isFuture() || !current.hasCatchup()) return false;
        beforeLoad.run();
        if (closed || port.channel() != current) return false;
        source.loadUrl(current, program);
        return true;
    }

    @Override public void close() {
        if (closed) return;
        closed = true;
        source.url().removeObserver(urlObserver);
        source.epg().removeObserver(epgObserver);
        source.xml().removeObserver(xmlObserver);
        source.live().removeObserver(liveObserver);
    }
}
