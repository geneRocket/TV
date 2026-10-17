package com.fongmi.android.tv.utils;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.ByteArrayOutputStream;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.InterruptedIOException;
import java.lang.reflect.Method;
import java.lang.reflect.InvocationTargetException;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.zip.GZIPOutputStream;
import com.github.catvod.net.HttpCallScope;

import static org.junit.Assert.*;

public class FileUtilGzipTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    @Test public void corruptTrailerPreservesThePreviousXml() throws Exception {
        File input = gzip(true);
        File output = temporary.newFile("epg.xml");
        Files.write(output.toPath(), "previous XML".getBytes(StandardCharsets.UTF_8));
        FileUtil.extractGzip(input, output);
        assertEquals("previous XML", text(output));
        assertEquals(2, temporary.getRoot().list().length);
    }

    @Test public void corruptFirstDownloadLeavesNoPartialXml() throws Exception {
        File input = gzip(true);
        File output = new File(temporary.getRoot(), "epg.xml");
        FileUtil.extractGzip(input, output);
        assertFalse(output.exists());
        assertEquals(1, temporary.getRoot().list().length);
    }

    @Test public void completeArchiveReplacesXmlAndRemovesStagingFile() throws Exception {
        File input = gzip(false);
        File output = temporary.newFile("epg.xml");
        Files.write(output.toPath(), "old".getBytes(StandardCharsets.UTF_8));
        FileUtil.extractGzip(input, output);
        assertEquals("new XML", text(output));
        assertEquals(2, temporary.getRoot().list().length);
    }

    @Test public void interruptedExtractionKeepsThePreviousXml() throws Exception {
        File input = gzip(false);
        File output = temporary.newFile("epg.xml");
        Files.write(output.toPath(), "previous XML".getBytes(StandardCharsets.UTF_8));
        Thread.currentThread().interrupt();
        try {
            assertFalse(FileUtil.tryExtractGzip(input, output));
            assertEquals("previous XML", text(output));
            assertEquals(2, temporary.getRoot().list().length);
        } finally { Thread.interrupted(); }
    }

    @Test public void cancelledScopeCannotPublishDecompressedXml() throws Exception {
        File input = gzip(false);
        File output = temporary.newFile("epg.xml");
        Files.write(output.toPath(), "previous XML".getBytes(StandardCharsets.UTF_8));
        HttpCallScope scope = new HttpCallScope();
        scope.call(() -> {
            scope.close();
            assertFalse(FileUtil.tryExtractGzip(input, output));
            return null;
        });
        assertEquals("previous XML", text(output));
        assertEquals(2, temporary.getRoot().list().length);
    }

    @Test public void cancellationDuringCopyStopsBeforeWritingTheNextChunk() throws Exception {
        HttpCallScope scope = new HttpCallScope();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        InputStream input = new ByteArrayInputStream(new byte[16384]) {
            @Override public synchronized int read(byte[] bytes, int offset, int length) {
                int read = super.read(bytes, offset, length);
                scope.close();
                return read;
            }
        };
        Method copy = FileUtil.class.getDeclaredMethod("copy", InputStream.class, OutputStream.class, long.class);
        copy.setAccessible(true);
        scope.call(() -> {
            try {
                copy.invoke(null, input, output, Long.MAX_VALUE);
                fail("Cancelled extraction continued copying");
            } catch (InvocationTargetException error) {
                assertTrue(error.getCause() instanceof InterruptedIOException);
            }
            return null;
        });
        assertEquals(0, output.size());
    }

    private File gzip(boolean corrupt) throws Exception {
        ByteArrayOutputStream data = new ByteArrayOutputStream();
        try (GZIPOutputStream out = new GZIPOutputStream(data)) {
            out.write("new XML".getBytes(StandardCharsets.UTF_8));
        }
        byte[] bytes = data.toByteArray();
        if (corrupt) bytes[bytes.length - 8] ^= 1;
        File file = temporary.newFile("epg.gz");
        Files.write(file.toPath(), bytes);
        return file;
    }

    private String text(File file) throws Exception {
        return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
    }
}
