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
import com.fongmi.android.tv.utils.ThreadPools;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class CollectActivity extends BaseActivity {

    private ActivityCollectBinding mBinding;
    private ArrayObjectAdapter mAdapter;
    private PageAdapter mPageAdapter;
    private SiteViewModel mViewModel;
    private PauseExecutor mExecutor;
    private List<Site> mSites;
    private final Set<String> mCollectKeys = new HashSet<>();
    private final Map<String, Collect> mCollectMap = new LinkedHashMap<>();
    private final Map<String, Set<String>> mVodKeys = new HashMap<>();
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
            if (!result.getKeyword().equals(getKeyword().trim())) return;
            updateCollects(result.getList());
        });
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
        mVodKeys.clear();
        Collect all = Collect.all();
        mCollectKeys.clear();
        mCollectKeys.add(all.getSite().getKey());
        mCollectMap.put(all.getSite().getKey(), all);
        mVodKeys.put(all.getSite().getKey(), new HashSet<>());
        mAdapter.add(all);
        syncPager();
        mBinding.recycler.setSelectedPosition(0);
        mBinding.pager.setCurrentItem(0, false);
        mExecutor = new PauseExecutor(Math.max(2, Math.min(6, Constant.THREAD_POOL)));
        mBinding.result.setText(getString(R.string.collect_result, getKeyword()));
        for (Site site : mSites) mExecutor.execute(() -> search(site));
    }

    private void addCollect(List<Vod> items) {
        if (items.isEmpty()) return;
        Collect collect = Collect.create(items);
        if (!mCollectKeys.add(collect.getSite().getKey())) return;
        mCollectMap.put(collect.getSite().getKey(), collect);
        mAdapter.add(collect);
        syncPager();
    }

    private void updateCollects(List<Vod> items) {
        if (isFinishing() || isDestroyed()) return;
        if (items.isEmpty()) return;
        List<Vod> added = filterNewItems(items.get(0).getSiteKey(), items);
        if (added.isEmpty()) return;
        appendCollect(Collect.all().getSite().getKey(), added);
        String key = added.get(0).getSiteKey();
        if (getCollect(key) == null) addCollect(added);
        else appendCollect(key, added);
        syncFragments(added);
    }

    private void appendCollect(String key, List<Vod> items) {
        Collect collect = getCollect(key);
        if (collect != null && !items.isEmpty()) collect.getList().addAll(items);
    }

    public Collect getCollect(String key) {
        return key == null ? null : mCollectMap.get(key);
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
        if (isFinishing() || isDestroyed()) return;
        if (items.isEmpty()) return;
        List<Vod> added = filterNewItems(Collect.all().getSite().getKey(), items);
        if (added.isEmpty()) return;
        appendCollect(Collect.all().getSite().getKey(), added);
        for (Fragment fragment : getSupportFragmentManager().getFragments()) {
            if (!(fragment instanceof CollectFragment)) continue;
            CollectFragment target = (CollectFragment) fragment;
            if ("all".equals(target.getSiteKey())) target.appendRows(added);
        }
    }

    private List<Vod> filterNewItems(String key, List<Vod> items) {
        Set<String> values = mVodKeys.computeIfAbsent(key, k -> new HashSet<>());
        List<Vod> results = new ArrayList<>();
        for (Vod item : items) if (values.add(getVodKey(item))) results.add(item);
        return results;
    }

    private String getVodKey(Vod item) {
        String id = item.getVodId();
        if (!id.isEmpty()) return item.getSiteKey() + "@" + id;
        return item.getSiteKey() + "@" + item.getVodName() + "@" + item.getVodPic() + "@" + item.getVodRemarks();
    }

    private void syncPager() {
        if (mPageAdapter == null) return;
        mPageAdapter.submit(new ArrayList<>(mCollectMap.keySet()));
    }

    private void search(Site site) {
        if (isFinishing() || isDestroyed() || mExecutor == null) return;
        try {
            mViewModel.searchContent(site, getKeyword(), false);
        } catch (Throwable e) {
            ThreadPools.log(e, "Collect search failed for " + site.getName());
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
