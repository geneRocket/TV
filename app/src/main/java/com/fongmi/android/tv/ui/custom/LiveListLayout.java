package com.fongmi.android.tv.ui.custom;

import android.view.View;

import com.fongmi.android.tv.bean.Channel;
import com.fongmi.android.tv.bean.Epg;
import com.fongmi.android.tv.bean.EpgData;
import com.fongmi.android.tv.bean.Group;
import com.fongmi.android.tv.bean.Live;
import com.fongmi.android.tv.utils.ResUtil;

/** Measures and applies the live group/channel/EPG columns while preserving each UI's typography. */
public final class LiveListLayout {
    private final View groups, channels, programs, divider;
    private final int font, logo, programPadding;

    public LiveListLayout(View groups, View channels, View programs, View divider, boolean tv) {
        this.groups = groups;
        this.channels = channels;
        this.programs = programs;
        this.divider = divider;
        font = tv ? 16 : 14;
        logo = ResUtil.dp2px(tv ? 60 : 56);
        programPadding = ResUtil.dp2px(tv ? 48 : 40);
    }

    public void render(Live live) {
        if (live.getWidth() == 0) for (Group group : live.getGroups())
            live.setWidth(Math.max(live.getWidth(), ResUtil.getTextWidth(group.getName(), font)));
        width(groups, live.getWidth() == 0 ? 0 : Math.min(live.getWidth() + ResUtil.dp2px(48), ResUtil.getScreenWidth() / 4));
        int visibility = live.getWidth() == 0 ? View.GONE : View.VISIBLE;
        if (divider.getVisibility() != visibility) divider.setVisibility(visibility);
    }

    public Group render(Group group) {
        if (group.isKeep()) group.setWidth(0);
        if (group.getWidth() == 0) for (Channel channel : group.getChannel())
            group.setWidth(Math.max(group.getWidth(), (channel.getLogo().isEmpty() ? 0 : logo)
                    + ResUtil.getTextWidth(channel.getNumber() + channel.getName(), font)));
        width(channels, group.getWidth() == 0 ? 0 : Math.min(group.getWidth() + ResUtil.dp2px(60), ResUtil.getScreenWidth() / 2));
        return group;
    }

    public void render(Epg epg) {
        if (epg.getList().isEmpty()) return;
        int min = ResUtil.getTextWidth(epg.getList().get(0).getTime(), font);
        if (epg.getWidth() == 0) for (EpgData program : epg.getList())
            epg.setWidth(Math.max(epg.getWidth(), ResUtil.getTextWidth(program.getTitle(), font)));
        width(programs, epg.getWidth() == 0 ? 0 : Math.min(Math.max(epg.getWidth(), min) + programPadding, ResUtil.getScreenWidth() / 2));
    }

    public void reset() {
        width(groups, 0);
        width(channels, 0);
        width(programs, 0);
        divider.setVisibility(View.GONE);
    }

    private void width(View view, int value) {
        if (view.getLayoutParams().width == value) return;
        view.getLayoutParams().width = value;
        view.requestLayout();
    }
}
