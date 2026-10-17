package com.xunlei.downloadlib;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.Assert.*;

public class TaskFileCleanupTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    @Test public void stoppedTaskDoesNotLeaveNestedEmptyDirectories() throws Exception {
        File root = temporary.newFolder("task");
        assertTrue(new File(root, "nested/empty").mkdirs());
        clean(root);
        assertFalse("Task directory remained after all its data was removed", root.exists());
    }

    @Test public void torrentMetadataKeepsItsParentButNotUnrelatedEmptyDirectories() throws Exception {
        File root = temporary.newFolder("task");
        File metadata = new File(root, "metadata.torrent");
        Files.write(metadata.toPath(), "cached torrent".getBytes(StandardCharsets.UTF_8));
        File empty = new File(root, "nested/empty");
        assertTrue(empty.mkdirs());
        clean(root);
        assertTrue(root.isDirectory());
        assertEquals("cached torrent", new String(Files.readAllBytes(metadata.toPath()), StandardCharsets.UTF_8));
        assertFalse(new File(root, "nested").exists());
    }

    @Test public void downloadedPayloadIsRemovedWhileNestedTorrentMetadataRemains() throws Exception {
        File root = temporary.newFolder("task");
        File nested = new File(root, "nested");
        assertTrue(nested.mkdirs());
        File metadata = new File(nested, "metadata.torrent");
        Files.write(metadata.toPath(), "cached torrent".getBytes(StandardCharsets.UTF_8));
        File payload = new File(nested, "video.mp4");
        Files.write(payload.toPath(), "downloaded data".getBytes(StandardCharsets.UTF_8));
        clean(root);
        assertTrue(root.isDirectory());
        assertTrue(nested.isDirectory());
        assertTrue(metadata.isFile());
        assertFalse(payload.exists());
    }

    private void clean(File file) throws Exception {
        Method method = XLTaskHelper.class.getDeclaredMethod("deleteFile", File.class);
        method.setAccessible(true);
        method.invoke(XLTaskHelper.get(), file);
    }
}
