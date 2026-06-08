package com.fongmi.android.tv.bean;

import android.text.TextUtils;

import androidx.annotation.Nullable;

import com.fongmi.android.tv.App;
import com.google.gson.JsonElement;
import com.google.gson.annotations.SerializedName;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

public class Rule {

    @SerializedName("name")
    private String name;
    @SerializedName("hosts")
    private List<String> hosts;
    @SerializedName("regex")
    private List<String> regex;
    @SerializedName("script")
    private List<String> script;
    @SerializedName("exclude")
    private List<String> exclude;

    private List<Pattern> regexPatterns;
    private List<Pattern> excludePatterns;

    public static Rule create(String name) {
        return new Rule(name);
    }

    public static Rule empty() {
        return new Rule("");
    }

    public Rule(String name) {
        this.name = name;
    }

    public static List<Rule> arrayFrom(JsonElement element) {
        Type listType = new TypeToken<List<Rule>>() {}.getType();
        List<Rule> items = App.gson().fromJson(element, listType);
        return items == null ? Collections.emptyList() : items;
    }

    public String getName() {
        return TextUtils.isEmpty(name) ? "" : name;
    }

    public List<String> getHosts() {
        return hosts == null ? Collections.emptyList() : hosts;
    }

    public List<String> getRegex() {
        return regex == null ? Collections.emptyList() : regex;
    }

    public List<Pattern> getRegexPatterns() {
        if (regexPatterns == null) {
            regexPatterns = new ArrayList<>();
            for (String item : getRegex()) regexPatterns.add(Pattern.compile(item));
        }
        return regexPatterns;
    }

    public List<String> getScript() {
        return script == null ? Collections.emptyList() : script;
    }

    public List<String> getExclude() {
        return exclude == null ? Collections.emptyList() : exclude;
    }

    public List<Pattern> getExcludePatterns() {
        if (excludePatterns == null) {
            excludePatterns = new ArrayList<>();
            for (String item : getExclude()) excludePatterns.add(Pattern.compile(item));
        }
        return excludePatterns;
    }

    @Override
    public boolean equals(@Nullable Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof Rule)) return false;
        Rule it = (Rule) obj;
        return getName().equals(it.getName());
    }
}
