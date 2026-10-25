package com.fongmi.android.tv.api.config;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Immutable validated input. Runtime selection models are deliberately separate. */
public final class ConfigDocument {
    private final String content;
    private final List<String> urls;
    public ConfigDocument(JsonObject object, List<String> urls) {
        this.content = object.toString();
        this.urls = Collections.unmodifiableList(new ArrayList<>(urls));
    }
    public JsonObject object() { return JsonParser.parseString(content).getAsJsonObject(); }
    public List<String> urls() { return urls; }
}
