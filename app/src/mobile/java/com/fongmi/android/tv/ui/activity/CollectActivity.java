package com.fongmi.android.tv.ui.activity;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.view.View;
import android.view.inputmethod.EditorInfo;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.Product;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.Setting;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.bean.Collect;
import com.fongmi.android.tv.bean.Hot;
import com.fongmi.android.tv.bean.Result;
import com.fongmi.android.tv.bean.Site;
import com.fongmi.android.tv.bean.Suggest;
import com.fongmi.android.tv.bean.SuggestTwo;
import com.fongmi.android.tv.bean.Vod;
import com.fongmi.android.tv.databinding.ActivityCollectBinding;
import com.fongmi.android.tv.impl.Callback;
import com.fongmi.android.tv.impl.SiteCallback;
import com.fongmi.android.tv.model.SearchRequestToken;
import com.fongmi.android.tv.model.SiteViewModel;
import com.fongmi.android.tv.ui.adapter.CollectAdapter;
import com.fongmi.android.tv.ui.adapter.RecordAdapter;
import com.fongmi.android.tv.ui.adapter.SearchAdapter;
import com.fongmi.android.tv.ui.adapter.VodAdapter;
import com.fongmi.android.tv.ui.adapter.WordAdapter;
import com.fongmi.android.tv.ui.base.BaseActivity;
import com.fongmi.android.tv.ui.base.ViewType;
import com.fongmi.android.tv.ui.custom.CustomScroller;
import com.fongmi.android.tv.ui.custom.CustomTextListener;
import com.fongmi.android.tv.ui.dialog.SiteDialog;
import com.fongmi.android.tv.utils.PauseExecutor;
import com.fongmi.android.tv.utils.ThreadPools;
import com.fongmi.android.tv.utils.ResUtil;
import com.fongmi.android.tv.utils.Util;
import com.github.catvod.net.OkHttp;
import com.github.catvod.utils.Trans;
import com.google.android.flexbox.FlexDirection;
import com.google.android.flexbox.FlexboxLayoutManager;
import com.google.common.net.HttpHeaders;

import java.io.IOException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import okhttp3.Call;
import okhttp3.Headers;
import okhttp3.Response;

public class CollectActivity extends BaseActivity implements CustomScroller.Callback, SiteCallback, WordAdapter.OnClickListener, RecordAdapter.OnClickListener, CollectAdapter.OnClickListener, VodAdapter.OnClickListener {

    private static final long RESULT_FLUSH_DELAY = 500;
    private static final int SEARCH_HINT_TIMEOUT = 5000;

    private ActivityCollectBinding mBinding;
    private CollectAdapter mCollectAdapter;
    private SearchAdapter mSearchAdapter;
    private RecordAdapter mRecordAdapter;
    private WordAdapter mWordAdapter;
    private CustomScroller mScroller;
    private SiteViewModel mViewModel;
    private PauseExecutor mExecutor;
    private List<Site> mSites;
    private String mSearchToken;
    private String mPageToken;
    private String mSearchKeyword = "";
    private String mSuggestKeyword;
    private volatile Call mHotCall;
    private volatile Call mSuggestOneCall;
    private volatile Call mSuggestTwoCall;
    private int mPendingSearchCount;
    private boolean mFlushScheduled;
    private final List<Result> mPendingResults = new ArrayList<>();
    private final Runnable mAddRecord = () -> mRecordAdapter.add(mBinding.keyword.getText().toString().trim());
    private final Runnable mShowSite = () -> SiteDialog.create(this).search().show();
    private final Runnable mRequestRecordLayout = () -> mBinding.recordRecycler.requestLayout();
    private final Runnable mFlushResults = this::flushResults;
    private final Runnable mSuggestTask = () -> {
        if (!TextUtils.isEmpty(mSuggestKeyword)) getSuggest(mSuggestKeyword);
    };

    public static void start(Activity activity) {
        start(activity, "");
    }

    public static void start(Activity activity, String keyword) {
        Intent intent = new Intent(activity, CollectActivity.class);
        intent.putExtra("keyword", keyword);
        activity.startActivity(intent);
    }

    private String getKeyword() {
        return getIntent().getStringExtra("keyword");
    }

    private boolean empty() {
        return mBinding.keyword.getText().toString().trim().isEmpty();
    }

    @Override
    protected ViewBinding getBinding() {
        return mBinding = ActivityCollectBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void initView(Bundle savedInstanceState) {
        mScroller = new CustomScroller(this);
        mSites = new ArrayList<>();
        setRecyclerView();
        setViewModel();
        checkKeyword();
        setViewType();
        setSite();
        getHot();
        search();
    }

    @Override
    protected void initEvent() {
        mBinding.site.setOnClickListener(this::onSite);
        mBinding.view.setOnClickListener(this::toggleView);
        mBinding.keyword.setOnEditorActionListener((textView, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) search();
            return true;
        });
        mBinding.keyword.addTextChangedListener(new CustomTextListener() {
            @Override
            public void afterTextChanged(Editable s) {
                String keyword = s.toString().trim();
                cancelSuggestTask();
                if (keyword.isEmpty()) {
                    getHot();
                } else {
                    cancelHotRequest();
                    cancelSuggestRequest();
                    mSuggestKeyword = keyword;
                    App.post(mSuggestTask, 250);
                }
            }
        });
    }

    private void setRecyclerView() {
        mBinding.collect.setHasFixedSize(true);
        mBinding.collect.setItemAnimator(null);
        mBinding.collect.setAdapter(mCollectAdapter = new CollectAdapter(this));
        mBinding.recycler.setHasFixedSize(true);
        mBinding.recycler.setItemViewCacheSize(20);
        mBinding.recycler.addOnScrollListener(mScroller);
        mBinding.recycler.setAdapter(mSearchAdapter = new SearchAdapter(this));
        mBinding.wordRecycler.setHasFixedSize(false);
        mBinding.wordRecycler.setAdapter(mWordAdapter = new WordAdapter(this));
        mBinding.wordRecycler.setLayoutManager(new FlexboxLayoutManager(this, FlexDirection.ROW));
        mBinding.recordRecycler.setHasFixedSize(false);
        mBinding.recordRecycler.setAdapter(mRecordAdapter = new RecordAdapter(this));
        mBinding.recordRecycler.setLayoutManager(new FlexboxLayoutManager(this, FlexDirection.ROW));
    }

    private void setViewType() {
        setViewType(Setting.getViewType(ViewType.GRID));
    }

    private void setViewType(int viewType) {
        int count = Product.getColumn(this) - 1;
        mSearchAdapter.setViewType(viewType, count);
        mSearchAdapter.setSize(Product.getSpec(this, ResUtil.dp2px(128 + (count) * 16), count));
        ((GridLayoutManager) mBinding.recycler.getLayoutManager()).setSpanCount(mSearchAdapter.isGrid() ? count : 1);
        mBinding.view.setImageResource(mSearchAdapter.isGrid() ? R.drawable.ic_action_list : R.drawable.ic_action_grid);
    }

    private void setViewModel() {
        mViewModel = new ViewModelProvider(this).get(SiteViewModel.class);
        mViewModel.search().observe(this, result -> {
            if (!isCurrentSearchResult(result)) return;
            enqueueResult(result);
        });
        mViewModel.result().observe(this, result -> {
            if (result == null || mPageToken == null || mCollectAdapter.getItemCount() == 0
                    || !TextUtils.equals(mPageToken, result.getRequestToken())
                    || !TextUtils.equals(mSearchKeyword, result.getKeyword())) return;
            Collect activated = mCollectAdapter.getActivated();
            if (!TextUtils.equals(activated.getSite().getKey(), result.getKey())) return;
            mPageToken = null;
            activated.getList().addAll(result.getList());
            activated.setPageCount(result.getPageCount());
            mSearchAdapter.addAll(result.getList());
            mScroller.endLoading(result);
            activated.setPage(mScroller.getPage());
        });
    }

    private boolean isCurrentSearchResult(Result result) {
        return result != null
                && TextUtils.equals(result.getKeyword(), mBinding.keyword.getText().toString().trim())
                && TextUtils.equals(result.getRequestToken(), mSearchToken);
    }

    private void checkKeyword() {
        if (TextUtils.isEmpty(getKeyword())) mBinding.keyword.requestFocus();
        else setKeyword(getKeyword());
    }

    private void setKeyword(String text) {
        mBinding.keyword.setText(text);
        mBinding.keyword.setSelection(text.length());
    }

    private void setSite() {
        for (Site site : VodConfig.get().getSites()) if (site.isSearchable()) mSites.add(site);
        Site home = VodConfig.get().getHome();
        if (!mSites.contains(home)) return;
        mSites.remove(home);
        mSites.add(0, home);
    }

    private void search() {
        if (empty()) return;
        App.removeCallbacks(mAddRecord);
        App.removeCallbacks(mFlushResults);
        mFlushScheduled = false;
        mPendingResults.clear();
        mSearchAdapter.clear();
        mCollectAdapter.clear();
        Util.hideKeyboard(mBinding.keyword);
        mBinding.site.setVisibility(View.GONE);
        mBinding.agent.setVisibility(View.GONE);
        mBinding.view.setVisibility(View.VISIBLE);
        mBinding.result.setVisibility(View.VISIBLE);
        stopSearch();
        mExecutor = new PauseExecutor(Math.min(6, ThreadPools.searchConcurrency()), Math.max(1, mSites.size()));
        String keyword = mBinding.keyword.getText().toString().trim();
        mSearchKeyword = keyword;
        mScroller.reset();
        mSearchAdapter.setKeyword(keyword);
        mSearchToken = SearchRequestToken.next("collect");
        mPendingSearchCount = mSites.size();
        mBinding.result.setEmptyText(R.string.error_empty);
        mBinding.result.showProgress();
        String token = mSearchToken;
        for (Site site : mSites) mExecutor.execute(() -> search(site, keyword, token));
        if (mPendingSearchCount == 0) mBinding.result.showEmpty();
        App.post(mAddRecord, 250);
    }

    private void search(Site site, String keyword, String token) {
        try {
            mViewModel.searchContent(site, keyword, site.isQuickSearch(), token);
        } catch (Throwable ignored) {
        } finally {
            App.post(() -> onSearchFinished(token));
        }
    }

    private void onSearchFinished(String token) {
        if (!TextUtils.equals(token, mSearchToken) || --mPendingSearchCount > 0) return;
        App.removeCallbacks(mFlushResults);
        flushResults();
        if (mSearchAdapter.getItemCount() == 0) mBinding.result.showEmpty();
        else mBinding.result.showContent();
    }

    private void enqueueResult(Result result) {
        mPendingResults.add(result);
        if (mFlushScheduled) return;
        mFlushScheduled = true;
        App.post(mFlushResults, RESULT_FLUSH_DELAY);
    }

    private void flushResults() {
        mFlushScheduled = false;
        if (isFinishing() || isDestroyed() || mPendingResults.isEmpty()) return;
        List<Result> results = new ArrayList<>(mPendingResults);
        List<Vod> allItems = new ArrayList<>();
        boolean allSelected = mCollectAdapter.getPosition() == 0;
        mPendingResults.clear();
        for (Result result : results) {
            if (result.getList().isEmpty()) continue;
            if (allSelected) allItems.addAll(result.getList());
            Collect collect = Collect.create(result.getList());
            collect.setPageCount(result.getPageCount());
            mCollectAdapter.add(collect);
            mCollectAdapter.add(result.getList());
        }
        if (allSelected && !allItems.isEmpty()) mSearchAdapter.addAll(allItems);
        if (mSearchAdapter.getItemCount() > 0) mBinding.result.showContent();
    }

    private void getHot() {
        cancelHotRequest();
        cancelSuggestRequest();
        mBinding.word.setText(R.string.search_hot);
        List<String> items = Hot.get(Setting.getHot());
        mWordAdapter.addAll(items);
        if (!items.isEmpty()) return;
        mHotCall = OkHttp.client(SEARCH_HINT_TIMEOUT).newCall(new okhttp3.Request.Builder().url("https://api.web.360kan.com/v1/rank?cat=1").headers(Headers.of(HttpHeaders.REFERER, "https://www.360kan.com/rank/general")).build());
        mHotCall.enqueue(new Callback() {
            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                try (Response res = response) {
                    if (!isHotRequestCurrent(call) || res.body() == null) return;
                    List<String> remote = Hot.get(res.body().string());
                    App.post(() -> {
                        if (isHotRequestValid(call) && !isFinishing() && !isDestroyed()) mWordAdapter.addAll(remote);
                    });
                }
            }
        });
    }

    private void getSuggest(String text) {
        cancelSuggestRequest();
        mBinding.word.setText(R.string.search_suggest);
        mWordAdapter.addAll(Collections.emptyList());
        mSuggestOneCall = OkHttp.newCall(OkHttp.client(SEARCH_HINT_TIMEOUT), "https://tv.aiseet.atianqi.com/i-tvbin/qtv_video/search/get_search_smart_box?format=json&page_num=0&page_size=20&key=" + encodeKeyword(text));
        mSuggestOneCall.enqueue(new Callback() {
            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                try (Response res = response) {
                    if (!isSuggestRequestCurrent(call) || res.body() == null) return;
                    List<String> items = SuggestTwo.get(res.body().string());
                    App.post(() -> {
                        if (isSuggestRequestValid(call, text) && !isFinishing() && !isDestroyed()) mWordAdapter.appendAll(items);
                    });
                }
            }
        });
        mSuggestTwoCall = OkHttp.newCall(OkHttp.client(SEARCH_HINT_TIMEOUT), "https://suggest.video.iqiyi.com/?if=mobile&key=" + encodeKeyword(text));
        mSuggestTwoCall.enqueue(new Callback() {
            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                try (Response res = response) {
                    if (!isSuggestRequestCurrent(call) || res.body() == null) return;
                    List<String> items = Suggest.get(res.body().string());
                    App.post(() -> {
                        if (isSuggestRequestValid(call, text) && !isFinishing() && !isDestroyed()) mWordAdapter.appendAll(items);
                    });
                }
            }
        });
    }

    private String encodeKeyword(String text) {
        try {
            return URLEncoder.encode(Trans.z2p(text), "UTF-8");
        } catch (Exception e) {
            return "";
        }
    }

    private boolean isSuggestRequestValid(Call call, String keyword) {
        return isSuggestRequestCurrent(call) && keyword.equals(mBinding.keyword.getText().toString().trim());
    }

    private boolean isSuggestRequestCurrent(Call call) {
        return call == mSuggestOneCall || call == mSuggestTwoCall;
    }

    private boolean isHotRequestValid(Call call) {
        return isHotRequestCurrent(call) && empty();
    }

    private boolean isHotRequestCurrent(Call call) {
        return call == mHotCall;
    }

    private void cancelSuggestTask() {
        App.removeCallbacks(mSuggestTask);
    }

    private void cancelSuggestRequest() {
        if (mSuggestOneCall != null) mSuggestOneCall.cancel();
        if (mSuggestTwoCall != null) mSuggestTwoCall.cancel();
        mSuggestOneCall = null;
        mSuggestTwoCall = null;
    }

    private void cancelHotRequest() {
        if (mHotCall != null) mHotCall.cancel();
        mHotCall = null;
    }

    private void onSite(View view) {
        Util.hideKeyboard(mBinding.keyword);
        App.removeCallbacks(mShowSite);
        App.post(mShowSite, 50);
    }

    private void toggleView(View view) {
        setViewType(mSearchAdapter.isGrid() ? ViewType.LIST : ViewType.GRID);
    }

    private void showAgent() {
        mScroller.reset();
        mSearchAdapter.clear();
        mCollectAdapter.clear();
        mBinding.view.setVisibility(View.GONE);
        mBinding.result.setVisibility(View.GONE);
        mBinding.site.setVisibility(View.VISIBLE);
        mBinding.agent.setVisibility(View.VISIBLE);
        stopSearch();
    }

    private void cancelPageSearch() {
        if (mViewModel != null) mViewModel.cancelSearchPage(mPageToken);
        mPageToken = null;
        if (mScroller != null) mScroller.setLoading(false);
    }

    private void stopSearch() {
        cancelPageSearch();
        App.removeCallbacks(mFlushResults);
        mFlushScheduled = false;
        mPendingResults.clear();
        if (mViewModel != null) mViewModel.cancelSearch(mSearchToken);
        if (mExecutor != null) mExecutor.shutdownNow();
        mExecutor = null;
        mSearchToken = null;
        mPendingSearchCount = 0;
    }

    @Override
    public void setSite(Site item) {
    }

    @Override
    public void onChanged() {
        mSites.clear();
        setSite();
    }

    @Override
    public void onItemClick(String text) {
        setKeyword(text);
        search();
    }

    @Override
    public void onDataChanged(int size) {
        mBinding.record.setVisibility(size == 0 ? View.GONE : View.VISIBLE);
        mBinding.recordRecycler.setVisibility(size == 0 ? View.GONE : View.VISIBLE);
        App.removeCallbacks(mRequestRecordLayout);
        App.post(mRequestRecordLayout, 250);
    }

    @Override
    public void onItemClick(int position, Collect item) {
        cancelPageSearch();
        mBinding.recycler.scrollToPosition(0);
        mCollectAdapter.setActivated(position);
        mSearchAdapter.setAll(item.getList());
        mScroller.setPage(item.getPage());
        mScroller.setEnable(item.getPageCount());
    }

    @Override
    public void onItemClick(Vod item) {
        if (item.isFolder()) FolderActivity.start(this, item.getSiteKey(), Result.folder(item));
        else VideoActivity.collect(this, item.getSiteKey(), item.getVodId(), item.getVodName(), item.getVodPic());
    }

    @Override
    public boolean onLongClick(Vod item) {
        return false;
    }

    @Override
    public void onLoadMore(String page) {
        Collect activated = mCollectAdapter.getActivated();
        if ("all".equals(activated.getSite().getKey())) return;
        mPageToken = SearchRequestToken.next("collect-page");
        mViewModel.searchContent(activated.getSite(), mSearchKeyword, page, mPageToken);
        mScroller.setLoading(true);
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
    protected void onDestroy() {
        super.onDestroy();
        App.removeCallbacks(mAddRecord);
        App.removeCallbacks(mShowSite);
        App.removeCallbacks(mRequestRecordLayout);
        App.removeCallbacks(mFlushResults);
        cancelHotRequest();
        cancelSuggestTask();
        cancelSuggestRequest();
        stopSearch();
    }

    @Override
    public void onBackPressed() {
        if (isVisible(mBinding.result)) {
            showAgent();
        } else {
            super.onBackPressed();
        }
    }
}
