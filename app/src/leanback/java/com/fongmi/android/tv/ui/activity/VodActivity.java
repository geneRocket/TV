package com.fongmi.android.tv.ui.activity;

import android.app.Activity;
import android.content.Intent;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentStatePagerAdapter;
import androidx.leanback.widget.ArrayObjectAdapter;
import androidx.leanback.widget.ItemBridgeAdapter;
import androidx.leanback.widget.OnChildViewHolderSelectedListener;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewbinding.ViewBinding;
import androidx.viewpager.widget.ViewPager;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.bean.Class;
import com.fongmi.android.tv.bean.Filter;
import com.fongmi.android.tv.bean.Result;
import com.fongmi.android.tv.bean.Site;
import com.fongmi.android.tv.databinding.ActivityVodBinding;
import com.fongmi.android.tv.ui.base.BaseActivity;
import com.fongmi.android.tv.ui.fragment.VodFragment;
import com.fongmi.android.tv.ui.presenter.TypePresenter;
import com.fongmi.android.tv.utils.KeyUtil;
import com.fongmi.android.tv.utils.ResUtil;
import com.fongmi.android.tv.utils.SiteCategoryUtil;

import java.util.ArrayList;
import java.util.List;

public class VodActivity extends BaseActivity implements TypePresenter.OnClickListener {

    private ActivityVodBinding mBinding;
    private ArrayObjectAdapter mAdapter;
    private PageAdapter mPageAdapter;
    private boolean coolDown;
    private View mOldView;
    private int mLastPagePosition;
    private final Runnable mCoolDownReset = () -> coolDown = false;

    public static void start(Activity activity, Result result) {
        start(activity, VodConfig.get().getHome().getKey(), result);
    }

    public static void start(Activity activity, String key, Result result) {
        if (result == null || result.getTypes().isEmpty()) return;
        Intent intent = new Intent(activity, VodActivity.class);
        intent.putExtra("key", key);
        intent.putExtra("result", result);
        activity.startActivity(intent);
    }

    private String getKey() {
        return getIntent().getStringExtra("key");
    }

    private Result getResult() {
        return getIntent().getParcelableExtra("result");
    }

    private List<Filter> getFilters(Result result, String typeId) {
        if (result == null || result.getFilters() == null) return new ArrayList<>();
        List<Filter> filters = result.getFilters().get(typeId);
        return filters == null ? new ArrayList<>() : filters;
    }

    private Site getSite() {
        return VodConfig.get().getSite(getKey());
    }

    @Override
    protected ViewBinding getBinding() {
        return mBinding = ActivityVodBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void initView() {
        setRecyclerView();
        setTypes();
        setPager();
    }

    @Override
    protected void initEvent() {
        mBinding.pager.addOnPageChangeListener(new ViewPager.SimpleOnPageChangeListener() {
            @Override
            public void onPageSelected(int position) {
                notifyPageHidden(mLastPagePosition, position);
                mLastPagePosition = position;
                mBinding.recycler.setSelectedPosition(position);
            }
        });
        mBinding.recycler.addOnChildViewHolderSelectedListener(new OnChildViewHolderSelectedListener() {
            @Override
            public void onChildViewHolderSelected(@NonNull RecyclerView parent, @Nullable RecyclerView.ViewHolder child, int position, int subposition) {
                onChildSelected(child);
            }
        });
    }

    private void setRecyclerView() {
        mBinding.recycler.setHorizontalSpacing(ResUtil.dp2px(16));
        mBinding.recycler.setRowHeight(ViewGroup.LayoutParams.WRAP_CONTENT);
        mBinding.recycler.setHasFixedSize(true);
        mBinding.recycler.setItemAnimator(null);
        mBinding.recycler.setItemViewCacheSize(12);
        mBinding.recycler.setAdapter(new ItemBridgeAdapter(mAdapter = new ArrayObjectAdapter(new TypePresenter(this))));
    }

    private List<Class> getTypes(Result result) {
        return SiteCategoryUtil.filter(getSite().getCategories(), result.getTypes());
    }

    private void setTypes() {
        Result result = getResult();
        result.setTypes(getTypes(result));
        for (Class item : result.getTypes()) item.setFilters(getFilters(result, item.getTypeId()));
        mAdapter.setItems(result.getTypes(), null);
    }

    private void setPager() {
        mBinding.pager.setAdapter(mPageAdapter = new PageAdapter(getSupportFragmentManager()));
        mLastPagePosition = mBinding.pager.getCurrentItem();
    }

    private void onChildSelected(@Nullable RecyclerView.ViewHolder child) {
        if (mOldView != null) mOldView.setActivated(false);
        if (child == null) return;
        mOldView = child.itemView;
        mOldView.setActivated(true);
        App.removeCallbacks(mRunnable);
        App.post(mRunnable, 100);
    }

    private final Runnable mRunnable = new Runnable() {
        @Override
        public void run() {
            int position = mBinding.recycler.getSelectedPosition();
            if (position < 0 || position >= mPageAdapter.getCount()) return;
            mBinding.pager.setCurrentItem(position);
        }
    };

    private void updateFilter(Class item) {
        if (item.getFilter() == null) return;
        getFragment().toggleFilter(item.toggleFilter());
        notifyTypeChanged(item);
    }

    public void updateTypeState(String typeId, java.util.Map<String, String> extend, boolean open) {
        Class item = findType(typeId);
        if (item == null) return;
        item.setExtend(extend);
        item.setFilter(item.getFilters().isEmpty() ? null : open);
        notifyTypeChanged(item);
    }

    @Nullable
    private Class findType(String typeId) {
        for (int i = 0; i < mAdapter.size(); i++) {
            Object item = mAdapter.get(i);
            if (item instanceof Class && ((Class) item).getTypeId().equals(typeId)) return (Class) item;
        }
        return null;
    }

    private void notifyTypeChanged(Class item) {
        int index = mAdapter.indexOf(item);
        if (index >= 0) mAdapter.notifyArrayItemRangeChanged(index, 1);
    }

    private VodFragment getFragment() {
        return getFragment(mBinding.pager.getCurrentItem());
    }

    private VodFragment getFragment(int position) {
        return (VodFragment) mPageAdapter.instantiateItem(mBinding.pager, position);
    }

    private void notifyPageHidden(int fromPosition, int toPosition) {
        if (fromPosition == toPosition || mPageAdapter == null) return;
        if (fromPosition < 0 || fromPosition >= mPageAdapter.getCount()) return;
        getFragment(fromPosition).onPageHidden();
    }

    private void setCoolDown() {
        App.removeCallbacks(mCoolDownReset);
        App.post(mCoolDownReset, 2000);
        coolDown = true;
    }

    @Override
    public void onItemClick(Class item) {
        updateFilter(item);
    }

    @Override
    public boolean onItemLongClick(Class item) {
        return true;
    }

    @Override
    public void onRefresh(Class item) {
        getFragment().onRefresh();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (KeyUtil.isMenuKey(event)) {
            updateFilter((Class) mAdapter.get(mBinding.pager.getCurrentItem()));
            return true;
        }
        if (KeyUtil.isBackKey(event) && event.isLongPress() && getFragment().goRoot()) setCoolDown();
        return super.dispatchKeyEvent(event);
    }

    @Override
    public void onBackPressed() {
        Class item = (Class) mAdapter.get(mBinding.pager.getCurrentItem());
        if (item.getFilter() != null && item.getFilter()) getFragment().resetFilterOnBack();
        else if (getFragment().canBack()) getFragment().goBack();
        else if (!coolDown) super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        App.removeCallbacks(mRunnable, mCoolDownReset);
        super.onDestroy();
    }

    class PageAdapter extends FragmentStatePagerAdapter {

        public PageAdapter(@NonNull FragmentManager fm) {
            super(fm, BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT);
        }

        @NonNull
        @Override
        public Fragment getItem(int position) {
            Class type = (Class) mAdapter.get(position);
            return VodFragment.newInstance(getKey(), type.getTypeId(), type.getStyle(), type.getExtend(false), new ArrayList<>(Filter.copy(type.getFilters())), "1".equals(type.getTypeFlag()), Boolean.TRUE.equals(type.getFilter()));
        }

        @Override
        public int getCount() {
            return mAdapter.size();
        }
    }
}
