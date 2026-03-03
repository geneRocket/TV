package com.fongmi.android.tv.ui.activity;

import android.app.Activity;
import android.content.Intent;
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
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewbinding.ViewBinding;
import androidx.viewpager.widget.ViewPager;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.Constant;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.bean.Collect;
import com.fongmi.android.tv.bean.Site;
import com.fongmi.android.tv.bean.Vod;
import com.fongmi.android.tv.databinding.ActivityCollectBinding;
import com.fongmi.android.tv.model.SiteViewModel;
import com.fongmi.android.tv.ui.base.BaseActivity;
import com.fongmi.android.tv.ui.fragment.CollectFragment;
import com.fongmi.android.tv.ui.presenter.CollectPresenter;
import com.fongmi.android.tv.utils.PauseExecutor;
import com.fongmi.android.tv.utils.ResUtil;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class CollectActivity extends BaseActivity {

    private ActivityCollectBinding mBinding;
    private ArrayObjectAdapter mAdapter;
    private PageAdapter mPageAdapter;
    private SiteViewModel mViewModel;
    private PauseExecutor mExecutor;
    private List<Site> mSites;
    private final Set<String> mCollectKeys = new HashSet<>();
    private View mOldView;

    public static void start(Activity activity, String keyword) {
        start(activity, keyword, false);
    }

    public static void start(Activity activity, String keyword, boolean clear) {
        Intent intent = new Intent(activity, CollectActivity.class);
        if (clear) intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        intent.putExtra("keyword", keyword);
        activity.startActivityForResult(intent, 1000);
    }

    private String getKeyword() {
        return getIntent().getStringExtra("keyword");
    }

    @Override
    protected ViewBinding getBinding() {
        return mBinding = ActivityCollectBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void initView() {
        setRecyclerView();
        setViewModel();
        setPager();
        setSite();
        search();
    }

    @Override
    protected void initEvent() {
        mBinding.pager.addOnPageChangeListener(new ViewPager.SimpleOnPageChangeListener() {
            @Override
            public void onPageSelected(int position) {
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
        mBinding.recycler.setSaveEnabled(false);
        mBinding.recycler.setHorizontalSpacing(ResUtil.dp2px(16));
        mBinding.recycler.setRowHeight(ViewGroup.LayoutParams.WRAP_CONTENT);
        mBinding.recycler.setAdapter(new ItemBridgeAdapter(mAdapter = new ArrayObjectAdapter(new CollectPresenter())));
    }

    private void setViewModel() {
        mViewModel = new ViewModelProvider(this).get(SiteViewModel.class);
        mViewModel.search.observe(this, result -> {
            updateCollects(result.getList());
            syncFragments(result.getList());
        });
    }

    private void setPager() {
        mBinding.pager.setSaveEnabled(false);
        mBinding.pager.setAdapter(mPageAdapter = new PageAdapter(getSupportFragmentManager()));
    }

    private void setSite() {
        mSites = new ArrayList<>();
        for (Site site : VodConfig.get().getSites()) if (site.isSearchable()) mSites.add(site);
        Site home = VodConfig.get().getHome();
        if (!mSites.contains(home)) return;
        mSites.remove(home);
        mSites.add(0, home);
    }

    private void search() {
        stop();
        mAdapter.clear();
        Collect all = Collect.all();
        mCollectKeys.clear();
        mCollectKeys.add(all.getSite().getKey());
        mAdapter.add(all);
        mPageAdapter.notifyDataSetChanged();
        mExecutor = new PauseExecutor(Constant.THREAD_POOL);
        mBinding.result.setText(getString(R.string.collect_result, getKeyword()));
        for (Site site : mSites) mExecutor.execute(() -> search(site));
    }

    private void addCollect(List<Vod> items) {
        if (items.isEmpty()) return;
        Collect collect = Collect.create(items);
        if (!mCollectKeys.add(collect.getSite().getKey())) return;
        mAdapter.add(collect);
        mPageAdapter.notifyDataSetChanged();
    }

    private void updateCollects(List<Vod> items) {
        if (items.isEmpty()) return;
        appendCollect(Collect.all().getSite().getKey(), items);
        String key = items.get(0).getSiteKey();
        if (getCollect(key) == null) addCollect(items);
        else appendCollect(key, items);
    }

    private void appendCollect(String key, List<Vod> items) {
        Collect collect = getCollect(key);
        if (collect != null && !items.isEmpty()) collect.getList().addAll(items);
    }

    public Collect getCollect(String key) {
        if (key == null) return null;
        for (int i = 0; i < mAdapter.size(); i++) {
            Collect collect = (Collect) mAdapter.get(i);
            if (key.equals(collect.getSite().getKey())) return collect;
        }
        return null;
    }

    private void syncFragments(List<Vod> items) {
        if (items.isEmpty()) return;
        String key = items.get(0).getSiteKey();
        for (Fragment fragment : getSupportFragmentManager().getFragments()) {
            if (!(fragment instanceof CollectFragment)) continue;
            CollectFragment target = (CollectFragment) fragment;
            if ("all".equals(target.getSiteKey()) || key.equals(target.getSiteKey())) target.appendRows(items);
        }
    }

    public void appendAllCollect(List<Vod> items) {
        if (items.isEmpty()) return;
        appendCollect(Collect.all().getSite().getKey(), items);
        for (Fragment fragment : getSupportFragmentManager().getFragments()) {
            if (!(fragment instanceof CollectFragment)) continue;
            CollectFragment target = (CollectFragment) fragment;
            if ("all".equals(target.getSiteKey())) target.appendRows(items);
        }
    }

    private void search(Site site) {
        try {
            mViewModel.searchContent(site, getKeyword(), false);
        } catch (Throwable ignored) {
        }
    }

    private void stop() {
        if (mExecutor == null) return;
        mExecutor.shutdownNow();
        mExecutor = null;
    }

    private void onChildSelected(@Nullable RecyclerView.ViewHolder child) {
        if (mOldView != null) mOldView.setActivated(false);
        if (child == null) return;
        mOldView = child.itemView;
        mOldView.setActivated(true);
        App.removeCallbacks(mRunnable);
        App.post(mRunnable, 200);
    }

    private final Runnable mRunnable = new Runnable() {
        @Override
        public void run() {
            mBinding.pager.setCurrentItem(mBinding.recycler.getSelectedPosition());
        }
    };

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK) return;
        setResult(RESULT_OK);
        finish();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mExecutor != null) mExecutor.resume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mExecutor != null) mExecutor.pause();
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        stop();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stop();
    }

    class PageAdapter extends FragmentStatePagerAdapter {

        public PageAdapter(@NonNull FragmentManager fm) {
            super(fm, BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT);
        }

        @NonNull
        @Override
        public Fragment getItem(int position) {
            return CollectFragment.newInstance(getKeyword(), ((Collect) mAdapter.get(position)).getSite().getKey());
        }

        @Override
        public int getCount() {
            return mAdapter.size();
        }
    }
}
