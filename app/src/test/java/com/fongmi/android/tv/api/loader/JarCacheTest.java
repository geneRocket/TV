package com.fongmi.android.tv.api.loader;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.Assert.*;

public class JarCacheTest {
    @Rule public TemporaryFolder folder = new TemporaryFolder();

    private byte[] dex() {
        byte[] dex = new byte[128];
        System.arraycopy("dex\n035\0".getBytes(StandardCharsets.US_ASCII), 0, dex, 0, 8);
        ByteBuffer header = ByteBuffer.wrap(dex).order(ByteOrder.LITTLE_ENDIAN);
        header.putInt(32, dex.length);
        header.putInt(36, 112);
        header.putInt(40, 0x12345678);
        return dex;
    }

    private byte[] archive(String name, byte[] content) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            ZipEntry entry = new ZipEntry(name);
            CRC32 crc = new CRC32();
            crc.update(content);
            entry.setMethod(ZipEntry.STORED);
            entry.setSize(content.length);
            entry.setCrc(crc.getValue());
            zip.putNextEntry(entry);
            zip.write(content);
            zip.closeEntry();
        }
        return bytes.toByteArray();
    }

    private void preservesOldCache(byte[] candidate, String md5) throws Exception {
        File target = folder.newFile();
        byte[] previous = archive("classes.dex", dex());
        Files.write(target.toPath(), previous);
        assertTrue(target.setReadOnly());
        try {
            JarCache.replace(target, candidate, md5);
            fail("Invalid update was accepted");
        } catch (IOException expected) { }
        assertArrayEquals(previous, Files.readAllBytes(target.toPath()));
        assertFalse(java.util.Arrays.stream(folder.getRoot().listFiles()).anyMatch(f -> f.getName().startsWith("jar-")));
    }

    @Test public void errorPagesEmptyAndTruncatedResponsesPreserveOldCache() throws Exception {
        preservesOldCache("<html>Unavailable</html>".getBytes(StandardCharsets.UTF_8), "");
        preservesOldCache(new byte[0], "");
        byte[] valid = archive("classes.dex", dex());
        preservesOldCache(java.util.Arrays.copyOf(valid, valid.length / 2), "");
    }

    @Test public void missingDexAndInvalidDexPreserveOldCache() throws Exception {
        preservesOldCache(archive("error.txt", new byte[]{1}), "");
        preservesOldCache(archive("classes.dex", new byte[128]), "");
    }

    @Test public void corruptEntryAndMd5MismatchPreserveOldCache() throws Exception {
        byte[] valid = archive("classes.dex", dex());
        byte[] corrupt = valid.clone();
        // Stored ZIP data begins after the local header and ten-byte entry name.
        corrupt[30 + "classes.dex".length() + 120] ^= 1;
        preservesOldCache(corrupt, "");
        preservesOldCache(valid, "00000000000000000000000000000000");
        preservesOldCache(valid, "<html>MD5 server error</html>");
    }

    @Test public void validUpdateReplacesReadOnlyCacheAndChecksMd5() throws Exception {
        File target = folder.newFile();
        Files.write(target.toPath(), new byte[]{1});
        assertTrue(target.setReadOnly());
        byte[] valid = archive("classes.dex", dex());
        StringBuilder md5 = new StringBuilder();
        for (byte value : MessageDigest.getInstance("MD5").digest(valid)) md5.append(String.format("%02X", value & 255));
        JarCache.replace(target, valid, md5.toString());
        assertArrayEquals(valid, Files.readAllBytes(target.toPath()));
    }
}
