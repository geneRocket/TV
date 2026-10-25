package com.fongmi.android.tv.ui.custom;

import android.content.Context;
import android.util.AttributeSet;
import java.util.function.LongSupplier;
import master.flame.danmaku.danmaku.model.android.DanmakuContext;
import master.flame.danmaku.danmaku.parser.BaseDanmakuParser;
import master.flame.danmaku.ui.widget.DanmakuView;

/** Uses the supplied media clock while keeping rendering paced by Choreographer. */
public final class PlaybackDanmakuView extends DanmakuView {
    private LongSupplier playbackTime;

    public PlaybackDanmakuView(Context context) { super(context); }
    public PlaybackDanmakuView(Context context, AttributeSet attrs) { super(context, attrs); }
    public PlaybackDanmakuView(Context context, AttributeSet attrs, int style) { super(context, attrs, style); }

    public void setPlaybackTime(LongSupplier playbackTime) { this.playbackTime = playbackTime; }

    @Override public void prepare(BaseDanmakuParser parser, DanmakuContext context) {
        // Non-block mode delegates timer advancement to updateTimer. Vsync pacing is
        // essential here: the library's separate update thread would otherwise busy-loop.
        context.updateMethod = 0;
        super.prepare(parser, context);
        handler.enableNonBlockMode(true);
        // The library calculates idle waits in wall time, which misses comments at 3x/5x.
        // Pausing/hiding still stops drawing through the controller.
        handler.setIdleSleep(false);
    }

    @Override public long getCurrentTime() {
        return playbackTime == null ? super.getCurrentTime() : playbackTime.getAsLong();
    }
}
