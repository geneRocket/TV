package com.fongmi.android.tv.ui.dialog;

import android.Manifest;
import android.content.DialogInterface;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;

import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.FragmentActivity;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.Setting;
import com.fongmi.android.tv.api.config.LiveConfig;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.api.config.WallConfig;
import com.fongmi.android.tv.bean.Config;
import com.fongmi.android.tv.databinding.DialogConfigBinding;
import com.fongmi.android.tv.event.ServerEvent;
import com.fongmi.android.tv.impl.ConfigCallback;
import com.fongmi.android.tv.server.Server;
import com.fongmi.android.tv.ui.custom.CustomTextListener;
import com.fongmi.android.tv.utils.QRCode;
import com.fongmi.android.tv.utils.ResUtil;
import com.fongmi.android.tv.utils.UrlUtil;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.permissionx.guolindev.PermissionX;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class ConfigDialog implements DialogInterface.OnDismissListener {

    private final DialogConfigBinding binding;
    private final FragmentActivity activity;
    private final ConfigCallback callback;
    private final AlertDialog dialog;
    private boolean append;
    private boolean registered;
    private boolean edit;
    private boolean reopenHistory;
    private String url;
    private int type;

    public static ConfigDialog create(FragmentActivity activity) {
        return new ConfigDialog(activity);
    }

    public ConfigDialog type(int type) {
        this.type = type;
        return this;
    }

    public ConfigDialog edit() {
        this.edit = true;
        return this;
    }

    public ConfigDialog returnToHistory() {
        this.reopenHistory = true;
        return this;
    }

    public ConfigDialog(FragmentActivity activity) {
        this.activity = activity;
        this.callback = (ConfigCallback) activity;
        this.binding = DialogConfigBinding.inflate(LayoutInflater.from(activity));
        this.dialog = new MaterialAlertDialogBuilder(activity).setView(binding.getRoot()).create();
        this.append = true;
    }

    public void show() {
        initDialog();
        initView();
        initEvent();
    }

    private void initDialog() {
        WindowManager.LayoutParams params = dialog.getWindow().getAttributes();
        params.width = (int) (ResUtil.getScreenWidth() * 0.55f);
        dialog.getWindow().setAttributes(params);
        dialog.getWindow().setDimAmount(0);
        dialog.setOnDismissListener(this);
        dialog.show();
    }

    private void initView() {
        binding.text.setText(url = getUrl());
        binding.text.setSelection(TextUtils.isEmpty(url) ? 0 : url.length());
        binding.text.requestFocus();
        binding.positive.setText(edit ? R.string.dialog_edit : R.string.dialog_positive);
        binding.code.setImageBitmap(QRCode.getBitmap(Server.get().getAddress(3), 200, 0));
        binding.info.setText(ResUtil.getString(R.string.push_info, Server.get().getAddress()).replace("，", "\n"));
        binding.storage.setVisibility(PermissionX.isGranted(activity, Manifest.permission.WRITE_EXTERNAL_STORAGE) ? View.GONE : View.VISIBLE);
    }

    private void initEvent() {
        if (!registered) {
            EventBus.getDefault().register(this);
            registered = true;
        }
        binding.storage.setOnClickListener(this::onStorage);
        binding.positive.setOnClickListener(this::onPositive);
        binding.negative.setOnClickListener(this::onNegative);
        binding.text.addTextChangedListener(new CustomTextListener() {
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                detect(s.toString());
            }
        });
        binding.text.setOnEditorActionListener((textView, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) binding.positive.performClick();
            return true;
        });
    }

    private String getUrl() {
        switch (type) {
            case 0:
                return VodConfig.getUrl();
            case 1:
                return LiveConfig.getUrl();
            case 2:
                return WallConfig.getUrl();
            default:
                return "";
        }
    }

    private void onStorage(View view) {
        PermissionX.init(activity).permissions(Manifest.permission.WRITE_EXTERNAL_STORAGE).request((allGranted, grantedList, deniedList) -> binding.storage.setVisibility(allGranted ? View.GONE : View.VISIBLE));
    }

    private void detect(String s) {
        if (append && "h".equalsIgnoreCase(s)) {
            append = false;
            binding.text.append("ttp://");
        } else if (append && "f".equalsIgnoreCase(s)) {
            append = false;
            binding.text.append("ile://");
        } else if (append && "a".equalsIgnoreCase(s)) {
            append = false;
            binding.text.append("ssets://");
        } else if (s.length() > 1) {
            append = false;
        } else if (s.length() == 0) {
            append = true;
        }
    }

    private void onPositive(View view) {
        String name = binding.name.getText() == null ? "" : binding.name.getText().toString().trim();
        String text = UrlUtil.fixUrl(binding.text.getText() == null ? "" : binding.text.getText().toString().trim());
        if (edit) Config.find(url, type).url(text).name(name).update();
        if (text.isEmpty()) Config.delete(url, type);
        Config config = name.isEmpty() ? Config.find(text, type) : Config.find(text, name, type);
        if (!edit && (type == 0 || type == 1) && !config.isEmpty()) callback.setConfigs(mergeConfigs(config));
        else callback.setConfig(config);
        dialog.dismiss();
        reopenHistory(config);
    }

    private List<Config> mergeConfigs(Config config) {
        List<Config> items = new ArrayList<>();
        Set<String> urls = new LinkedHashSet<>();
        for (String url : getEnabledUrls()) {
            if (!urls.add(url)) continue;
            items.add(Config.find(url, type));
        }
        if (urls.add(config.getUrl())) items.add(config);
        else items.set(findConfigIndex(items, config.getUrl()), config);
        return items;
    }

    private int findConfigIndex(List<Config> items, String url) {
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).getUrl().equals(url)) return i;
        }
        return 0;
    }

    private List<String> getEnabledUrls() {
        String value = type == 0 ? Setting.getVodConfigUrls() : Setting.getLiveConfigUrls();
        List<String> urls = new ArrayList<>();
        for (String item : value.split("[\\n\\r,，;；|]+")) {
            String url = item.trim();
            if (!url.isEmpty()) urls.add(url);
        }
        if (!urls.isEmpty()) return urls;
        String current = type == 0 ? VodConfig.getUrl() : LiveConfig.getUrl();
        if (!current.isEmpty()) urls.add(current);
        return urls;
    }

    private void reopenHistory(Config config) {
        if (!reopenHistory || edit || config == null || config.isEmpty()) return;
        if (type != 0 && type != 1) return;
        activity.getWindow().getDecorView().post(() -> HistoryDialog.create(activity).type(type).add(() -> ConfigDialog.create(activity).type(type).returnToHistory().show()).show());
    }

    private void onNegative(View view) {
        dialog.dismiss();
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onServerEvent(ServerEvent event) {
        if (event.getType() != ServerEvent.Type.SETTING) return;
        binding.name.setText(event.getName());
        binding.text.setText(event.getText());
        binding.text.setSelection(binding.text.getText() == null ? 0 : binding.text.getText().length());
    }

    @Override
    public void onDismiss(DialogInterface dialogInterface) {
        if (registered) {
            EventBus.getDefault().unregister(this);
            registered = false;
        }
    }
}
