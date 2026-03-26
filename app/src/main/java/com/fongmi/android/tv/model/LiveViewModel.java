package com.fongmi.android.tv.model;

import android.net.Uri;

import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.fongmi.android.tv.Constant;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.api.EpgParser;
import com.fongmi.android.tv.api.LiveParser;
import com.fongmi.android.tv.api.config.LiveConfig;
import com.fongmi.android.tv.bean.Channel;
import com.fongmi.android.tv.bean.Epg;
import com.fongmi.android.tv.bean.EpgData;
import com.fongmi.android.tv.bean.Group;
import com.fongmi.android.tv.bean.Live;
import com.fongmi.android.tv.exception.ExtractException;
import com.fongmi.android.tv.player.Source;
import com.github.catvod.net.OkHttp;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class LiveViewModel extends ViewModel {

    private static final int LIVE = 0;
    private static final int EPG = 1;
    private static final int URL = 2;
    private static final int XML = 3;

    private final TimeZone defaultTimeZone;
    private volatile TimeZone currentTimeZone;

    public MutableLiveData<Channel> url;
    public MutableLiveData<Boolean> xml;
    public MutableLiveData<Live> live;
    public MutableLiveData<Epg> epg;

    private ExecutorService executor1;
    private ExecutorService executor2;
    private ExecutorService executor3;
    private ExecutorService executor4;
    private final AtomicInteger liveSeq;
    private final AtomicInteger epgSeq;
    private final AtomicInteger urlSeq;
    private final AtomicInteger xmlSeq;

    public LiveViewModel() {
        this.defaultTimeZone = TimeZone.getDefault();
        this.currentTimeZone = defaultTimeZone;
        this.live = new MutableLiveData<>();
        this.epg = new MutableLiveData<>();
        this.url = new MutableLiveData<>();
        this.xml = new MutableLiveData<>();
        this.liveSeq = new AtomicInteger();
        this.epgSeq = new AtomicInteger();
        this.urlSeq = new AtomicInteger();
        this.xmlSeq = new AtomicInteger();
    }

    public void getLive(Live item) {
        currentTimeZone = resolveTimeZone(item.getTimeZone(), item.getEpg(), defaultTimeZone);
        execute(LIVE, () -> {
            LiveParser.start(item.recent());
            verify(item);
            return item;
        });
    }

    public void getXml(Live item) {
        execute(XML, () -> EpgParser.start(item));
    }

    public void getEpg(Channel item) {
        TimeZone timeZone = resolveTimeZone(null, item.getEpg(), currentTimeZone);
        String date = createDateFormat(timeZone).format(new Date());
        String url = item.getEpg().replace("{date}", date);
        List<SimpleDateFormat> formats = createTimeFormats(timeZone);
        execute(EPG, () -> {
            if (!url.startsWith("http")) return item.getData().selected();
            if (!item.getData().equal(date)) item.setData(Epg.objectFrom(OkHttp.string(url), item.getTvgId(), formats));
            return item.getData().selected();
        });
    }

    public void getUrl(Channel item) {
        Channel request = snapshot(item);
        execute(URL, () -> {
            request.setMsg(null);
            Source.get().stop();
            request.setUrl(Source.get().fetch(request));
            return request;
        });
    }

    public void getUrl(Channel item, EpgData data) {
        Channel request = snapshot(item);
        execute(URL, () -> {
            request.setUrl(request.getCatchup().format(request.getCurrent(), data));
            return request;
        });
    }

    private Channel snapshot(Channel item) {
        Channel request = Channel.create(item).group(item.getGroup());
        request.setLine(item.getLine());
        request.setUrl(item.getUrl());
        request.setMsg(item.getMsg());
        request.setSelected(item.isSelected());
        return request;
    }

    private TimeZone resolveTimeZone(String value, String epg, TimeZone fallback) {
        try {
            if ((value == null || value.isEmpty()) && epg != null && epg.contains("serverTimeZone=")) value = Uri.parse(epg).getQueryParameter("serverTimeZone");
            return value == null || value.isEmpty() ? fallback : TimeZone.getTimeZone(value);
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private SimpleDateFormat createDateFormat(TimeZone timeZone) {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        format.setTimeZone(timeZone);
        return format;
    }

    private List<SimpleDateFormat> createTimeFormats(TimeZone timeZone) {
        List<SimpleDateFormat> formats = new ArrayList<>();
        formats.add(createTimeFormat("yyyy-MM-ddHH:mm", timeZone));
        formats.add(createTimeFormat("yyyy-MM-ddHH:mm:ss", timeZone));
        return formats;
    }

    private SimpleDateFormat createTimeFormat(String pattern, TimeZone timeZone) {
        SimpleDateFormat format = new SimpleDateFormat(pattern, Locale.getDefault());
        format.setTimeZone(timeZone);
        return format;
    }

    private void verify(Live item) {
        Iterator<Group> iterator = item.getGroups().iterator();
        while (iterator.hasNext()) if (iterator.next().isEmpty()) iterator.remove();
        if (item.getGroups().isEmpty() || item.getGroups().get(0).isKeep()) return;
        item.getGroups().add(0, Group.create(R.string.keep));
        LiveConfig.get().setKeep(item.getGroups());
    }

    private void execute(int type, Callable<?> callable) {
        switch (type) {
            case LIVE:
                if (executor1 != null) executor1.shutdownNow();
                executor1 = Executors.newFixedThreadPool(2);
                executor1.execute(runnable(type, callable, executor1, liveSeq.incrementAndGet()));
                break;
            case EPG:
                if (executor2 != null) executor2.shutdownNow();
                executor2 = Executors.newFixedThreadPool(2);
                executor2.execute(runnable(type, callable, executor2, epgSeq.incrementAndGet()));
                break;
            case URL:
                if (executor3 != null) executor3.shutdownNow();
                executor3 = Executors.newFixedThreadPool(2);
                executor3.execute(runnable(type, callable, executor3, urlSeq.incrementAndGet()));
                break;
            case XML:
                if (executor4 != null) executor4.shutdownNow();
                executor4 = Executors.newFixedThreadPool(2);
                executor4.execute(runnable(type, callable, executor4, xmlSeq.incrementAndGet()));
                break;
        }
    }

    private Runnable runnable(int type, Callable<?> callable, ExecutorService executor, int seq) {
        return () -> {
            try {
                if (Thread.interrupted()) return;
                if (type == EPG) postEpg((Epg) executor.submit(callable).get(Constant.TIMEOUT_EPG, TimeUnit.MILLISECONDS), seq);
                if (type == LIVE) postLive((Live) executor.submit(callable).get(Constant.TIMEOUT_LIVE, TimeUnit.MILLISECONDS), seq);
                if (type == XML) postXml((Boolean) executor.submit(callable).get(Constant.TIMEOUT_XML, TimeUnit.MILLISECONDS), seq);
                if (type == URL) postUrl((Channel) executor.submit(callable).get(Constant.TIMEOUT_PARSE_LIVE, TimeUnit.MILLISECONDS), seq);
            } catch (Throwable e) {
                if (e instanceof InterruptedException || Thread.interrupted()) return;
                if (e.getCause() instanceof ExtractException) postUrl(Channel.error(e.getCause().getMessage()), seq);
                else if (type == URL) postUrl(new Channel(), seq);
                if (type == LIVE) postLive(new Live(), seq);
                if (type == EPG) postEpg(new Epg(), seq);
                if (type == XML) postXml(false, seq);
                e.printStackTrace();
            }
        };
    }

    private void postLive(Live value, int seq) {
        if (seq == liveSeq.get()) live.postValue(value);
    }

    private void postEpg(Epg value, int seq) {
        if (seq == epgSeq.get()) epg.postValue(value);
    }

    private void postUrl(Channel value, int seq) {
        if (seq == urlSeq.get()) url.postValue(value);
    }

    private void postXml(Boolean value, int seq) {
        if (seq == xmlSeq.get()) xml.postValue(value);
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        if (executor1 != null) executor1.shutdownNow();
        if (executor2 != null) executor2.shutdownNow();
        if (executor3 != null) executor3.shutdownNow();
        if (executor4 != null) executor4.shutdownNow();
    }
}
