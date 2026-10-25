package com.fongmi.android.tv.api.config;

import android.graphics.Bitmap;
import com.bumptech.glide.Glide;
import com.bumptech.glide.RequestManager;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.request.FutureTarget;
import com.fongmi.android.tv.App;
import com.fongmi.android.tv.utils.Download;
import com.fongmi.android.tv.utils.ResUtil;
import com.github.catvod.utils.Asset;
import com.github.catvod.utils.Path;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

/** Platform image I/O and Glide processing, with no configuration selection or publication. */
final class WallpaperImages {
    private WallpaperImages() {}
    static WallpaperFileStore store() { return new WallpaperFileStore(WallpaperImages::read, WallpaperImages::resize); }
    private static void read(String url, File file) throws Exception {
        if (url.startsWith("file")) {
            if (!Path.tryCopy(Path.local(url), file)) throw new IOException("Cannot read wallpaper file");
        } else if (url.startsWith("assets")) {
            if (!Path.tryCopy(Asset.open(url), file)) throw new IOException("Cannot read wallpaper asset");
        } else if (url.startsWith("http")) Download.create(url, file).sync();
        else throw new IOException("Unsupported wallpaper source");
    }
    private static void resize(File file) throws Exception {
        RequestManager manager = Glide.with(App.get());
        FutureTarget<Bitmap> target = manager.asBitmap().load(file).centerCrop()
                .override(ResUtil.getScreenWidth(), ResUtil.getScreenHeight()).skipMemoryCache(true)
                .diskCacheStrategy(DiskCacheStrategy.NONE).submit();
        try {
            Bitmap bitmap = target.get();
            try (FileOutputStream output = new FileOutputStream(file)) {
                if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 100, output)) throw new IOException("Cannot encode wallpaper");
            }
        } finally { manager.clear(target); }
    }
}
