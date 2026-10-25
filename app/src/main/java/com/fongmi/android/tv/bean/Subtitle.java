package com.fongmi.android.tv.bean;

import java.io.Serializable;

public class Subtitle implements Serializable {

    private String name;
    private String url;
    private Boolean isZip;
    private String source;
    private String language;
    private boolean external;
    private String movieTitle;

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

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getLanguage() { return language == null ? "" : language; }
    public void setLanguage(String language) { this.language = language; }
    public boolean isExternal() { return external; }
    public void setExternal(boolean external) { this.external = external; }
    public String getMovieTitle() { return movieTitle == null ? "" : movieTitle; }
    public void setMovieTitle(String movieTitle) { this.movieTitle = movieTitle; }
}
