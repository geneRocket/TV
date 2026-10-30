package com.fongmi.android.tv.test;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;

import androidx.leanback.widget.ArrayObjectAdapter;
import androidx.leanback.widget.ItemBridgeAdapter;
import androidx.leanback.widget.ListRow;
import androidx.leanback.widget.ListRowPresenter;
import androidx.leanback.widget.Presenter;
import androidx.leanback.widget.RowPresenter;
import androidx.leanback.widget.VerticalGridView;

import com.fongmi.android.tv.bean.Vod;
import com.fongmi.android.tv.ui.custom.CustomRowPresenter;
import com.fongmi.android.tv.ui.presenter.VodPresenter;

/** Exercises the real row and card presenters with deterministic, offline data. */
public class RowLayoutActivity extends Activity {
    private int created;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        boolean baseline = getIntent().getBooleanExtra("baseline", false);
        VerticalGridView grid = new VerticalGridView(this);
        grid.setHasFixedSize(true);
        grid.setItemAnimator(null);
        grid.setItemViewCacheSize(20);
        setContentView(grid, new ViewGroup.LayoutParams(-1, -1));
        CustomRowPresenter rowPresenter = new CustomRowPresenter(16) {
            @Override protected void initializeRowViewHolder(RowPresenter.ViewHolder holder) {
                super.initializeRowViewHolder(holder);
                if (baseline) ((ListRowPresenter.ViewHolder) holder).getGridView().setHasFixedSize(true);
            }
        };
        ArrayObjectAdapter rows = new ArrayObjectAdapter(rowPresenter);
        VodPresenter cards = new VodPresenter(new VodPresenter.OnClickListener() {
            @Override public void onItemClick(Vod item) { }
            @Override public boolean onLongClick(Vod item) { return false; }
        }) {
            @Override public Presenter.ViewHolder onCreateViewHolder(ViewGroup parent) {
                created++;
                return super.onCreateViewHolder(parent);
            }
        };
        for (int row = 0; row < 100; row++) {
            ArrayObjectAdapter items = new ArrayObjectAdapter(cards);
            for (int column = 0; column < 5; column++) {
                Vod vod = new Vod();
                vod.setVodId(row + "-" + column);
                vod.setVodName("Card " + row + "-" + column);
                items.add(vod);
            }
            rows.add(new ListRow(items));
        }
        grid.postOnAnimation(() -> {
            long start = System.nanoTime();
            grid.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
                @Override public boolean onPreDraw() {
                    if (created == 0) return true;
                    grid.getViewTreeObserver().removeOnPreDrawListener(this);
                    Log.i("RowLayoutBench", "baseline=" + baseline + " rows=100 cards=500 created="
                            + created + " attachedRows=" + grid.getChildCount()
                            + " firstLayoutMs=" + (System.nanoTime() - start) / 1e6);
                    return true;
                }
            });
            grid.setAdapter(new ItemBridgeAdapter(rows));
        });
    }
}
