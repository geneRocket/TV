package com.fongmi.android.tv.utils;

import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.text.TextUtils;

import androidx.core.content.FileProvider;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.impl.Callback;
import com.github.catvod.utils.Path;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLConnection;
import java.text.DecimalFormat;
import java.util.Enumeration;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

public class FileUtil {

    private static final int BUFFER_SIZE = 8192;
    private static final int MAX_ZIP_ENTRIES = 2048;
    private static final long MAX_ZIP_BYTES = 512L * 1024 * 1024;

    public static File getWall(int index) {
        return Path.files("wallpaper_" + index);
    }

    public static void openFile(File file) {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.setDataAndType(getShareUri(file), FileUtil.getMimeType(file.getName()));
        App.get().startActivity(intent);
    }

    public static void zipFolder(File folder, File zip) {
        if (folder == null || zip == null || !folder.isDirectory()) return;
        try {
            ensureParent(zip);
            File target = zip.getCanonicalFile();
            try (ZipOutputStream zipOut = new ZipOutputStream(new FileOutputStream(zip))) {
                folderToZip("", folder, target, zipOut);
            }
        } catch (Exception e) {
            ThreadPools.log(e, "Zip folder failed.");
        }
    }

    private static void folderToZip(String parentPath, File folder, File target, ZipOutputStream zipOut) throws Exception {
        File[] files = folder.listFiles();
        if (files == null) return;
        for (File file : files) {
            if (file.getCanonicalFile().equals(target)) continue;
            if (file.isDirectory()) {
                folderToZip(parentPath + file.getName() + "/", file, target, zipOut);
                continue;
            }
            ZipEntry zipEntry = new ZipEntry(parentPath + file.getName());
            zipOut.putNextEntry(zipEntry);

            try (FileInputStream in = new FileInputStream(file)) {
                copy(in, zipOut, Long.MAX_VALUE);
            }
            zipOut.closeEntry();
        }
    }

    public static void extractGzip(File target, File path) {
        if (target == null || path == null) return;
        try {
            ensureParent(path);
            try (GZIPInputStream is = new GZIPInputStream(new BufferedInputStream(new FileInputStream(target))); BufferedOutputStream os = new BufferedOutputStream(new FileOutputStream(path))) {
                copy(is, os, MAX_ZIP_BYTES);
            }
        } catch (Exception e) {
            ThreadPools.log(e, "Extract gzip failed.");
        }
    }

    public static void extractZip(File target, File path) {
        if (target == null || path == null) return;
        long total = 0;
        int count = 0;
        try (ZipFile zip = new ZipFile(target)) {
            if (!path.exists() && !path.mkdirs()) throw new IOException("Create output folder failed: " + path);
            Enumeration<?> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = (ZipEntry) entries.nextElement();
                if (++count > MAX_ZIP_ENTRIES) throw new IOException("Too many zip entries: " + target);
                File out = getZipOutputFile(path, entry);
                if (entry.isDirectory()) {
                    if (!out.exists() && !out.mkdirs()) throw new IOException("Create zip folder failed: " + out);
                    continue;
                }
                ensureParent(out);
                try (InputStream in = zip.getInputStream(entry); OutputStream os = new BufferedOutputStream(new FileOutputStream(out))) {
                    total += copy(in, os, MAX_ZIP_BYTES - total);
                }
                if (total > MAX_ZIP_BYTES) throw new IOException("Zip output is too large: " + target);
            }
        } catch (Exception e) {
            ThreadPools.log(e, "Extract zip failed.");
        }
    }

    private static File getZipOutputFile(File path, ZipEntry entry) throws Exception {
        File root = path.getCanonicalFile();
        File out = new File(root, entry.getName()).getCanonicalFile();
        String rootPath = root.getPath() + File.separator;
        if (!out.getPath().startsWith(rootPath)) throw new SecurityException("Unsafe zip entry: " + entry.getName());
        File parent = out.getParentFile();
        if (parent != null && !parent.exists()) parent.mkdirs();
        return out;
    }

    private static long copy(InputStream in, OutputStream out, long limit) throws IOException {
        byte[] buffer = new byte[BUFFER_SIZE];
        long total = 0;
        int read;
        while ((read = in.read(buffer)) != -1) {
            total += read;
            if (total > limit) throw new IOException("Output exceeds limit");
            out.write(buffer, 0, read);
        }
        return total;
    }

    private static void ensureParent(File file) throws IOException {
        File parent = file.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) throw new IOException("Create parent folder failed: " + parent);
    }

    public static void clearCache(Callback callback) {
        App.execute(() -> {
            Path.clear(Path.cache());
            if (callback != null) App.post(callback::success);
        });
    }

    public static void getCacheSize(Callback callback) {
        App.execute(() -> {
            String result = byteCountToDisplaySize(getFolderSize(Path.cache()));
            App.post(() -> callback.success(result));
        });
    }

    public static Uri getShareUri(String path) {
        return getShareUri(new File(path.replace("file://", "")));
    }

    public static Uri getShareUri(File file) {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.N ? Uri.fromFile(file) : FileProvider.getUriForFile(App.get(), App.get().getPackageName() + ".provider", file);
    }

    private static String getMimeType(String fileName) {
        String mimeType = URLConnection.guessContentTypeFromName(fileName);
        return TextUtils.isEmpty(mimeType) ? "*/*" : mimeType;
    }

    private static long getFolderSize(File file) {
        long size = 0;
        if (file == null) return 0;
        if (file.isDirectory()) for (File f : Path.list(file)) size += getFolderSize(f);
        else size = file.length();
        return size;
    }

    private static String byteCountToDisplaySize(long size) {
        if (size <= 0) return "0 KB";
        String[] units = new String[]{"bytes", "KB", "MB", "GB", "TB"};
        int digitGroups = (int) (Math.log10(size) / Math.log10(1024));
        return new DecimalFormat("#,##0.#").format(size / Math.pow(1024, digitGroups)) + " " + units[digitGroups];
    }
}
