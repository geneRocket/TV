package com.fongmi.android.tv.player.vod;

import com.fongmi.android.tv.event.ErrorEvent;
import com.fongmi.android.tv.player.PlaybackRecovery;
import com.fongmi.android.tv.player.PlayerType;
import com.fongmi.android.tv.player.RecoveryWindow;
import com.fongmi.android.tv.utils.TaskScheduler;

/** Shared recovery policy; platform ports perform effects and never decide retry ordering. */
public final class VodRecoveryFlow implements AutoCloseable {
    public interface Port {
        boolean mayRecover();
        int playerType();
        void started();
        void capturePosition();
        void cancelSourceWork();
        void cancelled(boolean expired);
        void retry();
        void toggleDecode();
        void reloadExo(ErrorEvent error);
        void switchPlayer(ErrorEvent error);
        void stopWithError(ErrorEvent error, boolean advancing);
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

    private final VodPlaybackCoordinator coordinator;
    private final Port port;
    private final RecoveryWindow window;
    private final PlaybackRecovery policy = new PlaybackRecovery();
    private boolean closed;

    public VodRecoveryFlow(VodPlaybackCoordinator coordinator, Port port, TaskScheduler scheduler) {
        this.coordinator = coordinator;
        this.port = port;
        window = new RecoveryWindow(scheduler, () -> cancel(true));
    }

    public boolean isRecovering() { return !closed && window.isRecovering(); }
    public boolean isActive() { return !closed && window.isActive(); }
    public RecoveryWindow.Phase phase() { return window.getPhase(); }

    public void onPhase(VodPlaybackPhase phase) {
        if (closed) return;
        if (phase == VodPlaybackPhase.READY) {
            window.ready();
            policy.reset();
        } else window.unstable();
    }

    public void recover(ErrorEvent error) {
        if (closed || error == null || !port.mayRecover()) return;
        boolean active = window.isActive();
        if (!window.begin()) return;
        if (!active) port.started();
        switch (policy.next(error, port.playerType() == PlayerType.EXO, port.playerType(), true, true)) {
            case RETRY: port.retry(); break;
            case TOGGLE_DECODE: port.toggleDecode(); break;
            case RELOAD_EXO: port.reloadExo(error); break;
            case SWITCH_PLAYER: port.switchPlayer(error); break;
            case ADVANCE:
                port.stopWithError(error, true);
                if (!advance()) window.cancel();
                break;
            case STOP:
                window.cancel();
                port.stopWithError(error, false);
                break;
        }
    }

    private boolean advance() {
        if (port.mayChangeSource()) {
            if (port.usesParse()) {
                if (port.selectNextParse()) return true;
                port.resetParse();
            }
            if (port.selectNextFlag()) return true;
            port.recoverSource();
            if (port.hasRecoveryWork()) return true;
        }
        if (port.isSourceSwitching()) {
            port.continueSourceSwitch();
            return port.hasRecoveryWork();
        }
        return false;
    }

    public void takeControl() {
        if (closed) return;
        if (window.isRecovering()) {
            port.capturePosition();
            port.cancelSourceWork();
            coordinator.stop();
        }
        reset();
    }

    public void reset() {
        if (closed) return;
        window.reset();
        policy.reset();
    }

    public void cancel(boolean expired) {
        if (closed) return;
        window.cancel();
        port.cancelSourceWork();
        coordinator.stop();
        port.cancelled(expired);
    }

    @Override public void close() {
        if (closed) return;
        closed = true;
        window.cancel();
    }
}
