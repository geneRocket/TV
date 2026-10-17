package com.fongmi.android.tv.model;

import com.fongmi.android.tv.bean.Result;
import com.fongmi.android.tv.bean.Site;
import com.fongmi.android.tv.utils.TaskScheduler;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

public class SiteSearchSessionTest {
    private final Scheduler scheduler = new Scheduler();
    private final Backend backend = new Backend();
    private final List<QueueExecutor> executors = new ArrayList<>();
    private final SiteSearchSession session = new SiteSearchSession(backend, () -> {
        QueueExecutor executor = new QueueExecutor();
        executors.add(executor);
        return executor;
    }, scheduler);
    private int completed;

    @Test public void automaticSearchSkipsDisabledAndConfigLockedSourcesBeforeDeduplication() {
        Site disabled = site("disabled");
        disabled.setChangeable(Integer.valueOf(2));
        Site locked = site("locked");
        locked.setChangeable(Integer.valueOf(0));
        Site duplicateDisabled = site("enabled");
        duplicateDisabled.setChangeable(Integer.valueOf(2));
        session.start(Arrays.asList(disabled, locked, duplicateDisabled, site("enabled")), "film", true, () -> completed++);
        executors.get(0).runAll();
        scheduler.runImmediate();
        assertEquals(Collections.singletonList("enabled:film"), backend.searches);
        assertEquals(1, completed);
        assertFalse(session.hasPending());
    }

    @Test public void manualSearchStillIncludesSourcesExcludedFromAutomaticSwitching() {
        Site disabled = site("disabled");
        disabled.setChangeable(Integer.valueOf(2));
        Site locked = site("locked");
        locked.setChangeable(Integer.valueOf(0));
        session.start(Arrays.asList(disabled, locked), "film", false, () -> completed++);
        executors.get(0).runAll();
        scheduler.runImmediate();
        assertEquals(Arrays.asList("disabled:film", "locked:film"), backend.searches);
        assertEquals(1, completed);
    }

    @Test public void automaticSearchWithNoEligibleSourcesSettlesWithoutWorkers() {
        Site disabled = site("disabled");
        disabled.setChangeable(Integer.valueOf(2));
        session.start(Collections.singletonList(disabled), "film", true, () -> completed++);
        assertTrue(executors.isEmpty());
        scheduler.runImmediate();
        assertEquals(1, completed);
        assertFalse(session.hasPending());
        assertTrue(scheduler.tasks.isEmpty());
    }

    @Test public void deduplicatesScopedSitesAndTrimsQuery() {
        session.start(Arrays.asList(site("a/x"), null, site("a/x"), site("b/x")), "  film  ", false, () -> completed++);
        executors.get(0).runAll();
        scheduler.runImmediate();
        assertEquals(Arrays.asList("a/x:film", "b/x:film"), backend.searches);
        assertEquals(1, completed);
        assertFalse(session.hasPending());
        assertTrue(scheduler.tasks.isEmpty());
    }

    @Test public void deadlineCancelsWorkersAndRejectsLateResults() {
        session.start(Collections.singletonList(site("a")), "film", true, () -> completed++);
        String token = session.getToken();
        Runnable lateWorker = executors.get(0).tasks.get(0);
        Runnable timeout = scheduler.delayed();
        timeout.run();
        timeout.run();
        lateWorker.run();
        Result late = result(token);
        assertFalse(session.accepts(late));
        assertFalse(session.hasPending());
        assertTrue(backend.cancelled.contains(token));
        assertTrue(executors.get(0).isShutdown());
        assertTrue(backend.searches.isEmpty());
        assertEquals(1, completed);
    }

    @Test public void restartIgnoresOldDeadlineAndCompletion() {
        session.start(Collections.singletonList(site("a")), "old", false, () -> completed += 100);
        Runnable deadline = scheduler.delayed();
        executors.get(0).runAll();
        Runnable completion = scheduler.immediate();
        session.start(Collections.singletonList(site("b")), "new", false, () -> completed++);
        deadline.run();
        completion.run();
        assertTrue(session.hasPending());
        assertEquals(0, completed);
        executors.get(1).runAll();
        scheduler.runImmediate();
        assertEquals(1, completed);
    }

    @Test public void blankQueryDoesNotAllocateWorkers() {
        session.start(Collections.singletonList(site("a")), "  ", false, null);
        scheduler.runImmediate();
        assertTrue(executors.isEmpty());
        assertFalse(session.hasPending());
    }

    @Test public void completedSearchAcceptsAlreadyPostedResultsUntilCancelled() {
        session.start(Collections.singletonList(site("a")), "film", false, () -> completed++);
        Result result = result(session.getToken());
        executors.get(0).runAll();
        scheduler.runImmediate();
        assertTrue(session.accepts(result));
        session.cancel();
        assertFalse(session.accepts(result));
        assertTrue(scheduler.tasks.isEmpty());
    }

    @Test public void sharedPoolSurvivesCancellationAndLimitsSubmittedWorkers() {
        QueueExecutor shared = new QueueExecutor();
        Backend firstBackend = new Backend();
        Backend secondBackend = new Backend();
        SiteSearchSession first = new SiteSearchSession(firstBackend, () -> shared, scheduler, false, 2);
        SiteSearchSession second = new SiteSearchSession(secondBackend, () -> shared, scheduler, false, 1);
        first.start(Arrays.asList(site("a"), site("b"), site("c")), "old", false, null);
        assertEquals(2, shared.tasks.size());
        first.cancel();
        assertFalse(shared.isShutdown());
        second.start(Arrays.asList(site("x"), site("y")), "new", false, () -> completed++);
        shared.runAll();
        scheduler.runImmediate();
        assertTrue(firstBackend.searches.isEmpty());
        assertEquals(Arrays.asList("x:new", "y:new"), secondBackend.searches);
        assertEquals(1, completed);
        assertFalse(shared.isShutdown());
        second.close();
        assertFalse(shared.isShutdown());
    }

    private static Result result(String token) {
        return new Result() { @Override public String getRequestToken() { return token; } };
    }

    private static Site site(String key) {
        return new Site() { @Override public String getKey() { return key; } };
    }

    private static final class Backend implements SiteSearchSession.Backend {
        final List<String> searches = new ArrayList<>();
        final List<String> cancelled = new ArrayList<>();
        public void search(Site site, String keyword, String token) { searches.add(site.getKey() + ":" + keyword); }
        public void cancel(String token) { cancelled.add(token); }
        public void finish(String token) {}
    }

    private static final class Scheduler implements TaskScheduler {
        final Map<Runnable, Long> tasks = new LinkedHashMap<>();
        public void post(Runnable task, long delay) { tasks.put(task, delay); }
        public void remove(Runnable task) { tasks.remove(task); }
        Runnable delayed() { return tasks.entrySet().stream().filter(e -> e.getValue() > 0).findFirst().get().getKey(); }
        Runnable immediate() { return tasks.entrySet().stream().filter(e -> e.getValue() == 0).findFirst().get().getKey(); }
        void runImmediate() { immediate().run(); }
    }

    private static final class QueueExecutor extends AbstractExecutorService {
        final List<Runnable> tasks = new ArrayList<>();
        boolean shutdown;
        public void execute(Runnable task) { tasks.add(task); }
        void runAll() { for (Runnable task : new ArrayList<>(tasks)) task.run(); tasks.clear(); }
        public void shutdown() { shutdown = true; }
        public List<Runnable> shutdownNow() { shutdown = true; List<Runnable> pending = new ArrayList<>(tasks); tasks.clear(); return pending; }
        public boolean isShutdown() { return shutdown; }
        public boolean isTerminated() { return shutdown; }
        public boolean awaitTermination(long timeout, TimeUnit unit) { return shutdown; }
    }
}
