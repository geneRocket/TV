package com.fongmi.android.tv.player;

import android.content.Context;
import android.media.AudioManager;
import android.os.SystemClock;
import android.util.Log;

import androidx.media3.common.C;
import androidx.media3.common.audio.BaseAudioProcessor;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.Setting;

import java.nio.ByteBuffer;

public class DynamicVolumeAudioProcessor extends BaseAudioProcessor {

    private static final long VOLUME_CACHE_MS = 1000;
    private static final double TARGET_GAIN = 1D;

    private final AudioManager manager;
    private double gain;
    private double analyzedRms;
    private double analyzedMaxGain;
    private volatile double volume;
    private volatile long lastTime;
    private boolean updating;
    private long lastLog;

    public DynamicVolumeAudioProcessor() {
        this.manager = (AudioManager) App.get().getSystemService(Context.AUDIO_SERVICE);
        this.volume = 1.0D;
        App.execute(this::updateVolume);
    }

    @Override
    protected AudioFormat onConfigure(AudioFormat inputAudioFormat) {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT) return AudioFormat.NOT_SET;
        gain = TARGET_GAIN;
        return inputAudioFormat;
    }

    private double getVolume() {
        if (manager == null) return 1D;
        long now = SystemClock.elapsedRealtime();
        if (now - lastTime > VOLUME_CACHE_MS && !updating) {
            updating = true;
            App.execute(this::updateVolume);
        }
        return volume;
    }

    private void updateVolume() {
        try {
            int max = manager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
            volume = max == 0 ? 0D : manager.getStreamVolume(AudioManager.STREAM_MUSIC) / (double) max;
        } catch (Exception ignored) {
        } finally {
            lastTime = SystemClock.elapsedRealtime();
            updating = false;
        }
    }

    @Override
    public void queueInput(ByteBuffer inputBuffer) {
        if (!analyzeVolume(inputBuffer)) {
            applyGain(inputBuffer, TARGET_GAIN * Setting.getVolumeScale());
            return;
        }
        double currentVolume = analyzedRms;
        double maxVolume = Setting.getLoudness();
        double volumeScale = Setting.getVolumeScale();
        double volumeRatio = getVolume();
        if (currentVolume != 0 && volumeRatio > 0 && volumeScale > 0) {
            if (gain <= 0D && maxVolume > 0D) gain = TARGET_GAIN;
            double maxGain = maxVolume / (currentVolume * volumeRatio * volumeScale);
            double currentVolumeAfterGain = currentVolume * gain * volumeRatio * volumeScale;
            if (currentVolumeAfterGain > maxVolume) {
                gain = Math.min(gain, maxGain);
            } else if (gain < TARGET_GAIN) {
                gain = Math.min(gain * 1.005D, TARGET_GAIN);
            }
            gain = Math.min(gain, maxGain);
            long now = SystemClock.elapsedRealtime();
            if (now - lastLog > 1000) {
                lastLog = now;
                currentVolumeAfterGain = currentVolume * gain * volumeRatio * volumeScale;
                Log.d("DynamicVolume", "Volume: " + (int) currentVolume + " Ratio: " + String.format("%.2f", volumeRatio) + " Scale: " + String.format("%.2f", volumeScale) + " Gain: " + String.format("%.2f", gain) + " Result: " + (int) currentVolumeAfterGain + " Max: " + (int) maxVolume);
            }
        }
        gain = Math.min(gain, Math.min(TARGET_GAIN, analyzedMaxGain));
        applyGain(inputBuffer, gain * volumeScale);
    }

    private boolean analyzeVolume(ByteBuffer inputBuffer) {
        final int position = inputBuffer.position();
        final int limit = inputBuffer.limit();
        int numSamples = (limit - position) / 2;
        if (numSamples == 0) {
            analyzedRms = 0D;
            analyzedMaxGain = Double.POSITIVE_INFINITY;
            return false;
        }

        double sum = 0D;
        double maxGain = Double.POSITIVE_INFINITY;
        int end = position + numSamples * 2;

        for (int i = position; i < end; i += 2) {
            short sample = inputBuffer.getShort(i);
            double sampleDouble = sample;
            sum += sampleDouble * sampleDouble;
            int abs = sample == Short.MIN_VALUE ? Short.MAX_VALUE : Math.abs(sample);
            maxGain = Math.min(maxGain, (double) Short.MAX_VALUE / Math.max(1, abs));
        }

        analyzedRms = Math.sqrt(sum / numSamples);
        analyzedMaxGain = maxGain;
        return true;
    }


    private void applyGain(ByteBuffer inputBuffer, double gain) {
        final int position = inputBuffer.position();
        final int limit = inputBuffer.limit();
        final int end = position + ((limit - position) / 2) * 2;
        ByteBuffer outputBuffer = replaceOutputBuffer(limit - position);

        for (int i = position; i < end; i += 2) {
            outputBuffer.putShort(saturateToShort(inputBuffer.getShort(i) * gain));
        }
        for (int i = end; i < limit; i++) outputBuffer.put(inputBuffer.get(i));

        inputBuffer.position(limit);
        outputBuffer.flip();
    }

    private short saturateToShort(double value) {
        long rounded = Math.round(value);
        if (rounded > Short.MAX_VALUE) return Short.MAX_VALUE;
        if (rounded < Short.MIN_VALUE) return Short.MIN_VALUE;
        return (short) rounded;
    }

    @Override
    protected void onReset() {
        gain = TARGET_GAIN;
        analyzedRms = 0D;
        analyzedMaxGain = Double.POSITIVE_INFINITY;
        lastLog = 0L;
    }

}
