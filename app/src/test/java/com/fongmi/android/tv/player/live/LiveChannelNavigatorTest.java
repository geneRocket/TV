package com.fongmi.android.tv.player.live;

import com.fongmi.android.tv.bean.Channel;
import com.fongmi.android.tv.bean.Group;
import org.junit.Test;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import static org.junit.Assert.*;

public class LiveChannelNavigatorTest {
    @Test public void channelWrapStaysInsideGroupWhenAcrossDisabled() {
        Fixture f = new Fixture();
        f.current.setPosition(1); f.nav.channel(true, false);
        assertEquals(0, f.current.getPosition()); assertSame(f.current.current(), f.played);
        f.nav.channel(false, false); assertEquals(1, f.current.getPosition());
    }
    @Test public void acrossGroupNavigationSkipsFavoritesAndWrapsBothWays() {
        Fixture f = new Fixture();
        f.current.setPosition(1); f.nav.channel(true, true);
        assertEquals(2, f.selected); assertEquals(0, f.current.getPosition());
        f.nav.channel(false, true);
        assertEquals(0, f.selected); assertEquals(1, f.current.getPosition());
    }
    @Test public void explicitGroupBrowsingMayIncludeFavorites() {
        Fixture f = new Fixture();
        assertTrue(f.nav.group(true, false)); assertEquals(1, f.selected);
    }
    @Test public void onlyPlayableGroupDoesNotLoopForeverOrPlayTwice() {
        Fixture f = new Fixture(); f.groups.remove(2);
        f.current.setPosition(1); f.nav.channel(true, true);
        assertEquals(0, f.selected); assertEquals(1, f.plays);
        assertEquals(0, f.current.getPosition());
    }
    @Test public void onlyPlayableGroupWrapsBackwardsToItsLastChannel() {
        Fixture f = new Fixture(); f.groups.remove(2);
        f.nav.channel(false, true);
        assertEquals(0, f.selected);
        assertEquals(1, f.current.getPosition());
        assertEquals(1, f.plays);
    }
    @Test public void automaticChannelChangeSkipsEmptyOrdinaryGroupsInBothDirections() {
        for (boolean next : new boolean[]{false, true}) {
            Fixture f = new Fixture();
            f.groups.add(1, new NamedGroup("empty", false, 0));
            f.current.setPosition(next ? 1 : 0);
            f.nav.channel(next, true);
            assertEquals(3, f.selected);
            assertEquals(next ? 0 : 2, f.current.getPosition());
            assertEquals(1, f.plays);
        }
    }
    @Test public void allEmptyGroupsCannotStartAnEmptyChannel() {
        Fixture f = new Fixture();
        f.groups.clear();
        f.groups.add(new NamedGroup("a", false, 0));
        f.groups.add(new NamedGroup("b", false, 0));
        f.current = f.groups.get(0);
        for (boolean next : new boolean[]{false, true}) f.nav.channel(next, true);
        assertEquals(0, f.plays);
        assertEquals(0, f.selected);
    }
    @Test public void channelNavigationExhaustsDirectionsAcrossGroupModesAndListSizes() {
        for (int size = 1; size <= 5; size++) {
            for (int position = 0; position < size; position++) {
                for (boolean next : new boolean[]{false, true}) {
                    for (boolean across : new boolean[]{false, true}) {
                        Fixture f = new Fixture();
                        f.groups.clear();
                        f.current = new NamedGroup("only", false, size);
                        f.groups.add(f.current);
                        f.current.setPosition(position);
                        f.nav.channel(next, across);
                        assertEquals(Math.floorMod(position + (next ? 1 : -1), size), f.current.getPosition());
                        assertEquals(1, f.plays);
                    }
                }
            }
        }
    }
    @Test public void emptyFavoritesCanAdvanceToRealChannels() {
        Fixture f = new Fixture(); f.current = f.groups.get(1); f.selected = 1;
        f.nav.channel(true, true);
        assertEquals(2, f.selected); assertNotNull(f.played);
    }
    @Test public void noGroupsOrNoCurrentGroupAreSafe() {
        Fixture f = new Fixture(); f.groups.clear();
        assertFalse(f.nav.group(false, true));
        f.current = null; f.nav.channel(true, true);
        assertEquals(0, f.plays);
    }
    static class NamedGroup extends Group {
        final String label; final boolean keep;
        NamedGroup(String label, boolean keep, int size) {
            super(label); this.label = label; this.keep = keep;
            for (int i = 0; i < size; i++) getChannel().add(new Channel());
            setPosition(0);
        }
        @Override public String getName() { return label; }
        @Override public boolean skip() { return keep; }
    }
    static class Fixture implements LiveChannelNavigator.Port {
        final List<Group> groups = new ArrayList<>(Arrays.asList(new NamedGroup("a", false, 2),
                new NamedGroup("favorites", true, 0), new NamedGroup("b", false, 3)));
        Group current = groups.get(0); int selected, plays; Channel played;
        final LiveChannelNavigator nav = new LiveChannelNavigator(this);
        @Override public Group currentGroup() { return current; }
        @Override public int groupCount() { return groups.size(); }
        @Override public int selectedGroup() { return selected; }
        @Override public Group groupAt(int index) { return groups.get(index); }
        @Override public void selectGroup(int index, Group group) { selected = index; current = group; }
        @Override public void play(Channel channel) { plays++; played = channel; }
    }
}
