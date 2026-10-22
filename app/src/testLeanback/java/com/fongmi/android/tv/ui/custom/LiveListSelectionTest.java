package com.fongmi.android.tv.ui.custom;

import com.fongmi.android.tv.bean.Channel;
import com.fongmi.android.tv.bean.EpgData;
import com.fongmi.android.tv.bean.Group;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;

public class LiveListSelectionTest {
    private final FakeRows<Channel> channels = new FakeRows<>();
    private final FakeRows<EpgData> programs = new FakeRows<>();
    private final LiveListSelection selection = new LiveListSelection(channels, programs);

    @Test public void changingPlaybackMovesHighlightAndNullClearsIt() {
        Channel first = channel("1");
        Channel second = channel("2");
        selection.showGroup(group(first, second));
        selection.activate(first);
        selection.activate(second);
        assertFalse(first.isSelected());
        assertTrue(second.isSelected());
        assertEquals(Arrays.asList(0, 0, 1), channels.changed);
        selection.activate((Channel) null);
        assertFalse(second.isSelected());
        assertEquals(Arrays.asList(0, 0, 1, 1), channels.changed);
    }

    @Test public void selectingProgramDoesNotAlterChannelHighlight() {
        Channel current = channel("1");
        selection.showGroup(group(current));
        selection.activate(current);
        EpgData first = program("first");
        EpgData second = program("second");
        selection.showPrograms(Arrays.asList(first, second));
        selection.activate(first);
        selection.activate(second);
        assertTrue(current.isSelected());
        assertFalse(first.isSelected());
        assertTrue(second.isSelected());
        assertEquals(Arrays.asList(0, 0, 1), programs.changed);
    }

    @Test public void revisitingSameGroupPreservesRowsUntilReset() {
        Group group = group(channel("1"));
        selection.showGroup(group);
        selection.showGroup(group);
        assertEquals(1, channels.replacements);
        selection.reset();
        assertTrue(channels.items.isEmpty());
        assertTrue(programs.items.isEmpty());
        selection.showGroup(group);
        assertEquals(3, channels.replacements);
    }

    @Test public void deferredRefreshResolvesCurrentIndexAfterLayout() {
        Channel first = channel("1");
        Channel second = channel("2");
        selection.showGroup(group(first, second));
        channels.layout = true;
        selection.activate(second);
        assertTrue(channels.changed.isEmpty());
        channels.items.remove(first);
        channels.layout = false;
        channels.runPending();
        assertEquals(Arrays.asList(0), channels.changed);
    }

    @Test public void removedItemIsNotNotifiedAfterLayout() {
        Channel first = channel("1");
        selection.showGroup(group(first));
        channels.layout = true;
        selection.activate(first);
        channels.items.clear();
        channels.layout = false;
        channels.runPending();
        assertTrue(channels.changed.isEmpty());
    }

    @Test public void replacingProgramsCancelsEvenAlreadyDequeuedOldRefresh() {
        EpgData item = program("same");
        selection.showPrograms(Arrays.asList(item));
        programs.layout = true;
        selection.activate(item);
        Runnable late = programs.pending.get(0);
        selection.showPrograms(Arrays.asList(program("same")));
        assertTrue(programs.pending.isEmpty());
        programs.layout = false;
        late.run();
        assertTrue(programs.changed.isEmpty());
    }

    @Test public void closeCancelsBothListsAndRejectsLateCalls() {
        Channel channel = channel("1");
        EpgData item = program("first");
        selection.showGroup(group(channel));
        selection.showPrograms(Arrays.asList(item));
        channels.layout = programs.layout = true;
        selection.activate(channel);
        selection.activate(item);
        Runnable lateChannel = channels.pending.get(0);
        Runnable lateProgram = programs.pending.get(0);
        selection.close();
        selection.close();
        assertTrue(channels.pending.isEmpty());
        assertTrue(programs.pending.isEmpty());
        channels.layout = programs.layout = false;
        lateChannel.run();
        lateProgram.run();
        selection.reset();
        selection.showGroup(group(channel("2")));
        selection.showPrograms(new ArrayList<>());
        selection.activate(channel("3"));
        selection.activate(program("second"));
        assertTrue(channels.changed.isEmpty());
        assertTrue(programs.changed.isEmpty());
        assertEquals(1, channels.replacements);
        assertEquals(1, programs.replacements);
    }

    private static Channel channel(String number) {
        // Keep the real equality/selection behavior without Android TextUtils in a JVM test.
        return new Channel() {
            @Override public String getNumber() { return number; }
            @Override public String getName() { return number; }
        };
    }

    private static Group group(Channel... channels) {
        Group result = new Group("fixture");
        result.setChannel(Arrays.asList(channels));
        return result;
    }

    private static EpgData program(String title) {
        return new EpgData() {
            @Override public String getTitle() { return title; }
            @Override public String getStart() { return ""; }
            @Override public String getEnd() { return ""; }
        };
    }

    private static class FakeRows<T> implements LiveListSelection.Rows<T> {
        List<T> items = new ArrayList<>();
        final List<Integer> changed = new ArrayList<>();
        final List<Runnable> pending = new ArrayList<>();
        boolean layout;
        int replacements;
        @Override public void replace(List<T> items) { this.items = new ArrayList<>(items); replacements++; }
        @Override public int indexOf(T item) { return items.indexOf(item); }
        @Override public boolean computingLayout() { return layout; }
        @Override public void changed(int index) { changed.add(index); }
        @Override public void post(Runnable task) { pending.add(task); }
        @Override public void remove(Runnable task) { pending.remove(task); }
        void runPending() {
            List<Runnable> tasks = new ArrayList<>(pending);
            pending.clear();
            for (Runnable task : tasks) task.run();
        }
    }
}
