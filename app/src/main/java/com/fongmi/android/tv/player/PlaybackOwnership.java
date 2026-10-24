package com.fongmi.android.tv.player;

/** One process-wide playback owner. Acquisition revokes the previous owner before any new prepare. */
public final class PlaybackOwnership<T> {
    public interface Handoff<T> { void revoke(T previous); }
    private final Handoff<T> handoff;
    private volatile T current;
    // PendingIntents can outlive this process; a restarted registry must not
    // accidentally accept a previous process's low numbered lease.
    private long generation = java.util.concurrent.ThreadLocalRandom.current().nextLong(1, Long.MAX_VALUE / 2);

    public PlaybackOwnership(Handoff<T> handoff) {
        if (handoff == null) throw new IllegalArgumentException("handoff required");
        this.handoff = handoff;
    }
    public T current() { return current; }
    public boolean owns(T owner) { return owner != null && current == owner; }
    public synchronized void acquire(T owner) {
        if (owner == null) throw new IllegalArgumentException("owner required");
        if (current == owner) return;
        T previous = current;
        current = owner;
        generation++;
        if (previous != null) handoff.revoke(previous);
    }
    public synchronized boolean release(T owner) {
        if (!owns(owner)) return false;
        current = null;
        generation++;
        return true;
    }
    public synchronized long lease(T owner) { return owns(owner) ? generation : 0; }
    public synchronized boolean dispatch(long lease, java.util.function.Consumer<T> command) {
        if (lease <= 0 || current == null || lease != generation) return false;
        command.accept(current);
        return true;
    }
}
