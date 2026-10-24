package com.fongmi.android.tv.player;

import com.fongmi.android.tv.bean.Result;
import com.fongmi.android.tv.player.resolver.PlaybackSourceResolver;
import com.fongmi.android.tv.player.resolver.ResolvedMedia;
import org.junit.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.Assert.*;

public class PlaybackSourcePipelineTest {
    @Test public void synchronousBackendFailureUsesTheSameTerminalErrorPath() {
        Fixture f = new Fixture(); f.throwOnResolve = true;
        f.start();
        assertEquals(1, f.failure);
        f.last().onResolved(media());
        assertEquals(0, f.success);
    }
    @Test public void replacingARequestRejectsBothOldSuccessAndOldFailure() {
        Fixture f = new Fixture();
        f.start(); PlaybackSourceResolver.Callback old = f.last();
        f.start(); old.onResolved(media()); old.onFailure();
        assertEquals(0, f.success + f.failure);
        f.last().onResolved(media());
        assertEquals(1, f.success);
    }
    @Test public void duplicateTerminalCallbacksCannotPrepareOrReportErrorsTwice() {
        for (boolean success : new boolean[]{false, true}) {
            Fixture f = new Fixture(); f.start();
            if (success) f.last().onResolved(media()); else f.last().onFailure();
            f.last().onResolved(media()); f.last().onFailure();
            assertEquals(1, f.success + f.failure);
        }
    }
    @Test public void revokedOwnersCannotDeliverOrStartNewWork() {
        Fixture f = new Fixture(); f.start();
        f.current = false;
        f.last().onResolved(media()); f.last().onFailure(); f.start();
        assertEquals(1, f.callbacks.size());
        assertEquals(0, f.success + f.failure);
    }
    @Test public void cancelledAndClosedRequestsCannotReachThePlayer() {
        Fixture f = new Fixture(); f.start(); PlaybackSourceResolver.Callback old = f.last();
        f.pipeline.cancel(); old.onResolved(media());
        f.start(); old = f.last(); f.pipeline.close(); f.pipeline.close();
        old.onFailure(); f.start();
        assertEquals(2, f.callbacks.size());
        assertEquals(0, f.success + f.failure);
    }
    @Test public void aPreparedResultMayStartAnotherRequestWithoutOldCleanupCancellingIt() {
        Fixture f = new Fixture(); f.reenter = true;
        f.start(); f.last().onResolved(media());
        assertEquals(2, f.callbacks.size());
        f.reenter = false; f.last().onResolved(media());
        assertEquals(2, f.success);
    }
    private static ResolvedMedia media() { return new ResolvedMedia(null, "https://video.example/a", null, null, null, false, ""); }
    private static final class Fixture implements PlaybackSourcePipeline.Port, PlaybackSourcePipeline.Backend {
        boolean current = true, reenter, throwOnResolve;
        int success, failure;
        final List<PlaybackSourceResolver.Callback> callbacks = new ArrayList<>();
        final PlaybackSourcePipeline pipeline = new PlaybackSourcePipeline(this, this);
        void start() { pipeline.resolve(Result.empty(), true, false); }
        PlaybackSourceResolver.Callback last() { return callbacks.get(callbacks.size() - 1); }
        @Override public boolean isCurrent() { return current; }
        @Override public void resolved(ResolvedMedia media) { success++; if (reenter) start(); }
        @Override public void failed() { failure++; }
        @Override public void resolve(Result result, boolean parse, boolean live, PlaybackSourceResolver.Callback callback) {
            callbacks.add(callback);
            if (throwOnResolve) throw new IllegalStateException("backend unavailable");
        }
        @Override public void cancel() {}
    }
}
