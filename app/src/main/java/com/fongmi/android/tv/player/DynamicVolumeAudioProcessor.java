package com.fongmi.android.tv.player;

import androidx.media3.common.audio.BaseAudioProcessor;

import com.fongmi.android.tv.player.pojo.RmsMaxGain;

import java.nio.ByteBuffer;
import java.util.Optional;

public class DynamicVolumeAudioProcessor extends BaseAudioProcessor {
    private static final double maxVolume = 9000;
    private static final double targetGain = 1;

    AudioFormat audioFormat;
    double gain;

    protected AudioFormat onConfigure(AudioFormat inputAudioFormat) {
        gain = 1;
        audioFormat = inputAudioFormat;
        return audioFormat;
    }


    @Override
    public void queueInput(ByteBuffer inputBuffer) {
        RmsMaxGain rmsMaxGain = calculateVolume(inputBuffer);
        Double currentVolume = Optional.ofNullable(rmsMaxGain).map(RmsMaxGain::getRms).orElse(null);
        Double maxGain = Optional.ofNullable(rmsMaxGain).map(RmsMaxGain::getMaxGain).orElse(9999.0);
        if (currentVolume != null && currentVolume != 0) {
            double currentVolumeAfterGain = currentVolume * gain;
            if (currentVolumeAfterGain > maxVolume) {
                gain = Math.max(gain * 0.99, maxVolume / currentVolume);
            } else {
                if (gain > targetGain) {
                    gain = Math.max(gain * 0.99, targetGain);
                } else {
                    gain = Math.min(gain * 1.01, targetGain);
                }
            }
        }
        gain = Math.min(gain, maxGain);
        applyGain(inputBuffer, gain);
    }

    private RmsMaxGain calculateVolume(ByteBuffer inputBuffer) {
        final int position = inputBuffer.position();
        final int limit = inputBuffer.limit();


        final int bytesPerFrame = audioFormat.bytesPerFrame;
        final int outputChannels = audioFormat.channelCount;
        final int bytesPerSample = bytesPerFrame / outputChannels;


        int numSamples = (limit - position) / (bytesPerSample);
        if (numSamples == 0) {
            return null;
        }
        double sum = 0;
        double maxGain = 99999;
        for (int i = 0; i < numSamples; i++) {
            double sample = 0;
            if (bytesPerSample == 2) {
                sample = inputBuffer.getShort();
                maxGain = Math.min(maxGain, Short.MAX_VALUE / Math.abs(sample));
            } else if (bytesPerSample == 4) {
                sample = inputBuffer.getInt();
                maxGain = Math.min(maxGain, Integer.MAX_VALUE / Math.abs(sample));
            } else if (bytesPerSample == 8) {
                sample = inputBuffer.getLong();
                maxGain = Math.min(maxGain, Long.MAX_VALUE / Math.abs(sample));
            }
            sum += sample * sample;
        }
        inputBuffer.position(position);
        inputBuffer.limit(limit);
        double rms = Math.sqrt(sum / numSamples);
        RmsMaxGain rmsMaxGain = new RmsMaxGain();
        rmsMaxGain.setRms(rms);
        rmsMaxGain.setMaxGain(maxGain);
        return rmsMaxGain;
    }


    private void applyGain(ByteBuffer inputBuffer, double gain) {
        final int position = inputBuffer.position();
        final int limit = inputBuffer.limit();

        final int bytesPerFrame = audioFormat.bytesPerFrame;
        final int outputChannels = audioFormat.channelCount;
        final int bytesPerSample = bytesPerFrame / outputChannels;

        int numSamples = (limit - position) / (bytesPerSample);

        ByteBuffer outputBuffer = replaceOutputBuffer(limit - position);
        for (int i = 0; i < numSamples; i++) {
            if (bytesPerSample == 2) {
                outputBuffer.putShort((short) Math.round((double) inputBuffer.getShort() * gain));
            } else if (bytesPerSample == 4) {
                outputBuffer.putInt((int) Math.round((double) inputBuffer.getInt() * gain));
            } else if (bytesPerSample == 8) {
                outputBuffer.putLong(Math.round((double) inputBuffer.getLong() * gain));
            }
        }
        inputBuffer.position(limit);
        outputBuffer.flip();
    }

    protected void onReset() {
        gain = 1;
        audioFormat = AudioFormat.NOT_SET;
    }

}