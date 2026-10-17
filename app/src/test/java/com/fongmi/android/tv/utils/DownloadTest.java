package com.fongmi.android.tv.utils;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import com.github.catvod.net.HttpCallScope;

import static org.junit.Assert.*;

public class DownloadTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    @Test
    public void interruptedBodyDoesNotReplaceWorkingCache() throws Exception {
        File target = temporary.newFile("epg.xml");
        Files.write(target.toPath(), "working cache".getBytes(StandardCharsets.UTF_8));
        try {
            new Download("", target, null).download(brokenBody(), 100);
            fail("Expected the interrupted response to fail");
        } catch (IOException expected) {
            assertEquals("working cache", new String(Files.readAllBytes(target.toPath()), StandardCharsets.UTF_8));
            assertEquals(1, temporary.getRoot().list().length);
        }
    }

    @Test
    public void failedFirstDownloadDoesNotCreateAUsableCacheFile() throws Exception {
        File target = new File(temporary.getRoot(), "epg.xml");
        try {
            new Download("", target, null).download(brokenBody(), 100);
            fail("Expected the interrupted response to fail");
        } catch (IOException expected) {
            assertFalse(target.exists());
            assertEquals(0, temporary.getRoot().list().length);
        }
    }

    @Test
    public void completeDownloadReplacesCacheAndRemovesTemporaryFile() throws Exception {
        File target = temporary.newFile("epg.xml");
        Files.write(target.toPath(), "old".getBytes(StandardCharsets.UTF_8));
        byte[] replacement = "complete new cache".getBytes(StandardCharsets.UTF_8);
        new Download("", target, null).download(new ByteArrayInputStream(replacement), replacement.length);
        assertEquals("complete new cache", new String(Files.readAllBytes(target.toPath()), StandardCharsets.UTF_8));
        assertEquals(1, temporary.getRoot().list().length);
    }

    @Test public void scopeCancellationAtEndOfBodyKeepsWorkingCache() throws Exception {
        File target = temporary.newFile("epg.xml");
        Files.write(target.toPath(), "working cache".getBytes(StandardCharsets.UTF_8));
        cancelledDownload(target, false);
        assertEquals("working cache", new String(Files.readAllBytes(target.toPath()), StandardCharsets.UTF_8));
        assertEquals(1, temporary.getRoot().list().length);
    }

    @Test public void scopeCancellationWhileClosingBodyKeepsWorkingCache() throws Exception {
        File target = temporary.newFile("epg.xml");
        Files.write(target.toPath(), "working cache".getBytes(StandardCharsets.UTF_8));
        cancelledDownload(target, true);
        assertEquals("working cache", new String(Files.readAllBytes(target.toPath()), StandardCharsets.UTF_8));
        assertEquals(1, temporary.getRoot().list().length);
    }

    @Test public void cancelledFirstDownloadLeavesNoCacheOrTemporaryFile() throws Exception {
        File target = new File(temporary.getRoot(), "epg.xml");
        cancelledDownload(target, false);
        assertFalse(target.exists());
        assertEquals(0, temporary.getRoot().list().length);
    }

    private void cancelledDownload(File target, boolean cancelOnClose) throws Exception {
        HttpCallScope scope = new HttpCallScope();
        byte[] bytes = "obsolete complete payload".getBytes(StandardCharsets.UTF_8);
        InputStream body = new ByteArrayInputStream(bytes) {
            @Override public synchronized int read(byte[] buffer, int offset, int length) {
                int result = super.read(buffer, offset, length);
                if (result == -1 && !cancelOnClose) scope.close();
                return result;
            }
            @Override public void close() throws IOException {
                if (cancelOnClose) scope.close();
                super.close();
            }
        };
        try {
            scope.call(() -> {
                new Download("", target, null).download(body, bytes.length);
                return null;
            });
            fail("Cancelled request published its downloaded file");
        } catch (java.io.InterruptedIOException expected) {
            assertFalse("Scope cancellation must work without thread interruption", Thread.currentThread().isInterrupted());
        }
    }

    private static InputStream brokenBody() {
        return new InputStream() {
            private boolean sent;
            @Override public int read() throws IOException { throw new IOException("Connection closed"); }
            @Override public int read(byte[] data, int offset, int length) throws IOException {
                if (sent) throw new IOException("Connection closed");
                sent = true;
                data[offset] = '<';
                return 1;
            }
        };
    }
}
