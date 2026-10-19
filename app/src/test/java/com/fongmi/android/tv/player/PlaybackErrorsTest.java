package com.fongmi.android.tv.player;

import com.fongmi.android.tv.event.ErrorEvent;

import org.junit.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;

import static org.junit.Assert.*;

public class PlaybackErrorsTest {
    @Test public void oneControllersErrorsCannotReachAnotherControllersPage() {
        Queue<Runnable> main = new ArrayDeque<>();
        List<ErrorEvent> first = new ArrayList<>(), second = new ArrayList<>();
        PlaybackErrors a = new PlaybackErrors(main::add, first::add);
        PlaybackErrors b = new PlaybackErrors(main::add, second::add);
        ErrorEvent error = ErrorEvent.url(1, 4001);
        a.report(error);
        assertTrue(first.isEmpty());
        drain(main);
        assertEquals(1, first.size());
        assertSame(error, first.get(0));
        assertTrue(second.isEmpty());
        b.report(ErrorEvent.parse());
        drain(main);
        assertEquals(1, first.size());
        assertEquals(ErrorEvent.Type.PARSE, second.get(0).getType());
    }

    @Test public void stoppedPlaybackDiscardsQueuedErrorButAcceptsNewPlaybackErrors() {
        Queue<Runnable> main = new ArrayDeque<>();
        List<ErrorEvent> received = new ArrayList<>();
        PlaybackErrors errors = new PlaybackErrors(main::add, received::add);
        errors.report(ErrorEvent.timeout());
        errors.invalidate();
        ErrorEvent next = ErrorEvent.extract("next source failed");
        errors.report(next);
        drain(main);
        assertEquals(1, received.size());
        assertSame(next, received.get(0));
    }

    @Test public void releasedControllerDropsQueuedAndFutureErrors() {
        Queue<Runnable> main = new ArrayDeque<>();
        List<ErrorEvent> received = new ArrayList<>();
        PlaybackErrors errors = new PlaybackErrors(main::add, received::add);
        errors.report(ErrorEvent.url(1));
        errors.close();
        errors.close();
        errors.report(ErrorEvent.timeout());
        drain(main);
        assertTrue(received.isEmpty());
    }

    @Test public void mainThreadDeliveryCanSynchronouslyStopPlayback() {
        List<ErrorEvent> received = new ArrayList<>();
        PlaybackErrors[] owner = new PlaybackErrors[1];
        owner[0] = new PlaybackErrors(Runnable::run, error -> {
            received.add(error);
            owner[0].close();
        });
        owner[0].report(ErrorEvent.flag());
        assertEquals(1, received.size());
        owner[0].report(ErrorEvent.timeout());
        assertEquals(1, received.size());
    }

    private static void drain(Queue<Runnable> main) {
        while (!main.isEmpty()) main.remove().run();
    }
}
