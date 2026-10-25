package com.fongmi.android.tv.player;

import java.util.function.LongSupplier;

/** Media time interpolated on the render thread without querying an Android player off its looper. */
final class DanmakuClock {
    private final LongSupplier uptime;
    private long anchorTime;
    private double anchorPosition;
    private double correction;
    private static final long CORRECTION_MS = 250;
    private float speed = 1;
    private boolean running;

    DanmakuClock(LongSupplier uptime) { this.uptime = uptime; }

    synchronized void update(long position, float speed, boolean running) {
        long now = uptime.getAsLong();
        float rate = validSpeed(speed);
        double target = Math.max(0, position);
        if (this.running && running) {
            // Correct coarse audio/player samples over time rather than jumping.
            anchorPosition = position(now);
            double limit = rate * CORRECTION_MS * .5;
            correction = Math.max(-limit, Math.min(limit, target - anchorPosition));
        } else {
            anchorPosition = target;
            correction = 0;
        }
        anchorTime = now;
        this.speed = rate;
        this.running = running;
    }

    synchronized void speed(float value) {
        long now = uptime.getAsLong();
        anchorPosition = position(now);
        anchorTime = now;
        speed = validSpeed(value);
        correction = 0;
    }

    synchronized void seek(long position) {
        anchorPosition = Math.max(0, position);
        anchorTime = uptime.getAsLong();
        correction = 0;
    }

    synchronized void pause() {
        long now = uptime.getAsLong();
        anchorPosition = position(now);
        anchorTime = now;
        running = false;
        correction = 0;
    }

    synchronized long position() { return (long) position(uptime.getAsLong()); }

    private double position(long now) {
        long elapsed = Math.max(0, now - anchorTime);
        return anchorPosition + (running ? elapsed * (double) speed
                + correction * Math.min(1, elapsed / (double) CORRECTION_MS) : 0);
    }

    private static float validSpeed(float speed) {
        return Float.isNaN(speed) || Float.isInfinite(speed) || speed <= 0 ? 1 : speed;
    }
}
