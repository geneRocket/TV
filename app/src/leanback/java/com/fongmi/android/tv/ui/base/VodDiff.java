package com.fongmi.android.tv.ui.base;

import androidx.annotation.NonNull;
import androidx.leanback.widget.DiffCallback;

import com.fongmi.android.tv.bean.Vod;

/** Stable item comparison shared by TV VOD rows. */
public final class VodDiff {

    public static final DiffCallback<Vod> ITEM = new DiffCallback<Vod>() {
        @Override
        public boolean areItemsTheSame(@NonNull Vod oldItem, @NonNull Vod newItem) {
            String oldId = oldItem.getVodId();
            String newId = newItem.getVodId();
            if (!oldId.isEmpty() || !newId.isEmpty()) return oldItem.getSiteKey().equals(newItem.getSiteKey()) && oldId.equals(newId);
            return oldItem.getVodName().equals(newItem.getVodName()) && oldItem.getVodPic().equals(newItem.getVodPic());
        }

        @Override
        public boolean areContentsTheSame(@NonNull Vod oldItem, @NonNull Vod newItem) {
            return oldItem.equals(newItem)
                    && oldItem.getVodRemarks().equals(newItem.getVodRemarks())
                    && oldItem.getVodYear().equals(newItem.getVodYear())
                    && oldItem.getSiteName().equals(newItem.getSiteName());
        }
    };

    private VodDiff() {
    }
}
