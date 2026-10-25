package com.fongmi.android.tv.api.config;

import com.github.catvod.net.HttpCallScope;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.Assert.*;

public class WallConfigWriteTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    @Test public void failedSourceReadKeepsTheWorkingWallpaper() throws Exception {
        File target = wallpaper();
        expectFailure(writer(true, false, false), target);
        assertEquals("working image", text(target));
        assertEquals(1, temporary.getRoot().list().length);
    }

    @Test public void failedImageProcessingKeepsTheWorkingWallpaper() throws Exception {
        File target = wallpaper();
        expectFailure(writer(false, true, false), target);
        assertEquals("working image", text(target));
        assertEquals(1, temporary.getRoot().list().length);
    }

    @Test public void failedFirstImageProcessingLeavesNoWallpaper() throws Exception {
        File target = new File(temporary.getRoot(), "wallpaper");
        expectFailure(writer(false, true, false), target);
        assertFalse(target.exists());
        assertEquals(0, temporary.getRoot().list().length);
    }

    @Test public void emptySourceIsNotSuccessfulWallpaper() throws Exception {
        File target = wallpaper();
        expectFailure(writer(false, false, true), target);
        assertEquals("working image", text(target));
    }

    @Test public void imageProcessingCannotSeeAnAlreadyReplacedWallpaper() throws Exception {
        File target = wallpaper();
        WallpaperFileStore owner = new WallpaperFileStore((url, file) -> writeBytes(file, "raw image"), file -> {
            assertEquals("working image", text(target));
            writeBytes(file, "processed image");
        });
        assertEquals(target, write(owner, target));
        assertEquals("processed image", text(target));
        assertEquals(1, temporary.getRoot().list().length);
    }

    @Test public void cancelledImageProcessingCannotPublishWallpaper() throws Exception {
        File target = wallpaper();
        HttpCallScope scope = new HttpCallScope();
        WallpaperFileStore owner = new WallpaperFileStore((url, file) -> writeBytes(file, "raw image"), file -> scope.close());
        scope.call(() -> { expectFailure(owner, target); return null; });
        assertEquals("working image", text(target));
        assertEquals(1, temporary.getRoot().list().length);
    }

    @Test public void failedReplacementRetainsTheDestinationAndCleansStaging() throws Exception {
        File target = temporary.newFolder("wallpaper");
        File child = new File(target, "existing");
        writeBytes(child, "existing file");
        expectFailure(writer(false, false, false), target);
        assertEquals("existing file", text(child));
        assertEquals(1, temporary.getRoot().list().length);
    }

    private WallpaperFileStore writer(boolean failRead, boolean failResize, boolean empty) {
        return new WallpaperFileStore((url, file) -> {
            assertEquals("https://fixture.test/wallpaper", url);
            writeBytes(file, empty ? "" : "raw image");
            if (failRead) throw new IOException("Network read failed");
        }, file -> {
            if (failResize) {
                writeBytes(file, "partial JPEG");
                throw new IOException("Image processing failed");
            }
        });
    }

    private File write(WallpaperFileStore store, File target) throws Exception {
        try (WallpaperFileStore.Prepared image = store.prepare("https://fixture.test/wallpaper", target)) {
            return image.commit();
        }
    }

    @Test public void preparedImageDoesNotPublishUntilExplicitCommit() throws Exception {
        File target = wallpaper();
        try (WallpaperFileStore.Prepared image = writer(false, false, false).prepare("https://fixture.test/wallpaper", target)) {
            assertEquals("working image", text(target));
        }
        assertEquals("working image", text(target));
        assertEquals(1, temporary.getRoot().list().length);
    }

    @Test public void cancellationAfterPrepareCannotReplaceTheVisibleImage() throws Exception {
        File target = wallpaper();
        HttpCallScope scope = new HttpCallScope();
        scope.call(() -> {
            try (WallpaperFileStore.Prepared image = writer(false, false, false).prepare("https://fixture.test/wallpaper", target)) {
                scope.close();
                try { image.commit(); fail(); } catch (IOException expected) { }
            }
            return null;
        });
        assertEquals("working image", text(target));
        assertEquals(1, temporary.getRoot().list().length);
    }

    private void expectFailure(WallpaperFileStore owner, File target) throws Exception {
        try {
            write(owner, target);
            fail("Failed wallpaper was treated as successful");
        } catch (IOException expected) { }
    }

    private File wallpaper() throws Exception {
        File file = temporary.newFile("wallpaper");
        writeBytes(file, "working image");
        return file;
    }

    private void writeBytes(File file, String text) throws IOException {
        Files.write(file.toPath(), text.getBytes(StandardCharsets.UTF_8));
    }

    private String text(File file) throws IOException {
        return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
    }
}
