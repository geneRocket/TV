package com.fongmi.android.tv.ui.dialog;

import android.view.KeyEvent;
import android.view.LayoutInflater;

import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.FragmentActivity;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.Setting;
import com.fongmi.android.tv.databinding.DialogVolumeBinding;
import com.fongmi.android.tv.utils.KeyUtil;
import com.fongmi.android.tv.utils.ResUtil;
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
        int value = (Setting.getLoudness() / 10) * 10;
        binding.slider.setValueTo(Math.max(5000, value));
        binding.slider.setStepSize(10);
        binding.slider.setValue(value);
    }

    private void initEvent() {
        binding.slider.addOnChangeListener((slider, value, fromUser) -> {
            Setting.putLoudness((int) value);
        });
        binding.slider.setOnKeyListener((view, keyCode, event) -> {
            if (event.getAction() == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_DPAD_RIGHT && binding.slider.getValue() >= binding.slider.getValueTo()) {
                binding.slider.setValueTo(binding.slider.getValueTo() + 1000);
            }
            boolean enter = KeyUtil.isEnterKey(event);
            if (enter) dialog.dismiss();
            return enter;
        });
    }
}
