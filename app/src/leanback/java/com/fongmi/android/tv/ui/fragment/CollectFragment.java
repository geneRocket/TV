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
import com.fongmi.android.tv.utils.Util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public class CollectFragment extends BaseFragment implements CustomScroller.Callback, VodPresenter.OnClickListener {

    private FragmentVodBinding mBinding;
    private ArrayObjectAdapter mAdapter;
    private ArrayObjectAdapter mLast;
    private CustomScroller mScroller;
    private SiteViewModel mViewModel;
    private String mKeyword;
    private String mSiteKey;
    private final Set<String> mVodKeys = new HashSet<>();

    public static CollectFragment newInstance(String keyword, String siteKey) {
        Bundle args = new Bundle();
        args.putString("keyword", keyword);
        args.putString("siteKey", siteKey);
        CollectFragment fragment = new CollectFragment();
        fragment.setArguments(args);
        return fragment;
    }

    private String getKeyword() {
        return mKeyword = mKeyword == null ? Objects.toString(getArguments() == null ? null : getArguments().getString("keyword"), "") : mKeyword;
    }

    public String getSiteKey() {
        return mSiteKey = mSiteKey == null ? Objects.toString(getArguments() == null ? null : getArguments().getString("siteKey"), "") : mSiteKey;
    }

    private Collect getCollect() {
        if (!(getActivity() instanceof CollectActivity)) return null;
        return ((CollectActivity) getActivity()).getCollect(getSiteKey());
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
        mBinding.recycler.setHasFixedSize(true);
        mBinding.recycler.setItemAnimator(null);
        mBinding.recycler.setHeader(getActivity().findViewById(R.id.result), getActivity().findViewById(R.id.recycler));
        mBinding.recycler.addOnScrollListener(mScroller = new CustomScroller(this));
        mBinding.recycler.setVerticalSpacing(ResUtil.dp2px(16));
    }

    private void setViewModel() {
        mViewModel = new ViewModelProvider(this).get(SiteViewModel.class);
        mViewModel.result.observe(getViewLifecycleOwner(), result -> {
            if (!isCurrentResult(result)) return;
            mScroller.endLoading(result);
            List<Vod> items = appendCollectItems(result.getList());
            addVideo(items);
            syncAllCollect(items);
        });
    }

    private boolean isCurrentResult(Result result) {
        return result != null
                && getKeyword().trim().equals(result.getKeyword())
                && getCollect() != null
                && getCollect().getSite().getKey().equals(result.getKey());
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

    private List<Vod> appendCollectItems(List<Vod> items) {
        List<Vod> added = filterNewItems(items);
        if (added.isEmpty() || getCollect() == null) return added;
        getCollect().getList().addAll(added);
        return added;
    }

    public void appendRows(List<Vod> items) {
        if (!isViewReady() || mAdapter == null || items.isEmpty() || getCollect() == null) return;
        if ("all".equals(getSiteKey())) {
            List<Vod> all = getCollect().getList();
            Collections.sort(all, (o1, o2) -> Double.compare(Util.similarity(o2.getVodName(), getKeyword()), Util.similarity(o1.getVodName(), getKeyword())));
            mAdapter.clear();
            addRows(all);
        } else {
            addRows(items);
        }
    }

    private void addRows(List<Vod> items) {
        if (!isViewReady() || checkLastSize(items) || getActivity() == null || getActivity().isFinishing() || getActivity().isDestroyed()) return;
        List<ListRow> rows = new ArrayList<>();
        int column = Product.getColumn();
        for (int start = 0; start < items.size(); start += column) {
            int end = Math.min(start + column, items.size());
            mLast = new ArrayObjectAdapter(new VodPresenter(this));
            mLast.setItems(new ArrayList<>(items.subList(start, end)), null);
            rows.add(new ListRow(mLast));
        }
        mAdapter.addAll(mAdapter.size(), rows);
    }

    private void syncRows() {
        if (mAdapter == null || mAdapter.size() > 0 || getCollect() == null) return;
        for (Vod item : getCollect().getList()) mVodKeys.add(getVodKey(item));
        addRows(new ArrayList<>(getCollect().getList()));
    }

    private boolean isViewReady() {
        return isAdded() && getView() != null;
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

    private List<Vod> filterNewItems(List<Vod> items) {
        List<Vod> results = new ArrayList<>();
        for (Vod item : items) if (mVodKeys.add(getVodKey(item))) results.add(item);
        return results;
    }

    private String getVodKey(Vod item) {
        String id = item.getVodId();
        if (!id.isEmpty()) return item.getSiteKey() + "@" + id;
        return item.getSiteKey() + "@" + item.getVodName() + "@" + item.getVodPic() + "@" + item.getVodRemarks();
    }
}
