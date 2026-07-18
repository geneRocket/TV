package com.fongmi.android.tv.ui.fragment;

import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.leanback.widget.ArrayObjectAdapter;
import androidx.leanback.widget.DiffCallback;
import androidx.leanback.widget.ItemBridgeAdapter;
import androidx.leanback.widget.ListRow;
import androidx.leanback.widget.OnChildViewHolderSelectedListener;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.Product;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.Setting;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.bean.Button;
import com.fongmi.android.tv.bean.Func;
import com.fongmi.android.tv.bean.History;
import com.fongmi.android.tv.bean.Keep;
import com.fongmi.android.tv.bean.Result;
import com.fongmi.android.tv.bean.Site;
import com.fongmi.android.tv.bean.Style;
import com.fongmi.android.tv.bean.Vod;
import com.fongmi.android.tv.databinding.FragmentHomeBinding;
import com.fongmi.android.tv.event.RefreshEvent;
import com.fongmi.android.tv.ui.activity.CollectActivity;
import com.fongmi.android.tv.ui.activity.HistoryActivity;
import com.fongmi.android.tv.ui.activity.HomeActivity;
import com.fongmi.android.tv.ui.activity.KeepActivity;
import com.fongmi.android.tv.ui.activity.LiveActivity;
import com.fongmi.android.tv.ui.activity.PushActivity;
import com.fongmi.android.tv.ui.activity.SearchActivity;
import com.fongmi.android.tv.ui.activity.SettingActivity;
import com.fongmi.android.tv.ui.activity.VideoActivity;
import com.fongmi.android.tv.ui.activity.VodActivity;
import com.fongmi.android.tv.ui.base.BaseFragment;
import com.fongmi.android.tv.ui.custom.CustomRowPresenter;
import com.fongmi.android.tv.ui.custom.CustomSelector;
import com.fongmi.android.tv.ui.presenter.FuncPresenter;
import com.fongmi.android.tv.ui.presenter.HeaderPresenter;
import com.fongmi.android.tv.ui.presenter.HistoryPresenter;
import com.fongmi.android.tv.ui.presenter.KeepPresenter;
import com.fongmi.android.tv.ui.presenter.ProgressPresenter;
import com.fongmi.android.tv.ui.presenter.VodPresenter;
import com.fongmi.android.tv.utils.ResUtil;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.common.collect.Lists;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class HomeFragment extends BaseFragment implements VodPresenter.OnClickListener, FuncPresenter.OnClickListener, HistoryPresenter.OnClickListener, KeepPresenter.OnClickListener {

    private static final DiffCallback<Vod> VOD_DIFF = new DiffCallback<Vod>() {
        @Override
        public boolean areItemsTheSame(@NonNull Vod oldItem, @NonNull Vod newItem) {
            String oldId = oldItem.getVodId();
            String newId = newItem.getVodId();
            if (!oldId.isEmpty() || !newId.isEmpty()) return oldItem.getSiteKey().equals(newItem.getSiteKey()) && oldId.equals(newId);
            return oldItem.getVodName().equals(newItem.getVodName()) && oldItem.getVodPic().equals(newItem.getVodPic());
        }

        @Override
        public boolean areContentsTheSame(@NonNull Vod oldItem, @NonNull Vod newItem) {
            return oldItem.equals(newItem)
                    && oldItem.getVodRemarks().equals(newItem.getVodRemarks())
                    && oldItem.getVodYear().equals(newItem.getVodYear())
                    && oldItem.getSiteName().equals(newItem.getSiteName());
        }
    };

    public FragmentHomeBinding mBinding;

    private Map<String, VodPresenter> mPresenters;
    private ArrayObjectAdapter mHistoryAdapter;
    private ArrayObjectAdapter mKeepAdapter;
    public HistoryPresenter mPresenter;
    public KeepPresenter mKeepPresenter;
    private ArrayObjectAdapter mAdapter;
    public boolean inited;
    private int homeUI;
    private String button;
    private int mHistoryRequestId;
    private int mKeepRequestId;
    private int mOpenRequestId;
    private String mRecommendStyleKey;
    private boolean mHasFuncRow;
    private Runnable mRetryAction;
    private boolean mHomeLoadFailed;
    private int mHomeLoadMessage = R.string.vod_load_failed;

    private Site getHome() {
        return VodConfig.get().getHome();
    }

    @Override
    protected ViewBinding getBinding(@NonNull LayoutInflater inflater, @Nullable ViewGroup container) {
        return mBinding = FragmentHomeBinding.inflate(inflater, container, false);
    }

    @Override
    protected void initView() {
        mPresenters = new HashMap<>();
        mBinding.progressLayout.showProgress();
        setRecyclerView();
        setAdapter();
        inited = true;
        applyHomeLoadState();
    }

    @Override
    protected void initData() {
        getHistory();
        getKeep();
    }

    protected void initEvent() {
        mBinding.empty.retry.setOnClickListener(view -> {
            if (mRetryAction != null) mRetryAction.run();
            else getHomeActicity().homeContent();
        });
        mBinding.recycler.addOnChildViewHolderSelectedListener(new OnChildViewHolderSelectedListener() {
            @Override
            public void onChildViewHolderSelected(@NonNull RecyclerView parent, @Nullable RecyclerView.ViewHolder child, int position, int subposition) {
                if (position < 4) getHomeActicity().showToolBar();
                else getHomeActicity().hideToolBar();
                if (mPresenter != null && mPresenter.isDelete()) setHistoryDelete(false);
                if (mKeepPresenter != null && mKeepPresenter.isDelete()) setKeepDelete(false);
            }
        });
    }

    private HomeActivity getHomeActicity() {
        return (HomeActivity) getActivity();
    }

    private void setRecyclerView() {
        CustomSelector selector = new CustomSelector();
        selector.addPresenter(Integer.class, new HeaderPresenter());
        selector.addPresenter(String.class, new ProgressPresenter());
        selector.addPresenter(ListRow.class, new CustomRowPresenter(16), VodPresenter.class);
        selector.addPresenter(ListRow.class, new CustomRowPresenter(22), FuncPresenter.class);
        selector.addPresenter(ListRow.class, new CustomRowPresenter(16), HistoryPresenter.class);
        selector.addPresenter(ListRow.class, new CustomRowPresenter(16), KeepPresenter.class);
        mBinding.recycler.setHasFixedSize(true);
        mBinding.recycler.setItemViewCacheSize(20);
        mBinding.recycler.setItemAnimator(null);
        mBinding.recycler.setAdapter(new ItemBridgeAdapter(mAdapter = new ArrayObjectAdapter(selector)));
        mBinding.recycler.setVerticalSpacing(ResUtil.dp2px(16));
    }

    private void setAdapter() {
        ListRow funcRow = getFuncRow();
        mHasFuncRow = funcRow != null;
        if (mHasFuncRow) mAdapter.add(funcRow);
        if (Setting.isHomeHistory()) mAdapter.add(R.string.home_history);
        mAdapter.add(R.string.home_keep);
        mAdapter.add(R.string.home_recommend);
        mHistoryAdapter = new ArrayObjectAdapter(mPresenter = new HistoryPresenter(this));
        mKeepAdapter = new ArrayObjectAdapter(mKeepPresenter = new KeepPresenter(this));
        homeUI = Setting.getHomeUI();
        button = Setting.getHomeButtons(Button.getDefaultButtons());
        setTitleNextFocus(funcRow);
    }

    private VodPresenter getPresenter(Style style) {
        String key = getStyleKey(style);
        if (!mPresenters.containsKey(key)) mPresenters.put(key, new VodPresenter(this, style));
        return mPresenters.get(key);
    }

    public void addVideo(Result result) {
        int index = getRecommendIndex();
        if (index < 0) return;
        Style style = result.getStyle(getHome().getStyle());
        List<List<Vod>> rows = Lists.partition(result.getList(), Product.getColumn(style));
        String styleKey = getStyleKey(style);
        if (!styleKey.equals(mRecommendStyleKey) || !hasRecommendRows(index)) {
            if (mAdapter.size() > index) mAdapter.removeItems(index, mAdapter.size() - index);
            mRecommendStyleKey = styleKey;
        }
        for (int i = 0; i < rows.size(); i++) {
            int position = index + i;
            if (position < mAdapter.size()) {
                ArrayObjectAdapter adapter = (ArrayObjectAdapter) ((ListRow) mAdapter.get(position)).getAdapter();
                adapter.setItems(rows.get(i), VOD_DIFF);
            } else {
                ArrayObjectAdapter adapter = new ArrayObjectAdapter(getPresenter(style));
                adapter.setItems(rows.get(i), VOD_DIFF);
                mAdapter.add(new ListRow(adapter));
            }
        }
        int end = index + rows.size();
        if (mAdapter.size() > end) mAdapter.removeItems(end, mAdapter.size() - end);
    }

    public void setHomeLoadFailed(boolean failed) {
        setHomeLoadFailed(failed, R.string.vod_load_failed, null);
    }

    public void setHomeLoadFailed(boolean failed, int message, Runnable retryAction) {
        mHomeLoadFailed = failed;
        mHomeLoadMessage = message;
        mRetryAction = failed ? retryAction : null;
        applyHomeLoadState();
    }

    private void applyHomeLoadState() {
        if (!inited || !isViewReady()) return;
        mBinding.empty.getRoot().setVisibility(mHomeLoadFailed ? View.VISIBLE : View.GONE);
        if (mHomeLoadFailed) {
            mBinding.empty.message.setText(mHomeLoadMessage);
            App.post(() -> {
                if (isViewReady() && mBinding.getRoot().isShown()) mBinding.empty.retry.requestFocus();
            });
        }
    }

    private String getStyleKey(Style style) {
        return style.getViewType() + "_" + style.getRatio();
    }

    private boolean hasRecommendRows(int index) {
        for (int i = index; i < mAdapter.size(); i++) if (!(mAdapter.get(i) instanceof ListRow)) return false;
        return true;
    }

    private ListRow getFuncRow() {
        List<Button> buttonList = Button.getButtons();
        if (buttonList.isEmpty()) return null;
        ArrayObjectAdapter adapter = new ArrayObjectAdapter(new FuncPresenter(this));
        for(int i=0; i<buttonList.size(); i++) {
            adapter.add(Func.create(buttonList.get(i).getResId()));
        }
        if (adapter.size() > 1) {
            ((Func) adapter.get(0)).setNextFocusLeft(((Func) adapter.get(adapter.size() - 1)).getId());
            ((Func) adapter.get(adapter.size() - 1)).setNextFocusRight(((Func) adapter.get(0)).getId());
        }
        return new ListRow(adapter);
    }

    private void setTitleNextFocus(ListRow funcRow) {
        int downId = -1;
        if (funcRow != null) {
            Func func = (Func) funcRow.getAdapter().get(0);
            downId = getHomeActicity().mBinding.recycler.getVisibility() == View.VISIBLE ? -1 : func.getId();
        }
        getHomeActicity().mBinding.title.setNextFocusDownId(downId);
    }

    private void refreshFuncRow() {
        if (homeUI == Setting.getHomeUI() && Setting.getHomeButtons(Button.getDefaultButtons()).equals(button)) return;
        if (mHasFuncRow) mAdapter.removeItems(0, 1);
        homeUI = Setting.getHomeUI();
        button = Setting.getHomeButtons(Button.getDefaultButtons());
        ListRow funcRow = getFuncRow();
        mHasFuncRow = funcRow != null;
        if (mHasFuncRow) mAdapter.add(0, funcRow);
        setTitleNextFocus(funcRow);
    }

    public void refreshRecommond() {
        int index = getRecommendIndex();
        if (index < 0 || index >= mAdapter.size()) return;
        mAdapter.notifyArrayItemRangeChanged(index, mAdapter.size() - index);
    }

    public void getHistory() {
        getHistory(false);
    }

    public void getHistory(boolean renew) {
        boolean enabled = Setting.isHomeHistory();
        if (!enabled) {
            mHistoryRequestId++;
            applyHistoryItems(renew, enabled, java.util.Collections.emptyList());
            return;
        }
        final int requestId = ++mHistoryRequestId;
        App.execute(() -> {
            List<History> items = History.getLoaded();
            App.post(() -> {
                if (!isViewReady() || requestId != mHistoryRequestId) return;
                boolean currentEnabled = Setting.isHomeHistory();
                applyHistoryItems(renew, currentEnabled, currentEnabled ? items : java.util.Collections.emptyList());
            });
        });
    }

    public void setHistoryDelete(boolean delete) {
        mPresenter.setDelete(delete);
        mHistoryAdapter.notifyArrayItemRangeChanged(0, mHistoryAdapter.size());
    }

    public void getKeep() {
        getKeep(false);
    }

    public void getKeep(boolean renew) {
        final int requestId = ++mKeepRequestId;
        App.execute(() -> {
            List<Keep> items = Keep.getVod();
            App.post(() -> {
                if (!isViewReady() || requestId != mKeepRequestId) return;
                applyKeepItems(renew, items);
            });
        });
    }

    private boolean isViewReady() {
        return isAdded() && getView() != null;
    }

    private boolean isActivityReady() {
        return isViewReady() && getActivity() != null && !getActivity().isFinishing() && !getActivity().isDestroyed();
    }

    private void openVodItem(int cid, String siteKey, String vodId, String vodName, String vodPic) {
        mOpenRequestId++;
        if (VodConfig.get().hasSite(siteKey)) {
            VideoActivity.start(getActivity(), siteKey, vodId, vodName, vodPic);
            return;
        }
        CollectActivity.start(getActivity(), vodName);
    }

    private void applyHistoryItems(boolean renew, boolean enabled, List<History> items) {
        if (!enabled) {
            removeHistorySection();
            return;
        }
        if (items.isEmpty()) {
            removeHistorySection();
            return;
        }
        int historyIndex = getHistoryIndex();
        if (historyIndex == -1) {
            int recommendIndex = getRecommendIndex();
            int historyStringIndex = recommendIndex - 1;
            historyStringIndex = historyStringIndex < 0 ? 0 : historyStringIndex;
            mAdapter.add(historyStringIndex, R.string.home_history);
        }
        historyIndex = getHistoryIndex();
        boolean exist = isHistoryRow(historyIndex);
        if (renew) {
            if (exist) mAdapter.removeItems(historyIndex, 1);
            mHistoryAdapter = new ArrayObjectAdapter(mPresenter = new HistoryPresenter(this));
            exist = false;
        }
        if (!exist) mAdapter.add(getHistoryIndex(), new ListRow(mHistoryAdapter));
        mHistoryAdapter.setItems(items, null);
    }

    private void applyKeepItems(boolean renew, List<Keep> items) {
        if (items.isEmpty()) {
            removeKeepSection();
            return;
        }
        int keepIndex = getKeepIndex();
        if (keepIndex == -1) {
            int recommendIndex = getRecommendIndex();
            int keepStringIndex = recommendIndex - 1;
            keepStringIndex = keepStringIndex < 0 ? 0 : keepStringIndex;
            mAdapter.add(keepStringIndex, R.string.home_keep);
        }
        keepIndex = getKeepIndex();
        boolean exist = isKeepRow(keepIndex);
        if (renew) {
            if (exist) mAdapter.removeItems(keepIndex, 1);
            mKeepAdapter = new ArrayObjectAdapter(mKeepPresenter = new KeepPresenter(this));
            exist = false;
        }
        if (!exist) mAdapter.add(getKeepIndex(), new ListRow(mKeepAdapter));
        mKeepAdapter.setItems(items, null);
    }

    public void setKeepDelete(boolean delete) {
        mKeepPresenter.setDelete(delete);
        mKeepAdapter.notifyArrayItemRangeChanged(0, mKeepAdapter.size());
    }

    private void clearHistory() {
        mHistoryRequestId++;
        removeHistorySection();
        History.deleteLoaded();
        mPresenter.setDelete(false);
        mHistoryAdapter.clear();
    }

    private void clearKeep() {
        mKeepRequestId++;
        removeKeepSection();
        Keep.deleteAll();
        mKeepPresenter.setDelete(false);
        mKeepAdapter.clear();
    }

    private int getHistoryIndex() {
        for (int i = 0; i < mAdapter.size(); i++) if (mAdapter.get(i).equals(R.string.home_history)) return i + 1;
        return -1;
    }

    private int getHistoryHeaderIndex() {
        return getHeaderIndex(R.string.home_history);
    }

    private int getKeepIndex() {
        for (int i = 0; i < mAdapter.size(); i++) if (mAdapter.get(i).equals(R.string.home_keep)) return i + 1;
        return -1;
    }

    private int getKeepHeaderIndex() {
        return getHeaderIndex(R.string.home_keep);
    }

    private int getRecommendIndex() {
        for (int i = 0; i < mAdapter.size(); i++) if (mAdapter.get(i).equals(R.string.home_recommend)) return i + 1;
        return -1;
    }

    private int getHeaderIndex(int resId) {
        for (int i = 0; i < mAdapter.size(); i++) if (mAdapter.get(i).equals(resId)) return i;
        return -1;
    }

    private void removeHistorySection() {
        int historyIndex = getHistoryIndex();
        if (isHistoryRow(historyIndex)) mAdapter.removeItems(historyIndex, 1);
        int headerIndex = getHistoryHeaderIndex();
        if (headerIndex >= 0) mAdapter.removeItems(headerIndex, 1);
        if (mPresenter != null) mPresenter.setDelete(false);
        if (mHistoryAdapter != null) mHistoryAdapter.clear();
    }

    private void removeKeepSection() {
        int keepIndex = getKeepIndex();
        if (isKeepRow(keepIndex)) mAdapter.removeItems(keepIndex, 1);
        int headerIndex = getKeepHeaderIndex();
        if (headerIndex >= 0) mAdapter.removeItems(headerIndex, 1);
        if (mKeepPresenter != null) mKeepPresenter.setDelete(false);
        if (mKeepAdapter != null) mKeepAdapter.clear();
    }

    private boolean isHistoryRow(int index) {
        return index >= 0 && index < mAdapter.size() && mAdapter.get(index) instanceof ListRow && ((ListRow) mAdapter.get(index)).getAdapter() == mHistoryAdapter;
    }

    private boolean isKeepRow(int index) {
        return index >= 0 && index < mAdapter.size() && mAdapter.get(index) instanceof ListRow && ((ListRow) mAdapter.get(index)).getAdapter() == mKeepAdapter;
    }

    @Override
    public void onItemClick(Func item) {
        int resId = item.getResId();
        if (resId == R.string.home_history_short) {
            HistoryActivity.start(getActivity());
        } else if (resId == R.string.home_vod) {
            Result result = getHomeActicity().mResult == null ? null : getHomeActicity().mResult.copyForVod();
            VodActivity.start(getActivity(), result);
        } else if (resId == R.string.home_live) {
            LiveActivity.start(getActivity());
        } else if (resId == R.string.home_search) {
            SearchActivity.start(getActivity());
        } else if (resId == R.string.home_keep) {
            KeepActivity.start(getActivity());
        } else if (resId == R.string.home_push) {
            PushActivity.start(getActivity());
        } else if (resId == R.string.home_setting) {
            SettingActivity.start(getActivity());
        }
    }

    @Override
    public void onItemClick(History item) {
        openVodItem(item.getCid(), item.getSiteKey(), item.getVodId(), item.getVodName(), item.getVodPic());
    }

    @Override
    public void onItemDelete(History item) {
        mHistoryRequestId++;
        mHistoryAdapter.remove(item.delete());
        if (mHistoryAdapter.size() > 0) return;
        removeHistorySection();
        mPresenter.setDelete(false);
    }

    @Override
    public boolean onLongClick() {
        if (mPresenter.isDelete()) {
            new MaterialAlertDialogBuilder(getActivity()).setTitle(R.string.dialog_delete_record).setMessage(R.string.dialog_delete_history).setNegativeButton(R.string.dialog_negative, null).setPositiveButton(R.string.dialog_positive, (dialog, which) -> clearHistory()).show();
        } else {
            setKeepDelete(false);
            setHistoryDelete(true);
        }
        return true;
    }

    @Override
    public boolean onLongClick(Keep item) {
        if (mKeepPresenter.isDelete()) {
            new MaterialAlertDialogBuilder(getActivity()).setTitle(R.string.dialog_delete_record).setMessage(R.string.dialog_delete_keep).setNegativeButton(R.string.dialog_negative, null).setPositiveButton(R.string.dialog_positive, (dialog, which) -> clearKeep()).show();
        } else {
            setHistoryDelete(false);
            setKeepDelete(true);
        }
        return true;
    }

    @Override
    public void onItemClick(Vod item) {
        if (getHome().isIndexs()) CollectActivity.start(getActivity(), item.getVodName());
        else VideoActivity.start(getActivity(), item.getVodId(), item.getVodName(), item.getVodPic());
    }

    @Override
    public boolean onLongClick(Vod item) {
        CollectActivity.start(getActivity(), item.getVodName());
        return true;
    }

    @Override
    public void onResume() {
        super.onResume();
        refreshFuncRow();
    }

    @Override
    public void onItemClick(Keep item) {
        openVodItem(item.getCid(), item.getSiteKey(), item.getVodId(), item.getVodName(), item.getVodPic());
    }

    @Override
    public void onItemDelete(Keep item) {
        mKeepRequestId++;
        mKeepAdapter.remove(item.delete());
        if (mKeepAdapter.size() > 0) return;
        removeKeepSection();
        mKeepPresenter.setDelete(false);
    }

    public boolean canBack() {
        return mBinding.recycler.getSelectedPosition() > 0;
    }

    public void goBack() {
        mBinding.recycler.scrollToPosition(0);
    }

}
