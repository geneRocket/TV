package com.fongmi.android.tv.ui.activity;

import android.app.Activity;
import android.content.Intent;
import android.view.View;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.Product;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.bean.History;
import com.fongmi.android.tv.repository.HistoryRepository;
import com.fongmi.android.tv.databinding.ActivityHistoryBinding;
import com.fongmi.android.tv.event.RefreshEvent;
import com.fongmi.android.tv.ui.adapter.HistoryAdapter;
import com.fongmi.android.tv.ui.base.BaseActivity;
import com.fongmi.android.tv.ui.custom.SpaceItemDecoration;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.List;

public class HistoryActivity extends BaseActivity implements HistoryAdapter.OnClickListener {

    private ActivityHistoryBinding mBinding;

    private HistoryAdapter mAdapter;
    private int mHistoryRequestId;
    private int mOpenRequestId;

    public static void start(Activity activity) {
        activity.startActivity(new Intent(activity, HistoryActivity.class));
    }

    @Override
    protected ViewBinding getBinding() {
        return mBinding = ActivityHistoryBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void initView() {
        mBinding.empty.text.setText(R.string.empty_history);
        setRecyclerView();
        getHistory();
    }

    @Override
    protected void initEvent() {
        mBinding.delete.setOnClickListener(this::onDelete);
    }

    private void setRecyclerView() {
        mBinding.recycler.setHasFixedSize(true);
        mBinding.recycler.setItemAnimator(null);
        mBinding.recycler.setItemViewCacheSize(20);
        mBinding.recycler.setAdapter(mAdapter = new HistoryAdapter(this));
        mBinding.recycler.setLayoutManager(new GridLayoutManager(this, Product.getColumn()));
        mBinding.recycler.addItemDecoration(new SpaceItemDecoration(Product.getColumn(), 16));
    }

    private void getHistory() {
        final int requestId = ++mHistoryRequestId;
        App.execute(() -> {
            List<History> items = HistoryRepository.get().loaded();
            App.post(() -> {
                if (isFinishing() || isDestroyed() || requestId != mHistoryRequestId) return;
                mAdapter.addAll(items);
                updateViews();
                mBinding.recycler.post(() -> {
                    if (!isFinishing() && !isDestroyed() && mAdapter.getItemCount() > 0) mBinding.recycler.requestFocus();
                });
            });
        });
    }

    private void updateViews() {
        boolean visible = mAdapter.getItemCount() > 0;
        mBinding.delete.setVisibility(visible ? View.VISIBLE : View.GONE);
        mBinding.delete.setFocusable(visible);
        mBinding.empty.getRoot().setVisibility(visible ? View.GONE : View.VISIBLE);
        mBinding.empty.getRoot().setFocusable(!visible);
        mBinding.empty.getRoot().setFocusableInTouchMode(!visible);
        if (!visible) mBinding.empty.getRoot().post(() -> {
            if (!isFinishing() && !isDestroyed()) mBinding.empty.getRoot().requestFocus();
        });
    }

    private void onDelete(View view) {
        if (mAdapter.isDelete()) {
            new MaterialAlertDialogBuilder(this).setTitle(R.string.dialog_delete_record).setMessage(R.string.dialog_delete_history).setNegativeButton(R.string.dialog_negative, null).setPositiveButton(R.string.dialog_positive, (dialog, which) -> {
                mHistoryRequestId++;
                mAdapter.clear();
                updateViews();
                App.execute(() -> HistoryRepository.get().deleteLoaded());
            }).show();
        } else if (mAdapter.getItemCount() > 0) {
            mAdapter.setDelete(true);
        } else {
            mBinding.delete.setVisibility(View.GONE);
        }
    }

    @Override
    public void onItemClick(History item) {
        mOpenRequestId++;
        if (VodConfig.get().hasSite(item.getSiteKey())) {
            VideoActivity.start(this, item.getSiteKey(), item.getVodId(), item.getVodName(), item.getVodPic());
            return;
        }
        CollectActivity.start(this, item.getVodName());
    }

    @Override
    public void onItemDelete(History item) {
        mHistoryRequestId++;
        int index = mAdapter.delete(item);
        App.execute(() -> HistoryRepository.get().delete(item));
        if (mAdapter.getItemCount() == 0) mAdapter.setDelete(false);
        updateViews();
        if (index != -1 && mAdapter.getItemCount() > 0) {
            int targetIndex = index == mAdapter.getItemCount() ? index - 1 : index;
            mBinding.recycler.post(() -> {
                View view = mBinding.recycler.getLayoutManager() == null ? null : mBinding.recycler.getLayoutManager().findViewByPosition(targetIndex);
                if (view != null) view.requestFocus();
            });
        }
    }

    @Override
    public boolean onLongClick() {
        mAdapter.setDelete(true);
        return true;
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onRefreshEvent(RefreshEvent event) {
        super.onRefreshEvent(event);
        switch (event.getType()) {
            case HISTORY:
                getHistory();
                break;
            case SIZE:
                getHistory();
                break;
        }
    }

    @Override
    public void onBackPressed() {
        if (mAdapter.isDelete()) mAdapter.setDelete(false);
        else super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        RefreshEvent.history();
    }

}
