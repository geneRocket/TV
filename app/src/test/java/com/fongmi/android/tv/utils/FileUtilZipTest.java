package com.fongmi.android.tv.utils;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.Assert.*;

public class FileUtilZipTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    @Test public void rootDirectoryEntryDoesNotRejectAnOtherwiseValidArchive() throws Exception {
        File archive = archive("./", "./config.json");
        File output = temporary.newFolder("output");
        assertTrue(FileUtil.extractZip(archive, output));
        assertEquals("payload", new String(Files.readAllBytes(new File(output, "config.json").toPath()), StandardCharsets.UTF_8));
    }

    @Test public void parentTraversalCannotWriteOutsideTheOutputDirectory() throws Exception {
        File archive = archive("../escaped.json");
        File output = temporary.newFolder("output");
        assertFalse(FileUtil.extractZip(archive, output));
        assertFalse(new File(temporary.getRoot(), "escaped.json").exists());
    }

    @Test public void aFileCannotReplaceTheOutputDirectory() throws Exception {
        File archive = archive(".");
        File output = temporary.newFolder("output");
        assertFalse(FileUtil.extractZip(archive, output));
        assertTrue(output.isDirectory());
    }

    private File archive(String... names) throws Exception {
        File archive = temporary.newFile("input.zip");
        try (ZipOutputStream out = new ZipOutputStream(new FileOutputStream(archive))) {
            for (String name : names) {
                out.putNextEntry(new ZipEntry(name));
                if (!name.endsWith("/")) out.write("payload".getBytes(StandardCharsets.UTF_8));
                out.closeEntry();
            }
        }
        return archive;
    }
}
