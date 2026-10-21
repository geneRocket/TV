package com.fongmi.android.tv.ui.custom;

import android.graphics.drawable.Drawable;
import com.bumptech.glide.request.target.CustomTarget;
import org.junit.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.Assert.*;

public class PlaybackArtworkTest {
    @Test public void oldCoverSuccessAndFailureCannotChangeNewFilmOrDestroyedPage() {
        List<CustomTarget<Drawable>> requests = new ArrayList<>();
        int[] writes = {0}, clears = {0};
        PlaybackArtwork artwork = new PlaybackArtwork(new PlaybackArtwork.Listener() {
            @Override public void loaded(Drawable drawable) { writes[0]++; }
            @Override public void failed(Drawable drawable) { writes[0]++; }
        }, new PlaybackArtwork.Loader() {
            @Override public void load(String url, CustomTarget<Drawable> target) { requests.add(target); }
            @Override public void clear(CustomTarget<Drawable> target) { clears[0]++; }
        });
        artwork.load("first");
        artwork.load("second");
        requests.get(0).onResourceReady(null, null);
        requests.get(0).onLoadFailed(null);
        assertEquals(0, writes[0]);
        assertEquals(1, clears[0]);
        requests.get(1).onResourceReady(null, null);
        assertEquals(1, writes[0]);
        artwork.close();
        requests.get(1).onLoadFailed(null);
        artwork.load("third");
        assertEquals(1, writes[0]);
        assertEquals(2, requests.size());
    }
}
