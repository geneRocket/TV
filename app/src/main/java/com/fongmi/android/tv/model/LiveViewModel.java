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

    private final SimpleDateFormat formatDate;
    private final SimpleDateFormat formatTime;
    private final List<SimpleDateFormat> formatTimeList;
    private final TimeZone defaultTimeZone;

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
        this.formatTime = new SimpleDateFormat("yyyy-MM-ddHH:mm", Locale.getDefault());
        this.formatDate = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        this.formatTimeList = new ArrayList<>();
        this.defaultTimeZone = TimeZone.getDefault();
        this.formatTimeList.add(formatTime);
        this.formatTimeList.add(new SimpleDateFormat("yyyy-MM-ddHH:mm:ss", Locale.getDefault()));
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
        execute(LIVE, () -> {
            LiveParser.start(item.recent());
            setTimeZone(item);
            verify(item);
            return item;
        });
    }

    public void getXml(Live item) {
        execute(XML, () -> EpgParser.start(item));
    }

    public void getEpg(Channel item) {
        String date = formatDate.format(new Date());
        String url = item.getEpg().replace("{date}", date);
        execute(EPG, () -> {
            if (!url.startsWith("http")) return item.getData().selected();
            if (!item.getData().equal(date)) item.setData(Epg.objectFrom(OkHttp.string(url), item.getTvgId(), formatTimeList));
            return item.getData().selected();
        });
    }

    public void getUrl(Channel item) {
        execute(URL, () -> {
            item.setMsg(null);
            Source.get().stop();
            item.setUrl(Source.get().fetch(item));
            return item;
        });
    }

    public void getUrl(Channel item, EpgData data) {
        execute(URL, () -> {
            item.setUrl(item.getCatchup().format(item.getCurrent(), data));
            return item;
        });
    }

    private void setTimeZone(Live item) {
        try {
            String value = item.getTimeZone();
            if (value.isEmpty() && item.getEpg().contains("serverTimeZone=")) value = Uri.parse(item.getEpg()).getQueryParameter("serverTimeZone");
            TimeZone timeZone = value == null || value.isEmpty() ? defaultTimeZone : TimeZone.getTimeZone(value);
            formatDate.setTimeZone(timeZone);
            formatTime.setTimeZone(timeZone);
            for (SimpleDateFormat itemFormat : formatTimeList) itemFormat.setTimeZone(timeZone);
        } catch (Exception ignored) {
            formatDate.setTimeZone(defaultTimeZone);
            formatTime.setTimeZone(defaultTimeZone);
            for (SimpleDateFormat itemFormat : formatTimeList) itemFormat.setTimeZone(defaultTimeZone);
        }
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
