package com.fongmi.android.tv.db;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import static org.junit.Assert.*;

public class DatabaseFileSwapTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    @Test public void missingStagedDatabaseCannotDeleteCurrentData() throws Exception {
        File target = currentBundle();
        expectFailure(() -> DatabaseFileSwap.install(new File(temporary.getRoot(), "missing"), target));
        assertCurrentBundle(target);
    }

    @Test public void failureSavingAnOldSidecarRollsBackEarlierMoves() throws Exception {
        File target = currentBundle(), staged = write("incoming", "new database");
        expectFailure(() -> DatabaseFileSwap.install(staged, target, (from, to) ->
                !from.equals(new File(target + "-wal")) && from.renameTo(to)));
        assertCurrentBundle(target);
        assertEquals("new database", read(staged));
    }

    @Test public void failedInstallRestoresTheWholePreviousBundle() throws Exception {
        File target = currentBundle(), staged = write("incoming", "new database");
        expectFailure(() -> DatabaseFileSwap.install(staged, target, (from, to) ->
                !from.equals(staged) && from.renameTo(to)));
        assertCurrentBundle(target);
        assertEquals("new database", read(staged));
    }

    @Test public void successfulInstallDiscardsOldSidecars() throws Exception {
        File target = currentBundle(), staged = write("incoming", "new database");
        DatabaseFileSwap.install(staged, target);
        assertEquals("new database", read(target));
        assertFalse(new File(target + "-wal").exists());
        assertFalse(new File(target + "-shm").exists());
        assertFalse(staged.exists());
    }

    @Test public void freshDatabaseInstallDoesNotRequireAnOldBundle() throws Exception {
        File target = new File(temporary.getRoot(), "tv"), staged = write("incoming", "new database");
        DatabaseFileSwap.install(staged, target);
        assertEquals("new database", read(target));
    }

    @Test public void failedRollbackPreservesRecoveryFiles() throws Exception {
        File target = currentBundle(), staged = write("incoming", "new database");
        expectFailure(() -> DatabaseFileSwap.install(staged, target, (from, to) ->
                !from.equals(staged) && !from.getParentFile().getName().startsWith(".tv-previous-") && from.renameTo(to)));
        File[] saved = temporary.getRoot().listFiles(file -> file.getName().startsWith(".tv-previous-"));
        assertNotNull(saved);
        assertEquals(1, saved.length);
        assertCurrentBundle(new File(saved[0], "tv"));
    }

    private File currentBundle() throws Exception {
        File target = write("tv", "old database");
        write("tv-wal", "old wal");
        write("tv-shm", "old shm");
        return target;
    }

    private void assertCurrentBundle(File target) throws Exception {
        assertTrue("Current database was lost", target.exists());
        assertEquals("old database", read(target));
        assertEquals("old wal", read(new File(target + "-wal")));
        assertEquals("old shm", read(new File(target + "-shm")));
    }

    private File write(String name, String data) throws Exception {
        File file = new File(temporary.getRoot(), name);
        Files.write(file.toPath(), data.getBytes(StandardCharsets.UTF_8));
        return file;
    }

    private String read(File file) throws Exception {
        return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
    }

    private interface Operation { void run() throws IOException; }
    private void expectFailure(Operation operation) throws IOException {
        try {
            operation.run();
            fail("Expected replacement to fail");
        } catch (IOException expected) { }
    }
}
