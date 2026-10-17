package com.fongmi.android.tv.db;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

final class DatabaseFileSwap {
    interface Mover {
        boolean move(File source, File target) throws IOException;
    }

    static void install(File staged, File target) throws IOException {
        install(staged, target, File::renameTo);
    }

    static void install(File staged, File target, Mover mover) throws IOException {
        if (!staged.isFile() || staged.getCanonicalFile().equals(target.getCanonicalFile())) {
            throw new IOException("Invalid staged database");
        }
        List<File> originals = new ArrayList<>();
        for (String suffix : new String[]{"", "-wal", "-shm"}) {
            File file = new File(target.getPath() + suffix);
            if (!file.exists()) continue;
            if (!file.isFile()) throw new IOException("Invalid database file: " + file);
            originals.add(file);
        }
        File previous = new File(target.getParentFile(), "." + target.getName() + "-previous-" + UUID.randomUUID());
        if (!previous.mkdir()) throw new IOException("Cannot preserve current database");
        List<File> saved = new ArrayList<>();
        try {
            for (File file : originals) {
                if (!mover.move(file, new File(previous, file.getName()))) throw new IOException("Cannot preserve " + file.getName());
                saved.add(file);
            }
            if (!mover.move(staged, target)) throw new IOException("Database replacement failed");
        } catch (IOException | RuntimeException failure) {
            boolean restored = true;
            for (int i = saved.size() - 1; i >= 0; i--) {
                File file = saved.get(i);
                try {
                    if (!mover.move(new File(previous, file.getName()), file)) restored = false;
                } catch (IOException | RuntimeException error) {
                    failure.addSuppressed(error);
                    restored = false;
                }
            }
            if (!restored) throw new IOException("Database recovery files preserved at " + previous, failure);
            previous.delete();
            throw failure;
        }
        for (File file : saved) new File(previous, file.getName()).delete();
        previous.delete();
    }
}
