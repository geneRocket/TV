package com.fongmi.android.tv.ui.dialog;

import android.app.Activity;

import com.fongmi.android.tv.bean.Sub;
import com.fongmi.android.tv.player.PlaybackCommand;
import com.fongmi.android.tv.player.PlaybackController;
import com.github.catvod.utils.Path;
import com.obsez.android.lib.filechooser.ChooserDialog;

import java.io.File;

public class FileChooserDialog {

    public static final int MODE_SUBTITLE = 0;
    public static final int MODE_DANMAKU = 1;

    private ChooserDialog dialog;
    private TrackDialog trackDialog;
    private PlaybackController player;
    private int mode = MODE_SUBTITLE;

    public static FileChooserDialog create() {
        return new FileChooserDialog();
    }

    public FileChooserDialog player(PlaybackController player) {
        this.player = player;
        return this;
    }

    public FileChooserDialog trackDialog(TrackDialog dialog) {
        this.trackDialog = dialog;
        return this;
    }

    public FileChooserDialog mode(int mode) {
        this.mode = mode;
        return this;
    }

    public void show(Activity activity) {
        dialog = new ChooserDialog(activity);
        if (mode == MODE_DANMAKU) dialog.withFilter(false, false, "xml", "txt", "json");
        else dialog.withFilter(false, false, "srt", "ass", "scc", "stl", "ttml");
        dialog.withStartFile(Path.downloadPath());
        dialog.withChosenListener(this::onChoosePath);
        dialog.build().show();
    }


    private void onChoosePath(String path, File pathFile) {
        if (mode == MODE_DANMAKU) player.command(PlaybackCommand.fromRefresh("danmaku", pathFile.getAbsolutePath()));
        else player.setSub(Sub.from(pathFile.getAbsolutePath()));
        if (dialog != null) dialog.dismiss();
        if (trackDialog != null) trackDialog.dismiss();
    }

}
