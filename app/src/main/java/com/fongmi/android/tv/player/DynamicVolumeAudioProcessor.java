package com.fongmi.android.tv.player;

import android.content.Context;
import android.media.AudioManager;
import android.util.Log;

import androidx.media3.common.audio.BaseAudioProcessor;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.Setting;

import java.nio.ByteBuffer;

public class DynamicVolumeAudioProcessor extends BaseAudioProcessor {

    private static final long VOLUME_CACHE_MS = 1000;
    private static final double TARGET_GAIN = 1D;

    private final AudioManager manager;
    private AudioFormat audioFormat;
    private double gain;
    private int bytesPerSample;
    private double analyzedRms;
    private double analyzedMaxGain;
    private double volume;
    private long lastTime;
    private long lastLog;

    public DynamicVolumeAudioProcessor() {
        this.manager = (AudioManager) App.get().getSystemService(Context.AUDIO_SERVICE);
    }

    @Override
    protected AudioFormat onConfigure(AudioFormat inputAudioFormat) {
        gain = TARGET_GAIN;
        audioFormat = inputAudioFormat;
        bytesPerSample = audioFormat.bytesPerFrame / Math.max(1, audioFormat.channelCount);
        return audioFormat;
    }

    private double getVolume() {
        if (System.currentTimeMillis() - lastTime < VOLUME_CACHE_MS) return volume;
        lastTime = System.currentTimeMillis();
        int max = manager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        return volume = max == 0 ? 0D : manager.getStreamVolume(AudioManager.STREAM_MUSIC) / (double) max;
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
            if (System.currentTimeMillis() - lastLog > 1000) {
                lastLog = System.currentTimeMillis();
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
        int numSamples = (limit - position) / bytesPerSample;
        if (numSamples == 0) {
            analyzedRms = 0D;
            analyzedMaxGain = Double.POSITIVE_INFINITY;
            return false;
        }

        double sum = 0D;
        double maxGain = Double.POSITIVE_INFINITY;

        if (bytesPerSample == 2) {
            for (int i = position; i < limit; i += 2) {
                short sample = inputBuffer.getShort(i);
                double sampleDouble = sample;
                sum += sampleDouble * sampleDouble;
                int abs = sample == Short.MIN_VALUE ? Short.MAX_VALUE : Math.abs(sample);
                maxGain = Math.min(maxGain, (double) Short.MAX_VALUE / Math.max(1, abs));
            }
        } else if (bytesPerSample == 4) {
            for (int i = position; i < limit; i += 4) {
                int sample = inputBuffer.getInt(i);
                double sampleDouble = sample;
                sum += sampleDouble * sampleDouble;
                int abs = sample == Integer.MIN_VALUE ? Integer.MAX_VALUE : Math.abs(sample);
                maxGain = Math.min(maxGain, (double) Integer.MAX_VALUE / Math.max(1, abs));
            }
        } else if (bytesPerSample == 8) {
            for (int i = position; i < limit; i += 8) {
                long sample = inputBuffer.getLong(i);
                double sampleDouble = sample;
                sum += sampleDouble * sampleDouble;
                double abs = sample == Long.MIN_VALUE ? (double) Long.MAX_VALUE : Math.abs(sampleDouble);
                maxGain = Math.min(maxGain, (double) Long.MAX_VALUE / Math.max(1D, abs));
            }
        } else {
            analyzedRms = 0D;
            analyzedMaxGain = Double.POSITIVE_INFINITY;
            return false;
        }

        analyzedRms = Math.sqrt(sum / numSamples);
        analyzedMaxGain = maxGain;
        return true;
    }


    private void applyGain(ByteBuffer inputBuffer, double gain) {
        final int position = inputBuffer.position();
        final int limit = inputBuffer.limit();
        ByteBuffer outputBuffer = replaceOutputBuffer(limit - position);

        if (bytesPerSample == 2) {
            for (int i = position; i < limit; i += 2) {
                outputBuffer.putShort(saturateToShort(inputBuffer.getShort(i) * gain));
            }
        } else if (bytesPerSample == 4) {
            for (int i = position; i < limit; i += 4) {
                outputBuffer.putInt(saturateToInt(inputBuffer.getInt(i) * gain));
            }
        } else if (bytesPerSample == 8) {
            for (int i = position; i < limit; i += 8) {
                outputBuffer.putLong(saturateToLong(inputBuffer.getLong(i) * gain));
            }
        } else {
            outputBuffer.put(inputBuffer);
        }

        inputBuffer.position(limit);
        outputBuffer.flip();
    }

    private short saturateToShort(double value) {
        long rounded = Math.round(value);
        if (rounded > Short.MAX_VALUE) return Short.MAX_VALUE;
        if (rounded < Short.MIN_VALUE) return Short.MIN_VALUE;
        return (short) rounded;
    }

    private int saturateToInt(double value) {
        long rounded = Math.round(value);
        if (rounded > Integer.MAX_VALUE) return Integer.MAX_VALUE;
        if (rounded < Integer.MIN_VALUE) return Integer.MIN_VALUE;
        return (int) rounded;
    }

    private long saturateToLong(double value) {
        if (value > Long.MAX_VALUE) return Long.MAX_VALUE;
        if (value < Long.MIN_VALUE) return Long.MIN_VALUE;
        return Math.round(value);
    }

    @Override
    protected void onReset() {
        gain = TARGET_GAIN;
        bytesPerSample = 0;
        analyzedRms = 0D;
        analyzedMaxGain = Double.POSITIVE_INFINITY;
        audioFormat = AudioFormat.NOT_SET;
    }

}
