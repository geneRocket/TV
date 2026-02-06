package com.fongmi.android.tv.ui.dialog;

import android.view.LayoutInflater;

import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.FragmentActivity;

import com.fongmi.android.tv.Setting;
import com.fongmi.android.tv.databinding.DialogVolumeBinding;
import com.fongmi.android.tv.utils.KeyUtil;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

public class VolumeDialog {

    private final DialogVolumeBinding binding;
    private final AlertDialog dialog;

    public static VolumeDialog create(FragmentActivity activity) {
        return new VolumeDialog(activity);
    }

    public VolumeDialog(FragmentActivity activity) {
        this.binding = DialogVolumeBinding.inflate(LayoutInflater.from(activity));
        this.dialog = new MaterialAlertDialogBuilder(activity).setView(binding.getRoot()).create();
    }

    public void show() {
        initDialog();
        initView();
        initEvent();
    }

    private void initDialog() {
        dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        dialog.show();
    }

    private void initView() {
        binding.slider.setValue(Setting.getVolumeScale() * 100f);
    }

    private void initEvent() {
        binding.slider.addOnChangeListener((slider, value, fromUser) -> Setting.putVolumeScale(value / 100f));
        binding.slider.setOnKeyListener((view, keyCode, event) -> {
            boolean enter = KeyUtil.isEnterKey(event);
            if (enter) dialog.dismiss();
            return enter;
        });
    }
}
