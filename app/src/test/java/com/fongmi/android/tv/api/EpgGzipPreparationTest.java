package com.fongmi.android.tv.api;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.zip.GZIPOutputStream;
import static org.junit.Assert.*;

public class EpgGzipPreparationTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    @Test public void corruptRefreshDoesNotPretendTheOldXmlIsFresh() throws Exception {
        File source = gzip(true), xml = xml("previous XML");
        expectFailure(source, xml, true);
        assertEquals("previous XML", text(xml));
        assertFalse(source.exists());
        assertEquals(1, temporary.getRoot().list().length);
    }

    @Test public void corruptFirstDownloadLeavesNoReusableBadCache() throws Exception {
        File source = gzip(true), xml = new File(temporary.getRoot(), "epg.xml");
        expectFailure(source, xml, true);
        assertFalse(xml.exists());
        assertFalse(source.exists());
        assertEquals(0, temporary.getRoot().list().length);
    }

    @Test public void successfulRefreshPublishesNewXmlWithoutDeletingTheArchive() throws Exception {
        File source = gzip(false), xml = xml("previous XML");
        EpgParser.prepareGzip(source, xml, true);
        assertEquals("new XML", text(xml));
        assertTrue(source.exists());
        assertEquals(2, temporary.getRoot().list().length);
    }

    @Test public void archiveNewerThanXmlMustBeRetriedAfterInterruptedPreparation() throws Exception {
        File source = gzip(true), xml = xml("previous XML");
        assertTrue(xml.setLastModified(1000));
        assertTrue(source.setLastModified(2000));
        expectFailure(source, xml, false);
        assertEquals("previous XML", text(xml));
        assertFalse(source.exists());
    }

    @Test public void currentXmlDoesNotNeedToBeExtractedAgain() throws Exception {
        File source = gzip(true), xml = xml("reusable XML");
        assertTrue(source.setLastModified(1000));
        assertTrue(xml.setLastModified(2000));
        EpgParser.prepareGzip(source, xml, false);
        assertEquals("reusable XML", text(xml));
        assertTrue(source.exists());
    }

    @Test public void cancelledRefreshCannotInvalidateTheArchive() throws Exception {
        File source = gzip(true), xml = xml("previous XML");
        Thread.currentThread().interrupt();
        try {
            expectFailure(source, xml, true);
            assertTrue(source.exists());
            assertEquals("previous XML", text(xml));
        } finally { Thread.interrupted(); }
    }

    private void expectFailure(File source, File xml, boolean refresh) throws Exception {
        try {
            EpgParser.prepareGzip(source, xml, refresh);
            fail("Failed extraction was treated as a usable EPG refresh");
        } catch (IOException expected) { }
    }

    private File gzip(boolean corrupt) throws Exception {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (GZIPOutputStream out = new GZIPOutputStream(buffer)) { out.write("new XML".getBytes(StandardCharsets.UTF_8)); }
        byte[] bytes = buffer.toByteArray();
        if (corrupt) bytes[bytes.length - 8] ^= 1;
        File file = temporary.newFile("epg.gz"); Files.write(file.toPath(), bytes); return file;
    }

    private File xml(String content) throws Exception {
        File file = temporary.newFile("epg.xml"); Files.write(file.toPath(), content.getBytes(StandardCharsets.UTF_8)); return file;
    }

    private String text(File file) throws IOException { return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8); }
}
