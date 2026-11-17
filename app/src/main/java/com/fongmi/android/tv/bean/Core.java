package com.fongmi.android.tv.bean;

import android.content.pm.PackageInfo;
import android.content.pm.Signature;
import android.text.TextUtils;

import androidx.annotation.Nullable;

import com.fongmi.android.tv.utils.UrlUtil;
import com.fongmi.hook.PackageManager;
import com.google.gson.annotations.SerializedName;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class Core extends PackageManager {

    @SerializedName("auth")
    private String auth;
    @SerializedName("name")
    private String name;
    @SerializedName("pass")
    private String pass;
    @SerializedName("broker")
    private String broker;
    @SerializedName("domain")
    private String domain;
    @SerializedName("option")
    private List<Option> options;
    @SerializedName("resp")
    private String resp;
    @SerializedName("sign")
    private String sign;
    @SerializedName("pkg")
    private String pkg;
    @SerializedName("so")
    private String so;

    public String getAuth() {
        return TextUtils.isEmpty(auth) ? "" : UrlUtil.convert(auth);
    }

    public String getName() {
        return TextUtils.isEmpty(name) ? "" : name;
    }

    public String getPass() {
        return TextUtils.isEmpty(pass) ? "" : pass;
    }

    public String getBroker() {
        return TextUtils.isEmpty(broker) ? "" : broker;
    }

    public String getDomain() {
        return Objects.toString(domain, "");
    }

    public List<Option> getOptions() {
        return options == null ? Collections.emptyList() : options;
    }

    public String getResp() {
        return TextUtils.isEmpty(resp) ? "" : resp;
    }

    public String getSign() {
        return TextUtils.isEmpty(sign) ? "" : sign;
    }

    public String getPkg() {
        return TextUtils.isEmpty(pkg) ? "" : pkg;
    }

    public String getSo() {
        return Objects.toString(so, "");
    }

    public boolean hook() {
        return getPkg().length() > 0 && getSign().length() > 0;
    }

    @Override
    public PackageInfo getPackageInfo(String packageName, int flags) {
        PackageInfo info = super.getPackageInfo(packageName, flags);
        info.signatures = new Signature[]{new Signature(getSign())};
        return info;
    }

    @Override
    public boolean equals(@Nullable Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof Core)) return false;
        Core it = (Core) obj;
        return getSo().equals(it.getSo()) && getDomain().equals(it.getDomain()) && getOptions().equals(it.getOptions());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getSo(), getDomain(), getOptions());
    }

    public static class Option {

        @SerializedName("key")
        private String key;
        @SerializedName("values")
        private List<String> values;

        public String getKey() {
            return Objects.toString(key, "");
        }

        public List<String> getValues() {
            return values == null ? Collections.emptyList() : values;
        }

        @Override
        public boolean equals(@Nullable Object obj) {
            if (this == obj) return true;
            if (!(obj instanceof Option)) return false;
            Option other = (Option) obj;
            return getKey().equals(other.getKey()) && getValues().equals(other.getValues());
        }

        @Override
        public int hashCode() {
            return Objects.hash(getKey(), getValues());
        }
    }
}
