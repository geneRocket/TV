package com.fongmi.android.tv.ui.base;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.viewbinding.ViewBinding;

public abstract class BaseFragment extends Fragment {

    protected abstract ViewBinding getBinding(@NonNull LayoutInflater inflater, @Nullable ViewGroup container);

    private boolean initialized;
    private boolean viewCreated;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return getBinding(inflater, container).getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        viewCreated = true;
        initView();
        initEvent();
        tryInitData();
    }

    protected void initView() {
    }

    protected void initEvent() {
    }

    protected void initData() {
    }

    private void tryInitData() {
        if (initialized || !viewCreated || !isResumed()) return;
        initData();
        initialized = true;
    }

    @Override
    public void onResume() {
        super.onResume();
        tryInitData();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        viewCreated = false;
        initialized = false;
    }
}
