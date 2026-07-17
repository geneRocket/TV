package com.fongmi.android.tv.player.exo;

import android.content.Context;
import android.os.Handler;

import androidx.annotation.NonNull;
import androidx.media3.common.util.Log;
import androidx.media3.exoplayer.Renderer;
import androidx.media3.exoplayer.audio.AudioRendererEventListener;
import androidx.media3.exoplayer.audio.AudioSink;
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector;
import androidx.media3.exoplayer.video.VideoRendererEventListener;

import com.fongmi.android.tv.player.DynamicVolumeRenderersFactory;
import com.fongmi.android.tv.player.Players;

import java.util.ArrayList;

import io.github.anilbeesetti.nextlib.media3ext.ffdecoder.FfmpegAudioRenderer;
import io.github.anilbeesetti.nextlib.media3ext.ffdecoder.FfmpegVideoRenderer;

public class NextRenderersFactory extends DynamicVolumeRenderersFactory {

    private static final String TAG = NextRenderersFactory.class.getSimpleName();

    public NextRenderersFactory(@NonNull Context context, int decode) {
        super(context);
        setEnableDecoderFallback(true);
        setExtensionRendererMode(decode == Players.SOFT ? EXTENSION_RENDERER_MODE_PREFER : EXTENSION_RENDERER_MODE_ON);
    }

    @Override
    protected void buildAudioRenderers(@NonNull Context context, int extensionRendererMode, @NonNull MediaCodecSelector mediaCodecSelector, boolean enableDecoderFallback, @NonNull AudioSink audioSink, @NonNull Handler eventHandler, @NonNull AudioRendererEventListener eventListener, @NonNull ArrayList<Renderer> out) {
        // 先让父类添加标准的 MediaCodec (硬件) 渲染器
        super.buildAudioRenderers(context, extensionRendererMode, mediaCodecSelector, enableDecoderFallback, audioSink, eventHandler, eventListener, out);

        // 修改点 2: 移除原来的 index 计算逻辑，直接 add 到列表末尾。
        // 列表末尾意味着优先级最低 (Fallback)。
        try {
            Renderer renderer = new FfmpegAudioRenderer(eventHandler, eventListener, audioSink);
            out.add(renderer);
            Log.i(TAG, "Loaded FfmpegAudioRenderer.");
        } catch (Throwable e) {
            Log.e(TAG, "Error instantiating Ffmpeg extension", e);
        }
    }

    @Override
    protected void buildVideoRenderers(@NonNull Context context, int extensionRendererMode, @NonNull MediaCodecSelector mediaCodecSelector, boolean enableDecoderFallback, @NonNull Handler eventHandler, @NonNull VideoRendererEventListener eventListener, long allowedVideoJoiningTimeMs, @NonNull ArrayList<Renderer> out) {
        // 先让父类添加标准的 MediaCodec (硬件) 渲染器
        super.buildVideoRenderers(context, extensionRendererMode, mediaCodecSelector, enableDecoderFallback, eventHandler, eventListener, allowedVideoJoiningTimeMs, out);

        // 修改点 3: 移除 index 计算，直接 add 到列表末尾。
        try {
            Renderer renderer = new FfmpegVideoRenderer(allowedVideoJoiningTimeMs, eventHandler, eventListener, MAX_DROPPED_VIDEO_FRAME_COUNT_TO_NOTIFY);
            out.add(renderer);
            Log.i(TAG, "Loaded FfmpegVideoRenderer.");
        } catch (Throwable e) {
            Log.e(TAG, "Error instantiating Ffmpeg extension", e);
        }
    }
}
