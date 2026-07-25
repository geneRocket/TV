package com.fongmi.android.tv.ui.activity;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.recyclerview.widget.GridLayoutManager;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.Product;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.bean.Config;
import com.fongmi.android.tv.repository.ConfigRepository;
import com.fongmi.android.tv.bean.History;
import com.fongmi.android.tv.repository.HistoryRepository;
import com.fongmi.android.tv.databinding.ActivityHistoryBinding;
import com.fongmi.android.tv.event.RefreshEvent;
import com.fongmi.android.tv.impl.Callback;
import com.fongmi.android.tv.ui.adapter.HistoryAdapter;
import com.fongmi.android.tv.ui.base.BaseActivity;
import com.fongmi.android.tv.ui.dialog.SyncDialog;
import com.fongmi.android.tv.utils.Notify;
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
    protected void initView(Bundle savedInstanceState) {
        setRecyclerView();
        getHistory();
    }

    @Override
    protected void initEvent() {
        mBinding.sync.setOnClickListener(this::onSync);
        mBinding.delete.setOnClickListener(this::onDelete);
    }

    private void setRecyclerView() {
        mBinding.recycler.setHasFixedSize(true);
        mBinding.recycler.getItemAnimator().setChangeDuration(0);
        mBinding.recycler.setLayoutManager(new GridLayoutManager(this, Product.getColumn(this)));
        mBinding.recycler.setAdapter(mAdapter = new HistoryAdapter(this));
        mAdapter.setSize(Product.getSpec(getActivity()));
    }

    private void getHistory() {
        int requestId = ++mHistoryRequestId;
        App.execute(() -> {
            List<History> items = HistoryRepository.get().loaded();
            App.post(() -> {
                if (isFinishing() || isDestroyed() || requestId != mHistoryRequestId) return;
                mAdapter.addAll(items);
                mBinding.delete.setVisibility(mAdapter.getItemCount() > 0 ? View.VISIBLE : View.GONE);
            });
        });
    }

    private void onSync(View view) {
        SyncDialog.create().history().show(this);
    }

    private void onDelete(View view) {
        if (mAdapter.isDelete()) {
            new MaterialAlertDialogBuilder(this).setTitle(R.string.dialog_delete_record).setMessage(R.string.dialog_delete_history).setNegativeButton(R.string.dialog_negative, null).setPositiveButton(R.string.dialog_positive, (dialog, which) -> {
                mHistoryRequestId++;
                mAdapter.clear();
                App.execute(() -> HistoryRepository.get().deleteLoaded());
            }).show();
        } else if (mAdapter.getItemCount() > 0) {
            mAdapter.setDelete(true);
        } else {
            mBinding.delete.setVisibility(View.GONE);
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onRefreshEvent(RefreshEvent event) {
        if (event.getType().equals(RefreshEvent.Type.HISTORY)) getHistory();
    }

    @Override
    public void onItemClick(History item) {
        mOpenRequestId++;
        if (VodConfig.get().hasSite(item.getSiteKey())) {
            VideoActivity.start(this, item.getSiteKey(), item.getVodId(), item.getVodName(), item.getVodPic());
            return;
        }
        final int requestId = mOpenRequestId;
        App.execute(() -> {
            Config config = ConfigRepository.get().find(item.getCid());
            App.post(() -> {
                if (isFinishing() || isDestroyed() || requestId != mOpenRequestId) return;
                if (config == null) {
                    CollectActivity.start(this, item.getVodName());
                    return;
                }
                VodConfig.load(config, new Callback() {
                    @Override
                    public void success() {
                        if (isFinishing() || isDestroyed() || requestId != mOpenRequestId) return;
                        VideoActivity.start(HistoryActivity.this, item.getSiteKey(), item.getVodId(), item.getVodName(), item.getVodPic());
                        RefreshEvent.history();
                        RefreshEvent.config();
                        RefreshEvent.video();
                    }

                    @Override
                    public void error(String msg) {
                        if (isFinishing() || isDestroyed() || requestId != mOpenRequestId) return;
                        Notify.show(msg);
                    }
                });
            });
        });
    }

    @Override
    public void onItemDelete(History item) {
        mAdapter.remove(item);
        App.execute(() -> HistoryRepository.get().delete(item));
        if (mAdapter.getItemCount() > 0) return;
        mBinding.delete.setVisibility(View.GONE);
        mAdapter.setDelete(false);
    }

    @Override
    public boolean onLongClick() {
        mAdapter.setDelete(!mAdapter.isDelete());
        return true;
    }

    @Override
    public void onBackPressed() {
        if (mAdapter.isDelete()) mAdapter.setDelete(false);
        else super.onBackPressed();
    }
}
