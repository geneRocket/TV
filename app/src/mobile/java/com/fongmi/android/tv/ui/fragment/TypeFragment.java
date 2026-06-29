package com.fongmi.android.tv.ui.fragment;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.Setting;
import com.fongmi.android.tv.Product;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.bean.Page;
import com.fongmi.android.tv.bean.Result;
import com.fongmi.android.tv.bean.Site;
import com.fongmi.android.tv.bean.Style;
import com.fongmi.android.tv.bean.Value;
import com.fongmi.android.tv.bean.Vod;
import com.fongmi.android.tv.bean.VodQueue;
import com.fongmi.android.tv.databinding.FragmentTypeBinding;
import com.fongmi.android.tv.model.SiteViewModel;
import com.fongmi.android.tv.ui.activity.CollectActivity;
import com.fongmi.android.tv.ui.activity.DetailActivity;
import com.fongmi.android.tv.ui.activity.VideoActivity;
import com.fongmi.android.tv.ui.adapter.VodAdapter;
import com.fongmi.android.tv.ui.base.BaseFragment;
import com.fongmi.android.tv.ui.custom.CustomScroller;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

public class TypeFragment extends BaseFragment implements CustomScroller.Callback, VodAdapter.OnClickListener, SwipeRefreshLayout.OnRefreshListener {

    private HashMap<String, String> mExtends;
    private FragmentTypeBinding mBinding;
    private CustomScroller mScroller;
    private SiteViewModel mViewModel;
    private VodAdapter mAdapter;
    private List<Page> mPages;
    private Set<String> mVodKeys;
    private String mPendingTypeId;
    private String mPendingPage;
    private String mPendingExtend;
    private Page mPage;

    public static TypeFragment newInstance(String key, String typeId, Style style, HashMap<String, String> extend, boolean folder) {
        Bundle args = new Bundle();
        args.putString("key", key);
        args.putString("typeId", typeId);
        args.putBoolean("folder", folder);
        args.putParcelable("style", style);
        args.putSerializable("extend", extend);
        TypeFragment fragment = new TypeFragment();
        fragment.setArguments(args);
        return fragment;
    }

    private String getKey() {
        return getArguments().getString("key");
    }

    private String getStoreKey() {
        return VodConfig.rawSiteKey(getKey());
    }

    private String getTypeId() {
        return mPages.isEmpty() ? getArguments().getString("typeId") : getLastPage().getVodId();
    }

    private Style getStyle() {
        return isFolder() ? Style.list() : getSite().getStyle(mPages.isEmpty() ? getArguments().getParcelable("style") : getLastPage().getStyle());
    }

    private boolean isIndexs() {
        return getSite().isIndexs();
    }

    private HashMap<String, String> getExtend() {
        Serializable extend = getArguments().getSerializable("extend");
        return extend == null ? new HashMap<>() : (HashMap<String, String>) extend;
    }

    private boolean isFolder() {
        return getArguments().getBoolean("folder");
    }

    private boolean isHome() {
        return "home".equals(getTypeId());
    }

    private Site getSite() {
        return VodConfig.get().getSite(getKey());
    }

    private VodFragment getParent() {
        return (VodFragment) getParentFragment();
    }

    private Page getLastPage() {
        return mPages.get(mPages.size() - 1);
    }

    @Override
    protected ViewBinding getBinding(@NonNull LayoutInflater inflater, @Nullable ViewGroup container) {
        return mBinding = FragmentTypeBinding.inflate(inflater, container, false);
    }

    @Override
    protected void initView() {
        mScroller = new CustomScroller(this);
        mPages = new ArrayList<>();
        mVodKeys = new HashSet<>();
        mExtends = getExtend();
        setRecyclerView();
        setViewModel();
    }

    @Override
    protected void initEvent() {
        mBinding.swipeLayout.setOnRefreshListener(this);
        mBinding.recycler.addOnScrollListener(mScroller = new CustomScroller(this));
    }

    @Override
    protected void initData() {
        mBinding.progressLayout.showProgress();
        getVideo();
    }

    private void setRecyclerView() {
        mBinding.recycler.setHasFixedSize(true);
        setStyle(getStyle());
    }

    private void setStyle(Style style) {
        mBinding.recycler.setAdapter(mAdapter = new VodAdapter(this, style, Product.getSpec(getActivity(), style)));
        mBinding.recycler.setLayoutManager(style.isList() ? new LinearLayoutManager(getActivity()) : new GridLayoutManager(getContext(), Product.getColumn(getActivity(), style)));
    }

    private void setViewModel() {
        mViewModel = new ViewModelProvider(this).get(SiteViewModel.class);
        mViewModel.result.observe(getViewLifecycleOwner(), this::setAdapter);
    }

    private void getHome() {
        mViewModel.homeContent();
        mVodKeys.clear();
        mAdapter.clear();
    }


    private void getVideo() {
        mScroller.reset();
        getVideo(getTypeId(), "1");
    }

    private void getVideo(String typeId, String page) {
        if ("1".equals(page)) {
            mVodKeys.clear();
            mAdapter.clear();
        }
        mPendingTypeId = typeId;
        mPendingPage = page;
        mPendingExtend = getRequestExtend(mExtends);
        if ("1".equals(page) && !mBinding.swipeLayout.isRefreshing()) mBinding.progressLayout.showProgress();
        if (isHome() && "1".equals(page)) setAdapter(getParent().getResult());
        else mViewModel.categoryContent(getKey(), typeId, page, true, mExtends);
    }

    private void setAdapter(Result result) {
        if (!isCurrentResult(result)) return;
        boolean first = mScroller.first();
        List<Vod> items = filterVodList(result == null ? null : result.getList());
        int size = items.size();
        mBinding.progressLayout.showContent(first, size);
        mBinding.swipeLayout.setRefreshing(false);
        if (size > 0) addVideo(result, items);
        mScroller.endLoading(result);
        checkPosition(first);
        checkMore(size);
    }

    private void addVideo(Result result, List<Vod> items) {
        Style style = items.get(0).getStyle(getStyle());
        if (!style.equals(mAdapter.getStyle())) setStyle(style);
        mAdapter.addAll(items);
    }

    private List<Vod> filterVodList(List<Vod> items) {
        List<Vod> filtered = new ArrayList<>();
        if (items == null) return filtered;
        for (Vod item : items) {
            String key = getVodKey(item);
            if (key.isEmpty() || !mVodKeys.add(key)) continue;
            filtered.add(item);
        }
        return filtered;
    }

    private String getVodKey(Vod item) {
        if (item == null) return "";
        String id = item.getVodId();
        String name = item.getVodName();
        return getTypeId() + "@" + (id.isEmpty() ? name : id);
    }

    private boolean isCurrentResult(Result result) {
        if (isHome()) return true;
        if (result == null) return false;
        return TextUtils.equals(result.getKey(), getKey())
                && TextUtils.equals(result.getRequestTypeId(), mPendingTypeId)
                && TextUtils.equals(result.getRequestPage(), mPendingPage)
                && TextUtils.equals(result.getRequestExtend(), mPendingExtend);
    }

    private String getRequestExtend(HashMap<String, String> extend) {
        if (extend == null || extend.isEmpty()) return "";
        return App.gson().toJson(new TreeMap<>(extend));
    }

    private void checkPosition(boolean first) {
        if (mPage != null) scrollToPosition(mPage.getPosition());
        else if (first) mBinding.recycler.scrollToPosition(0);
        mPage = null;
    }

    private void checkMore(int count) {
        if (mScroller.isDisable() || count == 0 || mBinding.recycler.canScrollVertically(1) || mBinding.recycler.getScrollState() > 0 || isHome()) return;
        getVideo(getTypeId(), String.valueOf(mScroller.addPage()));
    }

    private int findPosition() {
        if (mBinding.recycler.getLayoutManager() instanceof LinearLayoutManager) {
            return ((LinearLayoutManager) mBinding.recycler.getLayoutManager()).findFirstVisibleItemPosition();
        } else if (mBinding.recycler.getLayoutManager() instanceof GridLayoutManager) {
            return ((GridLayoutManager) mBinding.recycler.getLayoutManager()).findFirstVisibleItemPosition();
        } else {
            return 0;
        }
    }

    private void scrollToPosition(int position) {
        if (mBinding.recycler.getLayoutManager() instanceof LinearLayoutManager) {
            ((LinearLayoutManager) mBinding.recycler.getLayoutManager()).scrollToPositionWithOffset(position, 0);
        } else if (mBinding.recycler.getLayoutManager() instanceof GridLayoutManager) {
            ((GridLayoutManager) mBinding.recycler.getLayoutManager()).scrollToPositionWithOffset(position, 0);
        }
    }

    public void scrollToTop() {
        mBinding.recycler.smoothScrollToPosition(0);
    }

    public void setFilter(String key, Value value) {
        String old = mExtends.get(key);
        if (value.isActivated()) mExtends.put(key, value.getV());
        else mExtends.remove(key);
        if (Objects.equals(old, mExtends.get(key))) return;
        onRefresh();
    }

    @Override
    public void onRefresh() {
        if (isHome()) getHome();
        else getVideo();
    }

    @Override
    public void onLoadMore(String page) {
        if (isHome()) return;
        mScroller.setLoading(true);
        getVideo(getTypeId(), page);
    }

    @Override
    public void onItemClick(Vod item) {
        if (item == null) return;
        if (item.isAction()) {
            mViewModel.action(getKey(), item.getAction());
        } else if (item.isFolder()) {
            mPages.add(Page.get(item, findPosition()));
            getVideo(item.getVodId(), "1");
        } else {
            if (isIndexs()) CollectActivity.start(getActivity(), item.getVodName());
            else if (item.isManga()) DetailActivity.start(getActivity(), getKey(), item.getVodId(), item.getVodName(), item.getVodPic());
            else {
                VodQueue.set(getKey(), mAdapter.getItems());
                VideoActivity.startQueued(getActivity(), getKey(), item.getVodId(), item.getVodName(), item.getVodPic(), isFolder() ? item.getVodName() : null);
            }
        }
    }

    @Override
    public boolean onLongClick(Vod item) {
        if (item == null) return false;
        CollectActivity.start(getActivity(), item.getVodName());
        return true;
    }

    @Override
    public boolean canBack() {
        if (mPages.isEmpty()) return true;
        mPages.remove(mPage = getLastPage());
        onRefresh();
        return false;
    }
}
