package com.fongmi.android.tv.server.process;

import com.fongmi.android.tv.server.Nano;
import com.fongmi.android.tv.utils.FileUtil;
import com.github.catvod.utils.Path;
import com.google.common.net.HttpHeaders;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Arrays;
import java.util.Date;
import java.util.Locale;
import java.util.Map;

import fi.iki.elonen.NanoHTTPD;

public class Local implements Process {

    @Override
    public boolean isRequest(NanoHTTPD.IHTTPSession session, String path) {
        return path.startsWith("/file") || path.startsWith("/upload") || path.startsWith("/newFolder") || path.startsWith("/delFolder") || path.startsWith("/delFile");
    }

    @Override
    public NanoHTTPD.Response doResponse(NanoHTTPD.IHTTPSession session, String path, Map<String, String> files) {
        if (path.startsWith("/file")) return getFile(session.getHeaders(), path);
        if (path.startsWith("/upload")) return upload(session.getParms(), files);
        if (path.startsWith("/newFolder")) return newFolder(session.getParms());
        if (path.startsWith("/delFolder") || path.startsWith("/delFile")) return delFolder(session.getParms());
        return null;
    }

    private NanoHTTPD.Response getFile(Map<String, String> headers, String path) {
        try {
            File file = getRootFile(path.substring(5));
            if (file.isDirectory()) return getFolder(file);
            if (file.isFile()) return getFile(headers, file, NanoHTTPD.getMimeTypeForFile(path));
            throw new FileNotFoundException();
        } catch (Exception e) {
            return Nano.error(e.getMessage());
        }
    }

    private NanoHTTPD.Response upload(Map<String, String> params, Map<String, String> files) {
        try {
            File directory = getRootFile(params.get("path"));
            if (!directory.exists() && !directory.mkdirs()) return Nano.error("Create folder failed");
            if (!directory.isDirectory()) return Nano.error("Invalid upload path");
            for (String k : files.keySet()) {
                String fn = params.get(k);
                File temp = new File(files.get(k));
                if (fn == null) return Nano.error("Missing file name");
                if (fn.toLowerCase(Locale.ROOT).endsWith(".zip")) {
                    if (!extractZip(temp, directory)) return Nano.error("Extract zip failed");
                } else {
                    if (!copyFile(temp, getRootFile(new File(directory, fn)))) return Nano.error("Copy file failed");
                }
            }
            return Nano.success();
        } catch (Exception e) {
            return Nano.error(e.getMessage());
        }
    }

    private boolean extractZip(File target, File directory) throws IOException {
        File staged = Path.cache("upload-" + System.nanoTime());
        Path.clear(staged);
        try {
            return FileUtil.extractZip(target, staged) && copyFolder(staged, directory);
        } finally {
            Path.clear(staged);
        }
    }

    private boolean copyFolder(File source, File target) throws IOException {
        File[] files = source.listFiles();
        if (files == null) return source.isDirectory();
        for (File file : files) {
            File output = getRootFile(new File(target, file.getName()));
            if (file.isDirectory()) {
                if (output.exists() && !output.isDirectory()) return false;
                if (!output.exists() && !output.mkdirs()) return false;
                if (!copyFolder(file, output)) return false;
            } else if (!copyFile(file, output)) {
                return false;
            }
        }
        return true;
    }

    private boolean copyFile(File source, File target) {
        if (target.isDirectory()) return false;
        File staged = new File(target.getPath() + ".upload-" + System.nanoTime());
        Path.clear(staged);
        if (!Path.copy(source, staged)) {
            Path.clear(staged);
            return false;
        }
        boolean copied = replace(staged, target);
        if (!copied) Path.clear(staged);
        return copied;
    }

    private boolean replace(File source, File target) {
        Path.clear(target);
        if (source.renameTo(target)) return true;
        if (!Path.copy(source, target)) return false;
        Path.clear(source);
        return true;
    }

    private NanoHTTPD.Response newFolder(Map<String, String> params) {
        try {
            File folder = getRootFile(new File(getRootFile(params.get("path")), params.get("name")));
            return folder.mkdirs() || folder.isDirectory() ? Nano.success() : Nano.error("Create folder failed");
        } catch (Exception e) {
            return Nano.error(e.getMessage());
        }
    }

    private NanoHTTPD.Response delFolder(Map<String, String> params) {
        try {
            File target = getRootFile(params.get("path"));
            if (target.equals(Path.root().getCanonicalFile())) return Nano.error("Delete root is not allowed");
            Path.clear(target);
            return Nano.success();
        } catch (Exception e) {
            return Nano.error(e.getMessage());
        }
    }

    private File getRootFile(String path) throws IOException {
        return getRootFile(new File(Path.root(), path == null ? "" : path));
    }

    private File getRootFile(File target) throws IOException {
        File root = Path.root().getCanonicalFile();
        File file = target.getCanonicalFile();
        String rootPath = root.getPath() + File.separator;
        if (!file.equals(root) && !file.getPath().startsWith(rootPath)) throw new SecurityException("Unsafe local path");
        return file;
    }

    private NanoHTTPD.Response getFolder(File root) {
        File[] list = root.listFiles();
        JsonObject info = new JsonObject();
        java.text.SimpleDateFormat format = new java.text.SimpleDateFormat("yyyy/MM/dd HH:mm:ss", Locale.getDefault());
        info.addProperty("parent", root.equals(Path.root()) ? "." : root.getParent().replace(Path.rootPath(), ""));
        if (list == null || list.length == 0) {
            info.add("files", new JsonArray());
            return Nano.success(info.toString());
        }
        Arrays.sort(list, (o1, o2) -> {
            if (o1.isDirectory() && o2.isFile()) return -1;
            return o1.isFile() && o2.isDirectory() ? 1 : o1.getName().compareTo(o2.getName());
        });
        JsonArray files = new JsonArray();
        info.add("files", files);
        for (File file : list) {
            JsonObject obj = new JsonObject();
            obj.addProperty("name", file.getName());
            obj.addProperty("path", file.getAbsolutePath().replace(Path.rootPath(), ""));
            obj.addProperty("time", format.format(new Date(file.lastModified())));
            obj.addProperty("dir", file.isDirectory() ? 1 : 0);
            files.add(obj);
        }
        return Nano.success(info.toString());
    }

    private NanoHTTPD.Response getFile(Map<String, String> header, File file, String mime) throws Exception {
        long fileLen = file.length();
        long startFrom = 0;
        long endAt = -1;
        String range = header.get("range");
        if (range != null && range.startsWith("bytes=")) {
            range = range.substring("bytes=".length()).trim();
            int minus = range.indexOf('-');
            try {
                if (minus >= 0) {
                    String start = range.substring(0, minus).trim();
                    String end = range.substring(minus + 1).trim();
                    if (!start.isEmpty()) startFrom = Long.parseLong(start);
                    if (!end.isEmpty()) endAt = Long.parseLong(end);
                    if (start.isEmpty() && !end.isEmpty()) {
                        long suffixLen = Long.parseLong(end);
                        if (suffixLen > 0) {
                            startFrom = Math.max(fileLen - suffixLen, 0);
                            endAt = fileLen - 1;
                        }
                    }
                }
            } catch (NumberFormatException ignored) {
                startFrom = 0;
                endAt = -1;
            }
        }
        NanoHTTPD.Response res;
        String ifRange = header.get("if-range");
        String etag = Integer.toHexString((file.getAbsolutePath() + file.lastModified() + "" + file.length()).hashCode());
        boolean headerIfRangeMissingOrMatching = (ifRange == null || etag.equals(ifRange));
        String ifNoneMatch = header.get("if-none-match");
        boolean headerIfNoneMatchPresentAndMatching = ifNoneMatch != null && ("*".equals(ifNoneMatch) || ifNoneMatch.equals(etag));
        if (headerIfRangeMissingOrMatching && range != null && startFrom >= 0 && startFrom < fileLen) {
            if (headerIfNoneMatchPresentAndMatching) {
                res = NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.NOT_MODIFIED, mime, "");
                res.addHeader(HttpHeaders.ACCEPT_RANGES, "bytes");
                res.addHeader(HttpHeaders.ETAG, etag);
            } else {
                if (endAt < 0) endAt = fileLen - 1;
                endAt = Math.min(endAt, fileLen - 1);
                long newLen = endAt - startFrom + 1;
                if (newLen <= 0) {
                    res = NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.RANGE_NOT_SATISFIABLE, NanoHTTPD.MIME_PLAINTEXT, "");
                    res.addHeader(HttpHeaders.CONTENT_RANGE, "bytes */" + fileLen);
                    res.addHeader(HttpHeaders.ACCEPT_RANGES, "bytes");
                    res.addHeader(HttpHeaders.ETAG, etag);
                    return res;
                }
                FileInputStream fis = new FileInputStream(file);
                fis.skip(startFrom);
                res = NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.PARTIAL_CONTENT, mime, fis, newLen);
                res.addHeader(HttpHeaders.CONTENT_LENGTH, newLen + "");
                res.addHeader(HttpHeaders.CONTENT_RANGE, "bytes " + startFrom + "-" + endAt + "/" + fileLen);
                res.addHeader(HttpHeaders.ACCEPT_RANGES, "bytes");
                res.addHeader(HttpHeaders.ETAG, etag);
            }
        } else {
            if (headerIfRangeMissingOrMatching && range != null && startFrom >= fileLen) {
                res = NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.RANGE_NOT_SATISFIABLE, NanoHTTPD.MIME_PLAINTEXT, "");
                res.addHeader(HttpHeaders.CONTENT_RANGE, "bytes */" + fileLen);
                res.addHeader(HttpHeaders.ACCEPT_RANGES, "bytes");
                res.addHeader(HttpHeaders.ETAG, etag);
            } else if (headerIfNoneMatchPresentAndMatching && (!headerIfRangeMissingOrMatching || range == null)) {
                res = NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.NOT_MODIFIED, mime, "");
                res.addHeader(HttpHeaders.ACCEPT_RANGES, "bytes");
                res.addHeader(HttpHeaders.ETAG, etag);
            } else {
                res = NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.OK, mime, new FileInputStream(file), fileLen);
                res.addHeader(HttpHeaders.CONTENT_LENGTH, fileLen + "");
                res.addHeader(HttpHeaders.ACCEPT_RANGES, "bytes");
                res.addHeader(HttpHeaders.ETAG, etag);
            }
        }
        return res;
    }
}
