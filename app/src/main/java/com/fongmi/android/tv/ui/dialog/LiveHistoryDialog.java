package com.fongmi.android.tv.ui.dialog;

import android.widget.ArrayAdapter;

import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.bean.Channel;
import com.fongmi.android.tv.bean.EpgData;
import com.fongmi.android.tv.bean.Live;
import com.fongmi.android.tv.model.LiveViewModel;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public final class LiveHistoryDialog {

    private LiveHistoryDialog() {}

    public static void show(FragmentActivity activity, LiveViewModel model, Live source, Channel channel, Consumer<EpgData> play) {
        List<String> dates = model.historyDates(channel);
        if (dates.isEmpty()) return;
        AlertDialog dialog = new MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.live_history_date)
                .setItems(dates.toArray(new String[0]), (window, index) ->
                        programmes(activity, model, source, channel, dates.get(index), play))
                .setNegativeButton(android.R.string.cancel, null)
                .create();
        show(activity, dialog, () -> {});
    }

    private static void programmes(FragmentActivity activity, LiveViewModel model, Live source, Channel channel,
                                   String date, Consumer<EpgData> play) {
        List<EpgData> items = new ArrayList<>();
        ArrayAdapter<String> rows = new ArrayAdapter<>(activity, android.R.layout.simple_list_item_1);
        AlertDialog dialog = new MaterialAlertDialogBuilder(activity)
                .setTitle(activity.getString(R.string.live_history_loading, date))
                .setAdapter(rows, (window, index) -> play.accept(items.get(index)))
                .setNegativeButton(android.R.string.cancel, null)
                .create();
        show(activity, dialog, model::cancelHistoryEpg);
        model.getHistoryEpg(source, channel, date, epg -> {
            if (!dialog.isShowing()) return;
            items.addAll(epg.getList());
            dialog.setTitle(items.isEmpty() ? activity.getString(R.string.live_history_empty, date) : date);
            for (EpgData item : items) rows.add(item.getTime() + "  " + item.getTitle());
        });
    }

    private static void show(FragmentActivity activity, AlertDialog dialog, Runnable onDismiss) {
        DefaultLifecycleObserver lifecycle = new DefaultLifecycleObserver() {
            @Override public void onDestroy(LifecycleOwner owner) { dialog.dismiss(); }
        };
        dialog.setOnDismissListener(window -> {
            onDismiss.run();
            activity.getLifecycle().removeObserver(lifecycle);
        });
        dialog.show();
        activity.getLifecycle().addObserver(lifecycle);
    }
}
