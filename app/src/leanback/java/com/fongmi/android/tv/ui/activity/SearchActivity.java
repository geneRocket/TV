package com.fongmi.android.tv.ui.activity;

import android.app.Activity;
import android.content.Intent;
import android.text.Editable;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;

import androidx.annotation.NonNull;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.Setting;
import com.fongmi.android.tv.bean.Hot;
import com.fongmi.android.tv.bean.Site;
import com.fongmi.android.tv.bean.Suggest;
import com.fongmi.android.tv.bean.SuggestTwo;
import com.fongmi.android.tv.databinding.ActivitySearchBinding;
import com.fongmi.android.tv.impl.Callback;
import com.fongmi.android.tv.impl.SiteCallback;
import com.fongmi.android.tv.ui.adapter.RecordAdapter;
import com.fongmi.android.tv.ui.adapter.WordAdapter;
import com.fongmi.android.tv.ui.base.BaseActivity;
import com.fongmi.android.tv.ui.custom.CustomKeyboard;
import com.fongmi.android.tv.ui.custom.CustomTextListener;
import com.fongmi.android.tv.ui.custom.SpaceItemDecoration;
import com.fongmi.android.tv.ui.dialog.SiteDialog;
import com.fongmi.android.tv.utils.KeyUtil;
import com.fongmi.android.tv.utils.Util;
import com.github.catvod.net.OkHttp;
import com.github.catvod.utils.Trans;
import com.google.common.net.HttpHeaders;

import java.io.IOException;
import java.net.URLEncoder;
import java.util.Collections;
import java.util.List;

import okhttp3.Call;
import okhttp3.Headers;
import okhttp3.Response;

public class SearchActivity extends BaseActivity implements WordAdapter.OnClickListener, RecordAdapter.OnClickListener, CustomKeyboard.Callback, SiteCallback {

    private static final String EXTRA_KEYWORD = "keyword";
    private static final String EXTRA_AUTO = "auto";

    private ActivitySearchBinding mBinding;
    private RecordAdapter mRecordAdapter;
    private WordAdapter mWordAdapter;
    private boolean mAutoSearched;
    private Call mHotCall;
    private Call mSuggestOneCall;
    private Call mSuggestTwoCall;
    private String mSuggestKeyword;
    private final Runnable mSuggestTask = new Runnable() {
        @Override
        public void run() {
            if (TextUtils.isEmpty(mSuggestKeyword)) return;
            getSuggest(mSuggestKeyword);
        }
    };

    public static void start(Activity activity) {
        activity.startActivity(new Intent(activity, SearchActivity.class));
    }

    public static void start(Activity activity, String keyword, boolean autoSearch) {
        Intent intent = new Intent(activity, SearchActivity.class);
        intent.putExtra(EXTRA_KEYWORD, keyword);
        intent.putExtra(EXTRA_AUTO, autoSearch);
        activity.startActivity(intent);
    }

    @Override
    protected ViewBinding getBinding() {
        return mBinding = ActivitySearchBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void initView() {
        CustomKeyboard.init(this, mBinding);
        setRecyclerView();
        getHot();
    }

    @Override
    protected void initEvent() {
        mBinding.keyword.setOnEditorActionListener((textView, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) onSearch();
            return true;
        });
        mBinding.keyword.addTextChangedListener(new CustomTextListener() {
            @Override
            public void afterTextChanged(Editable s) {
                String keyword = s.toString().trim();
                if (keyword.isEmpty()) {
                    cancelSuggestTask();
                    getHot();
                } else {
                    cancelHotRequest();
                    cancelSuggestTask();
                    cancelSuggestRequest();
                    mSuggestKeyword = keyword;
                    App.post(mSuggestTask, 250);
                }
            }
        });
        mBinding.mic.setListener(this, new CustomTextListener() {
            @Override
            public void onEndOfSpeech() {
                mBinding.keyword.requestFocus();
                mBinding.mic.stop();
            }

            @Override
            public void onResults(String result) {
                mBinding.keyword.setText(result);
                mBinding.keyword.setSelection(mBinding.keyword.length());
            }
        });
        initKeyword();
    }

    private void initKeyword() {
        String keyword = getIntent().getStringExtra(EXTRA_KEYWORD);
        boolean auto = getIntent().getBooleanExtra(EXTRA_AUTO, false);
        if (TextUtils.isEmpty(keyword)) return;
        mBinding.keyword.setText(keyword);
        mBinding.keyword.setSelection(mBinding.keyword.length());
        if (auto && !mAutoSearched) {
            mAutoSearched = true;
            App.post(this::onSearch, 200);
        }
    }

    private void setRecyclerView() {
        mBinding.wordRecycler.setHasFixedSize(true);
        mBinding.wordRecycler.addItemDecoration(new SpaceItemDecoration(1, 16));
        mBinding.wordRecycler.setAdapter(mWordAdapter = new WordAdapter(this));
        mBinding.recordRecycler.setHasFixedSize(true);
        mBinding.recordRecycler.addItemDecoration(new SpaceItemDecoration(1, 16));
        mBinding.recordRecycler.setAdapter(mRecordAdapter = new RecordAdapter(this));
    }

    private void getHot() {
        cancelHotRequest();
        cancelSuggestRequest();
        mBinding.hint.setText(R.string.search_hot);
        List<String> items = Hot.get(Setting.getHot());
        mWordAdapter.addAll(items);
        if (!items.isEmpty()) return;
        mHotCall = OkHttp.newCall("https://api.web.360kan.com/v1/rank?cat=1", Headers.of(HttpHeaders.REFERER, "https://www.360kan.com/rank/general"));
        mHotCall.enqueue(new Callback() {
            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (!isHotRequestValid(call)) return;
                List<String> remote = Hot.get(response.body().string());
                App.post(() -> {
                    if (!isHotRequestValid(call)) return;
                    mWordAdapter.addAll(remote);
                });
            }
        });
    }

    private void getSuggest(String text) {
        cancelSuggestRequest();
        mBinding.hint.setText(R.string.search_suggest);
        mWordAdapter.addAll(Collections.emptyList());
        mSuggestOneCall = OkHttp.newCall("https://tv.aiseet.atianqi.com/i-tvbin/qtv_video/search/get_search_smart_box?format=json&page_num=0&page_size=10&key=" + URLEncoder.encode(Trans.z2p(text)));
        mSuggestOneCall.enqueue(new Callback() {
            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (!isSuggestRequestValid(call, text)) return;
                List<String> items = SuggestTwo.get(response.body().string());
                App.post(() -> {
                    if (isSuggestRequestValid(call, text)) mWordAdapter.appendAll(items);
                });
            }
        });
        mSuggestTwoCall = OkHttp.newCall("https://suggest.video.iqiyi.com/?if=mobile&key=" + URLEncoder.encode(Trans.z2p(text)));
        mSuggestTwoCall.enqueue(new Callback() {
            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (!isSuggestRequestValid(call, text)) return;
                List<String> items = Suggest.get(response.body().string());
                App.post(() -> {
                    if (isSuggestRequestValid(call, text)) mWordAdapter.appendAll(items);
                });
            }
        });
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

    private boolean isSuggestRequestValid(Call call, String requestKeyword) {
        boolean currentCall = call == mSuggestOneCall || call == mSuggestTwoCall;
        return currentCall && requestKeyword.equals(mBinding.keyword.getText().toString().trim());
    }

    private boolean isHotRequestValid(Call call) {
        return call == mHotCall && TextUtils.isEmpty(mBinding.keyword.getText().toString().trim());
    }

    @Override
    public void onItemClick(String text) {
        mBinding.keyword.setText(text);
        onSearch();
    }

    @Override
    public void onDataChanged(int size) {
        mBinding.recordLayout.setVisibility(size == 0 ? View.GONE : View.VISIBLE);
    }

    @Override
    public void onSearch() {
        String keyword = mBinding.keyword.getText().toString().trim();
        mBinding.keyword.setSelection(mBinding.keyword.length());
        Util.hideKeyboard(mBinding.keyword);
        if (TextUtils.isEmpty(keyword)) return;
        cancelHotRequest();
        cancelSuggestTask();
        cancelSuggestRequest();
        CollectActivity.start(this, keyword);
        App.post(() -> mRecordAdapter.add(keyword), 250);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (KeyUtil.isMenuKey(event)) showDialog();
        return super.dispatchKeyEvent(event);
    }

    @Override
    public void showDialog() {
        SiteDialog.create(this).search().show();
    }

    @Override
    public void onRemote() {
        PushActivity.start(this, 1);
    }

    @Override
    public void setSite(Site item) {
    }

    @Override
    public void onChanged() {
    }

    @Override
    protected void onResume() {
        super.onResume();
        mBinding.keyword.requestFocus();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        cancelHotRequest();
        cancelSuggestTask();
        cancelSuggestRequest();
    }
}
