package com.fongmi.android.tv.ui.activity;

import android.app.Activity;
import android.content.Intent;
import android.text.TextUtils;
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
import com.fongmi.android.tv.utils.ThreadPools;
import com.fongmi.android.tv.utils.Util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.concurrent.Future;

public class CollectActivity extends BaseActivity {

    private static final long RESULT_FLUSH_DELAY = 500;

    private ActivityCollectBinding mBinding;
    private ArrayObjectAdapter mAdapter;
    private PageAdapter mPageAdapter;
    private SiteViewModel mViewModel;
    private PauseExecutor mExecutor;
    private List<Site> mSites;
    private final Set<String> mCollectKeys = new HashSet<>();
    private final Map<String, Collect> mCollectMap = new LinkedHashMap<>();
    private final List<List<Vod>> mPendingResults = new ArrayList<>();
    private final Set<Future<?>> mSearchTasks = new HashSet<>();
    private View mOldView;
    private String mSearchToken;
    private boolean mPagerDirty;
    private boolean mFlushScheduled;
    private PriorityQueue<Vod> mAllQueue;
    private final Runnable mFlushResults = this::flushResults;

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
        return Objects.toString(getIntent().getStringExtra("keyword"), "");
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
            if (isFinishing() || isDestroyed()) return;
            if (!isCurrentSearchResult(result)) return;
            enqueueResult(result.getList());
        });
    }

    private boolean isCurrentSearchResult(com.fongmi.android.tv.bean.Result result) {
        return result != null
                && TextUtils.equals(result.getKeyword(), getKeyword().trim())
                && TextUtils.equals(result.getRequestToken(), mSearchToken);
    }

    private void setPager() {
        mBinding.pager.setSaveEnabled(false);
        mBinding.pager.setAdapter(mPageAdapter = new PageAdapter(getSupportFragmentManager()));
    }

    private void setSite() {
        mSites = new ArrayList<>();
        Set<String> keys = new HashSet<>();
        for (Site site : VodConfig.get().getSites()) {
            if (!site.isSearchable()) continue;
            if (!keys.add(site.getKey())) continue;
            mSites.add(site);
        }
        Site home = VodConfig.get().getHome();
        if (!mSites.contains(home)) return;
        mSites.remove(home);
        mSites.add(0, home);
    }

    private void search() {
        stop();
        if (mOldView != null) {
            mOldView.setActivated(false);
            mOldView = null;
        }
        mAdapter.clear();
        mCollectMap.clear();
        mPendingResults.clear();
        App.removeCallbacks(mFlushResults);
        mSearchToken = "collect:" + System.currentTimeMillis();
        mPagerDirty = false;
        mFlushScheduled = false;
        mAllQueue = new PriorityQueue<>(101, (o1, o2) -> Double.compare(o1.getScore(), o2.getScore()));
        Collect all = Collect.all();
        mCollectKeys.clear();
        mCollectKeys.add(all.getSite().getKey());
        mCollectMap.put(all.getSite().getKey(), all);
        mAdapter.add(all);
        syncPager();
        mBinding.recycler.setSelectedPosition(0);
        mBinding.pager.setCurrentItem(0, false);
        mExecutor = new PauseExecutor(Math.max(2, Math.min(6, Constant.THREAD_POOL)));
        mBinding.result.setText(getString(R.string.collect_result, getKeyword()));
        for (Site site : mSites) mSearchTasks.add(mExecutor.submit(() -> search(site)));
    }

    private void addCollect(List<Vod> items) {
        if (items.isEmpty()) return;
        Collect collect = Collect.create(items);
        if (!mCollectKeys.add(collect.getSite().getKey())) return;
        mCollectMap.put(collect.getSite().getKey(), collect);
        mAdapter.add(collect);
        mPagerDirty = true;
    }

    private void enqueueResult(List<Vod> items) {
        if (items == null || items.isEmpty()) return;
        mPendingResults.add(items);
        if (mFlushScheduled) return;
        mFlushScheduled = true;
        App.post(mFlushResults, RESULT_FLUSH_DELAY);
    }

    private void flushResults() {
        mFlushScheduled = false;
        if (isFinishing() || isDestroyed() || mPendingResults.isEmpty()) return;
        List<List<Vod>> batches = new ArrayList<>(mPendingResults);
        mPendingResults.clear();
        List<Vod> allAdded = new ArrayList<>();
        Map<String, List<Vod>> siteAdded = new HashMap<>();
        String allKey = Collect.all().getSite().getKey();
        for (List<Vod> items : batches) {
            if (items.isEmpty()) continue;
            String siteKey = items.get(0).getSiteKey();
            allAdded.addAll(items);
            siteAdded.computeIfAbsent(siteKey, k -> new ArrayList<>()).addAll(items);
        }
        boolean allChanged = false;
        if (!allAdded.isEmpty()) allChanged = appendCollect(allKey, allAdded);
        for (Map.Entry<String, List<Vod>> entry : siteAdded.entrySet()) {
            String key = entry.getKey();
            List<Vod> items = entry.getValue();
            if (getCollect(key) == null) addCollect(items);
            else appendCollect(key, items);
        }
        for (Fragment fragment : getSupportFragmentManager().getFragments()) {
            if (!(fragment instanceof CollectFragment)) continue;
            CollectFragment target = (CollectFragment) fragment;
            String key = target.getSiteKey();
            if ("all".equals(key)) {
                if (allChanged) target.appendRows(allAdded);
            } else if (siteAdded.containsKey(key)) {
                target.appendRows(siteAdded.get(key));
            }
        }
        if (mPagerDirty) {
            mPagerDirty = false;
            syncPager();
        }
    }

    private boolean appendCollect(String key, List<Vod> items) {
        Collect collect = getCollect(key);
        if (collect == null || items.isEmpty()) return false;
        if ("all".equals(key)) {
            if (mAllQueue == null) return false;
            boolean changed = false;
            String keyword = getKeyword().trim();
            for (Vod item : items) {
                item.setScore(Util.similarity(item.getVodName(), keyword));
                if (mAllQueue.size() < 100) {
                    mAllQueue.offer(item);
                    changed = true;
                } else if (mAllQueue.peek() != null && item.getScore() > mAllQueue.peek().getScore()) {
                    mAllQueue.poll();
                    mAllQueue.offer(item);
                    changed = true;
                }
            }
            if (!changed) return false;
            List<Vod> all = new ArrayList<>(mAllQueue);
            Collections.sort(all, (o1, o2) -> Double.compare(o2.getScore(), o1.getScore()));
            collect.getList().clear();
            collect.getList().addAll(all);
            return true;
        } else {
            collect.getList().addAll(items);
            return true;
        }
    }

    public Collect getCollect(String key) {
        return key == null ? null : mCollectMap.get(key);
    }

    public void appendAllCollect(List<Vod> items) {
        if (isFinishing() || isDestroyed()) return;
        if (items.isEmpty()) return;
        appendCollect(Collect.all().getSite().getKey(), items);
        for (Fragment fragment : getSupportFragmentManager().getFragments()) {
            if (!(fragment instanceof CollectFragment)) continue;
            CollectFragment target = (CollectFragment) fragment;
            if ("all".equals(target.getSiteKey())) target.appendRows(items);
        }
    }

    private void syncPager() {
        if (mPageAdapter == null) return;
        mPageAdapter.submit(new ArrayList<>(mCollectMap.keySet()));
    }

    private void search(Site site) {
        if (isFinishing() || isDestroyed() || mExecutor == null) return;
        try {
            mViewModel.searchContent(site, getKeyword(), false, mSearchToken);
        } catch (Throwable e) {
            ThreadPools.log(e, "Collect search failed for " + site.getName());
        }
    }

    private void stop() {
        App.removeCallbacks(mFlushResults);
        mPendingResults.clear();
        mFlushScheduled = false;
        mAllQueue = null;
        if (mViewModel != null) mViewModel.cancelSearch(mSearchToken);
        for (Future<?> task : mSearchTasks) task.cancel(true);
        mSearchTasks.clear();
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
            int position = mBinding.recycler.getSelectedPosition();
            if (position < 0 || position >= mPageAdapter.getCount()) return;
            mBinding.pager.setCurrentItem(position);
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
        stop();
        super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        App.removeCallbacks(mRunnable);
        super.onDestroy();
        stop();
    }

    class PageAdapter extends FragmentStatePagerAdapter {

        private final List<String> mKeys = new ArrayList<>();

        public PageAdapter(@NonNull FragmentManager fm) {
            super(fm, BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT);
        }

        public void submit(List<String> keys) {
            if (mKeys.equals(keys)) return;
            mKeys.clear();
            mKeys.addAll(keys);
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public Fragment getItem(int position) {
            return CollectFragment.newInstance(getKeyword(), mKeys.get(position));
        }

        @Override
        public int getItemPosition(@NonNull Object object) {
            if (!(object instanceof CollectFragment)) return POSITION_NONE;
            String key = ((CollectFragment) object).getSiteKey();
            int index = mKeys.indexOf(key);
            return index == -1 ? POSITION_NONE : index;
        }

        @Override
        public int getCount() {
            return mKeys.size();
        }
    }
}
