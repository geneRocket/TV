package com.fongmi.quickjs.utils;

import com.github.catvod.net.HttpCallScope;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ModuleTest {

    private static final String URL = "https://modules.invalid/index.js";
    private static final String OLD_SCRIPT = "export const epoch = 1;";
    private static final String NEW_SCRIPT = "export const epoch = 2;";

    @Rule public final TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void firstDiskHitCannotOverwriteACompletedRefresh() throws Exception {
        File file = folder.newFile("index.js");
        Files.write(file.toPath(), OLD_SCRIPT.getBytes(StandardCharsets.UTF_8));
        assertTrue(file.setLastModified(System.currentTimeMillis() - TimeUnit.HOURS.toMillis(1)));
        OkHttpClient client = new OkHttpClient.Builder().addInterceptor(chain -> content(chain.request())).build();
        Module module = module(client, file);

        assertEquals(OLD_SCRIPT, module.fetch(URL));
        assertEquals(NEW_SCRIPT, module.fetch(URL));
        assertEquals(NEW_SCRIPT, new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8));
    }

    @Test
    public void activeWaiterRetriesWhenTheDownloadOwnerCancels() throws Exception {
        File file = new File(folder.getRoot(), "index.js");
        CountDownLatch downloading = new CountDownLatch(1);
        CountDownLatch resumeOwner = new CountDownLatch(1);
        AtomicInteger requests = new AtomicInteger();
        OkHttpClient client = new OkHttpClient.Builder().addInterceptor(chain -> {
            if (requests.incrementAndGet() == 1) {
                downloading.countDown();
                try {
                    if (!resumeOwner.await(5, TimeUnit.SECONDS)) throw new IOException("fixture timed out");
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IOException(e);
                }
                if (chain.call().isCanceled()) throw new IOException("Canceled");
            }
            return content(chain.request());
        }).build();
        Module module = module(client, file);
        HttpCallScope ownerScope = new HttpCallScope();
        FutureTask<String> owner = new FutureTask<>(() -> ownerScope.call(() -> module.fetch(URL)));
        FutureTask<String> waiter = new FutureTask<>(() -> new HttpCallScope().call(() -> module.fetch(URL)));
        Thread ownerThread = new Thread(owner);
        Thread waiterThread = new Thread(waiter);
        try {
            ownerThread.start();
            assertTrue(downloading.await(5, TimeUnit.SECONDS));
            waiterThread.start();
            awaitWaiting(waiterThread);
            ownerScope.close();
            resumeOwner.countDown();

            assertEquals("", owner.get(5, TimeUnit.SECONDS));
            assertEquals(NEW_SCRIPT, waiter.get(5, TimeUnit.SECONDS));
            assertEquals(2, requests.get());
        } finally {
            resumeOwner.countDown();
            owner.cancel(true);
            waiter.cancel(true);
        }
    }

    private static void awaitWaiting(Thread thread) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (thread.getState() != Thread.State.WAITING && System.nanoTime() < deadline) Thread.sleep(1);
        assertEquals(Thread.State.WAITING, thread.getState());
    }

    private static Module module(OkHttpClient client, File file) {
        return new Module(new InlineExecutor(), url -> file,
                url -> HttpCallScope.track(client.newCall(new Request.Builder().url(url).build())));
    }

    private static Response content(Request request) {
        return new Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body(ResponseBody.create(NEW_SCRIPT, null)).build();
    }

    /** Model a refresh worker that finishes before the initial caller continues. */
    private static final class InlineExecutor extends AbstractExecutorService {
        @Override public void execute(Runnable command) { command.run(); }
        @Override public void shutdown() {}
        @Override public List<Runnable> shutdownNow() { return Collections.emptyList(); }
        @Override public boolean isShutdown() { return false; }
        @Override public boolean isTerminated() { return false; }
        @Override public boolean awaitTermination(long timeout, TimeUnit unit) { return false; }
    }
}
