package com.fongmi.android.tv.ui.player;

import com.fongmi.android.tv.event.ErrorEvent;
import com.fongmi.android.tv.player.PlaybackRecovery;

/** Owns the ordered TV VOD recovery flow. */
public final class TvVodRecoveryController {

    public interface Port {
        PlaybackRecovery.Action decide(ErrorEvent error);
        void retry();
        void toggleDecode();
        void reloadExo(ErrorEvent error);
        void switchPlayerAndRetry();
        void stopWithError(ErrorEvent error);
        boolean mayChangeSource();
        boolean usesParse();
        boolean selectNextParse();
        void resetParse();
        boolean selectNextFlag();
        void recoverSource();
        boolean hasRecoveryWork();
        boolean isSourceSwitching();
        void continueSourceSwitch();
    }

    private final Port port;

    public TvVodRecoveryController(Port port) {
        this.port = port;
    }

    public void handle(ErrorEvent error) {
        switch (port.decide(error)) {
            case RETRY: port.retry(); break;
            case TOGGLE_DECODE: port.toggleDecode(); break;
            case RELOAD_EXO: port.reloadExo(error); break;
            case SWITCH_PLAYER: port.switchPlayerAndRetry(); break;
            case ADVANCE:
                port.stopWithError(error);
                continueFlow();
                break;
            case STOP: port.stopWithError(error); break;
        }
    }

    private void continueFlow() {
        if (advance()) return;
        if (port.isSourceSwitching()) port.continueSourceSwitch();
    }

    private boolean advance() {
        if (!port.mayChangeSource()) return false;
        if (port.usesParse()) {
            if (port.selectNextParse()) return true;
            port.resetParse();
        }
        if (port.selectNextFlag()) return true;
        port.recoverSource();
        return port.hasRecoveryWork();
    }
}
