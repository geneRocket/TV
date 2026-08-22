package com.fongmi.android.tv.ui.activity;

import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.databinding.ActivityCrashBinding;
import cat.ereza.customactivityoncrash.CustomActivityOnCrash;
import cat.ereza.customactivityoncrash.config.CaocConfig;

public class CrashActivity extends AppCompatActivity {

    private ActivityCrashBinding mBinding;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mBinding = ActivityCrashBinding.inflate(getLayoutInflater());
        setContentView(mBinding.getRoot());
        initEvent();
    }

    private void initEvent() {
        mBinding.details.setOnClickListener(v -> showError());
        mBinding.restart.setOnClickListener(v -> {
            CaocConfig config = CustomActivityOnCrash.getConfigFromIntent(getIntent());
            if (config == null) finish();
            else CustomActivityOnCrash.restartApplication(this, config);
        });
    }

    private void showError() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.crash_details_title)
                .setMessage(CustomActivityOnCrash.getAllErrorDetailsFromIntent(this, getIntent()))
                .setPositiveButton(R.string.crash_details_close, null)
                .show();
    }
}
