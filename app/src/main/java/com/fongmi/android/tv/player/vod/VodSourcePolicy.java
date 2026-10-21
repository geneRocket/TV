package com.fongmi.android.tv.player.vod;

/** Pure candidate-arrival policy shared by TV and mobile source searches. */
public final class VodSourcePolicy {
    public enum Decision { WAIT, SELECT, CLEAR_SWITCH, FINISH_MANUAL, EMPTY_AUTOMATIC }
    private VodSourcePolicy() {}

    public static Decision decide(boolean settled, boolean loading, boolean pending, boolean first,
                                  boolean automatic, boolean switching, int count, boolean threshold) {
        if (loading || (settled && pending)) return Decision.WAIT;
        if (first && count > 0) return !pending || threshold ? Decision.SELECT : Decision.WAIT;
        if (!settled) return switching && count > 0 ? Decision.SELECT : Decision.WAIT;
        if (switching && count == 0) return Decision.CLEAR_SWITCH;
        if (!automatic) return Decision.FINISH_MANUAL;
        return count == 0 ? Decision.EMPTY_AUTOMATIC : Decision.WAIT;
    }
}
