package com.fongmi.android.tv.player;

import android.content.Context;

import androidx.media3.common.audio.AudioProcessor;
import androidx.media3.exoplayer.DefaultRenderersFactory;
import androidx.media3.exoplayer.audio.AudioSink;
import androidx.media3.exoplayer.audio.DefaultAudioSink;

public class DynamicVolumeRenderersFactory extends DefaultRenderersFactory {


    /**
     * @param context A {@link Context}.
     */
    public DynamicVolumeRenderersFactory(Context context) {
        super(context);
    }

    @Override
    protected AudioSink buildAudioSink(Context context, boolean enableFloatOutput, boolean enableAudioTrackPlaybackParams) {
        AudioProcessor[] audioProcessors = new AudioProcessor[]{new DynamicVolumeAudioProcessor()};
        return new DefaultAudioSink.Builder(context).setEnableFloatOutput(enableFloatOutput).setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams).setAudioProcessors(audioProcessors).build();
    }
}