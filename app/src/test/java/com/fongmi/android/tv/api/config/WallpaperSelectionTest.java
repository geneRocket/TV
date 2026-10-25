package com.fongmi.android.tv.api.config;

import org.junit.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.Assert.*;

public class WallpaperSelectionTest {
    @Test public void pendingUserChoiceWinsOverEarlierVodInheritance() {
        WallpaperSelection selection = new WallpaperSelection();
        selection.choose("user-wall", false, choice -> {});
        assertFalse(selection.needSync("old-vod-wall", true, "new-vod-wall"));
        assertFalse(selection.inherit("old-vod-wall", true, "new-vod-wall", choice -> fail()));
    }

    @Test public void oldCompletionCannotClearTheNewSelectionIntent() {
        WallpaperSelection selection = new WallpaperSelection();
        List<WallpaperSelection.Choice> scheduled = new ArrayList<>();
        selection.choose("first",false,scheduled::add); selection.choose("second",false,scheduled::add);
        selection.finish(scheduled.get(0));
        assertFalse(selection.needSync("old",true,"different"));
        assertTrue(selection.needSync("old",false,"second"));
        selection.finish(scheduled.get(1));
        assertTrue(selection.needSync("old",true,"different"));
    }

    @Test public void failureRestoresTheActiveSelectionPolicy() {
        WallpaperSelection selection = new WallpaperSelection();
        selection.choose("failed",false,choice -> selection.finish(choice));
        assertTrue(selection.needSync("old-vod",true,"new-vod"));
        assertFalse(selection.needSync("old-user",false,"new-vod"));
    }

    @Test public void inheritedChoicesContinueFollowingVodAndExplicitLaterChoicesWin() {
        WallpaperSelection selection = new WallpaperSelection();
        assertTrue(selection.inherit("old",true,"vod-a",choice -> {}));
        assertTrue(selection.inherit("old",false,"vod-b",choice -> {}));
        selection.choose("user",false,choice -> {});
        assertFalse(selection.inherit("old",true,"vod-c",choice -> fail()));
    }

    @Test public void queueRejectionDoesNotLeaveAPhantomPendingChoice() {
        WallpaperSelection selection = new WallpaperSelection();
        try {selection.choose("failed",false,choice -> {throw new IllegalStateException();});fail();}
        catch (IllegalStateException expected) { }
        assertTrue(selection.needSync("old",true,"new"));
    }
}
