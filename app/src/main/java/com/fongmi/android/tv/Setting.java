package com.fongmi.android.tv;

import android.content.SharedPreferences;
import android.content.Intent;
import android.provider.Settings;

import com.fongmi.android.tv.player.Players;
import com.fongmi.android.tv.utils.LanguageUtil;
import com.github.catvod.utils.Prefers;

public class Setting {

    private static SharedPreferences getPref() {
        return Prefers.getPrefers();
    }

    private static SharedPreferences.Editor getEditor() {
        return getPref().edit();
    }

    // region Core Settings

    public static String getDoh() {
        return getPref().getString("doh", "");
    }

    public static void putDoh(String doh) {
        getEditor().putString("doh", doh).apply();
    }

    public static String getProxy() {
        return getPref().getString("proxy", "");
    }

    public static void putProxy(String proxy) {
        getEditor().putString("proxy", proxy).apply();
    }

    public static String getUa() {
        return getPref().getString("ua", "");
    }

    public static void putUa(String ua) {
        getEditor().putString("ua", ua).apply();
    }

    public static int getQuality() {
        return getPref().getInt("quality", 2);
    }

    public static void putQuality(int index) {
        getEditor().putInt("quality", index).apply();
    }

    public static float getThumbnail() {
        return 0.3f * getQuality() + 0.4f;
    }

    public static int getConfigCache() {
        return Math.min(getPref().getInt("config_cache", 0), 2);
    }

    public static void putConfigCache(int value) {
        getEditor().putInt("config_cache", value).apply();
    }

    // endregion

    // region Player Settings

    public static int getPlayer() {
        return getPref().getInt("player", Players.EXO);
    }

    public static void putPlayer(int player) {
        getEditor().putInt("player", player).apply();
    }

    public static int getLivePlayer() {
        return getPref().getInt("player_live", getPlayer());
    }

    public static void putLivePlayer(int player) {
        getEditor().putInt("player_live", player).apply();
    }

    public static int getDecode(int player) {
        return getPref().getInt("decode_" + player, Players.HARD);
    }

    public static void putDecode(int player, int decode) {
        getEditor().putInt("decode_" + player, decode).apply();
    }

    public static int getRender() {
        return getPref().getInt("render", 0);
    }

    public static void putRender(int render) {
        getEditor().putInt("render", render).apply();
    }

    public static boolean isTunnel() {
        return getPref().getBoolean("exo_tunnel", false);
    }

    public static void putTunnel(boolean tunnel) {
        getEditor().putBoolean("exo_tunnel", tunnel).apply();
    }

    public static boolean isPlayWithOthers() {
        return getPref().getBoolean("play_with_others", false);
    }

    public static void putPlayWithOthers(boolean value) {
        getEditor().putBoolean("play_with_others", value).apply();
    }

    public static int getBuffer() {
        return Math.min(Math.max(getPref().getInt("exo_buffer", 0), 1), 15);
    }

    public static void putBuffer(int value) {
        getEditor().putInt("exo_buffer", value).apply();
    }

    public static String getBufferText() {
        return getBufferText(getBuffer());
    }

    public static String getBufferText(int buffer) {
        return Math.min(Math.max(buffer, 1), 15) * 64 + " MB";
    }

    public static int getBufferBytes() {
        return getBuffer() * 64 * 1024 * 1024;
    }

    public static float getVolumeScale() {
        return Math.min(Math.max(Prefers.getFloat("volume_scale", 1.0f), 0f), 1f);
    }

    public static void putVolumeScale(float value) {
        getEditor().putFloat("volume_scale", Math.min(Math.max(value, 0f), 1f)).apply();
    }

    public static float getPlaySpeed() {
        return Prefers.getFloat("play_speed", 1.0f);
    }

    public static void putPlaySpeed(float speed) {
        getEditor().putFloat("play_speed", speed).apply();
    }

    public static int getRtsp() {
        return getPref().getInt("rtsp", 0);
    }

    public static void putRtsp(int value) {
        getEditor().putInt("rtsp", value).apply();
    }

    public static int getHttp() {
        return getPref().getInt("exo_http", 1);
    }

    public static void putHttp(int value) {
        getEditor().putInt("exo_http", value).apply();
    }

    public static int getBackground() {
        return getPref().getInt("background", 2);
    }

    public static void putBackground(int value) {
        getEditor().putInt("background", value).apply();
    }

    // endregion

    // region Danmaku Settings

    public static boolean isDanmu() {
        return getPref().getBoolean("danmu", true);
    }

    public static void putDanmu(boolean value) {
        getEditor().putBoolean("danmu", value).apply();
    }

    public static int getDanmuSpeed() {
        return Math.min(Math.max(getPref().getInt("danmu_speed", 2), 0), 3);
    }

    public static void putDanmuSpeed(int value) {
        getEditor().putInt("danmu_speed", value).apply();
    }

    public static float getDanmuSize() {
        return Math.min(Math.max(Prefers.getFloat("danmu_size", 1.0f), 0.6f), 2.0f);
    }

    public static void putDanmuSize(float size) {
        getEditor().putFloat("danmu_size", size).apply();
    }

    public static int getDanmuLine(int def) {
        return Math.min(Math.max(getPref().getInt("danmu_line", def), 1), 15);
    }

    public static void putDanmuLine(int line) {
        getEditor().putInt("danmu_line", line).apply();
    }

    public static int getDanmuAlpha() {
        return Math.min(Math.max(getPref().getInt("danmu_alpha", 90), 10), 100);
    }

    public static void putDanmuAlpha(int alpha) {
        getEditor().putInt("danmu_alpha", alpha).apply();
    }

    public static boolean isDanmuLoad() {
        return isDanmu();
    }

    public static void putDanmuLoad(boolean value) {
        putDanmu(value);
    }

    // endregion

    // region VOD & Live Settings

    public static String getVodConfigUrls() {
        return getPref().getString("vod_config_urls", "");
    }

    public static void putVodConfigUrls(String urls) {
        getEditor().putString("vod_config_urls", urls).apply();
    }

    public static String getVodConfigDesc() {
        return getPref().getString("vod_config_desc", "");
    }

    public static void putVodConfigDesc(String desc) {
        getEditor().putString("vod_config_desc", desc).apply();
    }

    public static String getLiveConfigUrls() {
        return getPref().getString("live_config_urls", "");
    }

    public static void putLiveConfigUrls(String urls) {
        getEditor().putString("live_config_urls", urls).apply();
    }

    public static String getLiveConfigDesc() {
        return getPref().getString("live_config_desc", "");
    }

    public static void putLiveConfigDesc(String desc) {
        getEditor().putString("live_config_desc", desc).apply();
    }

    public static String getKeep() {
        return getPref().getString("keep", "");
    }

    public static void putKeep(String keep) {
        getEditor().putString("keep", keep).apply();
    }

    public static String getHot() {
        return getPref().getString("hot", "");
    }

    public static void putHot(String hot) {
        getEditor().putString("hot", hot).apply();
    }

    public static boolean isBootLive() {
        return getPref().getBoolean("boot_live", false);
    }

    public static void putBootLive(boolean value) {
        getEditor().putBoolean("boot_live", value).apply();
    }

    public static int getLiveScale() {
        return getPref().getInt("scale_live", getScale());
    }

    public static void putLiveScale(int index) {
        getEditor().putInt("scale_live", index).apply();
    }

    public static boolean isInvert() {
        return getPref().getBoolean("invert", false);
    }

    public static void putInvert(boolean value) {
        getEditor().putBoolean("invert", value).apply();
    }

    public static boolean isAcross() {
        return getPref().getBoolean("across", true);
    }

    public static void putAcross(boolean value) {
        getEditor().putBoolean("across", value).apply();
    }

    public static boolean isChange() {
        return getPref().getBoolean("change", true);
    }

    public static void putChange(boolean value) {
        getEditor().putBoolean("change", value).apply();
    }

    public static void putHomeMenuKey(int value) {
        getEditor().putInt("home_menu_key", value).apply();
    }

    public static int getHomeMenuKey() {
        return getPref().getInt("home_menu_key", 0);
    }

    public static int getHomeUI() {
        return getPref().getInt("home_ui", 1);
    }

    public static void putHomeUI(int value) {
        getEditor().putInt("home_ui", value).apply();
    }

    public static boolean isHomeHistory() {
        return getPref().getBoolean("home_history", true);
    }

    public static void putHomeHistory(boolean value) {
        getEditor().putBoolean("home_history", value).apply();
    }

    public static String getHomeButtons(String def) {
        return getPref().getString("home_buttons", def);
    }

    public static void putHomeButtons(String value) {
        getEditor().putString("home_buttons", value).apply();
    }

    public static String getHomeButtonsSorted(String def) {
        return getPref().getString("home_buttons_sorted", def);
    }

    public static void putHomeButtonsSorted(String value) {
        getEditor().putString("home_buttons_sorted", value).apply();
    }

    // endregion

    // region Display Settings

    public static boolean isDisplaySpeed() {
        return getPref().getBoolean("display_speed", false);
    }

    public static void putDisplaySpeed(boolean value) {
        getEditor().putBoolean("display_speed", value).apply();
    }

    public static boolean isDisplayTime() {
        return getPref().getBoolean("display_time", false);
    }

    public static void putDisplayTime(boolean value) {
        getEditor().putBoolean("display_time", value).apply();
    }

    public static boolean isDisplayVideoTitle() {
        return getPref().getBoolean("display_video_title", false);
    }

    public static void putDisplayVideoTitle(boolean value) {
        getEditor().putBoolean("display_video_title", value).apply();
    }

    public static boolean isDisplayDuration() {
        return getPref().getBoolean("display_duration", false);
    }

    public static void putDisplayDuration(boolean value) {
        getEditor().putBoolean("display_duration", value).apply();
    }

    public static boolean isDisplayMiniProgress() {
        return getPref().getBoolean("display_mini_progress", false);
    }

    public static void putDisplayMiniProgress(boolean value) {
        getEditor().putBoolean("display_mini_progress", value).apply();
    }

    // endregion

    // region Misc Settings

    public static int getReset() {
        return getPref().getInt("reset", 0);
    }

    public static void putReset(int reset) {
        getEditor().putInt("reset", reset).apply();
    }

    public static boolean isCaption() {
        return getPref().getBoolean("caption", false);
    }

    public static boolean hasCaption() {
        return new Intent(Settings.ACTION_CAPTIONING_SETTINGS).resolveActivity(App.get().getPackageManager()) != null;
    }

    public static void putCaption(boolean value) {
        getEditor().putBoolean("caption", value).apply();
    }

    public static boolean isRemoveAd() {
        return getPref().getBoolean("remove_ad", false);
    }

    public static void putRemoveAd(boolean value) {
        getEditor().putBoolean("remove_ad", value).apply();
    }

    public static float getSubtitleTextSize() {
        return Prefers.getFloat("subtitle_text_size", 0);
    }

    public static void putSubtitleTextSize(float size) {
        getEditor().putFloat("subtitle_text_size", size).apply();
    }

    public static float getSubtitleBottomPadding() {
        return Prefers.getFloat("subtitle_bottom_padding", 0);
    }

    public static void putSubtitleBottomPadding(float padding) {
        getEditor().putFloat("subtitle_bottom_padding", padding).apply();
    }

    public static int getLanguage() {
        return getPref().getInt("language", LanguageUtil.locale());
    }

    public static void putLanguage(int lang) {
        getEditor().putInt("language", lang).apply();
    }

    public static int getBackupMode() {
        return getPref().getInt("backup_mode", 1);
    }

    public static void putBackupMode(int mode) {
        getEditor().putInt("backup_mode", mode).apply();
    }

    public static int getParseWebView() {
        return getPref().getInt("parse_webview", 0);
    }

    public static void putParseWebView(int value) {
        getEditor().putInt("parse_webview", value).apply();
    }

    public static boolean isAggregatedSearch() {
        return getPref().getBoolean("aggregated_search", false);
    }

    public static void putAggregatedSearch(boolean value) {
        getEditor().putBoolean("aggregated_search", value).apply();
    }

    public static int getWall() {
        return getPref().getInt("wall", 1);
    }

    public static void putWall(int index) {
        getEditor().putInt("wall", index).apply();
    }

    public static boolean isIncognito() {
        return getPref().getBoolean("incognito", false);
    }

    public static void putIncognito(boolean value) {
        getEditor().putBoolean("incognito", value).apply();
    }

    public static int getScale() {
        return getPref().getInt("scale", 0);
    }

    public static void putScale(int value) {
        getEditor().putInt("scale", value).apply();
    }

    public static int getEpisode() {
        return getPref().getInt("episode", 0);
    }

    public static void putEpisode(int index) {
        getEditor().putInt("episode", index).apply();
    }

    public static int getFlag() {
        return getPref().getInt("flag", 0);
    }

    public static void putFlag(int value) {
        getEditor().putInt("flag", value).apply();
    }

    public static int getFullscreenMenuKey() {
        return getPref().getInt("fullscreen_menu_key", 0);
    }

    public static void putFullscreenMenuKey(int index) {
        getEditor().putInt("fullscreen_menu_key", index).apply();
    }

    public static int getSmallWindowBackKey() {
        return getPref().getInt("small_window_back_key", 0);
    }

    public static void putSmallWindowBackKey(int value) {
        getEditor().putInt("small_window_back_key", value).apply();
    }

    public static boolean isHomeSiteLock() {
        return getPref().getBoolean("home_site_lock", false);
    }

    public static void putHomeSiteLock(boolean value) {
        getEditor().putBoolean("home_site_lock", value).apply();
    }

    public static boolean isHomeDisplayName() {
        return getPref().getBoolean("home_display_name", false);
    }

    public static void putHomeDisplayName(boolean value) {
        getEditor().putBoolean("home_display_name", value).apply();
    }

    public static String getThunderCacheDir() {
        return getPref().getString("thunder_cache_dir", "");
    }

    public static void putThunderCacheDir(String value) {
        getEditor().putString("thunder_cache_dir", value).apply();
    }

    public static int getSize() {
        return getPref().getInt("size", 2);
    }

    public static void putSize(int index) {
        getEditor().putInt("size", index).apply();
    }

    public static int getViewType(int def) {
        return getPref().getInt("viewType", def);
    }

    public static void putViewType(int type) {
        getEditor().putInt("viewType", type).apply();
    }

    public static String getKeyword() {
        return getPref().getString("keyword", "");
    }

    public static void putKeyword(String keyword) {
        getEditor().putString("keyword", keyword).apply();
    }

    public static boolean isBackgroundOn() {
        return getBackground() == 1 || getBackground() == 2;
    }

    public static boolean isBackgroundOff() {
        return getBackground() == 0;
    }

    public static boolean isBackgroundPiP() {
        return getBackground() == 2;
    }

    public static boolean isZhuyin() {
        return getPref().getBoolean("zhuyin", false);
    }

    public static void putZhuyin(boolean value) {
        getEditor().putBoolean("zhuyin", value).apply();
    }

    public static int getSiteMode() {
        return getPref().getInt("site_mode", 1);
    }

    public static void putSiteMode(int value) {
        getEditor().putInt("site_mode", value).apply();
    }

    public static boolean isSiteSearch() {
        return getPref().getBoolean("site_search", false);
    }

    public static void putSiteSearch(boolean value) {
        getEditor().putBoolean("site_search", value).apply();
    }

    public static int getSyncMode() {
        return getPref().getInt("sync_mode", 0);
    }

    public static void putSyncMode(int value) {
        getEditor().putInt("sync_mode", value).apply();
    }

    public static boolean getUpdate() {
        return getPref().getBoolean("update", true);
    }

    public static void putUpdate(boolean value) {
        getEditor().putBoolean("update", value).apply();
    }

    public static void putBackgroundOn(boolean value) {
        getEditor().putBoolean("background_on", value).apply();
    }

    public static void putBackgroundOff(boolean value) {
        getEditor().putBoolean("background_off", value).apply();
    }

    public static void putBackgroundPiP(boolean value) {
        getEditor().putBoolean("background_pip", value).apply();
    }

    // endregion
}
