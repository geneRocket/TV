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
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.Setting;
import com.fongmi.android.tv.Product;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.bean.Page;
import com.fongmi.android.tv.bean.Result;
import com.fongmi.android.tv.bean.Site;
import com.fongmi.android.tv.bean.Style;
import com.fongmi.android.tv.bean.Value;
import com.fongmi.android.tv.bean.Vod;
import com.fongmi.android.tv.databinding.FragmentTypeBinding;
import com.fongmi.android.tv.model.SiteViewModel;
import com.fongmi.android.tv.model.CategoryRequest;
import com.fongmi.android.tv.ui.activity.CollectActivity;
import com.fongmi.android.tv.ui.activity.DetailActivity;
import com.fongmi.android.tv.ui.activity.VideoActivity;
import com.fongmi.android.tv.ui.adapter.VodAdapter;
import com.fongmi.android.tv.ui.base.BaseFragment;
import com.fongmi.android.tv.ui.custom.CustomScroller;
import com.fongmi.android.tv.utils.Notify;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public class TypeFragment extends BaseFragment implements CustomScroller.Callback, VodAdapter.OnClickListener, SwipeRefreshLayout.OnRefreshListener {

    private HashMap<String, String> mExtends;
    private FragmentTypeBinding mBinding;
    private CustomScroller mScroller;
    private SiteViewModel mViewModel;
    private VodAdapter mAdapter;
    private List<Page<VodAdapter, Set<String>>> mPages;
    private Set<String> mVodKeys;
    private CategoryRequest mRequest;
    private CategoryRequest mLoadedRequest;
    private int mLoadedPage = 1;
    private boolean mLoadedMore;
    private HashMap<String, String> mLoadedExtends = new HashMap<>();
    private Page<VodAdapter, Set<String>> mPage;
    private Result mHomeResult;
    private String mHomeToken;
    private long mHomeTokenSeed;
    private boolean mLastRequestFailed;

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

    private Page<VodAdapter, Set<String>> getLastPage() {
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
        mBinding.progressLayout.setOnClickListener(view -> {
            if (mLastRequestFailed) onRefresh();
        });
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
        mViewModel.actionResult().observe(getViewLifecycleOwner(), response -> {
            if (!getKey().equals(response.getKey())) return;
            if (response.hasMsg()) com.fongmi.android.tv.utils.Notify.show(response.getMsg());
            else if (response.isRequestFailed()) com.fongmi.android.tv.utils.Notify.show(R.string.vod_load_failed);
            if (!response.isRequestFailed() && !response.getList().isEmpty()) onRefresh();
        });
        mViewModel.result().observe(getViewLifecycleOwner(), this::setAdapter);
    }

    private void getHome() {
        mScroller.reset();
        mScroller.setLoading(true);
        mHomeResult = null;
        mHomeToken = "home:" + (++mHomeTokenSeed);
        mViewModel.homeContent(getKey(), mHomeToken);
    }


    private void getVideo() {
        mScroller.reset();
        getVideo(getTypeId(), "1");
    }

    private void getVideo(String typeId, String page) {
        mRequest = CategoryRequest.create(getKey(), typeId, page, mExtends);
        if ("1".equals(page)) {
            mScroller.reset();
            mScroller.setLoading(true);
            mLastRequestFailed = false;
            mBinding.progressLayout.setEmptyText(R.string.error_empty);
            if (!mBinding.swipeLayout.isRefreshing()) mBinding.progressLayout.showProgress();
        }
        if (isHome() && "1".equals(page)) {
            mHomeToken = null;
            setAdapter(mHomeResult = getParent().getResult());
        } else {
            mHomeResult = null;
            mHomeToken = null;
            mViewModel.categoryContent(getKey(), typeId, page, true, mExtends);
        }
    }

    private void setAdapter(Result result) {
        if (!isCurrentResult(result)) return;
        boolean first = mScroller.first();
        boolean failed = result.isRequestFailed();
        // Do not discard the current list until a replacement request has succeeded. This keeps
        // a pull-to-refresh failure from turning a previously usable page into an empty screen.
        if (first && !failed) {
            mVodKeys.clear();
            mAdapter.clear();
        }
        List<Vod> items = filterVodList(result == null ? null : result.getList());
        int size = items.size();
        mLastRequestFailed = failed;
        mBinding.progressLayout.setEmptyText(mLastRequestFailed ? R.string.error_load_retry : R.string.error_empty);
        int visibleCount = mAdapter.getItemCount();
        mBinding.progressLayout.showContent(failed && visibleCount > 0 ? false : first, failed && visibleCount > 0 ? visibleCount : size);
        mBinding.swipeLayout.setRefreshing(false);
        if (size > 0) addVideo(result, items);
        if (failed && visibleCount > 0) Notify.show(R.string.error_load_retry);
        mScroller.endLoading(result);
        if (failed && first && mRequest != null && mRequest.sameContent(mLoadedRequest)) {
            mScroller.restore(mLoadedPage, mLoadedMore);
        } else if (failed && first) {
            // A new filter must replace the old list before any later page is appended.
            mScroller.restore(1, false);
        } else if (!failed) {
            mLoadedRequest = mRequest;
            mLoadedExtends = new HashMap<>(mExtends);
            mLoadedPage = mScroller.getPage();
            mLoadedMore = !mScroller.isDisable();
        }
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
        if (isHome()) {
            if (result == null) return false;
            if (result == mHomeResult) return true;
            boolean current = !TextUtils.isEmpty(mHomeToken)
                    && TextUtils.equals(result.getKey(), getKey())
                    && TextUtils.equals(result.getRequestToken(), mHomeToken);
            if (current) mHomeToken = null;
            return current;
        }
        if (result == null) return false;
        return mRequest != null && mRequest.matches(result);
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
            Page<VodAdapter, Set<String>> page = Page.<VodAdapter, Set<String>>get(item, findPosition(), mAdapter, new HashSet<>(mVodKeys), mLoadedPage, mLoadedMore).context(mLoadedExtends, false);
            mPages.add(page);
            setStyle(getStyle());
            getVideo(item.getVodId(), "1");
        } else {
            if (isIndexs()) CollectActivity.start(getActivity(), item.getVodName());
            else if (item.isManga()) DetailActivity.start(getActivity(), getKey(), item.getVodId(), item.getVodName(), item.getVodPic());
            else VideoActivity.start(getActivity(), getKey(), item.getVodId(), item.getVodName(), item.getVodPic(), isFolder() ? item.getVodName() : null, false);
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
        restorePage(mPage);
        return false;
    }

    private void restorePage(Page<VodAdapter, Set<String>> page) {
        mViewModel.cancelCategoryContent();
        mRequest = null;
        mAdapter = page.getAdapter();
        mBinding.recycler.setAdapter(mAdapter);
        mBinding.recycler.setLayoutManager(mAdapter.getStyle().isList() ? new LinearLayoutManager(getActivity()) : new GridLayoutManager(getContext(), Product.getColumn(getActivity(), mAdapter.getStyle())));
        mVodKeys.clear();
        mVodKeys.addAll(page.getExtra());
        mHomeResult = null;
        mHomeToken = null;
        mScroller.restore(page.getPage(), page.isEnable());
        mLoadedRequest = CategoryRequest.create(getKey(), getTypeId(), String.valueOf(page.getPage()), page.getExtend());
        mLoadedExtends = new HashMap<>(page.getExtend());
        mLoadedPage = page.getPage();
        mLoadedMore = page.isEnable();
        if (!page.getExtend().equals(mExtends)) {
            mPage = null;
            getVideo();
            return;
        }
        mBinding.swipeLayout.setRefreshing(false);
        mBinding.progressLayout.showContent(false, mAdapter.getItemCount());
        checkPosition(false);
    }
}
