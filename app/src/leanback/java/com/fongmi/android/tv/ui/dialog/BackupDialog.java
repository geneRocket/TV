package com.fongmi.android.tv.ui.dialog;

import android.app.Activity;
import android.view.LayoutInflater;
import android.view.WindowManager;

import androidx.appcompat.app.AlertDialog;
import com.fongmi.android.tv.App;
import com.fongmi.android.tv.databinding.DialogBackupBinding;
import com.fongmi.android.tv.db.AppDatabase;
import com.fongmi.android.tv.impl.BackupCallback;
import com.fongmi.android.tv.ui.adapter.BackupAdapter;
import com.fongmi.android.tv.ui.custom.SpaceItemDecoration;
import com.fongmi.android.tv.utils.ResUtil;
import com.github.catvod.utils.Path;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class BackupDialog implements BackupAdapter.OnClickListener {

    private final Activity activity;
    private final DialogBackupBinding binding;
    private final BackupCallback callback;
    private final BackupAdapter adapter;
    private final AlertDialog dialog;

    public static BackupDialog create(Activity activity) {
        return new BackupDialog(activity);
    }


    public BackupDialog(Activity activity) {
        this.activity = activity;
        this.callback = (BackupCallback) activity;
        this.binding = DialogBackupBinding.inflate(LayoutInflater.from(activity));
        this.dialog = new MaterialAlertDialogBuilder(activity).setView(binding.getRoot()).create();
        this.adapter = new BackupAdapter(this);
    }

    public void show() {
        setRecyclerView();
    }

    private void setRecyclerView() {
        binding.recycler.setHasFixedSize(true);
        binding.recycler.setAdapter(adapter);
        if (binding.recycler.getItemDecorationCount() == 0) binding.recycler.addItemDecoration(new SpaceItemDecoration(1, 16));
        App.execute(() -> {
            List<String> items = new ArrayList<>();
            for (File file : Path.list(Path.tv())) if (file.getAbsolutePath().endsWith(AppDatabase.BACKUP_SUFFIX)) items.add(file.getName().replace("." + AppDatabase.BACKUP_SUFFIX, ""));
            Collections.sort(items);
            App.post(() -> {
                if (activity.isFinishing() || activity.isDestroyed()) return;
                adapter.setItems(items);
                setDialog();
                if (dialog.isShowing()) binding.recycler.requestFocus();
            });
        });
    }

    private void setDialog() {
        if (dialog.isShowing()) return;
        if (adapter.getItemCount() == 0) return;
        WindowManager.LayoutParams params = dialog.getWindow().getAttributes();
        params.width = (int) (ResUtil.getScreenWidth() * 0.4f);
        dialog.getWindow().setAttributes(params);
        dialog.getWindow().setDimAmount(0);
        dialog.show();
    }

    @Override
    public void onTextClick(String item) {
        callback.restore(new File(Path.tv(), item + "." + AppDatabase.BACKUP_SUFFIX));
        dialog.dismiss();
    }

    @Override
    public void onDeleteClick(String item) {
        if (adapter.remove(item) == 0) dialog.dismiss();
    }
}
