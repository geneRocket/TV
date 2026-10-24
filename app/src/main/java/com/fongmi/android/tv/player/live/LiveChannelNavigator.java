package com.fongmi.android.tv.player.live;

import com.fongmi.android.tv.bean.Channel;
import com.fongmi.android.tv.bean.Group;

/** Channel/group navigation rules shared by TV keys, mobile controls and media sessions. */
public final class LiveChannelNavigator {
    public interface Port {
        Group currentGroup();
        int groupCount();
        int selectedGroup();
        Group groupAt(int index);
        void selectGroup(int index, Group group);
        void play(Channel channel);
    }

    private final Port port;
    public LiveChannelNavigator(Port port) { this.port = port; }

    public boolean group(boolean next, boolean skipFavorites) {
        int size = port.groupCount();
        Group current = port.currentGroup();
        if (current == null || size == 0) return false;
        int selected = Math.max(0, Math.min(port.selectedGroup(), size - 1));
        for (int offset = 1; offset <= size; offset++) {
            int index = Math.floorMod(selected + (next ? offset : -offset), size);
            Group group = port.groupAt(index);
            if (current.equals(group)) return false;
            if (skipFavorites && (group.skip() || group.isEmpty())) continue;
            port.selectGroup(index, group);
            group.setPosition(next ? 0 : group.getChannel().size() - 1);
            return true;
        }
        return false;
    }

    public void channel(boolean next, boolean acrossGroups) {
        Group group = port.currentGroup();
        if (group == null) return;
        int size = group.getChannel().size();
        int position = group.getPosition() + (next ? 1 : -1);
        boolean outside = position < 0 || position >= size;
        if (!(outside && acrossGroups && group(next, true))) {
            group.setPosition(outside ? (next ? 0 : size - 1) : position);
        }
        group = port.currentGroup();
        if (group != null && !group.isEmpty()) port.play(group.current());
    }
}
