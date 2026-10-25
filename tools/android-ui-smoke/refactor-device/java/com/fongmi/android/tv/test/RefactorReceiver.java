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
    private static volatile JSONObject vodProbe;

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
