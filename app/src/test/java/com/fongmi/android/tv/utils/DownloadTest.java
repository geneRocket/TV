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
