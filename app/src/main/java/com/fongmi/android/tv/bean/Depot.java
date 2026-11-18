package com.fongmi.android.tv.bean;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.utils.TextValues;
import com.fongmi.android.tv.utils.UrlUtil;
import com.google.gson.annotations.SerializedName;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.Collections;
import java.util.List;

public class Depot {

    @SerializedName("url")
    private String url;
    @SerializedName("name")
    private String name;

    public Depot(String url, String name) {
        this.url = url;
        this.name = name;
    }

    public static List<Depot> arrayFrom(String str) {
        Type listType = new TypeToken<List<Depot>>() {}.getType();
        List<Depot> items = App.gson().fromJson(str, listType);
        return items == null ? Collections.emptyList() : items;
    }

    public String getUrl() {
        return TextValues.isEmpty(url) ? "" : UrlUtil.fixUrl(url);
    }

    public String getName() {
        return TextValues.isEmpty(name) ? getUrl() : name;
    }
}
