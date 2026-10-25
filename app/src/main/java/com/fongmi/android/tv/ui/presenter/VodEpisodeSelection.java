package com.fongmi.android.tv.ui.presenter;
import com.fongmi.android.tv.bean.*;
import java.util.*;
/** Shared route/episode identity and order, independent of any adapter or screen lifecycle. */
public final class VodEpisodeSelection {
    private final List<Flag> flags = new ArrayList<>();
    public void detail(Vod detail) {
        flags.clear();
        for (Flag flag : detail.getVodFlags()) if (flag != null && !flag.getEpisodes().isEmpty()) flags.add(flag);
    }
    public void clear() { flags.clear(); }
    public List<Flag> flags() { return Collections.unmodifiableList(flags); }
    public int flagIndex() {
        for (int i = 0; i < flags.size(); i++) if (flags.get(i).isActivated()) return i;
        return 0;
    }
    public Flag flag() { return flags.isEmpty() ? null : flags.get(flagIndex()); }
    public int episodeIndex() {
        Flag flag = flag(); if (flag == null) return 0;
        for (int i = 0; i < flag.getEpisodes().size(); i++) if (flag.getEpisodes().get(i).isActivated()) return i;
        return 0;
    }
    public boolean selectFlag(Flag requested, boolean fallback) {
        if (requested == null || flags.isEmpty()) return false;
        int index = flags.indexOf(requested);
        if (index < 0 && !fallback) return false;
        Flag selected = flags.get(Math.max(0, index));
        for (Flag flag : flags) flag.setActivated(selected);
        return true;
    }
    public boolean selectEpisode(Episode episode) {
        Flag selected = flag();
        if (selected == null || episode == null || !selected.getEpisodes().contains(episode)) return false;
        for (Flag flag : flags) flag.toggle(flag == selected, episode);
        return true;
    }
    public Episode chooseEpisode(Flag selected, String name, int fallback) {
        int index = flags.indexOf(selected);
        if (index < 0) return null;
        Flag flag = flags.get(index);
        if (flag.getEpisodes().isEmpty()) return null;
        Episode found = flag.find(name, name != null && !name.isEmpty());
        if (found != null) return found;
        return flag.getEpisodes().get(fallback >= 0 && fallback < flag.getEpisodes().size() ? fallback : 0);
    }
    public void reverseEpisodes() { for (Flag flag : flags) Collections.reverse(flag.getEpisodes()); }

}
