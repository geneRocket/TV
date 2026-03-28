package com.fongmi.android.tv.model;

import android.net.Uri;
import android.text.TextUtils;

import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.fongmi.android.tv.App;
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
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;

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
    private RunningTask liveTask;
    private RunningTask epgTask;
    private RunningTask urlTask;
    private RunningTask xmlTask;
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
        this.executor1 = Executors.newSingleThreadExecutor();
        this.executor2 = Executors.newSingleThreadExecutor();
        this.executor3 = Executors.newSingleThreadExecutor();
        this.executor4 = Executors.newSingleThreadExecutor();
        this.liveSeq = new AtomicInteger();
        this.epgSeq = new AtomicInteger();
        this.urlSeq = new AtomicInteger();
        this.xmlSeq = new AtomicInteger();
    }

    private static final class RunningTask {
        private Future<?> future;
        private Runnable timeout;
    }

    private interface ValuePoster<T> {
        void post(T value, int seq);
    }

    private interface ValueFallback<T> {
        T create(Throwable error);
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
        }, error -> urlFallback(request, error));
    }

    public void getUrl(Channel item, EpgData data) {
        Channel request = snapshot(item);
        execute(URL, () -> {
            request.setUrl(request.getCatchup().format(request.getCurrent(), data));
            return request;
        }, error -> urlFallback(request, error));
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
        execute(type, callable, null);
    }

    private void execute(int type, Callable<?> callable, ValueFallback<?> fallbackOverride) {
        switch (type) {
            case LIVE: {
                int seq = liveSeq.incrementAndGet();
                cancelTask(liveTask);
                liveTask = submit(executor1, callable, Constant.TIMEOUT_LIVE, seq, this::postLive, error -> new Live());
                break;
            }
            case EPG: {
                int seq = epgSeq.incrementAndGet();
                cancelTask(epgTask);
                epgTask = submit(executor2, callable, Constant.TIMEOUT_EPG, seq, this::postEpg, error -> new Epg());
                break;
            }
            case URL: {
                int seq = urlSeq.incrementAndGet();
                cancelTask(urlTask);
                @SuppressWarnings("unchecked")
                ValueFallback<Channel> fallback = fallbackOverride == null ? this::urlFallback : (ValueFallback<Channel>) fallbackOverride;
                urlTask = submit(executor3, callable, Constant.TIMEOUT_PARSE_LIVE, seq, this::postUrl, fallback);
                break;
            }
            case XML: {
                int seq = xmlSeq.incrementAndGet();
                cancelTask(xmlTask);
                xmlTask = submit(executor4, callable, Constant.TIMEOUT_XML, seq, this::postXml, error -> false);
                break;
            }
        }
    }

    private <T> RunningTask submit(ExecutorService executor, Callable<?> callable, long timeout, int seq, ValuePoster<T> poster, ValueFallback<T> fallback) {
        AtomicBoolean completed = new AtomicBoolean(false);
        RunningTask task = new RunningTask();
        task.timeout = () -> {
            if (!completed.compareAndSet(false, true)) return;
            if (task.future != null) task.future.cancel(true);
            clearTask(task);
            poster.post(fallback.create(new TimeoutException()), seq);
        };
        try {
            task.future = executor.submit(() -> {
                try {
                    @SuppressWarnings("unchecked")
                    T value = (T) callable.call();
                    if (!completed.compareAndSet(false, true)) return;
                    clearTask(task);
                    poster.post(value, seq);
                } catch (Throwable e) {
                    if (e instanceof InterruptedException) Thread.currentThread().interrupt();
                    if (!completed.compareAndSet(false, true)) return;
                    clearTask(task);
                    poster.post(fallback.create(e), seq);
                    if (!(e instanceof InterruptedException)) e.printStackTrace();
                }
            });
            App.post(task.timeout, timeout);
        } catch (RejectedExecutionException e) {
            clearTask(task);
            poster.post(fallback.create(e), seq);
        }
        return task;
    }

    private void clearTask(RunningTask task) {
        if (task != null && task.timeout != null) App.removeCallbacks(task.timeout);
    }

    private void cancelTask(RunningTask task) {
        clearTask(task);
        if (task != null && task.future != null) task.future.cancel(true);
    }

    private void shutdownExecutor(ExecutorService executor) {
        if (executor != null) executor.shutdownNow();
    }

    private Channel urlFallback(Throwable error) {
        return error instanceof ExtractException ? Channel.error(error.getMessage()) : new Channel();
    }

    private Channel urlFallback(Channel request, Throwable error) {
        String msg = error instanceof ExtractException ? error.getMessage() : error.getMessage();
        if (TextUtils.isEmpty(msg)) msg = App.get().getString(R.string.error_play_timeout);
        request.setMsg(msg);
        return request;
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
        cancelTask(liveTask);
        cancelTask(epgTask);
        cancelTask(urlTask);
        cancelTask(xmlTask);
        shutdownExecutor(executor1);
        shutdownExecutor(executor2);
        shutdownExecutor(executor3);
        shutdownExecutor(executor4);
    }
}
