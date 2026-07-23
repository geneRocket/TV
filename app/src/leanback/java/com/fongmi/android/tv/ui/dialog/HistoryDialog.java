package com.fongmi.android.tv.ui.dialog;

import android.app.Activity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;

import androidx.fragment.app.FragmentActivity;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.Setting;
import com.fongmi.android.tv.api.config.LiveConfig;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.bean.Config;
import com.fongmi.android.tv.repository.ConfigRepository;
import com.fongmi.android.tv.databinding.DialogConfigHistoryBinding;
import com.fongmi.android.tv.impl.ConfigCallback;
import com.fongmi.android.tv.ui.adapter.ConfigAdapter;
import com.fongmi.android.tv.ui.custom.SpaceItemDecoration;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.ConfigUrlParser;
import com.fongmi.android.tv.utils.ResUtil;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.List;

public class HistoryDialog implements ConfigAdapter.OnClickListener {

    private final FragmentActivity activity;
    private final DialogConfigHistoryBinding binding;
    private final ConfigCallback callback;
    private final ConfigAdapter adapter;
    private final androidx.appcompat.app.AlertDialog dialog;
    private Runnable onAdd;
    private int type;
    private boolean multi;

    public static HistoryDialog create(Activity activity) {
        return new HistoryDialog(activity);
    }

    public HistoryDialog type(int type) {
        this.type = type;
        return this;
    }

    public HistoryDialog add(Runnable onAdd) {
        this.onAdd = onAdd;
        return this;
    }

    public HistoryDialog(Activity activity) {
        this.activity = (FragmentActivity) activity;
        this.callback = (ConfigCallback) activity;
        this.binding = DialogConfigHistoryBinding.inflate(LayoutInflater.from(activity));
        this.dialog = new MaterialAlertDialogBuilder(activity).setView(binding.getRoot()).create();
        this.adapter = new ConfigAdapter(this);
    }

    public void show() {
        setView();
        setRecyclerView();
    }

    private void setView() {
        binding.add.setVisibility(View.VISIBLE);
        binding.add.setOnClickListener(v -> onAddClick());
        binding.negative.setOnClickListener(v -> dialog.dismiss());
        binding.positive.setOnClickListener(v -> onPositive());
    }

    private void onAddClick() {
        Runnable action = getOnAdd();
        dialog.dismiss();
        activity.getWindow().getDecorView().post(action);
    }

    private void setRecyclerView() {
        multi = type == 0 || type == 1;
        binding.recycler.setHasFixedSize(true);
        binding.recycler.setAdapter(adapter.multi(multi).includeCurrent(multi));
        if (binding.recycler.getItemDecorationCount() == 0) binding.recycler.addItemDecoration(new SpaceItemDecoration(1, 16));
        App.execute(() -> {
            List<Config> items = ConfigRepository.get().all(type);
            if (!multi) items.remove(type == 0 ? VodConfig.get().getConfig() : LiveConfig.get().getConfig());
            App.post(() -> {
                if (activity.isFinishing() || activity.isDestroyed()) return;
                adapter.setItems(items);
                if (multi) adapter.select(getEnabledUrls());
                setDialog();
                if (dialog.isShowing()) binding.recycler.requestFocus();
            });
        });
    }

    private void setDialog() {
        if (dialog.isShowing()) return;
        if (adapter.getItemCount() == 0 && getOnAdd() == null) return;
        WindowManager.LayoutParams params = dialog.getWindow().getAttributes();
        params.width = (int) (ResUtil.getScreenWidth() * 0.4f);
        dialog.getWindow().setAttributes(params);
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
        int index = adapter.remove(item);
        if (multi) syncEnabledConfigs();
        if (adapter.getItemCount() == 0) {
            dialog.dismiss();
            return;
        }
        focusAfterDelete(index);
    }

    private void focusAfterDelete(int index) {
        if (index == -1) return;
        int targetIndex = index == adapter.getItemCount() ? index - 1 : index;
        binding.recycler.post(() -> {
            View view = binding.recycler.getLayoutManager() == null ? null : binding.recycler.getLayoutManager().findViewByPosition(targetIndex);
            if (view != null) view.requestFocus();
        });
    }

    private void onPositive() {
        if (!multi) return;
        List<Config> selected = adapter.getSelected();
        if (selected.isEmpty()) {
            Notify.show(R.string.error_empty);
            return;
        }
        if (selected.size() == 1) callback.setConfig(selected.get(0));
        else callback.setConfigs(selected);
        dialog.dismiss();
    }

    private Runnable getOnAdd() {
        if (onAdd != null) return onAdd;
        return () -> ConfigDialog.create(activity).type(type).show();
    }

    private List<String> getEnabledUrls() {
        String value = type == 0 ? Setting.getVodConfigUrls() : Setting.getLiveConfigUrls();
        List<String> urls = new ArrayList<>(ConfigUrlParser.parse(value));
        if (!urls.isEmpty()) return urls;
        if (type == 0 && !VodConfig.getUrl().isEmpty()) urls.add(VodConfig.getUrl());
        if (type == 1 && !LiveConfig.getUrl().isEmpty()) urls.add(LiveConfig.getUrl());
        return urls;
    }

    private void syncEnabledConfigs() {
        List<Config> selected = adapter.getSelected();
        if (type == 0) {
            Setting.putVodConfigDesc(getConfigsDesc(selected));
            Setting.putVodConfigUrls(getConfigsUrls(selected));
        } else if (type == 1) {
            Setting.putLiveConfigDesc(getConfigsDesc(selected));
            Setting.putLiveConfigUrls(getConfigsUrls(selected));
        }
    }

    private String getConfigsDesc(List<Config> configs) {
        if (configs.isEmpty()) return "";
        if (configs.size() == 1) return configs.get(0).getDesc();
        if (configs.size() == 2) return configs.get(0).getDesc() + " + " + configs.get(1).getDesc();
        return configs.get(0).getDesc() + " +" + (configs.size() - 1);
    }

    private String getConfigsUrls(List<Config> configs) {
        List<String> urls = new ArrayList<>();
        for (Config config : configs) urls.add(config.getUrl());
        return String.join("\n", urls);
    }
}
