package com.fongmi.android.tv.player.extractor;

import android.net.Uri;
import android.os.SystemClock;

import com.fongmi.android.tv.Constant;
import com.fongmi.android.tv.bean.Episode;
import com.fongmi.android.tv.exception.ExtractException;
import com.fongmi.android.tv.player.Source;
import com.fongmi.android.tv.utils.Download;
import com.fongmi.android.tv.utils.UrlUtil;
import com.github.catvod.utils.Path;
import com.github.catvod.utils.Util;
import com.xunlei.downloadlib.XLTaskHelper;
import com.xunlei.downloadlib.parameter.GetTaskId;
import com.xunlei.downloadlib.parameter.TorrentFileInfo;
import com.xunlei.downloadlib.parameter.XLTaskInfo;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.regex.Pattern;

public class Thunder implements Source.Extractor {

    private GetTaskId taskId;

    @Override
    public boolean match(Uri uri) {
        String scheme = uri.getScheme() == null ? "" : uri.getScheme();
        return "magnet".equals(scheme) || "thunder".equals(scheme) || "ed2k".equals(scheme) || "ftp".equals(scheme);
    }

    @Override
    public String fetch(String url) throws Exception {
        if (isTorrentTask(url)) return addTorrentTask(Uri.parse(url));
        String scheme = UrlUtil.scheme(url);
        if ("magnet".equals(scheme) || "thunder".equals(scheme)) return addParsedTask(url);
        return addThunderTask(url);
    }

    private String addTorrentTask(Uri uri) throws Exception {
        String path = uri.getPath();
        String name = uri.getQueryParameter("name");
        String indexValue = uri.getQueryParameter("index");
        if (path == null || path.isEmpty() || name == null || name.isEmpty() || indexValue == null || indexValue.isEmpty()) {
            throw new ExtractException("Invalid thunder torrent task");
        }
        File torrent = new File(path);
        File parent = torrent.getParentFile();
        if (parent == null) throw new ExtractException("Missing thunder torrent parent");
        int index;
        try {
            index = Integer.parseInt(indexValue);
        } catch (NumberFormatException e) {
            throw new ExtractException("Invalid thunder torrent index");
        }
        taskId = XLTaskHelper.get().addTorrentTask(torrent, parent, index);
        long start = SystemClock.elapsedRealtime();
        while (true) {
            XLTaskInfo taskInfo = XLTaskHelper.get().getBtSubTaskInfo(taskId, index).mTaskInfo;
            if (taskInfo.mTaskStatus == 3) throw new ExtractException(taskInfo.getErrorMsg());
            if (SystemClock.elapsedRealtime() - start > 60000) throw new ExtractException("Thunder task timeout");
            if (taskInfo.mTaskStatus != 0) return XLTaskHelper.get().getLocalUrl(new File(torrent.getParent(), name));
            else SystemClock.sleep(300);
        }
    }

    private String addParsedTask(String url) throws Exception {
        List<Episode> episodes = Parser.get(url).call();
        if (episodes.isEmpty()) throw new ExtractException("Thunder task no media");
        String parsedUrl = episodes.get(0).getUrl();
        if (isTorrentTask(parsedUrl)) return addTorrentTask(Uri.parse(parsedUrl));
        if ("ed2k".equals(UrlUtil.scheme(parsedUrl))) return addThunderTask(parsedUrl);
        return parsedUrl;
    }

    private String addThunderTask(String url) {
        File folder = Path.thunder(Util.md5(url));
        taskId = XLTaskHelper.get().addThunderTask(url, folder);
        return XLTaskHelper.get().getLocalUrl(taskId.getSaveFile());
    }

    @Override
    public void stop() {
        if (taskId == null) return;
        XLTaskHelper.get().deleteTask(taskId);
        taskId = null;
    }

    @Override
    public void exit() {
    }

    public static class Parser implements Callable<List<Episode>> {

        private static final Pattern THUNDER = Pattern.compile("(magnet|thunder|ed2k):.*");
        private final String url;
        private int time;

        public static boolean match(String url) {
            return THUNDER.matcher(url).find() || isTorrent(url);
        }

        public static Parser get(String url) {
            return new Parser(url);
        }

        public Parser(String url) {
            this.url = url;
        }

        private void sleep() {
            SystemClock.sleep(100);
            time += 100;
        }

        private static boolean isTorrent(String url) {
            int index = url.indexOf(';');
            String value = index < 0 ? url : url.substring(0, index);
            return !value.startsWith("magnet") && value.endsWith(".torrent");
        }

        @Override
        public List<Episode> call() throws Exception {
            boolean torrent = isTorrent(url);
            List<Episode> episodes = new ArrayList<>();
            GetTaskId taskId = XLTaskHelper.get().parse(url, Path.thunder(Util.md5(url)));
            try {
                if (!torrent && !taskId.getRealUrl().startsWith("magnet")) return Arrays.asList(Episode.create(taskId.getFileName(), taskId.getRealUrl()));
                if (torrent) Download.create(url, taskId.getSaveFile()).sync();
                else while (XLTaskHelper.get().getTaskInfo(taskId).getTaskStatus() != 2 && time < 60000) sleep();
                List<TorrentFileInfo> medias = XLTaskHelper.get().getTorrentInfo(taskId.getSaveFile()).getMedias();
                for (TorrentFileInfo media : medias) episodes.add(Episode.create(media.getFileName(), media.getSize(), media.getPlayUrl()));
            } finally {
                XLTaskHelper.get().stopTask(taskId);
            }
            return episodes;
        }
    }

    private static boolean isTorrentTask(String url) {
        Uri uri = Uri.parse(url);
        String scheme = UrlUtil.scheme(uri);
        return "magnet".equals(scheme) && uri.getQueryParameter("xt") == null && uri.getPath() != null && !uri.getPath().isEmpty() && uri.getQueryParameter("index") != null;
    }
}
