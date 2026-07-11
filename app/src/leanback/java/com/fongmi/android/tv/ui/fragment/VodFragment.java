package com.fongmi.android.tv.ui.fragment;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.leanback.widget.ArrayObjectAdapter;
import androidx.leanback.widget.FocusHighlight;
import androidx.leanback.widget.HorizontalGridView;
import androidx.leanback.widget.ItemBridgeAdapter;
import androidx.leanback.widget.ListRowPresenter;
import androidx.leanback.widget.ListRow;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.Product;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.bean.Filter;
import com.fongmi.android.tv.bean.Page;
import com.fongmi.android.tv.bean.Result;
import com.fongmi.android.tv.bean.Site;
import com.fongmi.android.tv.bean.Style;
import com.fongmi.android.tv.bean.Value;
import com.fongmi.android.tv.bean.Vod;
import com.fongmi.android.tv.databinding.FragmentVodBinding;
import com.fongmi.android.tv.model.SiteViewModel;
import com.fongmi.android.tv.ui.activity.HomeActivity;
import com.fongmi.android.tv.ui.activity.VodActivity;
import com.fongmi.android.tv.ui.activity.CollectActivity;
import com.fongmi.android.tv.ui.activity.VideoActivity;
import com.fongmi.android.tv.ui.base.BaseFragment;
import com.fongmi.android.tv.ui.custom.CustomRowPresenter;
import com.fongmi.android.tv.ui.custom.CustomScroller;
import com.fongmi.android.tv.ui.custom.CustomSelector;
import com.fongmi.android.tv.ui.presenter.FilterPresenter;
import com.fongmi.android.tv.ui.presenter.VodPresenter;
import com.fongmi.android.tv.utils.ResUtil;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class VodFragment extends BaseFragment implements CustomScroller.Callback, VodPresenter.OnClickListener {

    private Map<String, VodPresenter> mPresenters;
    private HashMap<String, String> mExtends;
    private FragmentVodBinding mBinding;
    private ArrayObjectAdapter mAdapter;
    private ArrayObjectAdapter mLast;
    private CustomScroller mScroller;
    private SiteViewModel mViewModel;
    private List<Filter> mFilters;
    private List<Page<ArrayObjectAdapter, ArrayObjectAdapter>> mPages;
    private boolean mOpen;
    private Page<ArrayObjectAdapter, ArrayObjectAdapter> mPage;
    private String mRequestTypeId;
    private String mRequestPage;
    private String mRequestExtend;
    private final Runnable mFilterRefresh = this::getVideo;

    public static VodFragment newInstance(String key, String typeId, Style style, HashMap<String, String> extend, ArrayList<Filter> filters, boolean folder, boolean open) {
        Bundle args = new Bundle();
        args.putString("key", key);
        args.putString("typeId", typeId);
        args.putBoolean("folder", folder);
        args.putBoolean("open", open);
        args.putParcelable("style", style);
        args.putSerializable("extend", extend);
        args.putParcelableArrayList("filters", filters);
        VodFragment fragment = new VodFragment();
        fragment.setArguments(args);
        return fragment;
    }

    private String getKey() {
        return getArguments().getString("key");
    }

    private String getRootTypeId() {
        return getArguments().getString("typeId");
    }

    private String getTypeId() {
        return mPages.isEmpty() ? getArguments().getString("typeId") : getLastPage().getVodId();
    }

    private List<Filter> getFilter() {
        ArrayList<Filter> filters = getArguments().getParcelableArrayList("filters");
        return filters == null ? new ArrayList<>() : new ArrayList<>(filters);
    }

    private HashMap<String, String> getExtend() {
        Serializable extend = getArguments().getSerializable("extend");
        HashMap<String, String> result = new HashMap<>();
        if (extend instanceof Map<?, ?>) {
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) extend).entrySet()) {
                if (!(entry.getKey() instanceof String) || !(entry.getValue() instanceof String)) continue;
                result.put((String) entry.getKey(), (String) entry.getValue());
            }
        }
        return result;
    }

    private boolean isFolder() {
        return getArguments().getBoolean("folder");
    }

    private boolean isOpen() {
        return getArguments().getBoolean("open");
    }

    private Site getSite() {
        return VodConfig.get().getSite(getKey());
    }

    private boolean isIndexs() {
        return getSite().isIndexs();
    }

    private Page<ArrayObjectAdapter, ArrayObjectAdapter> getLastPage() {
        return mPages.get(mPages.size() - 1);
    }

    private Style getStyle() {
        return isFolder() ? Style.list() : getSite().getStyle(mPages.isEmpty() ? getArguments().getParcelable("style") : getLastPage().getStyle());
    }

    @Override
    protected ViewBinding getBinding(@NonNull LayoutInflater inflater, @Nullable ViewGroup container) {
        return mBinding = FragmentVodBinding.inflate(inflater, container, false);
    }

    @Override
    protected void initView() {
        mPresenters = new HashMap<>();
        mPages = new ArrayList<>();
        mOpen = isOpen();
        mExtends = getExtend();
        mFilters = getFilter();
        setRecyclerView();
        setViewModel();
        setFilters();
        if (mOpen) showFilter();
    }

    @Override
    protected void initData() {
        getVideo();
    }

    @SuppressLint("RestrictedApi")
    private void setRecyclerView() {
        CustomSelector selector = new CustomSelector();
        selector.addPresenter(Vod.class, new VodPresenter(this, Style.list()));
        selector.addPresenter(ListRow.class, new CustomRowPresenter(16), VodPresenter.class);
        selector.addPresenter(ListRow.class, new CustomRowPresenter(8, FocusHighlight.ZOOM_FACTOR_NONE, HorizontalGridView.FOCUS_SCROLL_ALIGNED), FilterPresenter.class);
        mBinding.recycler.addOnScrollListener(mScroller = new CustomScroller(this));
        mBinding.recycler.setAdapter(new ItemBridgeAdapter(mAdapter = new ArrayObjectAdapter(selector)));
        mBinding.recycler.setHeader(getActivity().findViewById(R.id.recycler));
        mBinding.recycler.setHasFixedSize(true);
        mBinding.recycler.setItemViewCacheSize(20);
        mBinding.recycler.setVerticalSpacing(ResUtil.dp2px(16));
        mBinding.recycler.setItemAnimator(null);
    }

    private void setViewModel() {
        mViewModel = new ViewModelProvider(this).get(SiteViewModel.class);
        mViewModel.result.observe(getViewLifecycleOwner(), result -> {
            if (!isCurrentRequest(result)) return;
            boolean first = "1".equals(result.getRequestPage());
            int size = result.getList().size();
            if (first || size > 0) addVideo(result);
            mScroller.endLoading(result);
            checkPosition(first);
            checkMore(size);
            hideProgress();
        });
    }

    private void setFilters() {
        for (Filter filter : mFilters) {
            for (Value value : filter.getValue()) {
                if (value != null) value.setActivated(false);
            }
        }
        for (Filter filter : mFilters) {
            if (mExtends.containsKey(filter.getKey())) {
                filter.setActivated(mExtends.get(filter.getKey()));
            }
        }
    }

    private HashMap<String, String> getDefaultExtend() {
        HashMap<String, String> extend = new HashMap<>();
        for (Filter filter : mFilters) {
            if (filter.getInit() != null) extend.put(filter.getKey(), filter.getInit());
        }
        return extend;
    }

    private void setClick(ArrayObjectAdapter adapter, String key, Value item) {
        for (int i = 0; i < adapter.size(); i++) ((Value) adapter.get(i)).setActivated(item);
        adapter.notifyArrayItemRangeChanged(0, adapter.size());
        if (item.isActivated()) mExtends.put(key, item.getV());
        else mExtends.remove(key);
        dispatchTypeState();
        scheduleFilterRefresh();
    }

    private void scheduleFilterRefresh() {
        App.removeCallbacks(mFilterRefresh);
        App.post(mFilterRefresh, 180);
    }

    private void getVideo() {
        mScroller.reset();
        requestVideo(getTypeId(), "1");
    }

    private void requestVideo(String typeId, String page) {
        boolean first = "1".equals(page);
        mRequestTypeId = typeId;
        mRequestPage = page;
        mRequestExtend = getRequestExtend(mExtends);
        if (first) mLast = null;
        if (first) showProgress();
        mViewModel.categoryContent(getKey(), typeId, page, true, mExtends);
    }

    private boolean isCurrentRequest(Result result) {
        return result != null
                && getKey().equals(result.getKey())
                && mRequestTypeId != null
                && mRequestTypeId.equals(result.getRequestTypeId())
                && mRequestPage != null
                && mRequestPage.equals(result.getRequestPage())
                && mRequestExtend != null
                && mRequestExtend.equals(result.getRequestExtend());
    }

    private String getRequestExtend(HashMap<String, String> extend) {
        if (extend == null || extend.isEmpty()) return "";
        return App.gson().toJson(new TreeMap<>(extend));
    }

    private void addVideo(Result result) {
        Style style = result.getStyle(getStyle());
        List<Vod> items = result.getList();
        if (result.getRequestPage().equals("1")) {
            int start = mOpen ? mFilters.size() : 0;
            if (style.isList()) setItems(start, items);
            else updateRows(start, items, style);
        } else {
            if (items.isEmpty()) return;
            if (style.isList()) mAdapter.addAll(mAdapter.size(), items);
            else addGrid(items, style);
        }
    }

    private void checkPosition(boolean first) {
        if (mPage != null && mPage.getPosition() > 0) mBinding.recycler.hideHeader();
        if (mPage != null && mPage.getPosition() < 1) mBinding.recycler.showHeader();
        if (mPage != null) mBinding.recycler.setSelectedPosition(mPage.getPosition());
        else if (first && !mOpen) mBinding.recycler.moveToTop();
        mPage = null;
    }

    private void checkMore(int count) {
        if (mScroller.isDisable() || count == 0 || mAdapter.size() >= 5) return;
        requestVideo(getTypeId(), String.valueOf(mScroller.addPage()));
    }

    private boolean checkLastSize(List<Vod> items, Style style) {
        if (mLast == null || items.isEmpty()) return false;
        int size = Product.getColumn(style) - mLast.size();
        if (size == 0) return false;
        size = Math.min(size, items.size());
        mLast.addAll(mLast.size(), new ArrayList<>(items.subList(0, size)));
        addGrid(new ArrayList<>(items.subList(size, items.size())), style);
        return true;
    }

    private VodPresenter getPresenter(Style style) {
        String key = style.getViewType() + "_" + style.getRatio();
        if (!mPresenters.containsKey(key)) mPresenters.put(key, new VodPresenter(this, style));
        return mPresenters.get(key);
    }

    private void addGrid(List<Vod> items, Style style) {
        if (checkLastSize(items, style)) return;
        List<ListRow> rows = new ArrayList<>();
        int column = Product.getColumn(style);
        for (int start = 0; start < items.size(); start += column) {
            int end = Math.min(start + column, items.size());
            mLast = new ArrayObjectAdapter(getPresenter(style));
            mLast.setItems(new ArrayList<>(items.subList(start, end)), null);
            rows.add(new ListRow(mLast));
        }
        mAdapter.addAll(mAdapter.size(), rows);
    }

    private void setItems(int start, List<Vod> items) {
        if (mAdapter.size() > start) mAdapter.removeItems(start, mAdapter.size() - start);
        mAdapter.addAll(start, items);
    }

    private void updateRows(int start, List<Vod> items, Style style) {
        if (mBinding == null || getActivity() == null || getActivity().isFinishing() || getActivity().isDestroyed()) return;
        int column = Product.getColumn(style);
        int rowCount = (int) Math.ceil((double) items.size() / column);
        if (rowCount == 0) mLast = null;
        if (mAdapter.size() > start && !(mAdapter.get(start) instanceof ListRow)) mAdapter.removeItems(start, mAdapter.size() - start);
        if (mAdapter.size() > start && mAdapter.get(start) instanceof ListRow) {
            Object adapter = ((ListRow) mAdapter.get(start)).getAdapter();
            if (adapter instanceof ArrayObjectAdapter && ((ArrayObjectAdapter) adapter).getPresenterSelector().getPresenter(null) != getPresenter(style)) mAdapter.removeItems(start, mAdapter.size() - start);
        }
        if (mAdapter.size() > start + rowCount) mAdapter.removeItems(start + rowCount, mAdapter.size() - (start + rowCount));
        for (int i = 0; i < rowCount; i++) {
            int index = start + i;
            int startItem = i * column;
            int endItem = Math.min(startItem + column, items.size());
            List<Vod> subList = new ArrayList<>(items.subList(startItem, endItem));
            if (index < mAdapter.size()) {
                Object item = mAdapter.get(index);
                if (item instanceof ListRow) {
                    ArrayObjectAdapter adapter = (ArrayObjectAdapter) ((ListRow) item).getAdapter();
                    if (adapter != null) adapter.setItems(subList, null);
                    if (i == rowCount - 1) mLast = adapter;
                }
            } else {
                mLast = new ArrayObjectAdapter(getPresenter(style));
                mLast.setItems(subList, null);
                mAdapter.add(new ListRow(mLast));
            }
        }
    }

    private ListRow getRow(Filter filter) {
        FilterPresenter presenter = new FilterPresenter(filter.getKey());
        ArrayObjectAdapter adapter = new ArrayObjectAdapter(presenter);
        presenter.setOnClickListener((key, item) -> setClick(adapter, key, item));
        adapter.setItems(filter.getValue(), null);
        return new ListRow(adapter);
    }

    private void showProgress() {
        if (!mOpen) mBinding.progress.getRoot().setVisibility(View.VISIBLE);
    }

    private void hideProgress() {
        mBinding.progress.getRoot().setVisibility(View.GONE);
    }

    private void showFilter() {
        if (mFilters.isEmpty()) return;
        List<ListRow> rows = new ArrayList<>();
        for (Filter filter : mFilters) rows.add(getRow(filter));
        setTopPadding(4);
        App.post(() -> {
            if (isAdded() && mBinding != null) mBinding.recycler.scrollToPosition(0);
        }, 48);
        mAdapter.addAll(0, rows);
        hideProgress();
    }

    private void hideFilter() {
        int count = Math.min(mFilters.size(), mAdapter.size());
        if (count > 0) mAdapter.removeItems(0, count);
        setTopPadding(12);
    }

    private void setTopPadding(int top) {
        if (mBinding == null) return;
        mBinding.recycler.setPadding(mBinding.recycler.getPaddingLeft(), ResUtil.dp2px(top), mBinding.recycler.getPaddingRight(), mBinding.recycler.getPaddingBottom());
    }

    public void toggleFilter(boolean open) {
        if (mOpen == open) return;
        if (open) showFilter();
        else hideFilter();
        mOpen = open;
        dispatchTypeState();
    }

    public void resetFilterOnBack() {
        HashMap<String, String> defaults = getDefaultExtend();
        boolean changed = !getRequestExtend(mExtends).equals(getRequestExtend(defaults));
        mExtends.clear();
        mExtends.putAll(defaults);
        setFilters();
        if (mOpen) hideFilter();
        mOpen = false;
        dispatchTypeState();
        if (changed) onRefresh();
    }

    public void onRefresh() {
        getVideo();
    }

    public boolean canBack() {
        return !mPages.isEmpty();
    }

    public void goBack() {
        if (mPages.size() == 1) mBinding.recycler.setMoveTop(true);
        mPages.remove(mPage = getLastPage());
        restorePage(mPage);
    }

    public boolean goRoot() {
        if (mPages.isEmpty()) return false;
        mPages.clear();
        getVideo();
        return true;
    }

    @Override
    public void onItemClick(Vod item) {
        if (item.isAction()) {
            mViewModel.action(getKey(), item.getAction());
        } else if (item.isFolder()) {
            openFolder(item);
        } else {
            openVod(item);
        }
    }

    private void openFolder(Vod item) {
        Page<ArrayObjectAdapter, ArrayObjectAdapter> page = Page.get(item, getFolderRowPosition(item), getFolderChildPosition(item), mAdapter, mLast, mScroller.getPage(), !mScroller.isDisable());
        mPages.add(page);
        mBinding.recycler.setAdapter(new ItemBridgeAdapter(mAdapter = new ArrayObjectAdapter(mAdapter.getPresenterSelector())));
        mBinding.recycler.setMoveTop(false);
        requestVideo(item.getVodId(), "1");
    }

    private void restorePage(Page<ArrayObjectAdapter, ArrayObjectAdapter> page) {
        mViewModel.cancelCategoryContent();
        mRequestTypeId = null;
        mRequestPage = null;
        mRequestExtend = null;
        mBinding.recycler.setAdapter(new ItemBridgeAdapter(mAdapter = page.getAdapter()));
        mLast = page.getExtra();
        mScroller.restore(page.getPage(), page.isEnable());
        checkPosition(false);
        restoreChildPosition(page);
        hideProgress();
    }

    private int getFolderRowPosition(Vod item) {
        for (int i = 0; i < mAdapter.size(); i++) {
            Object row = mAdapter.get(i);
            if (item.equals(row)) return i;
            if (row instanceof ListRow && getChildPosition((ListRow) row, item) >= 0) return i;
        }
        return mBinding.recycler.getSelectedPosition();
    }

    private int getFolderChildPosition(Vod item) {
        int row = getFolderRowPosition(item);
        if (row < 0 || row >= mAdapter.size()) return -1;
        Object object = mAdapter.get(row);
        return object instanceof ListRow ? getChildPosition((ListRow) object, item) : -1;
    }

    private int getChildPosition(ListRow row, Vod item) {
        if (!(row.getAdapter() instanceof ArrayObjectAdapter)) return -1;
        ArrayObjectAdapter adapter = (ArrayObjectAdapter) row.getAdapter();
        return adapter.indexOf(item);
    }

    private void restoreChildPosition(Page<ArrayObjectAdapter, ArrayObjectAdapter> page) {
        mBinding.recycler.post(() -> {
            if (mBinding == null) return;
            RecyclerView.ViewHolder holder = mBinding.recycler.findViewHolderForLayoutPosition(page.getPosition());
            if (holder == null) return;
            if (page.getChildPosition() >= 0 && holder instanceof ItemBridgeAdapter.ViewHolder && ((ItemBridgeAdapter.ViewHolder) holder).getViewHolder() instanceof ListRowPresenter.ViewHolder) {
                HorizontalGridView row = ((ListRowPresenter.ViewHolder) ((ItemBridgeAdapter.ViewHolder) holder).getViewHolder()).getGridView();
                row.setSelectedPosition(page.getChildPosition());
                row.post(() -> {
                    RecyclerView.ViewHolder child = row.findViewHolderForLayoutPosition(page.getChildPosition());
                    if (child != null) child.itemView.requestFocus();
                });
            } else {
                holder.itemView.requestFocus();
            }
        });
    }

    private void openVod(Vod item) {
        if (isIndexs()) {
            CollectActivity.start(getActivity(), item.getVodName());
        } else if (!isFolder()) {
            VideoActivity.start(getActivity(), getKey(), item.getVodId(), item.getVodName(), item.getVodPic());
        } else {
            VideoActivity.start(getActivity(), getKey(), item.getVodId(), item.getVodName(), item.getVodPic(), item.getVodName());
        }
    }

    @Override
    public boolean onLongClick(Vod item) {
        CollectActivity.start(getActivity(), item.getVodName());
        return true;
    }

    @Override
    public void onLoadMore(String page) {
        mScroller.setLoading(true);
        requestVideo(getTypeId(), page);
    }

    public void onPageHidden() {
        if (mBinding != null) mBinding.recycler.moveToTop();
    }

    @Override
    public void onDestroyView() {
        App.removeCallbacks(mFilterRefresh);
        super.onDestroyView();
    }

    private void dispatchTypeState() {
        if (getActivity() instanceof VodActivity) {
            ((VodActivity) getActivity()).updateTypeState(getRootTypeId(), mExtends, mOpen);
        } else if (getActivity() instanceof HomeActivity) {
            ((HomeActivity) getActivity()).updateTypeState(getRootTypeId(), mExtends, mOpen);
        }
    }
}
