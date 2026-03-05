package com.fongmi.android.tv.impl;

import com.fongmi.android.tv.bean.Config;

import java.util.List;

public interface ConfigCallback {

    void setConfig(Config config);

    default void setConfigs(List<Config> configs) {
        if (configs == null || configs.isEmpty()) return;
        setConfig(configs.get(0));
    }
}
