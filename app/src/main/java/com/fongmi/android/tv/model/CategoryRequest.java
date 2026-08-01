package com.fongmi.android.tv.model;

import android.text.TextUtils;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.bean.Result;

import java.util.Map;
import java.util.TreeMap;

public final class CategoryRequest {

    private final String key;
    private final String typeId;
    private final String page;
    private final String extend;

    private CategoryRequest(String key, String typeId, String page, Map<String, String> extend) {
        this.key = key;
        this.typeId = typeId;
        this.page = page;
        this.extend = extend == null || extend.isEmpty() ? "" : App.gson().toJson(new TreeMap<>(extend));
    }

    public static CategoryRequest create(String key, String typeId, String page, Map<String, String> extend) {
        return new CategoryRequest(key, typeId, page, extend);
    }

    public boolean matches(Result result) {
        return result != null
                && TextUtils.equals(key, result.getKey())
                && TextUtils.equals(typeId, result.getRequestTypeId())
                && TextUtils.equals(page, result.getRequestPage())
                && TextUtils.equals(extend, result.getRequestExtend());
    }
}
