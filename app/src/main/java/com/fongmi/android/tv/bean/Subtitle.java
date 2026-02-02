package com.fongmi.android.tv.bean;

import java.io.Serializable;

public class Subtitle implements Serializable {

    private String name;
    private String url;
    private Boolean isZip;

    public Subtitle() {
    }

    public Subtitle(String name, String url, Boolean isZip) {
        this.name = name;
        this.url = url;
        this.isZip = isZip;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public Boolean getIsZip() {
        return isZip;
    }

    public void setIsZip(Boolean isZip) {
        this.isZip = isZip;
    }
}
