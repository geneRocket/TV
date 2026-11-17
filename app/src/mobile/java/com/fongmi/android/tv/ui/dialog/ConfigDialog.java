package com.fongmi.android.tv.ui.dialog;

import android.content.DialogInterface;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.inputmethod.EditorInfo;

import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.Setting;
import com.fongmi.android.tv.api.config.LiveConfig;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.api.config.WallConfig;
import com.fongmi.android.tv.bean.Config;
import com.fongmi.android.tv.repository.ConfigRepository;
import com.fongmi.android.tv.databinding.DialogConfigBinding;
import com.fongmi.android.tv.impl.ConfigCallback;
import com.fongmi.android.tv.ui.custom.CustomTextListener;
import com.fongmi.android.tv.utils.FileChooser;
import com.fongmi.android.tv.utils.ConfigUrlParser;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.UrlUtil;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class ConfigDialog {

    private final DialogConfigBinding binding;
    private final ConfigCallback callback;
    private final Fragment fragment;
    private AlertDialog dialog;
    private boolean append;
    private boolean edit;
    private String ori;
    private int type;

    public static ConfigDialog create(Fragment fragment) {
        return new ConfigDialog(fragment);
    }

    public ConfigDialog type(int type) {
        this.type = type;
        return this;
    }

    public ConfigDialog edit() {
        this.edit = true;
        return this;
    }

    public ConfigDialog(Fragment fragment) {
        this.fragment = fragment;
        this.callback = (ConfigCallback) fragment;
        this.binding = DialogConfigBinding.inflate(LayoutInflater.from(fragment.getContext()));
        this.append = true;
    }

    public void show() {
        initDialog();
        initView();
        initEvent();
    }

    private void initDialog() {
        dialog = new MaterialAlertDialogBuilder(binding.getRoot().getContext()).setTitle(type == 0 ? R.string.setting_vod : type == 1 ? R.string.setting_live : R.string.setting_wall).setView(binding.getRoot()).setPositiveButton(edit ? R.string.dialog_edit : R.string.dialog_positive, null).setNegativeButton(R.string.dialog_negative, this::onNegative).create();
        dialog.getWindow().setDimAmount(0);
        dialog.show();
    }

    private void initView() {
        Config config = getConfig();
        boolean addingMulti = !edit && (type == 0 || type == 1);
        binding.name.setText(addingMulti || config == null ? "" : config.getName());
        binding.url.setText(ori = addingMulti || config == null ? "" : config.getUrl());
        binding.input.setVisibility(edit ? View.VISIBLE : View.GONE);
        binding.url.setSelection(TextUtils.isEmpty(ori) ? 0 : ori.length());
    }

    private void initEvent() {
        dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener(v -> onPositive());
        binding.choose.setEndIconOnClickListener(this::onChoose);
        binding.url.addTextChangedListener(new CustomTextListener() {
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                detect(s.toString());
            }
        });
        binding.url.setOnEditorActionListener((textView, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) dialog.getButton(DialogInterface.BUTTON_POSITIVE).performClick();
            return true;
        });
    }

    private Config getConfig() {
        switch (type) {
            case 0:
                return VodConfig.get().getConfig();
            case 1:
                return LiveConfig.get().getConfig();
            case 2:
                return WallConfig.get().getConfig();
            default:
                return null;
        }
    }

    private void onChoose(View view) {
        FileChooser.from(fragment).show();
        dialog.dismiss();
    }

    private void detect(String s) {
        if (append && "h".equalsIgnoreCase(s)) {
            append = false;
            binding.url.append("ttp://");
        } else if (append && "f".equalsIgnoreCase(s)) {
            append = false;
            binding.url.append("ile://");
        } else if (append && "a".equalsIgnoreCase(s)) {
            append = false;
            binding.url.append("ssets://");
        } else if (s.length() > 1) {
            append = false;
        } else if (s.length() == 0) {
            append = true;
        }
    }

    private void onPositive() {
        String url = UrlUtil.fixUrl(binding.url.getText().toString().trim());
        String name = binding.name.getText().toString().trim();
        if (!edit && url.isEmpty()) {
            Notify.show(R.string.error_empty);
            return;
        }
        List<String> enabled = type == 0 || type == 1 ? getEnabledUrls() : java.util.Collections.emptyList();
        boolean editingSelection = edit && enabled.contains(ori);
        if (edit && !url.isEmpty() && !ConfigRepository.get().edit(ori, url, name, type)) {
            Notify.show(R.string.error_config_exists);
            return;
        }
        if (url.isEmpty()) ConfigRepository.get().delete(ori, type);
        Config config = name.isEmpty() ? ConfigRepository.get().find(url, type) : ConfigRepository.get().find(url, name, type);
        if (editingSelection || (!edit && (type == 0 || type == 1) && !config.isEmpty())) {
            List<Config> selection = mergeConfigs(config, enabled);
            if (selection.isEmpty()) callback.setConfig(config);
            else callback.setConfigs(selection);
        } else callback.setConfig(config);
        dialog.dismiss();
    }

    private List<Config> mergeConfigs(Config config, List<String> enabled) {
        List<Config> items = new ArrayList<>();
        Set<String> urls = new LinkedHashSet<>();
        for (String oldUrl : enabled) {
            String url = edit && oldUrl.equals(ori) ? config.getUrl() : oldUrl;
            if (url.isEmpty() || !urls.add(url)) continue;
            items.add(url.equals(config.getUrl()) ? config : ConfigRepository.get().find(url, type));
        }
        if (!config.isEmpty() && urls.add(config.getUrl())) items.add(config);
        return items;
    }

    private List<String> getEnabledUrls() {
        String value = type == 0 ? Setting.getVodConfigUrls() : Setting.getLiveConfigUrls();
        List<String> urls = new ArrayList<>(ConfigUrlParser.parse(value));
        if (!urls.isEmpty()) return urls;
        String current = type == 0 ? VodConfig.getUrl() : LiveConfig.getUrl();
        if (!current.isEmpty()) urls.add(current);
        return urls;
    }

    private void onNegative(DialogInterface dialog, int which) {
        dialog.dismiss();
    }
}
