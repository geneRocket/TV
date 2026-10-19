package com.fongmi.android.tv.player;

import org.junit.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;

import static org.junit.Assert.*;

public class PlaybackCommandsTest {
    @Test public void remoteRefreshTypesAndPathsKeepTheirMeaning() {
        String path = "https://host/sub.srt?token=A%2FB&name=中文";
        assertEquals(PlaybackCommand.Type.DETAIL, PlaybackCommand.fromRefresh("detail", null).getType());
        assertEquals(PlaybackCommand.Type.RELOAD, PlaybackCommand.fromRefresh("player", null).getType());
        assertEquals(PlaybackCommand.Type.SUBTITLE, PlaybackCommand.fromRefresh("subtitle", path).getType());
        assertEquals(path, PlaybackCommand.fromRefresh("subtitle", path).getPath());
        assertEquals(PlaybackCommand.Type.DANMAKU, PlaybackCommand.fromRefresh("danmaku", path).getType());
        assertEquals(path, PlaybackCommand.fromRefresh("danmaku", path).getPath());
        assertNull(PlaybackCommand.fromRefresh("live", path));
        assertNull(PlaybackCommand.fromRefresh("unknown", path));
        assertNull(PlaybackCommand.fromRefresh(null, path));
    }

    @Test public void playerChangeBeforeDeliveryDoesNotRedirectToNewPlayer() {
        Queue<Runnable> main = new ArrayDeque<>();
        List<PlaybackCommand> old = new ArrayList<>(), replacement = new ArrayList<>();
        boolean[] firstCurrent = {true};
        PlaybackCommands first = new PlaybackCommands(main::add, () -> firstCurrent[0], old::add);
        PlaybackCommands second = new PlaybackCommands(main::add, () -> !firstCurrent[0], replacement::add);
        first.send(PlaybackCommand.fromRefresh("subtitle", "first.srt"));
        firstCurrent[0] = false;
        drain(main);
        assertTrue(old.isEmpty());
        assertTrue(replacement.isEmpty());
        second.send(PlaybackCommand.fromRefresh("subtitle", "second.srt"));
        drain(main);
        assertEquals("second.srt", replacement.get(0).getPath());
    }

    @Test public void releasedPageReceivesNeitherPendingNorFutureCommands() {
        Queue<Runnable> main = new ArrayDeque<>();
        List<PlaybackCommand> received = new ArrayList<>();
        PlaybackCommands commands = new PlaybackCommands(main::add, () -> true, received::add);
        commands.send(PlaybackCommand.fromRefresh("player", null));
        commands.close();
        commands.send(PlaybackCommand.fromRefresh("danmaku", "feed.xml"));
        drain(main);
        assertTrue(received.isEmpty());
    }

    @Test public void releasingEngineDiscardsPendingCommandsEvenIfPageBecomesCurrentAgain() {
        Queue<Runnable> main = new ArrayDeque<>();
        List<PlaybackCommand> received = new ArrayList<>();
        PlaybackCommands commands = new PlaybackCommands(main::add, () -> true, received::add);
        commands.send(PlaybackCommand.fromRefresh("detail", null));
        commands.invalidate();
        commands.send(PlaybackCommand.fromRefresh("player", null));
        drain(main);
        assertEquals(1, received.size());
        assertEquals(PlaybackCommand.Type.RELOAD, received.get(0).getType());
    }

    @Test public void localCommandsAreSynchronousAndIgnoreUnsupportedRefreshes() {
        List<PlaybackCommand> received = new ArrayList<>();
        PlaybackCommands commands = new PlaybackCommands(Runnable::run, () -> true, received::add);
        commands.send(null);
        commands.send(PlaybackCommand.fromRefresh("danmaku", "/storage/emulated/0/弹幕.xml"));
        assertEquals(1, received.size());
        assertEquals("/storage/emulated/0/弹幕.xml", received.get(0).getPath());
    }

    private static void drain(Queue<Runnable> main) {
        while (!main.isEmpty()) main.remove().run();
    }
}
