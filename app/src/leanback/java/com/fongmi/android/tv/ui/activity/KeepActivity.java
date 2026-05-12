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
import com.fongmi.android.tv.bean.Keep;
import com.fongmi.android.tv.databinding.ActivityKeepBinding;
import com.fongmi.android.tv.event.RefreshEvent;
import com.fongmi.android.tv.ui.adapter.KeepAdapter;
import com.fongmi.android.tv.ui.base.BaseActivity;
import com.fongmi.android.tv.ui.custom.SpaceItemDecoration;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.List;

public class KeepActivity extends BaseActivity implements KeepAdapter.OnClickListener {

    private ActivityKeepBinding mBinding;
    private KeepAdapter mAdapter;
    private int mKeepRequestId;
    private int mOpenRequestId;

    public static void start(Activity activity) {
        activity.startActivity(new Intent(activity, KeepActivity.class));
    }

    @Override
    protected ViewBinding getBinding() {
        return mBinding = ActivityKeepBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void initView() {
        mBinding.empty.text.setText(R.string.empty_keep);
        setRecyclerView();
        getKeep();
    }

    @Override
    protected void initEvent() {
        mBinding.delete.setOnClickListener(this::onDelete);
    }

    private void setRecyclerView() {
        mBinding.recycler.setHasFixedSize(true);
        mBinding.recycler.setItemAnimator(null);
        mBinding.recycler.setAdapter(mAdapter = new KeepAdapter(this));
        mBinding.recycler.setLayoutManager(new GridLayoutManager(this, Product.getColumn()));
        mBinding.recycler.addItemDecoration(new SpaceItemDecoration(Product.getColumn(), 16));
    }

    private void getKeep() {
        final int requestId = ++mKeepRequestId;
        mOpenRequestId++;
        App.execute(() -> {
            List<Keep> items = Keep.getVod();
            App.post(() -> {
                if (isFinishing() || isDestroyed() || requestId != mKeepRequestId) return;
                mAdapter.addAll(items);
                updateEmptyView();
                if (mAdapter.getItemCount() > 0) mBinding.recycler.post(() -> {
                    if (!isFinishing() && !isDestroyed()) mBinding.recycler.requestFocus();
                });
            });
        });
    }

    private void updateEmptyView() {
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
            new MaterialAlertDialogBuilder(this).setTitle(R.string.dialog_delete_record).setMessage(R.string.dialog_delete_keep).setNegativeButton(R.string.dialog_negative, null).setPositiveButton(R.string.dialog_positive, (dialog, which) -> {
                mKeepRequestId++;
                mOpenRequestId++;
                Keep.deleteAll();
                mAdapter.clear();
                updateEmptyView();
                RefreshEvent.keep();
            }).show();
        } else if (mAdapter.getItemCount() > 0) {
            mAdapter.setDelete(true);
        } else {
            mBinding.delete.setVisibility(View.GONE);
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onRefreshEvent(RefreshEvent event) {
        if (event.getType() == RefreshEvent.Type.KEEP) getKeep();
    }

    @Override
    public void onItemClick(Keep item) {
        mOpenRequestId++;
        if (VodConfig.get().hasSite(item.getSiteKey())) {
            VideoActivity.start(this, item.getSiteKey(), item.getVodId(), item.getVodName(), item.getVodPic());
            return;
        }
        CollectActivity.start(this, item.getVodName());
    }

    @Override
    public void onItemDelete(Keep item) {
        mKeepRequestId++;
        mOpenRequestId++;
        int index = mAdapter.delete(item.delete());
        if (mAdapter.getItemCount() == 0) mAdapter.setDelete(false);
        updateEmptyView();
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

    @Override
    public void onBackPressed() {
        if (mAdapter.isDelete()) mAdapter.setDelete(false);
        else super.onBackPressed();
    }
}
