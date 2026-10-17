package com.fongmi.android.tv.server.process;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import static org.junit.Assert.*;

public class LocalUploadTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    @Test public void failedReplacementKeepsThePreviousFile() throws Exception {
        File target = temporary.newFile("existing.mp4");
        byte[] original = "previous contents".getBytes(StandardCharsets.UTF_8);
        Files.write(target.toPath(), original);
        File missing = new File(temporary.getRoot(), "removed-staged-upload");
        try {
            assertFalse(replace(missing, target));
        } catch (InvocationTargetException error) {
            assertTrue("Previous file was deleted before replacement succeeded", target.exists());
            throw error;
        }
        assertArrayEquals(original, Files.readAllBytes(target.toPath()));
    }

    @Test public void completeStagedUploadReplacesThePreviousFile() throws Exception {
        File target = temporary.newFile("existing.mp4");
        Files.write(target.toPath(), "old".getBytes(StandardCharsets.UTF_8));
        File staged = temporary.newFile("staged-upload");
        byte[] updated = "new complete contents".getBytes(StandardCharsets.UTF_8);
        Files.write(staged.toPath(), updated);
        assertTrue(replace(staged, target));
        assertArrayEquals(updated, Files.readAllBytes(target.toPath()));
        assertFalse(staged.exists());
    }

    private boolean replace(File source, File target) throws Exception {
        Method replace = Local.class.getDeclaredMethod("replace", File.class, File.class);
        replace.setAccessible(true);
        return (Boolean) replace.invoke(new Local(), source, target);
    }
}
