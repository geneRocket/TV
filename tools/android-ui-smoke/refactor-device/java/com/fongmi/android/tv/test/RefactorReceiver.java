package com.fongmi.android.tv.test;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.view.View;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.Setting;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.api.config.LiveConfig;
import com.fongmi.android.tv.bean.Config;
import com.fongmi.android.tv.impl.Callback;
import com.fongmi.android.tv.bean.Sub;
import com.fongmi.android.tv.bean.Channel;
import com.fongmi.android.tv.bean.EpgData;
import com.fongmi.android.tv.player.PlaybackController;
import com.fongmi.android.tv.player.DirectPlaybackFlow;
import com.fongmi.android.tv.player.vod.VodPlaybackFlow;
import com.fongmi.android.tv.ui.presenter.VodPagePresenter;
import com.fongmi.android.tv.server.Server;
import com.fongmi.android.tv.ui.custom.CustomSeekView;

import org.json.JSONObject;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/** Included only by init.gradle in isolated test application ids. Never added to production variants. */
public final class RefactorReceiver extends BroadcastReceiver {
    private static VodPlaybackFlow lastFlow;
    private static PlaybackController lastPlayer;
    private static DirectPlaybackFlow lastDirect;
    private static String liveConfigStatus = "idle";
    private static String vodConfigStatus = "idle";
    private static String wallConfigStatus = "idle";
    private static volatile JSONObject vodProbe;
    private static volatile String subtitleDownloadStatus = "idle";

    @Override public void onReceive(Context context, Intent intent) {
        try {
            Activity activity = App.activity();
            PlaybackController player = Server.get().getPlayer();
            if (player != null) {
                lastPlayer = player;
                Object handler = field(player, "inputHandler");
                if (handler instanceof VodPlaybackFlow) { lastFlow = (VodPlaybackFlow) handler; lastDirect = null; }
                if (handler instanceof DirectPlaybackFlow) { lastDirect = (DirectPlaybackFlow) handler; lastFlow = null; }
            }
            String command = intent.getStringExtra("cmd");
            if (command == null) command = "state";
            switch (command) {
                case "subtitleLive":
                    com.fongmi.android.tv.ui.dialog.SearchSubtitleDialog.create().title(intent.getStringExtra("query"))
                            .show((androidx.fragment.app.FragmentActivity) activity);
                    break;
                case "subtitleClose": searchSubtitleDialog(activity).dismiss(); break;
                case "subtitleOpenSources": searchSubtitleDialog(activity).requireView().findViewById(com.fongmi.android.tv.R.id.source).performClick(); break;
                case "subtitleOpenPreferences": searchSubtitleDialog(activity).requireView().findViewById(com.fongmi.android.tv.R.id.preference).performClick(); break;
                case "subtitleSource":
                    new androidx.lifecycle.ViewModelProvider(searchSubtitleDialog(activity)).get(com.fongmi.android.tv.viewmodel.SubtitleViewModel.class)
                            .source(com.fongmi.android.tv.subtitle.SubtitleSource.valueOf(intent.getStringExtra("value")));
                    break;
                case "subtitlePreference":
                    new androidx.lifecycle.ViewModelProvider(searchSubtitleDialog(activity)).get(com.fongmi.android.tv.viewmodel.SubtitleViewModel.class)
                            .preference(com.fongmi.android.tv.subtitle.SubtitlePreference.valueOf(intent.getStringExtra("value")));
                    break;
                case "subtitleProbeDownload": {
                    com.fongmi.android.tv.subtitle.SubtitleSearchState current = new androidx.lifecycle.ViewModelProvider(searchSubtitleDialog(activity))
                            .get(com.fongmi.android.tv.viewmodel.SubtitleViewModel.class).searchResults().getValue();
                    com.fongmi.android.tv.bean.Subtitle direct = null;
                    for (com.fongmi.android.tv.bean.Subtitle item : current.items) if (!item.isExternal() && !Boolean.TRUE.equals(item.getIsZip())) { direct = item; break; }
                    if (direct == null) { subtitleDownloadStatus = "unavailable"; break; }
                    String url = direct.getUrl(); subtitleDownloadStatus = "loading";
                    App.execute(() -> {
                        try (okhttp3.Response response = com.github.catvod.net.OkHttp.client().newBuilder().callTimeout(8, java.util.concurrent.TimeUnit.SECONDS).build()
                                .newCall(new okhttp3.Request.Builder().url(url).build()).execute()) {
                            byte[] bytes = new byte[8192];
                            int length = response.body() == null ? -1 : response.body().byteStream().read(bytes);
                            String text = length <= 0 ? "" : new String(bytes, 0, length, java.nio.charset.StandardCharsets.UTF_8);
                            subtitleDownloadStatus = response.isSuccessful() && (text.contains("-->") || text.contains("[Script Info]")) ? "valid" : "invalid:" + response.code();
                        } catch (Exception error) { subtitleDownloadStatus = "error:" + error.getClass().getSimpleName(); }
                    });
                    break;
                }
                case "configWall":
                    wallConfigStatus = "loading";
                    com.fongmi.android.tv.api.config.WallConfig.load(Config.create(2, intent.getStringExtra("url")), new Callback() {
                        @Override public void success() { wallConfigStatus = "success"; }
                        @Override public void error(String message) { wallConfigStatus = "error: " + message; }
                    });
                    break;
                case "subtitleDialog": {
                    SubtitleFixtureDialog.selected = "";
                    SubtitleFixtureDialog.slowResolve = false;
                    SubtitleFixtureDialog.searches = SubtitleFixtureDialog.details = 0;
                    new SubtitleFixtureDialog().title("Movie").listener(item -> SubtitleFixtureDialog.selected = item.getUrl())
                            .show((androidx.fragment.app.FragmentActivity) activity);
                    break;
                }
                case "subtitleNext": subtitleDialog(activity).requireView().findViewById(com.fongmi.android.tv.R.id.next).performClick(); break;
                case "subtitleSlowResolve": SubtitleFixtureDialog.slowResolve = intent.getBooleanExtra("value", true); break;
                case "subtitleRetry": subtitleDialog(activity).requireView().findViewById(com.fongmi.android.tv.R.id.retry).performClick(); break;
                case "subtitleBack": subtitleDialog(activity).requireView().findViewById(com.fongmi.android.tv.R.id.back_results).performClick(); break;
                case "subtitleInput": {
                    android.widget.EditText input = subtitleDialog(activity).requireView().findViewById(com.fongmi.android.tv.R.id.input);
                    input.setText(intent.getStringExtra("query")); input.requestFocus(); break;
                }
                case "subtitleEnter": {
                    android.widget.EditText input = subtitleDialog(activity).requireView().findViewById(com.fongmi.android.tv.R.id.input);
                    input.dispatchKeyEvent(new android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_ENTER));
                    input.dispatchKeyEvent(new android.view.KeyEvent(android.view.KeyEvent.ACTION_UP, android.view.KeyEvent.KEYCODE_ENTER));
                    break;
                }
                case "subtitleIme": ((android.widget.EditText) subtitleDialog(activity).requireView()
                        .findViewById(com.fongmi.android.tv.R.id.input)).onEditorAction(android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH); break;
                case "subtitlePick": {
                    androidx.recyclerview.widget.RecyclerView list = subtitleDialog(activity).requireView().findViewById(com.fongmi.android.tv.R.id.recycler);
                    list.findViewHolderForAdapterPosition(0).itemView.performClick();
                    break;
                }
                case "across": Setting.putAcross(intent.getBooleanExtra("value", true)); break;
                case "replayFuture":
                    EpgData future = new EpgData();
                    future.setStartTime(Long.MAX_VALUE);
                    invoke(activity, "onItemClick", future);
                    break;
                case "replayCurrent":
                    Channel channel = (Channel) field(activity, "mChannel");
                    invoke(activity, "onItemClick", channel.getData().getList().get(0));
                    break;
                case "configLive":
                    liveConfigStatus = "loading";
                    LiveConfig.load(Config.create(1, intent.getStringExtra("url")), new Callback() {
                        @Override public void success() { liveConfigStatus = "success"; }
                        @Override public void error(String message) { liveConfigStatus = "error: " + message; }
                    });
                    break;
                case "clearLive": LiveConfig.release(); break;
                case "configLiveBatch": {
                    liveConfigStatus = "loading";
                    java.util.List<Config> configs = new java.util.ArrayList<>();
                    for (String url : intent.getStringExtra("urls").split("\\|")) configs.add(Config.create(1, url));
                    LiveConfig.load(configs, new Callback() {
                        @Override public void success() { liveConfigStatus = "success"; }
                        @Override public void error(String message) { liveConfigStatus = "error: " + message; }
                    });
                    break;
                }
                case "liveHome":
                    invoke(activity, "setLive", LiveConfig.get().getLives().get(intent.getIntExtra("value", 0)));
                    break;
                case "liveSelect":
                    invoke(activity, "setPosition", new int[]{intent.getIntExtra("group", 0), intent.getIntExtra("channel", 0)});
                    break;
                case "removeAd": Setting.putRemoveAd(intent.getBooleanExtra("value", true)); break;
                case "openLive":
                    context.startActivity(new Intent().setClassName(context.getPackageName(), "com.fongmi.android.tv.ui.activity.LiveActivity")
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra("empty", false));
                    break;
                case "openCast":
                    Class<?> actionType = Class.forName("com.android.cast.dlna.dmr.CastAction");
                    android.os.Parcelable action = (android.os.Parcelable) actionType.getConstructor(String.class, String.class, String.class, String.class, String.class)
                            .newInstance(intent.getStringExtra("url"), intent.hasExtra("metadata") ? intent.getStringExtra("metadata") : "", "", "", "");
                    String actionKey = (String) Class.forName("com.android.cast.dlna.dmr.service.RendererInterfaceKt").getField("keyExtraCastAction").get(null);
                    context.startActivity(new Intent().setClassName(context.getPackageName(), "com.fongmi.android.tv.ui.activity.CastActivity")
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra(actionKey, action));
                    break;
                case "config":
                    vodConfigStatus = "loading";
                    VodConfig.load(Config.create(0, intent.getStringExtra("url")), new Callback() {
                        @Override public void success() { vodConfigStatus = "success"; }
                        @Override public void error(String message) { vodConfigStatus = "error: " + message; }
                    });
                    break;
                case "vodProbe":
                    vodProbe = new JSONObject().put("status", "loading");
                    String siteKey = intent.getStringExtra("key");
                    App.execute(() -> {
                        JSONObject probe = new JSONObject();
                        try {
                            com.fongmi.android.tv.bean.Site site = VodConfig.get().getSite(siteKey);
                            com.github.catvod.crawler.Spider spider = site.spider();
                            JSONObject home = new JSONObject(spider.homeContent(false));
                            org.json.JSONArray list = home.optJSONArray("list");
                            if (list == null || list.length() == 0) list = new JSONObject(spider.homeVideoContent()).optJSONArray("list");
                            probe.put("site", site.getKey());
                            probe.put("status", "success");
                            probe.put("count", list == null ? 0 : list.length());
                            if (list != null && list.length() > 0) probe.put("first", list.getJSONObject(0));
                        } catch (Throwable error) {
                            try { probe.put("status", "error").put("error", error.toString()); } catch (org.json.JSONException ignored) {}
                        }
                        vodProbe = probe;
                    });
                    break;
                case "background": Setting.putBackground(intent.getIntExtra("value", 0)); break;
                case "open":
                    context.startActivity(new Intent().setClassName(context.getPackageName(), "com.fongmi.android.tv.ui.activity.VideoActivity")
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra("key", intent.hasExtra("key") ? intent.getStringExtra("key") : "push_agent")
                            .putExtra("id", intent.getStringExtra("url")).putExtra("name", "Playback fixture"));
                    break;
                case "click": ((View) path(activity, intent.getStringExtra("path"))).performClick(); break;
                case "route": invoke(activity, "onFlagClick", invoke(activity, "getFlag")); break;
                case "flag": {
                    VodPagePresenter page = (VodPagePresenter) field(activity, "mPage");
                    invoke(activity, context.getPackageName().endsWith(".tv") ? "onFlagClick" : "onItemClick",
                            page.flags().get(intent.getIntExtra("value", 0)));
                    break;
                }
                case "episode": {
                    VodPagePresenter page = (VodPagePresenter) field(activity, "mPage");
                    invoke(activity, context.getPackageName().endsWith(".tv") ? "setEpisodeActivated" : "onItemClick",
                            page.flag().getEpisodes().get(intent.getIntExtra("value", 0)));
                    break;
                }
                case "player": invoke(activity, "onPlayerClick", intent.getIntExtra("value", 2)); break;
                case "decode": invoke(activity, "onDecode", true); break;
                case "subtitle": player.setSub(Sub.from(intent.getStringExtra("url"))); break;
                case "speed": player.setSpeed(intent.getFloatExtra("value", 1.5f)); break;
                case "danmakuReconcile":
                    if (context.getPackageName().endsWith(".tv")) invoke(activity, "reconcilePlaybackUiState");
                    else invoke(activity, "prepareDanmaku", player.getDanmaku());
                    break;
                case "danmakuRefresh": invoke(activity, "setDanmaku", intent.hasExtra("url")
                        ? com.fongmi.android.tv.bean.Danmaku.from(intent.getStringExtra("url")) : player.getDanmaku()); break;
                case "speedHold": invoke(activity, "onSpeedUp"); break;
                case "speedRelease": invoke(activity, "onSpeedEnd"); break;
                case "speedKeyHold": {
                    long now = android.os.SystemClock.uptimeMillis();
                    activity.dispatchKeyEvent(new android.view.KeyEvent(now, now, android.view.KeyEvent.ACTION_DOWN,
                            android.view.KeyEvent.KEYCODE_DPAD_UP, 0));
                    activity.dispatchKeyEvent(new android.view.KeyEvent(now, now, android.view.KeyEvent.ACTION_DOWN,
                            android.view.KeyEvent.KEYCODE_DPAD_UP, 1, 0, -1, 0, android.view.KeyEvent.FLAG_LONG_PRESS));
                    break;
                }
                case "speedKeyRelease": {
                    long now = android.os.SystemClock.uptimeMillis();
                    activity.dispatchKeyEvent(new android.view.KeyEvent(now, now, android.view.KeyEvent.ACTION_UP,
                            android.view.KeyEvent.KEYCODE_DPAD_UP, 0));
                    break;
                }
                case "danmakuFixture": {
                    StringBuilder xml = new StringBuilder("<i>");
                    for (int i = 0; i < 1200; i++) xml.append("<d p='").append(i * .2)
                            .append(",1,25,16777215'>Smooth frame ").append(i).append("</d>");
                    xml.append("</i>");
                    player.resetDanmu();
                    master.flame.danmaku.danmaku.model.android.DanmakuContext config =
                            (master.flame.danmaku.danmaku.model.android.DanmakuContext) field(activity, "mDanmakuContext");
                    java.util.Map<Integer,Integer> lines = new java.util.HashMap<>(); lines.put(1, 3);
                    config.setMaximumLines(lines).setScrollSpeedFactor(1.8f);
                    player.setDanmuVisible(true);
                    ((View) path(activity, "danmaku")).setVisibility(View.VISIBLE);
                    player.prepareDanmu(new com.fongmi.android.tv.player.danmu.Parser(xml.toString()), config);
                    break;
                }
                case "mediaPause": player.getSession().getController().getTransportControls().pause(); break;
                case "mediaPlay": player.getSession().getController().getTransportControls().play(); break;
                case "mediaSeek": player.getSession().getController().getTransportControls().seekTo(intent.getLongExtra("position", 0)); break;
                case "mediaNext": player.getSession().getController().getTransportControls().skipToNext(); break;
                case "mediaPrevious": player.getSession().getController().getTransportControls().skipToPrevious(); break;
                case "seekBar":
                    ((CustomSeekView) path(activity, "control.seek")).onScrubStop(null, intent.getLongExtra("position", 0), false);
                    break;
                case "gestureSeek":
                    int offset = intent.getIntExtra("value", 5_000);
                    if (context.getPackageName().endsWith(".tv")) {
                        invoke(activity, "onSeeking", offset);
                        invoke(activity, "onSeekTo", offset);
                    } else {
                        invoke(activity, "onSeek", offset);
                        invoke(activity, "onSeekEnd", offset);
                    }
                    break;
                case "back": activity.onBackPressed(); break;
                case "pip": invoke(activity, "onUserLeaveHint"); break;
                case "rotate": activity.setRequestedOrientation(intent.getIntExtra("value", 1)); break;
                case "finish": activity.finish(); break;
                case "forget": lastFlow = null; lastPlayer = null; lastDirect = null; break;
            }
            JSONObject data = new JSONObject();
            data.put("command", command);
            data.put("wallConfigStatus", wallConfigStatus);
            data.put("wallConfigUrl", com.fongmi.android.tv.api.config.WallConfig.getUrl());
            java.io.File wallpaper = com.fongmi.android.tv.utils.FileUtil.getWall(0);
            if (wallpaper.isFile()) {
                java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
                try (java.io.FileInputStream stream = new java.io.FileInputStream(wallpaper)) {
                    byte[] bytes = new byte[8192]; int length;
                    while ((length = stream.read(bytes)) != -1) digest.update(bytes, 0, length);
                }
                data.put("wallHash", android.util.Base64.encodeToString(digest.digest(), android.util.Base64.NO_WRAP));
            }
            data.put("subtitleSelected", SubtitleFixtureDialog.selected);
            data.put("subtitleDownload", subtitleDownloadStatus);
            com.fongmi.android.tv.ui.dialog.SearchSubtitleDialog subtitleDialog = searchSubtitleDialog(activity);
            if (subtitleDialog != null && subtitleDialog.getView() != null) {
                com.fongmi.android.tv.viewmodel.SubtitleViewModel model = new androidx.lifecycle.ViewModelProvider(subtitleDialog).get(com.fongmi.android.tv.viewmodel.SubtitleViewModel.class);
                com.fongmi.android.tv.subtitle.SubtitleSearchState state = model.searchResults().getValue();
                if (state != null) {
                    data.put("subtitlePhase", state.phase.name()); data.put("subtitleScreen", state.screen.name()); data.put("subtitlePage", state.page);
                    data.put("subtitleQuery", state.query); data.put("subtitleSource", state.source.name()); data.put("subtitlePreference", state.preference.name());
                    data.put("subtitleUnavailable", new org.json.JSONArray(state.unavailable));
                    JSONObject counts = new JSONObject(); org.json.JSONArray entries = new org.json.JSONArray();
                    for (com.fongmi.android.tv.bean.Subtitle item : state.items) {
                        String source = item.getSource() == null ? "fixture" : item.getSource(); counts.put(source,counts.optInt(source)+1);
                        if (entries.length() < 5) entries.put(new JSONObject().put("name", item.getName()).put("source", source)
                                .put("language",item.getLanguage()).put("external",item.isExternal()));
                    }
                    data.put("subtitleSources", counts); data.put("subtitleEntries", entries);
                }
                View root = subtitleDialog.requireView();
                androidx.recyclerview.widget.RecyclerView list = root.findViewById(com.fongmi.android.tv.R.id.recycler);
                data.put("subtitleCount", list.getAdapter().getItemCount());
                data.put("subtitlePrevious", root.findViewById(com.fongmi.android.tv.R.id.previous).isEnabled());
                data.put("subtitleNext", root.findViewById(com.fongmi.android.tv.R.id.next).isEnabled());
                data.put("subtitleSearchCount", SubtitleFixtureDialog.searches);
                data.put("subtitleDetailsCount", SubtitleFixtureDialog.details);
                data.put("subtitleEmptyText", ((android.widget.TextView) root.findViewById(com.fongmi.android.tv.R.id.empty)).getText());
                data.put("subtitleStatus", ((android.widget.TextView) root.findViewById(com.fongmi.android.tv.R.id.status)).getText());
                JSONObject colors = new JSONObject();
                for (int id : new int[]{com.fongmi.android.tv.R.id.search, com.fongmi.android.tv.R.id.source,
                        com.fongmi.android.tv.R.id.preference, com.fongmi.android.tv.R.id.websites,
                        com.fongmi.android.tv.R.id.input, com.fongmi.android.tv.R.id.empty}) {
                    android.widget.TextView item = root.findViewById(id);
                    int surface = com.google.android.material.color.MaterialColors.getColor(item, com.google.android.material.R.attr.colorSurface);
                    android.content.res.ColorStateList tint = item.getBackgroundTintList();
                    int background = tint == null ? surface : androidx.core.graphics.ColorUtils.compositeColors(
                            tint.getColorForState(item.getDrawableState(), tint.getDefaultColor()), surface);
                    int text = androidx.core.graphics.ColorUtils.compositeColors(item.getCurrentTextColor(), background);
                    colors.put(item.getResources().getResourceEntryName(id), new JSONObject().put("text", text).put("background", background)
                            .put("contrast", androidx.core.graphics.ColorUtils.calculateContrast(text, background)));
                }
                data.put("subtitleColors", colors);
            }
            data.put("liveConfigStatus", liveConfigStatus);
            data.put("vodConfigStatus", vodConfigStatus);
            data.put("vodSites", VodConfig.get().getSites().size());
            data.put("vodConfigUrl", VodConfig.getUrl());
            if (activity != null && activity.getClass().getSimpleName().equals("VideoActivity")) {
                VodPagePresenter page = (VodPagePresenter) field(activity, "mPage");
                data.put("selectedFlag", page.flag() == null ? "" : page.flag().getFlag());
                data.put("selectedEpisodeIndex", page.episodeIndex());
                if (page.history() != null) {
                    data.put("historyKey", page.history().getKey());
                    data.put("historyCid", page.history().getCid());
                }
            }
            if (vodProbe != null) data.put("vodProbe", vodProbe);
            data.put("liveSources", LiveConfig.get().getLives().size());
            data.put("liveConfigUrl", LiveConfig.getUrl());
            org.json.JSONArray liveNames = new org.json.JSONArray();
            for (com.fongmi.android.tv.bean.Live live : LiveConfig.get().getLives()) liveNames.put(live.getName());
            data.put("liveNames", liveNames);
            data.put("removeAd", Setting.isRemoveAd());
            data.put("activity", activity == null ? JSONObject.NULL : activity.getClass().getSimpleName());
            if (lastFlow != null) {
                data.put("phase", lastFlow.state().getPhase().name());
                data.put("request", lastFlow.requestGeneration());
                data.put("targetPosition", lastFlow.state().getPosition());
                data.put("recovery", lastFlow.recovery().phase().name());
                data.put("episode", lastFlow.state().getEpisode() == null ? "" : lastFlow.state().getEpisode().getName());
            }
            if (lastDirect != null) {
                data.put("phase", lastDirect.state().getPhase().name());
                data.put("sourceGeneration", lastDirect.generation());
                data.put("canAutoplay", lastDirect.playWhenReady(true));
            }
            if (lastPlayer != null) {
                data.put("engine", lastPlayer.getPlayer());
                data.put("position", lastPlayer.getPosition());
                data.put("duration", lastPlayer.getDuration());
                data.put("playing", lastPlayer.isPlaying());
                data.put("speed", lastPlayer.getSpeed());
                data.put("videoWidth", lastPlayer.getVideoWidth());
                data.put("videoHeight", lastPlayer.getVideoHeight());
                if (lastPlayer.exo() != null && lastPlayer.exo().getCurrentMediaItem() != null
                        && lastPlayer.exo().getCurrentMediaItem().localConfiguration != null) {
                    data.put("mimeType", lastPlayer.exo().getCurrentMediaItem().localConfiguration.mimeType);
                }
            }
            if (activity != null && activity.getClass().getSimpleName().equals("VideoActivity")) {
                com.fongmi.android.tv.ui.custom.PlaybackDanmakuView danmaku =
                        (com.fongmi.android.tv.ui.custom.PlaybackDanmakuView) path(activity, "danmaku");
                data.put("danmakuReady", danmaku.isPrepared());
                data.put("danmakuPaused", danmaku.isPaused());
                data.put("danmakuTime", danmaku.getCurrentTime());
                data.put("danmakuScroll", danmaku.getConfig() == null ? -1 : danmaku.getConfig().scrollSpeedFactor);
                org.json.JSONArray comments = new org.json.JSONArray();
                master.flame.danmaku.danmaku.model.IDanmakus visible = danmaku.getCurrentVisibleDanmakus();
                if (visible != null) visible.forEachSync(new master.flame.danmaku.danmaku.model.IDanmakus.Consumer<master.flame.danmaku.danmaku.model.BaseDanmaku, Void>() {
                    public int accept(master.flame.danmaku.danmaku.model.BaseDanmaku comment) {
                        try { comments.put(new JSONObject().put("index",comment.index).put("left",comment.getLeft())
                                .put("time",comment.getTimer().currMillisecond)); }
                        catch (org.json.JSONException ignored) {}
                        return ACTION_CONTINUE;
                    }
                });
                data.put("danmakuComments", comments);
                data.put("fullscreen", invoke(activity, "isFullscreen"));
                data.put("pip", activity.isInPictureInPictureMode());
                for (String name : new String[]{"widget.progress", "widget.center", "widget.seek"}) {
                    try { data.put(name, ((View) path(activity, name)).getVisibility()); }
                    catch (ReflectiveOperationException ignored) { }
                }
            }
            if (activity != null && activity.getClass().getSimpleName().equals("CastActivity")) {
                data.put("rendererState", String.valueOf(invoke(activity, "getState")));
                data.put("title", ((android.widget.TextView) path(activity, "widget.title")).getText());
                data.put("rendererConnected", field(field(activity, "mRenderer"), "peer") != null);
            }
            if (activity != null && activity.getClass().getSimpleName().equals("LiveActivity")) {
                Channel channel = (Channel) field(activity, "mChannel");
                if (channel != null) {
                    data.put("channel", channel.getName());
                    data.put("group", channel.getGroup().getName());
                    data.put("tvgId", channel.getTvgId());
                    data.put("tvgName", channel.getTvgName());
                    data.put("epgCount", channel.getData().getList().size());
                    data.put("epgText", ((android.widget.TextView) path(activity, "widget.play")).getText());
                }
            }
            setResultCode(Activity.RESULT_OK);
            setResultData(data.toString());
        } catch (Throwable error) {
            android.util.Log.e("RefactorDevice", "Test command failed", error);
            setResultCode(1);
            setResultData("{\"error\":\"" + error.getClass().getSimpleName() + ": " + String.valueOf(error.getMessage()).replace('"', '\'') + "\"}");
        }
    }

    private static Object field(Object object, String name) throws ReflectiveOperationException {
        for (Class<?> owner = object.getClass(); owner != null; owner = owner.getSuperclass()) {
            try { Field field = owner.getDeclaredField(name); field.setAccessible(true); return field.get(object); }
            catch (NoSuchFieldException ignored) { }
        }
        throw new NoSuchFieldException(name);
    }

    private static SubtitleFixtureDialog subtitleDialog(Activity activity) {
        if (!(activity instanceof androidx.fragment.app.FragmentActivity)) return null;
        for (androidx.fragment.app.Fragment fragment : ((androidx.fragment.app.FragmentActivity) activity).getSupportFragmentManager().getFragments()) {
            if (fragment instanceof SubtitleFixtureDialog) return (SubtitleFixtureDialog) fragment;
        }
        return null;
    }

    private static com.fongmi.android.tv.ui.dialog.SearchSubtitleDialog searchSubtitleDialog(Activity activity) {
        if (!(activity instanceof androidx.fragment.app.FragmentActivity)) return null;
        for (androidx.fragment.app.Fragment fragment : ((androidx.fragment.app.FragmentActivity) activity).getSupportFragmentManager().getFragments()) {
            if (fragment instanceof com.fongmi.android.tv.ui.dialog.SearchSubtitleDialog) return (com.fongmi.android.tv.ui.dialog.SearchSubtitleDialog) fragment;
        }
        return null;
    }

    private static Object path(Activity activity, String path) throws ReflectiveOperationException {
        Object value = field(activity, "mBinding");
        for (String component : path.split("\\.")) value = field(value, component);
        return value;
    }

    private static Object invoke(Object object, String name, Object... args) throws ReflectiveOperationException {
        for (Class<?> owner = object.getClass(); owner != null; owner = owner.getSuperclass()) {
            for (Method method : owner.getDeclaredMethods()) {
                if (!method.getName().equals(name) || method.getParameterCount() != args.length) continue;
                boolean matches = true;
                for (int i = 0; i < args.length; i++) if (args[i] != null && !method.getParameterTypes()[i].isPrimitive()
                        && !method.getParameterTypes()[i].isInstance(args[i])) matches = false;
                if (!matches) continue;
                method.setAccessible(true);
                return method.invoke(object, args);
            }
        }
        throw new NoSuchMethodException(name);
    }
}
