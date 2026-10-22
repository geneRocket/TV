package com.fongmi.android.tv.ui.custom;

import androidx.leanback.widget.ArrayObjectAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.fongmi.android.tv.bean.Channel;
import com.fongmi.android.tv.bean.EpgData;
import com.fongmi.android.tv.bean.Group;
import com.fongmi.android.tv.utils.CallbackScope;
import com.fongmi.android.tv.utils.TaskScheduler;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** TV live list contents and playback highlights. Focus and playback stay with the page. */
public final class LiveListSelection implements AutoCloseable {
    public interface Rows<T> {
        void replace(List<T> items);
        int indexOf(T item);
        boolean computingLayout();
        void changed(int index);
        void post(Runnable task);
        void remove(Runnable task);
    }

    public static <T> Rows<T> rows(ArrayObjectAdapter adapter, RecyclerView view) {
        return new Rows<T>() {
            @Override public void replace(List<T> items) { adapter.setItems(items, null); }
            @Override public int indexOf(T item) { return adapter.indexOf(item); }
            @Override public boolean computingLayout() { return view.isComputingLayout(); }
            @Override public void changed(int index) { adapter.notifyArrayItemRangeChanged(index, 1); }
            @Override public void post(Runnable task) { view.post(task); }
            @Override public void remove(Runnable task) { view.removeCallbacks(task); }
        };
    }

    private final Updates<Channel> channels;
    private final Updates<EpgData> programs;
    private Group displayedGroup;
    private Channel channel;
    private EpgData program;
    private boolean closed;

    public LiveListSelection(Rows<Channel> channels, Rows<EpgData> programs) {
        this.channels = new Updates<>(channels);
        this.programs = new Updates<>(programs);
    }

    public void showGroup(Group group) {
        if (closed || displayedGroup == group) return;
        channels.replace(group.getChannel());
        displayedGroup = group;
    }

    public void showPrograms(List<EpgData> items) {
        if (closed) return;
        programs.replace(items);
        program = null;
    }

    public void activate(Channel selected) {
        if (closed) return;
        if (channel != null && !Objects.equals(channel, selected)) {
            channel.setSelected(false);
            channels.refresh(channel);
        }
        if (selected != null) {
            selected.setSelected(true);
            channels.refresh(selected);
        }
        channel = selected;
    }

    public void activate(EpgData selected) {
        if (closed) return;
        if (program != null && !Objects.equals(program, selected)) {
            program.setSelected(false);
            programs.refresh(program);
        }
        selected.setSelected(true);
        programs.refresh(selected);
        program = selected;
    }

    public void reset() {
        if (closed) return;
        channels.replace(Collections.emptyList());
        programs.replace(Collections.emptyList());
        displayedGroup = null;
        channel = null;
        program = null;
    }

    @Override public void close() {
        if (closed) return;
        closed = true;
        channels.callbacks.close();
        programs.callbacks.close();
        displayedGroup = null;
        channel = null;
        program = null;
    }

    private static final class Updates<T> {
        private final Rows<T> rows;
        private final CallbackScope callbacks;

        Updates(Rows<T> rows) {
            this.rows = rows;
            callbacks = new CallbackScope(new TaskScheduler() {
                @Override public void post(Runnable task, long delay) { rows.post(task); }
                @Override public void remove(Runnable task) { rows.remove(task); }
            });
        }

        void replace(List<T> items) {
            callbacks.reset();
            rows.replace(items);
        }

        void refresh(T item) {
            int index = rows.indexOf(item);
            if (index < 0) return;
            if (rows.computingLayout()) callbacks.post(() -> refresh(item));
            else rows.changed(index);
        }
    }
}
