package com.fongmi.android.tv.player.exo;

import android.content.Context;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.accessibility.CaptioningManager;

import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.TrackSelectionOverride;
import androidx.media3.common.Tracks;
import androidx.media3.exoplayer.DefaultLoadControl;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.LoadControl;
import androidx.media3.exoplayer.RenderersFactory;
import androidx.media3.exoplayer.source.MediaSource;
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector;
import androidx.media3.exoplayer.trackselection.TrackSelector;
import androidx.media3.ui.CaptionStyleCompat;
import androidx.media3.ui.PlayerView;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.Setting;
import com.fongmi.android.tv.bean.Drm;
import com.fongmi.android.tv.bean.Sub;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class ExoUtil {

    private static final String EXTRA_FORCE_LIVE = "__force_live";

    public static LoadControl buildLoadControl() {
        int userSettingMs = Setting.getBuffer() * 1000;

        int bufferForPlaybackMs = 1000;
        int bufferForPlaybackAfterRebufferMs = 1500;

        int minBufferMs = Math.max(10000, userSettingMs * 2);

        int maxBufferMs = Math.max(minBufferMs + 10000, userSettingMs * 4);

        return new DefaultLoadControl.Builder()
                .setBufferDurationsMs(
                        minBufferMs,
                        maxBufferMs,
                        bufferForPlaybackMs,
                        bufferForPlaybackAfterRebufferMs)
                .setPrioritizeTimeOverSizeThresholds(true)
                .setBackBuffer(10000, true)
                .build();
    }

    public static TrackSelector buildTrackSelector() {
        DefaultTrackSelector trackSelector = new DefaultTrackSelector(App.get());
        trackSelector.setParameters(trackSelector.buildUponParameters()
                .setPreferredTextLanguage(Locale.getDefault().getISO3Language())
                .setForceHighestSupportedBitrate(true)
                .setTunnelingEnabled(Setting.isTunnel())
                .setAllowVideoMixedMimeTypeAdaptiveness(true)
                .setAllowVideoNonSeamlessAdaptiveness(true));
        return trackSelector;
    }

    public static RenderersFactory buildRenderersFactory(int decode) {
        return new NextRenderersFactory(App.get(), decode);
    }

    public static MediaSource.Factory buildMediaSourceFactory() {
        return new MediaSourceFactory();
    }

    public static CaptionStyleCompat getCaptionStyle() {
        return Setting.isCaption() ? CaptionStyleCompat.createFromCaptionStyle(((CaptioningManager) App.get().getSystemService(Context.CAPTIONING_SERVICE)).getUserStyle()) : new CaptionStyleCompat(Color.WHITE, Color.TRANSPARENT, Color.TRANSPARENT, CaptionStyleCompat.EDGE_TYPE_OUTLINE, Color.BLACK, null);
    }

    public static boolean haveTrack(Tracks tracks, int type) {
        int count = 0;
        for (Tracks.Group trackGroup : tracks.getGroups()) if (trackGroup.getType() == type) count += trackGroup.length;
        return count > 0;
    }

    public static void selectTrack(ExoPlayer player, int group, int track) {
        List<Integer> trackIndices = new ArrayList<>();
        selectTrack(player, group, track, trackIndices);
        setTrackParameters(player, group, trackIndices);
    }

    public static void deselectTrack(ExoPlayer player, int group, int track) {
        List<Integer> trackIndices = new ArrayList<>();
        deselectTrack(player, group, track, trackIndices);
        setTrackParameters(player, group, trackIndices);
    }

    public static void setSubtitleView(PlayerView exo) {
        exo.getSubtitleView().setStyle(getCaptionStyle());
        exo.getSubtitleView().setApplyEmbeddedFontSizes(false);
        exo.getSubtitleView().setApplyEmbeddedStyles(!Setting.isCaption());
        if (Setting.getSubtitleTextSize() != 0) exo.getSubtitleView().setFractionalTextSize(Setting.getSubtitleTextSize());
        if (Setting.getSubtitleBottomPadding() != 0) exo.getSubtitleView().setBottomPaddingFraction(Setting.getSubtitleBottomPadding());
    }

    public static String getMimeType(String path) {
        if (TextUtils.isEmpty(path)) return "";
        path = path.split("\\?", 2)[0].toLowerCase(Locale.US);
        if (path.endsWith(".vtt")) return MimeTypes.TEXT_VTT;
        if (path.endsWith(".ssa") || path.endsWith(".ass")) return MimeTypes.TEXT_SSA;
        if (path.endsWith(".ttml") || path.endsWith(".xml") || path.endsWith(".dfxp")) return MimeTypes.APPLICATION_TTML;
        return MimeTypes.APPLICATION_SUBRIP;
    }

    public static String getMimeType(int errorCode) {
        if (errorCode == PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED || errorCode == PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED || errorCode == PlaybackException.ERROR_CODE_IO_UNSPECIFIED) return MimeTypes.APPLICATION_M3U8;
        return null;
    }

    public static int getRetry(int errorCode) {
        if (errorCode == PlaybackException.ERROR_CODE_IO_UNSPECIFIED) return 2;
        if (errorCode == PlaybackException.ERROR_CODE_DECODER_INIT_FAILED) return 2;
        if (errorCode >= PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED && errorCode <= PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED) return 2;
        if (errorCode >= PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED && errorCode <= PlaybackException.ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED) return 2;
        return 1;
    }

    public static MediaItem getMediaItem(Map<String, String> headers, Uri uri, String mimeType, Drm drm, List<Sub> subs, int decode, boolean forceLive) {
        MediaItem.Builder builder = new MediaItem.Builder().setUri(uri);
        builder.setRequestMetadata(getRequestMetadata(headers, uri, forceLive));
        builder.setSubtitleConfigurations(getSubtitleConfigs(subs));
        if (drm != null) builder.setDrmConfiguration(drm.get());
        if (TextUtils.isEmpty(mimeType) && forceLive) mimeType = guessLiveMimeType(uri);
        if (mimeType != null) builder.setMimeType(mimeType);
        if (forceLive) builder.setLiveConfiguration(new MediaItem.LiveConfiguration.Builder().build());
        builder.setMediaId(uri.toString());
        return builder.build();
    }

    private static String guessLiveMimeType(Uri uri) {
        String value = uri.toString().toLowerCase(Locale.US);
        if (value.contains(".m3u8")) return MimeTypes.APPLICATION_M3U8;
        if (value.contains(".mpd")) return MimeTypes.APPLICATION_MPD;
        return null;
    }

    private static MediaItem.RequestMetadata getRequestMetadata(Map<String, String> headers, Uri uri, boolean forceLive) {
        Bundle extras = new Bundle();
        for (Map.Entry<String, String> header : headers.entrySet()) extras.putString(header.getKey(), header.getValue());
        extras.putBoolean(EXTRA_FORCE_LIVE, forceLive);
        return new MediaItem.RequestMetadata.Builder().setMediaUri(uri).setExtras(extras).build();
    }

    public static boolean isForceLive(MediaItem mediaItem) {
        return mediaItem.requestMetadata != null && mediaItem.requestMetadata.extras != null && mediaItem.requestMetadata.extras.getBoolean(EXTRA_FORCE_LIVE, false);
    }

    private static List<MediaItem.SubtitleConfiguration> getSubtitleConfigs(List<Sub> subs) {
        List<MediaItem.SubtitleConfiguration> configs = new ArrayList<>();
        for (Sub sub : subs) configs.add(sub.getConfig());
        return configs;
    }

    private static void selectTrack(ExoPlayer player, int group, int track, List<Integer> trackIndices) {
        if (group >= player.getCurrentTracks().getGroups().size()) return;
        Tracks.Group trackGroup = player.getCurrentTracks().getGroups().get(group);
        for (int i = 0; i < trackGroup.length; i++) {
            if (i == track || trackGroup.isTrackSelected(i)) trackIndices.add(i);
        }
    }

    private static void deselectTrack(ExoPlayer player, int group, int track, List<Integer> trackIndices) {
        if (group >= player.getCurrentTracks().getGroups().size()) return;
        Tracks.Group trackGroup = player.getCurrentTracks().getGroups().get(group);
        for (int i = 0; i < trackGroup.length; i++) {
            if (i != track && trackGroup.isTrackSelected(i)) trackIndices.add(i);
        }
    }

    private static void setTrackParameters(ExoPlayer player, int group, List<Integer> trackIndices) {
        if (group >= player.getCurrentTracks().getGroups().size()) return;
        player.setTrackSelectionParameters(player.getTrackSelectionParameters().buildUpon().setOverrideForType(new TrackSelectionOverride(player.getCurrentTracks().getGroups().get(group).getMediaTrackGroup(), trackIndices)).build());
    }
}
