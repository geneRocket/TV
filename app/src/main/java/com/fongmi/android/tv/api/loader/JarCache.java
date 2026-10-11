package com.fongmi.android.tv.api.loader;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Enumeration;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Stage and validate remote dex archives before replacing the last working cache. */
final class JarCache {
    static void replace(File target, byte[] data, String expectedMd5) throws IOException {
        if (data.length == 0) throw new IOException("Empty JAR response");
        if (!expectedMd5.isEmpty() && (!expectedMd5.matches("(?i)[0-9a-f]{32}")
                || !expectedMd5.equalsIgnoreCase(md5(data)))) throw new IOException("JAR MD5 mismatch");
        File parent = target.getAbsoluteFile().getParentFile();
        if (!parent.isDirectory() && !parent.mkdirs()) throw new IOException("Cannot create JAR cache directory");
        File staged = File.createTempFile("jar-", ".tmp", parent);
        try {
            try (FileOutputStream output = new FileOutputStream(staged)) {
                output.write(data);
                output.flush();
                output.getFD().sync();
            }
            validate(staged);
            if (!staged.setReadOnly()) throw new IOException("Cannot protect cached JAR");
            // Same-directory rename atomically replaces the path on Android/Linux.
            if (!staged.renameTo(target)) throw new IOException("Cannot replace cached JAR");
        } finally {
            if (staged.exists()) staged.delete();
        }
    }

    private static void validate(File file) throws IOException {
        boolean primaryDex = false;
        try (ZipFile zip = new ZipFile(file)) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            byte[] buffer = new byte[8192];
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (entry.isDirectory()) continue;
                boolean dex = entry.getName().matches("classes(?:[2-9]|[1-9][0-9]+)?\\.dex");
                byte[] header = new byte[112];
                CRC32 crc = new CRC32();
                long size = 0;
                try (InputStream input = zip.getInputStream(entry)) {
                    int count;
                    while ((count = input.read(buffer)) != -1) {
                        if (dex && size < header.length) {
                            System.arraycopy(buffer, 0, header, (int) size, Math.min(count, header.length - (int) size));
                        }
                        crc.update(buffer, 0, count);
                        size += count;
                    }
                }
                if (size != entry.getSize() || crc.getValue() != entry.getCrc()) throw new IOException("Corrupt JAR entry");
                if (dex && (size < 112 || header[0] != 'd' || header[1] != 'e' || header[2] != 'x'
                        || header[3] != '\n' || header[4] != '0' || header[5] < '0' || header[5] > '9'
                        || header[6] < '0' || header[6] > '9' || header[7] != 0
                        || integer(header, 32) != size || integer(header, 36) < 112 || integer(header, 36) > size
                        || integer(header, 40) != 0x12345678L)) throw new IOException("Invalid DEX header");
                if (entry.getName().equals("classes.dex")) primaryDex = true;
            }
        }
        if (!primaryDex) throw new IOException("JAR has no classes.dex");
    }

    private static long integer(byte[] data, int offset) {
        return (data[offset] & 255L) | (data[offset + 1] & 255L) << 8
                | (data[offset + 2] & 255L) << 16 | (data[offset + 3] & 255L) << 24;
    }

    private static String md5(byte[] data) throws IOException {
        try {
            byte[] digest = MessageDigest.getInstance("MD5").digest(data);
            StringBuilder result = new StringBuilder(32);
            for (byte value : digest) result.append(String.format(java.util.Locale.ROOT, "%02x", value & 255));
            return result.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IOException(e);
        }
    }
}
