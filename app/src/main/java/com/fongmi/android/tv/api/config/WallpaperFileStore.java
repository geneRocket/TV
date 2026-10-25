package com.fongmi.android.tv.api.config;

import com.github.catvod.net.HttpCallScope;
import java.io.File;
import java.io.IOException;

/** Builds an image privately; only an explicit commit may replace the visible wallpaper. */
final class WallpaperFileStore {
    interface Reader { void read(String url, File destination) throws Exception; }
    interface Processor { void process(File file) throws Exception; }
    private final Reader reader;
    private final Processor processor;
    WallpaperFileStore(Reader reader, Processor processor) { this.reader = reader; this.processor = processor; }

    Prepared prepare(String url, File destination) throws Exception {
        check();
        File parent = destination.getAbsoluteFile().getParentFile();
        if (parent == null || (!parent.isDirectory() && !parent.mkdirs())) throw new IOException("Cannot create wallpaper directory");
        File staged = File.createTempFile("wallpaper-", ".tmp", parent);
        try {
            reader.read(url, staged);
            requireImage(staged, "Empty wallpaper source");
            check();
            processor.process(staged);
            check();
            requireImage(staged, "Empty processed wallpaper");
            return new Prepared(staged, destination);
        } catch (Throwable error) {
            staged.delete();
            throw error;
        }
    }

    private static void requireImage(File file, String error) throws IOException {
        if (file.length() == 0) throw new IOException(error);
    }
    private static void check() throws IOException { ConfigLoadTask.check(); HttpCallScope.throwIfCancelled(); }

    static final class Prepared implements AutoCloseable {
        private final File staged, destination;
        Prepared(File staged, File destination) { this.staged = staged; this.destination = destination; }
        File commit() throws IOException {
            check();
            if (!staged.renameTo(destination)) throw new IOException("Cannot replace wallpaper");
            return destination;
        }
        @Override public void close() { if (staged.exists()) staged.delete(); }
    }
}
