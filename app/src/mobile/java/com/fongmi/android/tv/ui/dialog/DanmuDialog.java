package com.fongmi.android.tv.ui.dialog;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.Setting;
import com.fongmi.android.tv.bean.Danmaku;
import com.fongmi.android.tv.databinding.DialogDanmuBinding;
import com.fongmi.android.tv.ui.activity.VideoActivity;
import com.fongmi.android.tv.utils.ResUtil;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.slider.Slider;

import java.util.List;

public class DanmuDialog extends BaseDialog {

    private DialogDanmuBinding binding;
    private String[] danmuSpeed;
    private int speed;
    private VideoActivity activity;

    public static DanmuDialog create() {
        return new DanmuDialog();
    }

    public DanmuDialog() {
    }

    public DanmuDialog show(FragmentActivity activity) {
        this.activity = (VideoActivity) activity;
        for (Fragment f : activity.getSupportFragmentManager().getFragments()) if (f instanceof BottomSheetDialogFragment) return this;
        show(activity.getSupportFragmentManager(), null);
        return this;
    }

    @Override
    protected ViewBinding getBinding(@NonNull LayoutInflater inflater, @Nullable ViewGroup container) {
        return binding = DialogDanmuBinding.inflate(inflater, container, false);
    }

    @Override
    protected void initView() {
        binding.list.setVisibility(View.VISIBLE);
        binding.speed.setValue(speed = Setting.getDanmuSpeed());
        binding.size.setValue(Setting.getDanmuSize());
        binding.line.setValue(Setting.getDanmuLine(2));
        binding.alpha.setValue(Setting.getDanmuAlpha());
        binding.speedText.setText((danmuSpeed = ResUtil.getStringArray(R.array.select_danmu_speed))[speed]);
        binding.lineText.setText(String.valueOf(Setting.getDanmuLine(2)));
        binding.alphaText.setText(Setting.getDanmuAlpha() + "%");
        setSourceList(activity == null ? List.of() : activity.getDanmakus());
    }

    @Override
    protected void initEvent() {
        binding.choose.setOnClickListener(view -> {
            if (activity != null) activity.chooseDanmakuFile();
            safeDismiss();
        });
        binding.speed.addOnChangeListener((@NonNull Slider slider, float value, boolean fromUser) -> {
            int val = (int) slider.getValue();
            binding.speedText.setText(danmuSpeed[val]);
            Setting.putDanmuSpeed(val);
            this.activity.setDanmuViewSettings();
        });
        binding.size.addOnChangeListener((@NonNull Slider slider, float value, boolean fromUser) -> {
            Setting.putDanmuSize(slider.getValue());
            this.activity.setDanmuViewSettings();
        });
        binding.line.addOnChangeListener((@NonNull Slider slider, float value, boolean fromUser) -> {
            Setting.putDanmuLine((int) slider.getValue());
            binding.lineText.setText(String.valueOf((int) slider.getValue()));
            this.activity.setDanmuViewSettings();
        });
        binding.alpha.addOnChangeListener((@NonNull Slider slider, float value, boolean fromUser) -> {
            Setting.putDanmuAlpha((int) slider.getValue());
            binding.alphaText.setText((int) slider.getValue() + "%");
            this.activity.setDanmuViewSettings();
        });
    }

    private void setSourceList(List<Danmaku> items) {
        binding.sources.removeAllViews();
        binding.sourceTitle.setVisibility(items.isEmpty() ? View.GONE : View.VISIBLE);
        binding.sources.setVisibility(items.isEmpty() ? View.GONE : View.VISIBLE);
        for (Danmaku item : items) binding.sources.addView(createSourceView(item));
    }

    private View createSourceView(Danmaku item) {
        TextView view = new TextView(requireContext());
        view.setText(item.isSelected() ? "• " + item.getName() : item.getName());
        view.setPadding(0, 12, 0, 12);
        view.setTextSize(14);
        view.setTextColor(ResUtil.getColor(item.isSelected() ? R.color.blue_500 : android.R.color.white));
        view.setOnClickListener(v -> {
            activity.setDanmaku(item);
            safeDismiss();
        });
        return view;
    }
}
