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
import com.fongmi.android.tv.api.EpgDay;
import com.fongmi.android.tv.api.LiveParser;
import com.fongmi.android.tv.api.config.LiveConfig;
import com.fongmi.android.tv.bean.Channel;
import com.fongmi.android.tv.bean.Epg;
import com.fongmi.android.tv.bean.EpgData;
import com.fongmi.android.tv.bean.Group;
import com.fongmi.android.tv.bean.Live;
import com.fongmi.android.tv.player.Source;
import com.fongmi.android.tv.utils.AppTaskScheduler;
import com.fongmi.android.tv.utils.ThreadPools;
import com.fongmi.android.tv.utils.UrlUtil;
import com.github.catvod.net.OkHttp;
import com.github.catvod.net.HttpCallScope;
import com.github.catvod.utils.Json;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;
import com.fongmi.android.tv.utils.TaskScope;

import okhttp3.Call;
import okhttp3.Headers;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

public class LiveViewModel extends ViewModel {

    private final TimeZone defaultTimeZone;
    private volatile TimeZone currentTimeZone;

    private final MutableLiveData<Channel> url;
    private final MutableLiveData<Boolean> xml;
    private final MutableLiveData<Live> live;
    private final MutableLiveData<Epg> epg;

    private final TaskScope tasks;
    private final TaskScope urlTasks;
    private final ExecutorService executor;
    private final ExecutorService urlExecutor;

    public LiveViewModel() {
        this.defaultTimeZone = TimeZone.getDefault();
        this.currentTimeZone = defaultTimeZone;
        this.live = new MutableLiveData<>();
        this.epg = new MutableLiveData<>();
        this.url = new MutableLiveData<>();
        this.xml = new MutableLiveData<>();
        this.executor = ThreadPools.newFixed("live", 3);
        this.urlExecutor = ThreadPools.newSingle("live-url");
        this.tasks = new TaskScope(executor, AppTaskScheduler.get(), error -> ThreadPools.log(error, "Live request failed."), false);
        this.urlTasks = new TaskScope(urlExecutor, AppTaskScheduler.get(), error -> ThreadPools.log(error, "Live URL request failed."), false);
    }

    public void getLive(Live item) {
        currentTimeZone = resolveTimeZone(item.getTimeZone(), item.getEpg(), defaultTimeZone);
        submitLive(() -> {
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
        submitXml(() -> EpgParser.start(item));
    }

    public void getEpg(Channel item) {
        TimeZone timeZone = resolveTimeZone(null, item.getEpg(), currentTimeZone);
        String date = createDateFormat(timeZone).format(new Date());
        String epg = item.getEpg().replace("{date}", date);
        String url = epg.startsWith("file") ? epg : UrlUtil.normalize(epg, "");
        List<SimpleDateFormat> formats = createTimeFormats(timeZone);
        submitEpg(() -> {
            if (!url.startsWith("http")) return item.getData().selected();
            if (!item.getData().equal(date)) item.setData(Epg.objectFrom(requestString(url, Constant.TIMEOUT_EPG), item.getTvgId(), formats));
            return item.getData().selected();
        });
    }

    public boolean hasHistory(Live source, Channel channel) {
        return channel.hasCatchup() && EpgDay.days(channel.getCatchup().getDays()) > 0
                && (channel.getEpg().contains("{date}") || !source.getEpgXml().isEmpty());
    }

    public List<String> historyDates(Channel channel) {
        TimeZone zone = resolveTimeZone(null, channel.getEpg(), currentTimeZone);
        return EpgDay.previousDates(zone, System.currentTimeMillis(), channel.getCatchup().getDays());
    }

    public void getHistoryEpg(Live source, Channel channel, String date, Consumer<Epg> delivery) {
        TimeZone zone = resolveTimeZone(null, channel.getEpg(), currentTimeZone);
        HttpCallScope calls = new HttpCallScope();
        tasks.submit("epg-history", () -> calls.call(() -> loadHistory(source, channel, date, zone)),
                Constant.TIMEOUT_EPG + Constant.TIMEOUT_EPG, delivery, error -> new Epg(), calls::close);
    }

    public void cancelHistoryEpg() {
        tasks.cancel("epg-history");
    }

    private Epg loadHistory(Live source, Channel channel, String date, TimeZone zone) throws Exception {
        String template = channel.getEpg();
        if (template.contains("{date}")) {
            String url = UrlUtil.normalize(template.replace("{date}", date), "");
            if (url.startsWith("http")) {
                try {
                    EpgDay day = new EpgDay(date, zone);
                    String body = requestString(url, Constant.TIMEOUT_EPG);
                    Epg result = Json.valid(body) ? Epg.objectFrom(body, channel.getTvgId(), createTimeFormats(zone), date)
                            : EpgParser.readHistory(body, source, channel, date, zone);
                    List<EpgData> programmes = new ArrayList<>(result.getList());
                    programmes.removeIf(item -> !day.contains(item));
                    programmes.sort(java.util.Comparator.comparingLong(EpgData::getStartTime));
                    if (!programmes.isEmpty()) {
                        result.setList(programmes);
                        return result;
                    }
                } catch (Exception error) {
                    HttpCallScope.throwIfCancelled();
                    if (source.getEpgXml().isEmpty()) throw error;
                }
            }
        }
        return EpgParser.getHistory(source, channel, date, zone);
    }

    private String requestString(String url, int timeout) throws Exception {
        Map<String, String> headers = UrlUtil.getTagHeaders(url);
        Request request = new Request.Builder().url(UrlUtil.stripTag(url)).headers(Headers.of(headers)).build();
        Call call = OkHttp.client(timeout).newCall(request);
        return requestString(call);
    }

    String requestString(Call call) throws Exception {
        HttpCallScope.track(call);
        if (Thread.currentThread().isInterrupted()) call.cancel();
        try (Response response = call.execute()) {
            if (!response.isSuccessful()) throw new IOException(response.code() + " " + response.message());
            ResponseBody body = response.body();
            return body == null ? "" : body.string();
        }
    }

    public void getUrl(Channel item) {
        Channel request = snapshot(item);
        request.setReplay(false);
        submitUrl(() -> {
            request.setMsg(null);
            Source.get().stop();
            request.setUrl(UrlUtil.normalize(Source.get().fetch(request), ""));
            return request;
        }, error -> urlFallback(request, error));
    }

    public void getUrl(Channel item, EpgData data) {
        Channel request = snapshot(item);
        request.setReplay(true);
        submitUrl(() -> {
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

    private void submitLive(java.util.concurrent.Callable<Live> task) {
        HttpCallScope calls = new HttpCallScope();
        tasks.submit("live", () -> calls.call(task), Constant.TIMEOUT_LIVE, live::setValue, error -> {
            Live failed = new Live();
            if (error instanceof com.fongmi.android.tv.exception.ExtractException) failed.setLoadError(error.getMessage());
            return failed;
        }, calls::close);
    }

    private void submitEpg(java.util.concurrent.Callable<Epg> task) {
        HttpCallScope calls = new HttpCallScope();
        tasks.submit("epg", () -> calls.call(task), Constant.TIMEOUT_EPG, epg::setValue, error -> new Epg(), calls::close);
    }

    private void submitUrl(java.util.concurrent.Callable<Channel> task, java.util.function.Function<Throwable, Channel> fallback) {
        HttpCallScope calls = new HttpCallScope();
        urlTasks.submit("url", () -> calls.call(task), Constant.TIMEOUT_PARSE_LIVE, url::setValue, fallback, () -> {
            calls.close();
            Source.get().stop();
        });
    }

    private void submitXml(java.util.concurrent.Callable<Boolean> task) {
        HttpCallScope calls = new HttpCallScope();
        tasks.submit("xml", () -> calls.call(task), Constant.TIMEOUT_XML, xml::setValue, error -> false, calls::close);
    }

    private Channel urlFallback(Channel request, Throwable error) {
        String msg = error.getMessage();
        if (TextUtils.isEmpty(msg)) msg = App.get().getString(R.string.error_play_timeout);
        request.setMsg(msg);
        return request;
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        tasks.close();
        urlTasks.close();
        ThreadPools.shutdown(executor);
        ThreadPools.shutdown(urlExecutor);
    }
}
