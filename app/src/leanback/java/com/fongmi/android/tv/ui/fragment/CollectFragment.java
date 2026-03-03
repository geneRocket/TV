package com.fongmi.android.tv.ui.fragment;

import android.app.Activity;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.leanback.widget.ArrayObjectAdapter;
import androidx.leanback.widget.ItemBridgeAdapter;
import androidx.leanback.widget.ListRow;
import androidx.lifecycle.ViewModelProvider;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.Product;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.bean.Collect;
import com.fongmi.android.tv.bean.Result;
import com.fongmi.android.tv.bean.Vod;
import com.fongmi.android.tv.databinding.FragmentVodBinding;
import com.fongmi.android.tv.model.SiteViewModel;
import com.fongmi.android.tv.ui.activity.CollectActivity;
import com.fongmi.android.tv.ui.activity.VideoActivity;
import com.fongmi.android.tv.ui.activity.VodActivity;
import com.fongmi.android.tv.ui.base.BaseFragment;
import com.fongmi.android.tv.ui.custom.CustomRowPresenter;
import com.fongmi.android.tv.ui.custom.CustomScroller;
import com.fongmi.android.tv.ui.custom.CustomSelector;
import com.fongmi.android.tv.ui.presenter.VodPresenter;
import com.fongmi.android.tv.utils.ResUtil;
import com.google.common.collect.Lists;

import java.util.ArrayList;
import java.util.List;

public class CollectFragment extends BaseFragment implements CustomScroller.Callback, VodPresenter.OnClickListener {

    private FragmentVodBinding mBinding;
    private ArrayObjectAdapter mAdapter;
    private ArrayObjectAdapter mLast;
    private CustomScroller mScroller;
    private SiteViewModel mViewModel;
    private Collect mCollect;
    private String mKeyword;
    private String mSiteKey;

    public static CollectFragment newInstance(String keyword, String siteKey) {
        Bundle args = new Bundle();
        args.putString("keyword", keyword);
        args.putString("siteKey", siteKey);
        CollectFragment fragment = new CollectFragment();
        fragment.setArguments(args);
        return fragment;
    }

    private String getKeyword() {
        return mKeyword = mKeyword == null ? getArguments().getString("keyword") : mKeyword;
    }

    public String getSiteKey() {
        return mSiteKey = mSiteKey == null ? getArguments().getString("siteKey") : mSiteKey;
    }

    private Collect getCollect() {
        if (mCollect != null) return mCollect;
        if (getActivity() instanceof CollectActivity) mCollect = ((CollectActivity) getActivity()).getCollect(getSiteKey());
        return mCollect;
    }

    @Override
    protected ViewBinding getBinding(@NonNull LayoutInflater inflater, @Nullable ViewGroup container) {
        return mBinding = FragmentVodBinding.inflate(inflater, container, false);
    }

    @Override
    protected void initView() {
        setRecyclerView();
        setViewModel();
    }

    private void setRecyclerView() {
        CustomSelector selector = new CustomSelector();
        selector.addPresenter(ListRow.class, new CustomRowPresenter(16), VodPresenter.class);
        mBinding.recycler.setAdapter(new ItemBridgeAdapter(mAdapter = new ArrayObjectAdapter(selector)));
        mBinding.recycler.setSaveEnabled(false);
        mBinding.recycler.setHeader(getActivity().findViewById(R.id.result), getActivity().findViewById(R.id.recycler));
        mBinding.recycler.addOnScrollListener(mScroller = new CustomScroller(this));
        mBinding.recycler.setVerticalSpacing(ResUtil.dp2px(16));
    }

    private void setViewModel() {
        mViewModel = new ViewModelProvider(this).get(SiteViewModel.class);
        mViewModel.result.observe(getViewLifecycleOwner(), result -> {
            mScroller.endLoading(result);
            appendCollectItems(result.getList());
            addVideo(result.getList());
            syncAllCollect(result.getList());
        });
    }

    @Override
    protected void initData() {
        syncRows();
    }

    private boolean checkLastSize(List<Vod> items) {
        if (mLast == null || items.size() == 0) return false;
        int size = Product.getColumn() - mLast.size();
        if (size == 0) return false;
        size = Math.min(size, items.size());
        mLast.addAll(mLast.size(), new ArrayList<>(items.subList(0, size)));
        addVideo(new ArrayList<>(items.subList(size, items.size())));
        return true;
    }

    public void addVideo(List<Vod> items) {
        addRows(items);
    }

    private void appendCollectItems(List<Vod> items) {
        if (items.isEmpty() || getCollect() == null) return;
        getCollect().getList().addAll(items);
    }

    public void appendRows(List<Vod> items) {
        if (mAdapter == null || items.isEmpty()) return;
        addRows(items);
    }

    private void addRows(List<Vod> items) {
        if (checkLastSize(items) || getActivity() == null || getActivity().isFinishing()) return;
        List<ListRow> rows = new ArrayList<>();
        for (List<Vod> part : Lists.partition(items, Product.getColumn())) {
            mLast = new ArrayObjectAdapter(new VodPresenter(this));
            mLast.setItems(part, null);
            rows.add(new ListRow(mLast));
        }
        mAdapter.addAll(mAdapter.size(), rows);
    }

    private void syncRows() {
        if (mAdapter == null || mAdapter.size() > 0 || getCollect() == null) return;
        addRows(new ArrayList<>(getCollect().getList()));
    }

    private void syncAllCollect(List<Vod> items) {
        if (items.isEmpty()) return;
        if (!(getActivity() instanceof CollectActivity)) return;
        if ("all".equals(getSiteKey())) return;
        ((CollectActivity) getActivity()).appendAllCollect(items);
    }

    @Override
    public void onItemClick(Vod item) {
        getActivity().setResult(Activity.RESULT_OK);
        if (item.isFolder()) VodActivity.start(getActivity(), item.getSiteKey(), Result.folder(item));
        else VideoActivity.collect(getActivity(), item.getSiteKey(), item.getVodId(), item.getVodName(), item.getVodPic());
    }

    @Override
    public boolean onLongClick(Vod item) {
        return false;
    }

    @Override
    public void onLoadMore(String page) {
        if (getCollect() == null || "all".equals(getCollect().getSite().getKey())) return;
        mViewModel.searchContent(getCollect().getSite(), getKeyword(), page);
        mScroller.setLoading(true);
    }

    @Override
    public void onResume() {
        super.onResume();
        syncRows();
    }
}
