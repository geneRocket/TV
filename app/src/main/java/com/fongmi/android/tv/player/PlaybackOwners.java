package com.fongmi.android.tv.player;

/** Application composition root; HTTP and UI both query this same ownership authority. */
public final class PlaybackOwners {
    private PlaybackOwners() {}
    private static final PlaybackOwnership<PlaybackController> OWNERS = new PlaybackOwnership<>(PlaybackController::revokeOwnership);
    public static PlaybackController current() { return OWNERS.current(); }
    public static void acquire(PlaybackController owner) { OWNERS.acquire(owner); }
    public static boolean owns(PlaybackController owner) { return OWNERS.owns(owner); }
    public static boolean release(PlaybackController owner) { return OWNERS.release(owner); }
    public static long lease(PlaybackController owner) { return OWNERS.lease(owner); }
    public static boolean dispatch(long lease, PlaybackInput input) {
        return input != null && OWNERS.dispatch(lease, owner -> owner.dispatchInput(input));
    }
    public static boolean dispatchSystem(long lease, PlaybackInput input, boolean dismissal) {
        return input != null && OWNERS.dispatch(lease, owner -> {
            if (PlaybackSystemActionPolicy.accepts(dismissal, owner.hasForegroundSurface())) owner.dispatchInput(input);
        });
    }
}
