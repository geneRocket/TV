package com.fongmi.android.tv.ui.dialog;

import android.view.LayoutInflater;

import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.Setting;
import com.fongmi.android.tv.api.config.LiveConfig;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.bean.Config;
import com.fongmi.android.tv.databinding.DialogHistoryBinding;
import com.fongmi.android.tv.impl.ConfigCallback;
import com.fongmi.android.tv.ui.adapter.ConfigAdapter;
import com.fongmi.android.tv.ui.custom.SpaceItemDecoration;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.List;

public class HistoryDialog implements ConfigAdapter.OnClickListener {

    private final DialogHistoryBinding binding;
    private final ConfigCallback callback;
    private final ConfigAdapter adapter;
    private final AlertDialog dialog;
    private Runnable onAdd;
    private int type;
    private boolean multi;

    public static HistoryDialog create(Fragment fragment) {
        return new HistoryDialog(fragment);
    }

    public HistoryDialog type(int type) {
        this.type = type;
        return this;
    }

    public HistoryDialog add(Runnable onAdd) {
        this.onAdd = onAdd;
        return this;
    }

    public HistoryDialog(Fragment fragment) {
        this.callback = (ConfigCallback) fragment;
        this.binding = DialogHistoryBinding.inflate(LayoutInflater.from(fragment.getContext()));
        this.dialog = new MaterialAlertDialogBuilder(fragment.getActivity()).setView(binding.getRoot()).setPositiveButton(R.string.dialog_positive, (dialog, which) -> onPositive()).setNegativeButton(R.string.dialog_negative, null).create();
        this.adapter = new ConfigAdapter(this);
    }

    public void show() {
        setRecyclerView();
        setDialog();
    }

    private void setRecyclerView() {
        multi = type == 0 || type == 1;
        binding.recycler.setHasFixedSize(true);
        binding.recycler.addItemDecoration(new SpaceItemDecoration(1, 8));
        binding.recycler.setAdapter(adapter.multi(multi).includeCurrent(multi).addAll(type));
        if (multi) adapter.select(getEnabledUrls());
    }

    private void setDialog() {
        if (adapter.getItemCount() == 0 && onAdd == null) return;
        if (onAdd != null) dialog.setButton(AlertDialog.BUTTON_NEUTRAL, dialog.getContext().getString(R.string.dialog_add), (d, w) -> onAdd.run());
        dialog.getWindow().setDimAmount(0);
        dialog.show();
    }

    @Override
    public void onTextClick(Config item) {
        if (!multi) {
            callback.setConfig(item);
            dialog.dismiss();
            return;
        }
        adapter.toggle(item);
    }

    @Override
    public void onDeleteClick(Config item) {
        if (adapter.remove(item) == 0) dialog.dismiss();
    }

    private void onPositive() {
        if (!multi) return;
        List<Config> selected = adapter.getSelected();
        if (selected.isEmpty()) return;
        if (selected.size() == 1) callback.setConfig(selected.get(0));
        else callback.setConfigs(selected);
    }

    private List<String> getEnabledUrls() {
        String value = type == 0 ? Setting.getVodConfigUrls() : Setting.getLiveConfigUrls();
        List<String> urls = new ArrayList<>();
        for (String url : value.split("[\\n\\r,，;；|]+")) if (!url.trim().isEmpty()) urls.add(url.trim());
        if (!urls.isEmpty()) return urls;
        if (type == 0 && !VodConfig.getUrl().isEmpty()) urls.add(VodConfig.getUrl());
        if (type == 1 && !LiveConfig.getUrl().isEmpty()) urls.add(LiveConfig.getUrl());
        return urls;
    }
}
