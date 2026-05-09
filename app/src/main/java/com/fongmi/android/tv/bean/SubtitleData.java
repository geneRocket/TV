package com.fongmi.android.tv.bean;

import java.util.List;
import java.util.Collections;

public class SubtitleData {

    private List<Subtitle> subtitleList;
    private Boolean isNew;
    private Boolean isZip;

    public SubtitleData() {
    }

    public SubtitleData(List<Subtitle> subtitleList, Boolean isNew, Boolean isZip) {
        this.subtitleList = subtitleList;
        this.isNew = isNew;
        this.isZip = isZip;
    }

    public List<Subtitle> getSubtitleList() {
        return subtitleList == null ? Collections.emptyList() : subtitleList;
    }

    public void setSubtitleList(List<Subtitle> subtitleList) {
        this.subtitleList = subtitleList;
    }

    public Boolean getIsNew() {
        return isNew;
    }

    public void setIsNew(Boolean isNew) {
        this.isNew = isNew;
    }

    public Boolean getIsZip() {
        return isZip;
    }

    public void setIsZip(Boolean isZip) {
        this.isZip = isZip;
    }
}
