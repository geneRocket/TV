package com.fongmi.android.tv.model;

import android.net.Uri;
import android.text.TextUtils;

import androidx.lifecycle.LiveData;
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
import com.fongmi.android.tv.utils.AppTaskScheduler;
import com.fongmi.android.tv.utils.ThreadPools;
import com.fongmi.android.tv.utils.UrlUtil;
import com.github.catvod.net.OkHttp;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.Callable;
import com.fongmi.android.tv.utils.LatestTask;

import okhttp3.Headers;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

public class LiveViewModel extends ViewModel {

    private static final int LIVE = 0;
    private static final int EPG = 1;
    private static final int URL = 2;
    private static final int XML = 3;

    private final TimeZone defaultTimeZone;
    private volatile TimeZone currentTimeZone;

    private final MutableLiveData<Channel> url;
    private final MutableLiveData<Boolean> xml;
    private final MutableLiveData<Live> live;
    private final MutableLiveData<Epg> epg;

    private final LatestTask<Live> liveTask;
    private final LatestTask<Epg> epgTask;
    private final LatestTask<Channel> urlTask;
    private final LatestTask<Boolean> xmlTask;

    public LiveViewModel() {
        this.defaultTimeZone = TimeZone.getDefault();
        this.currentTimeZone = defaultTimeZone;
        this.live = new MutableLiveData<>();
        this.epg = new MutableLiveData<>();
        this.url = new MutableLiveData<>();
        this.xml = new MutableLiveData<>();
        this.liveTask = new LatestTask<>(ThreadPools.newSingle("live-load"), AppTaskScheduler.get(), error -> ThreadPools.log(error, "Live request failed."));
        this.epgTask = new LatestTask<>(ThreadPools.newSingle("live-epg"), AppTaskScheduler.get(), error -> ThreadPools.log(error, "Live request failed."));
        this.urlTask = new LatestTask<>(ThreadPools.newSingle("live-url"), AppTaskScheduler.get(), error -> ThreadPools.log(error, "Live request failed."));
        this.xmlTask = new LatestTask<>(ThreadPools.newSingle("live-xml"), AppTaskScheduler.get(), error -> ThreadPools.log(error, "Live request failed."));
    }

    public void getLive(Live item) {
        currentTimeZone = resolveTimeZone(item.getTimeZone(), item.getEpg(), defaultTimeZone);
        execute(LIVE, () -> {
            LiveParser.start(item.recent());
            verify(item);
            return item;
        });
    }

    public LiveData<Channel> url() {
        return url;
    }

    public LiveData<Boolean> xml() {
        return xml;
    }

    public LiveData<Live> live() {
        return live;
    }

    public LiveData<Epg> epg() {
        return epg;
    }

    public void getXml(Live item) {
        execute(XML, () -> EpgParser.start(item));
    }

    public void getEpg(Channel item) {
        TimeZone timeZone = resolveTimeZone(null, item.getEpg(), currentTimeZone);
        String date = createDateFormat(timeZone).format(new Date());
        String epg = item.getEpg().replace("{date}", date);
        String url = epg.startsWith("file") ? epg : UrlUtil.normalize(epg, "");
        List<SimpleDateFormat> formats = createTimeFormats(timeZone);
        execute(EPG, () -> {
            if (!url.startsWith("http")) return item.getData().selected();
            if (!item.getData().equal(date)) item.setData(Epg.objectFrom(requestString(url, Constant.TIMEOUT_EPG), item.getTvgId(), formats));
            return item.getData().selected();
        });
    }

    private String requestString(String url, int timeout) throws Exception {
        Map<String, String> headers = UrlUtil.getTagHeaders(url);
        Request request = new Request.Builder().url(UrlUtil.stripTag(url)).headers(Headers.of(headers)).build();
        try (Response response = OkHttp.client(timeout).newCall(request).execute()) {
            if (!response.isSuccessful()) throw new IOException(response.code() + " " + response.message());
            ResponseBody body = response.body();
            return body == null ? "" : body.string();
        }
    }

    public void getUrl(Channel item) {
        Channel request = snapshot(item);
        execute(URL, () -> {
            request.setMsg(null);
            Source.get().stop();
            request.setUrl(UrlUtil.normalize(Source.get().fetch(request), ""));
            return request;
        }, error -> urlFallback(request, error));
    }

    public void getUrl(Channel item, EpgData data) {
        Channel request = snapshot(item);
        execute(URL, () -> {
            request.setUrl(UrlUtil.normalize(request.getCatchup().format(request.getCurrent(), data), ""));
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
            if (value == null || value.isEmpty()) return fallback;
            TimeZone timeZone = TimeZone.getTimeZone(value);
            if ("GMT".equals(timeZone.getID()) && !isGmtTimeZone(value)) return fallback;
            return timeZone;
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private boolean isGmtTimeZone(String value) {
        String normalized = value == null ? "" : value.trim().toUpperCase(Locale.US);
        return "GMT".equals(normalized) || "UTC".equals(normalized) || normalized.startsWith("GMT+") || normalized.startsWith("GMT-");
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

    private void execute(int type, Callable<?> callable, java.util.function.Function<Throwable, ?> fallbackOverride) {
        switch (type) {
            case LIVE:
                liveTask.submit(() -> (Live) callable.call(), Constant.TIMEOUT_LIVE, live::postValue, error -> new Live(), null);
                break;
            case EPG:
                epgTask.submit(() -> (Epg) callable.call(), Constant.TIMEOUT_EPG, epg::postValue, error -> new Epg(), null);
                break;
            case URL:
                @SuppressWarnings("unchecked") java.util.function.Function<Throwable, Channel> fallback = fallbackOverride == null ? this::urlFallback : (java.util.function.Function<Throwable, Channel>) fallbackOverride;
                urlTask.submit(() -> (Channel) callable.call(), Constant.TIMEOUT_PARSE_LIVE, url::postValue, fallback, Source.get()::stop);
                break;
            case XML:
                xmlTask.submit(() -> (Boolean) callable.call(), Constant.TIMEOUT_XML, xml::postValue, error -> false, null);
                break;
        }
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

    @Override
    protected void onCleared() {
        super.onCleared();
        liveTask.close();
        epgTask.close();
        urlTask.close();
        xmlTask.close();
    }
}
