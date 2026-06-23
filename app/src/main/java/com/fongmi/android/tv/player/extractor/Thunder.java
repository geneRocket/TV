package com.fongmi.android.tv.player.extractor;

import android.net.Uri;
import android.os.SystemClock;

import com.fongmi.android.tv.bean.Episode;
import com.fongmi.android.tv.bean.Url;
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
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Callable;

public class Thunder implements Source.Extractor {

    private static final long PARSE_TIMEOUT = 60000;
    private static final long TASK_POLL_INTERVAL = 500;
    private static final String MAGNET_QUERY = "magnet:?";

    private GetTaskId taskId;

    @Override
    public boolean match(Uri url) {
        String scheme = UrlUtil.scheme(url);
        return "magnet".equals(scheme) || "thunder".equals(scheme) || "ed2k".equals(scheme);
    }

    @Override
    public String fetch(String url) throws Exception {
        stop();
        if (url.startsWith("thunder://")) return addParsedThunderTask(url);
        if (url.startsWith(MAGNET_QUERY)) return addMagnetTask(url);
        return isTorrentTask(url) ? addTorrentTask(Uri.parse(url)) : addThunderTask(url);
    }

    private String addParsedThunderTask(String url) throws Exception {
        GetTaskId task = XLTaskHelper.get().parse(url, Path.thunder(Util.md5(url)));
        String realUrl = task.getRealUrl();
        if (realUrl == null) throw new ExtractException("Parse thunder url failed");
        if (realUrl.startsWith(MAGNET_QUERY)) return addMagnetTask(realUrl);
        if ("ftp".equals(UrlUtil.scheme(realUrl)) || "ed2k".equals(UrlUtil.scheme(realUrl))) return addThunderTask(realUrl);
        return realUrl;
    }

    private String addMagnetTask(String url) throws Exception {
        GetTaskId magnetTask = null;
        try {
            long start = SystemClock.elapsedRealtime();
            magnetTask = XLTaskHelper.get().parse(url, Path.thunder(Util.md5(url)));
            while (XLTaskHelper.get().getTaskInfo(magnetTask).getTaskStatus() != 2 && SystemClock.elapsedRealtime() - start < PARSE_TIMEOUT) Thread.sleep(TASK_POLL_INTERVAL);
            TorrentFileInfo media = getFirstMedia(XLTaskHelper.get().getTorrentInfo(magnetTask.getSaveFile()).getMedias());
            if (media == null) throw new ExtractException("No playable media");
            return addTorrentTask(Uri.parse(media.getPlayUrl()));
        } finally {
            if (magnetTask != null && magnetTask.getTaskId() != 0) XLTaskHelper.get().stopTask(magnetTask);
        }
    }

    private TorrentFileInfo getFirstMedia(List<TorrentFileInfo> medias) {
        if (medias == null || medias.isEmpty()) return null;
        TorrentFileInfo result = medias.get(0);
        for (TorrentFileInfo media : medias) if (media.getFileSize() > result.getFileSize()) result = media;
        return result;
    }

    private String addTorrentTask(Uri uri) throws Exception {
        String path = uri.getPath();
        String name = uri.getQueryParameter("name");
        String value = uri.getQueryParameter("index");
        if (path == null || name == null || value == null) throw new ExtractException("Invalid torrent task");
        File torrent = new File(path);
        int index = Integer.parseInt(value);
        taskId = XLTaskHelper.get().addTorrentTask(torrent, Objects.requireNonNull(torrent.getParentFile()), index);
        while (true) {
            XLTaskInfo taskInfo = XLTaskHelper.get().getBtSubTaskInfo(taskId, index).mTaskInfo;
            if (taskInfo.mTaskStatus == 3) throw new ExtractException(taskInfo.getErrorMsg());
            if (taskInfo.mTaskStatus != 0) return XLTaskHelper.get().getLocalUrl(new File(torrent.getParent(), name));
            else Thread.sleep(TASK_POLL_INTERVAL);
        }
    }

    private String addThunderTask(String url) throws ExtractException {
        File folder = Path.thunder(Util.md5(url));
        taskId = XLTaskHelper.get().addThunderTask(url, folder);
        if (taskId.getTaskId() == 0) throw new ExtractException("Create thunder task failed");
        return XLTaskHelper.get().getLocalUrl(taskId.getSaveFile());
    }

    private static boolean isTorrentTask(String url) {
        return url != null && url.startsWith("magnet://");
    }

    @Override
    public void stop() {
        if (taskId == null) return;
        XLTaskHelper.get().deleteTask(taskId);
        taskId = null;
    }

    @Override
    public void exit() {
        XLTaskHelper.get().release();
    }

    public static class Parser implements Callable<List<Episode>> {

        private final String url;
        private int time;

        public static boolean match(String url) {
            return url != null && (url.startsWith("magnet:?") || url.startsWith("thunder://") || url.startsWith("ed2k://") || isTorrent(url));
        }

        public static Parser get(String url) {
            return new Parser(url);
        }

        public Parser(String url) {
            this.url = url;
        }

        private void sleep() throws InterruptedException {
            Thread.sleep(100);
            time += 100;
        }

        private static boolean isTorrent(String url) {
            return url != null && !url.startsWith("magnet") && url.split(";")[0].endsWith(".torrent");
        }

        @Override
        public List<Episode> call() {
            GetTaskId taskId = null;
            try {
                boolean torrent = isTorrent(url);
                List<Episode> episodes = new ArrayList<>();
                taskId = XLTaskHelper.get().parse(url, Path.thunder(Util.md5(url)));
                String realUrl = taskId.getRealUrl();
                if (realUrl == null) return Collections.emptyList();
                if (!torrent && !realUrl.startsWith(MAGNET_QUERY)) return Arrays.asList(Episode.create(taskId.getFileName(), realUrl));
                if (torrent) Download.create(url, taskId.getSaveFile()).start();
                else while (XLTaskHelper.get().getTaskInfo(taskId).getTaskStatus() != 2 && time < PARSE_TIMEOUT) sleep();
                List<TorrentFileInfo> medias = XLTaskHelper.get().getTorrentInfo(taskId.getSaveFile()).getMedias();
                for (TorrentFileInfo media : medias) episodes.add(Episode.create(media.getFileName(), media.getSize(), media.getPlayUrl()));
                return episodes;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return Collections.emptyList();
            } finally {
                if (taskId != null && taskId.getTaskId() != 0) XLTaskHelper.get().stopTask(taskId);
            }
        }
    }
}
