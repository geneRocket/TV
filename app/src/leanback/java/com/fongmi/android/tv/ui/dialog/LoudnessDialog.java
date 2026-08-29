package com.fongmi.android.tv.ui.dialog;

import android.view.LayoutInflater;

import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.FragmentActivity;

import com.fongmi.android.tv.Setting;
import com.fongmi.android.tv.databinding.DialogVolumeBinding;
import com.fongmi.android.tv.utils.KeyUtil;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

public class LoudnessDialog {

    private final DialogVolumeBinding binding;
    private final AlertDialog dialog;

    public static LoudnessDialog create(FragmentActivity activity) {
        return new LoudnessDialog(activity);
    }

    public LoudnessDialog(FragmentActivity activity) {
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
        binding.slider.setValue(Setting.getLoudness() / 100f);
        binding.slider.setValueTo(100);
        binding.slider.setStepSize(1);
    }

    private void initEvent() {
        binding.slider.addOnChangeListener((slider, value, fromUser) -> Setting.putLoudness((int) value * 100));
        binding.slider.setOnKeyListener((view, keyCode, event) -> {
            boolean enter = KeyUtil.isEnterKey(event);
            if (enter) dialog.dismiss();
            return enter;
        });
    }
}
