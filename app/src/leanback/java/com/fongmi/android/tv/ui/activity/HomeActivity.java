package com.fongmi.android.tv.ui.activity;

import android.Manifest;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
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
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewbinding.ViewBinding;
import androidx.viewpager.widget.ViewPager;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;
import com.fongmi.android.tv.App;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.Setting;
import com.fongmi.android.tv.Updater;
import com.fongmi.android.tv.api.config.LiveConfig;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.api.config.WallConfig;
import com.fongmi.android.tv.bean.Button;
import com.fongmi.android.tv.bean.Class;
import com.fongmi.android.tv.bean.Config;
import com.fongmi.android.tv.bean.Filter;
import com.fongmi.android.tv.bean.Result;
import com.fongmi.android.tv.bean.Site;
import com.fongmi.android.tv.bean.Style;
import com.fongmi.android.tv.databinding.ActivityHomeBinding;
import com.fongmi.android.tv.db.AppDatabase;
import com.fongmi.android.tv.event.CastEvent;
import com.fongmi.android.tv.event.RefreshEvent;
import com.fongmi.android.tv.event.ServerEvent;
import com.fongmi.android.tv.impl.Callback;
import com.fongmi.android.tv.impl.ConfigCallback;
import com.fongmi.android.tv.model.SiteViewModel;
import com.fongmi.android.tv.player.Source;
import com.fongmi.android.tv.server.Server;
import com.fongmi.android.tv.service.StableDLNARendererService;
import com.fongmi.android.tv.ui.base.BaseActivity;
import com.fongmi.android.tv.ui.custom.CustomTitleView;
import com.fongmi.android.tv.ui.dialog.HistoryDialog;
import com.fongmi.android.tv.ui.dialog.MenuDialog;
import com.fongmi.android.tv.ui.dialog.SiteDialog;
import com.fongmi.android.tv.ui.fragment.HomeFragment;
import com.fongmi.android.tv.ui.fragment.VodFragment;
import com.fongmi.android.tv.ui.presenter.TypePresenter;
import com.fongmi.android.tv.utils.Clock;
import com.fongmi.android.tv.utils.FileChooser;
import com.fongmi.android.tv.utils.FileUtil;
import com.fongmi.android.tv.utils.KeyUtil;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.ResUtil;
import com.fongmi.android.tv.utils.SiteCategoryUtil;
import com.fongmi.android.tv.utils.Tbs;
import com.fongmi.android.tv.utils.UrlUtil;
import com.permissionx.guolindev.PermissionX;

import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class HomeActivity extends BaseActivity implements CustomTitleView.Listener, TypePresenter.OnClickListener, ConfigCallback {

    public ActivityHomeBinding mBinding;
    private ArrayObjectAdapter mAdapter;
    private HomeActivity.PageAdapter mPageAdapter;
    private SiteViewModel mViewModel;
    public Result mResult;
    private boolean loading;
    private boolean coolDown;
    private View mOldView;
    private boolean confirm;
    private Clock mClock;
    private View mFocus;
    private int mLastPagePosition;
    private long mHomeRequestSeed;
    private String mPendingHomeToken;
    private final Runnable mCoolDownReset = () -> coolDown = false;
    private final Runnable mConfirmReset = () -> confirm = false;
    private final Runnable mEnableTitleFocus = () -> {
        if (!isFinishing() && !isDestroyed()) mBinding.title.setFocusable(true);
    };

    private Site getHome() {
        return VodConfig.get().getHome();
    }

    @Override
    protected ViewBinding getBinding() {
        return mBinding = ActivityHomeBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        checkAction(intent);
    }

    @Override
    protected void initView() {
        StableDLNARendererService.start(this, R.drawable.ic_logo);
        mClock = Clock.create(mBinding.clock).format("MM/dd HH:mm:ss");
        Updater.get().release().start(this);
        Server.get().start();
        Tbs.init();
        setTitleView();
        setRecyclerView();
        setViewModel();
        setHomeType();
        setPager();
        initConfig();
    }

    @Override
    protected void initEvent() {
        mBinding.title.setListener(this);
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

    private void checkAction(Intent intent) {
        if (Intent.ACTION_SEND.equals(intent.getAction())) {
            VideoActivity.push(this, intent.getStringExtra(Intent.EXTRA_TEXT));
        } else if (Intent.ACTION_VIEW.equals(intent.getAction()) && intent.getData() != null) {
            if ("text/plain".equals(intent.getType()) || UrlUtil.path(intent.getData()).endsWith(".m3u")) {
                String path = FileChooser.getPathFromUri(this, intent.getData());
                if (TextUtils.isEmpty(path)) Notify.show(R.string.error_empty);
                else loadLive("file:/" + path);
            } else {
                VideoActivity.push(this, intent.getData().toString());
            }
        }
    }

    private void setTitleView() {
        if (Setting.getHomeUI() == 0) {
            mBinding.title.setTextSize(24);
            mBinding.clock.setTextSize(24);
        } else {
            mBinding.title.setTextSize(20);
            mBinding.clock.setTextSize(20);
        }
    }

    private void setRecyclerView() {
        setHomeUI();
        mBinding.recycler.setHorizontalSpacing(ResUtil.dp2px(16));
        mBinding.recycler.setRowHeight(ViewGroup.LayoutParams.WRAP_CONTENT);
        mBinding.recycler.setAdapter(new ItemBridgeAdapter(mAdapter = new ArrayObjectAdapter(new TypePresenter(this))));
    }

    private void setHomeUI() {
        if (Setting.getHomeUI() == 0) mBinding.recycler.setVisibility(View.GONE);
        else mBinding.recycler.setVisibility(View.VISIBLE);
    }

    private void setViewModel() {
        mViewModel = new ViewModelProvider(this).get(SiteViewModel.class);
        mViewModel.result.observe(this, result -> {
            if (!isCurrentHomeResult(result)) return;
            setTypes(mResult = result);
        });
    }

    private List<Class> getTypes(Result result) {
        return SiteCategoryUtil.filter(getHome().getCategories(), result.getTypes());
    }

    private String getKey() {
        return getHome().getKey();
    }

    private List<Filter> getFilters(Result result, String typeId) {
        if (result == null || result.getFilters() == null) return new ArrayList<>();
        List<Filter> filters = result.getFilters().get(typeId);
        return filters == null ? new ArrayList<>() : filters;
    }

    private void setHomeType() {
        Class home = new Class();
        home.setTypeId("home");
        home.setTypeName(ResUtil.getString(R.string.home));
        mAdapter.add(home);
    }

    public void homeContent() {
        mResult = Result.empty();
        updateHomeTitle();
        if (getHome().getKey().isEmpty()) {
            mPendingHomeToken = null;
            showHomeContent();
            setLoading(false);
            return;
        }
        mFocus = getCurrentFocus();
        showHomeProgress();
        mPendingHomeToken = nextHomeRequestToken();
        mViewModel.homeContent(getKey(), mPendingHomeToken);
    }

    private String nextHomeRequestToken() {
        return "home:" + (++mHomeRequestSeed);
    }

    private boolean isCurrentHomeResult(Result result) {
        return result != null
                && TextUtils.equals(result.getKey(), getKey())
                && TextUtils.equals(result.getRequestToken(), mPendingHomeToken);
    }

    private void updateHomeTitle() {
        String title = getHome().getName();
        mBinding.title.setText(title.isEmpty() ? ResUtil.getString(R.string.app_name) : title);
    }

    public void setTypes(Result result) {
        result.setTypes(getTypes(result));
        updateTypeFilters(result);
        updateTypeAdapter(result);
        refreshHomePage(result);
        App.post(() -> setFocus(), 200);
    }

    private void updateTypeFilters(Result result) {
        for (Class item : result.getTypes()) item.setFilters(getFilters(result, item.getTypeId()));
    }

    private void updateTypeAdapter(Result result) {
        if (mAdapter.size() > 1) mAdapter.removeItems(1, mAdapter.size() - 1);
        if (!result.getTypes().isEmpty()) mAdapter.addAll(1, result.getTypes());
    }

    private void refreshHomePage(Result result) {
        setPager();
        HomeFragment fragment = getHomeFragmentSafe();
        if (fragment != null) fragment.addVideo(result);
        showHomeContent();
    }

    private void setPager() {
        if (mPageAdapter == null) {
            mBinding.pager.setAdapter(mPageAdapter = new HomeActivity.PageAdapter(getSupportFragmentManager()));
        } else {
            mPageAdapter.submit();
        }
        int count = mPageAdapter.getCount();
        int current = mBinding.pager.getCurrentItem();
        int target = count == 0 ? 0 : Math.max(0, Math.min(current, count - 1));
        if (current != target) mBinding.pager.setCurrentItem(target, false);
        mBinding.pager.setNoScrollItem(0);
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
            if (position == 0) showToolBar();
            else hideToolBar();
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
        for (int i = 1; i < mAdapter.size(); i++) {
            Object item = mAdapter.get(i);
            if (item instanceof Class && ((Class) item).getTypeId().equals(typeId)) return (Class) item;
        }
        return null;
    }

    private void notifyTypeChanged(Class item) {
        int index = mAdapter.indexOf(item);
        if (index >= 0) mAdapter.notifyArrayItemRangeChanged(index, 1);
    }

    private boolean isHomePageSelected() {
        return mBinding.pager.getCurrentItem() == 0;
    }

    public void hideToolBar() {
        mBinding.toolbar.setVisibility(View.GONE);
        if (mBinding.recycler.getVisibility() == View.VISIBLE) mBinding.blank.setVisibility(View.VISIBLE);
        else mBinding.blank.setVisibility(View.GONE);
    }

    public void showToolBar() {
        mBinding.toolbar.setVisibility(View.VISIBLE);
        mBinding.blank.setVisibility(View.GONE);
    }

    private HomeFragment getHomeFragment() {
        return (HomeFragment) mPageAdapter.instantiateItem(mBinding.pager, 0);
    }

    @Nullable
    private HomeFragment getHomeFragmentSafe() {
        if (mPageAdapter == null) return null;
        Object fragment = mPageAdapter.instantiateItem(mBinding.pager, 0);
        return fragment instanceof HomeFragment ? (HomeFragment) fragment : null;
    }

    private void showHomeProgress() {
        HomeFragment fragment = getHomeFragmentSafe();
        if (fragment == null || !fragment.inited || fragment.mBinding == null) return;
        fragment.mBinding.progressLayout.showProgress();
    }

    private void showHomeContent() {
        HomeFragment fragment = getHomeFragmentSafe();
        if (fragment == null || !fragment.inited || fragment.mBinding == null) return;
        fragment.mBinding.progressLayout.showContent();
    }

    private VodFragment getFragment() {
        return getFragment(mBinding.pager.getCurrentItem());
    }

    private VodFragment getFragment(int position) {
        return (VodFragment) mPageAdapter.instantiateItem(mBinding.pager, position);
    }

    private void notifyPageHidden(int fromPosition, int toPosition) {
        if (fromPosition == toPosition || mPageAdapter == null || fromPosition == 0) return;
        if (fromPosition < 0 || fromPosition >= mPageAdapter.getCount()) return;
        getFragment(fromPosition).onPageHidden();
    }

    private void setCoolDown() {
        App.removeCallbacks(mCoolDownReset);
        App.post(mCoolDownReset, 2000);
        coolDown = true;
    }

    private boolean hasSettingButton() {
        return Setting.getHomeButtons(Button.getDefaultButtons()).contains("6");
    }

    @Override
    public void onItemClick(Class item) {
        if (isHomePageSelected()) {
            SiteDialog.create(this).show();
        } else {
            updateFilter(item);
        }
    }

    @Override
    public void onRefresh(Class item) {
        if (isHomePageSelected()) mBinding.title.requestFocus();
        else getFragment().onRefresh();
    }

    @Override
    public void setConfig(Config config) {
        setConfig(config, "");
    }

    @Override
    public void setConfigs(List<Config> configs) {
        setConfigs(configs, "");
    }

    private void setConfig(Config config, String success) {
        if (config.getUrl().startsWith("file") && !PermissionX.isGranted(getActivity(), Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
            PermissionX.init(this).permissions(Manifest.permission.WRITE_EXTERNAL_STORAGE).request((allGranted, grantedList, deniedList) -> load(config, success));
        } else {
            load(config, success);
        }
    }

    private void setConfigs(List<Config> configs, String success) {
        if (configs == null || configs.isEmpty()) return;
        boolean needStorage = false;
        for (Config config : configs) if (config.getUrl().startsWith("file")) needStorage = true;
        if (needStorage && !PermissionX.isGranted(getActivity(), Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
            PermissionX.init(this).permissions(Manifest.permission.WRITE_EXTERNAL_STORAGE).request((allGranted, grantedList, deniedList) -> load(configs, success));
        } else {
            load(configs, success);
        }
    }

    public void initConfig() {
        if (isLoading()) return;
        setLoading(true);
        App.execute(() -> {
            try {
                WallConfig.get().init();
                List<Config> liveConfigs = getStartupConfigs(1);
                List<Config> vodConfigs = getStartupConfigs(0);
                App.post(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    if (liveConfigs.size() == 1) LiveConfig.load(liveConfigs.get(0), getLiveCallback(), true);
                    else LiveConfig.load(liveConfigs, getLiveCallback(), true);
                    if (vodConfigs.size() == 1) VodConfig.load(vodConfigs.get(0), getCallback(""), true);
                    else VodConfig.load(vodConfigs, getCallback(""), true, true);
                });
            } catch (Throwable e) {
                App.post(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    showHomeContent();
                    App.post(() -> {
                        if (!isFinishing() && !isDestroyed()) showHomeContent();
                    }, 1000);
                    setLoading(false);
                    Notify.show(Notify.getError(R.string.error_config_parse, e));
                });
            }
        });
    }

    private Callback getLiveCallback() {
        return new Callback() {
            @Override
            public void error(String msg) {
                Notify.show(msg);
            }
        };
    }

    private List<Config> getStartupConfigs(int type) {
        String value = type == 0 ? Setting.getVodConfigUrls() : Setting.getLiveConfigUrls();
        List<Config> configs = new ArrayList<>();
        Set<String> urls = new LinkedHashSet<>();
        for (String url : value.split("[\\n\\r,，;；|]+")) {
            String itemUrl = url.trim();
            if (itemUrl.isEmpty() || !urls.add(itemUrl)) continue;
            configs.add(Config.find(itemUrl, type));
        }
        if (!configs.isEmpty()) return configs;
        configs.add(type == 0 ? Config.vod() : Config.live());
        return configs;
    }

    private Callback getCallback(String success) {
        return new Callback() {
            @Override
            public void success(String result) {
                Notify.show(result);
                success();
            }

            @Override
            public void success() {
                checkAction(getIntent());
                RefreshEvent.video();
                setLogo();
                if (!TextUtils.isEmpty(success)) Notify.show(success);
            }

            @Override
            public void error(String msg) {
                showHomeContent();
                App.post(() -> {
                    if (!isFinishing() && !isDestroyed()) showHomeContent();
                }, 1000);
                mResult = Result.empty();
                Notify.show(msg);
                setLoading(false);
            }
        };
    }

    private void load(Config config, String success) {
        switch (config.getType()) {
            case 0:
                showHomeProgress();
                Setting.putVodConfigDesc(config.getDesc());
                Setting.putVodConfigUrls(config.getUrl());
                VodConfig.load(config, getCallback(success));
                break;
        }
    }

    private void load(List<Config> configs, String success) {
        Config first = configs.get(0);
        switch (first.getType()) {
            case 0:
                showHomeProgress();
                Setting.putVodConfigDesc(getConfigsDesc(configs));
                Setting.putVodConfigUrls(getConfigsUrls(configs));
                VodConfig.load(configs, getCallback(success));
                break;
        }
    }

    private String getConfigsDesc(List<Config> configs) {
        if (configs.size() == 1) return configs.get(0).getDesc();
        if (configs.size() == 2) return configs.get(0).getDesc() + " + " + configs.get(1).getDesc();
        return configs.get(0).getDesc() + " +" + (configs.size() - 1);
    }

    private String getConfigsUrls(List<Config> configs) {
        StringBuilder sb = new StringBuilder();
        for (Config config : configs) {
            if (sb.length() > 0) sb.append('\n');
            sb.append(config.getUrl());
        }
        return sb.toString();
    }

    private void loadLive(String url) {
        App.execute(() -> {
            Config config = Config.find(url, 1);
            App.post(() -> {
                if (isFinishing() || isDestroyed()) return;
                LiveConfig.load(config, new Callback() {
                    @Override
                    public void success() {
                        LiveActivity.start(getActivity());
                    }
                });
            });
        });
    }

    private void setConfirm() {
        confirm = true;
        Notify.show(R.string.app_exit);
        App.removeCallbacks(mConfirmReset);
        App.post(mConfirmReset, 5000);
    }


    @Override
    public void showDialog() {
        if (!hasSettingButton()) {
            MenuDialog.create(this).show();
            return;
        }
        if (Setting.isHomeSiteLock()) return;
        SiteDialog.create(this).show();
    }

    @Override
    public void onRefresh() {
        FileUtil.clearCache(new Callback() {
            @Override
            public void success() {
                Config config = VodConfig.get().getConfig().json("").save();
                if (!config.isEmpty()) setConfig(config, ResUtil.getString(R.string.config_refreshed));
            }
        });
    }

    @Override
    public boolean onItemLongClick(Class item) {
        if (mBinding.pager.getCurrentItem() != 0) return true;
        onRefresh();
        return true;
    }


    @Override
    public void setSite(Site item) {
        VodConfig.get().setHome(item);
        homeContent();
    }

    @Override
    public void onChanged() {
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onRefreshEvent(RefreshEvent event) {
        super.onRefreshEvent(event);
        switch (event.getType()) {
            case CONFIG:
                setLogo();
                getHomeFragment().getHistory();
                getHomeFragment().getKeep();
                break;
            case VIDEO:
                getHomeFragment().getHistory();
                homeContent();
                break;
            case IMAGE:
                getHomeFragment().refreshRecommond();
                break;
            case HISTORY:
                getHomeFragment().getHistory();
                break;
            case KEEP:
                getHomeFragment().getKeep();
                break;
            case SIZE:
                homeContent();
                break;
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onServerEvent(ServerEvent event) {
        switch (event.getType()) {
            case SEARCH:
                CollectActivity.start(this, event.getText(), true);
                break;
            case PUSH:
                VideoActivity.push(this, event.getText());
                break;
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onCastEvent(CastEvent event) {
        if (VodConfig.get().getConfig().equals(event.getConfig())) {
            VideoActivity.cast(this, event.getHistory().update(VodConfig.getCid()));
        } else {
            VodConfig.load(event.getConfig(), getCallback(event));
        }
    }

    private Callback getCallback(CastEvent event) {
        return new Callback() {
            @Override
            public void success() {
                RefreshEvent.history();
                RefreshEvent.config();
                RefreshEvent.video();
                onCastEvent(event);
            }

            @Override
            public void error(String msg) {
                Notify.show(msg);
            }
        };
    }

    public boolean isLoading() {
        return loading;
    }

    public void setLoading(boolean loading) {
        this.loading = loading;
    }

    private void setLogo() {
        Glide.with(App.get()).load(UrlUtil.convert(VodConfig.get().getConfig().getLogo())).circleCrop().override(Target.SIZE_ORIGINAL, Target.SIZE_ORIGINAL).listener(getListener()).into(mBinding.logo);
    }

    private RequestListener<Drawable> getListener() {
        return new RequestListener<Drawable>() {
            @Override
            public boolean onLoadFailed(@Nullable GlideException e, Object model, @NonNull Target<Drawable> target, boolean isFirstResource) {
                mBinding.logo.setVisibility(View.GONE);
                return false;
            }

            @Override
            public boolean onResourceReady(@NonNull Drawable resource, @NonNull Object model, Target<Drawable> target, @NonNull DataSource dataSource, boolean isFirstResource) {
                mBinding.logo.setVisibility(View.VISIBLE);
                return false;
            }
        };
    }

    private void setFocus() {
        setLoading(false);
        if (!mBinding.title.isFocusable()) {
            App.removeCallbacks(mEnableTitleFocus);
            App.post(mEnableTitleFocus, 500);
        }
        if (mFocus != mBinding.title) {
            if (Setting.getHomeUI() == 0) {
                HomeFragment fragment = getHomeFragmentSafe();
                if (fragment != null && fragment.inited && fragment.mBinding != null) fragment.mBinding.recycler.requestFocus();
            }
            else mBinding.recycler.requestFocus();
        }
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        boolean isHomeFragment = mBinding.pager.getCurrentItem() == 0;
        if (isHomeFragment && KeyUtil.isMenuKey(event)) {
            if (Setting.getHomeMenuKey() == 0) MenuDialog.create(this).show();
            else if (Setting.getHomeMenuKey() == 1) SiteDialog.create(this).show();
            else if (Setting.getHomeMenuKey() == 2) HistoryDialog.create(this).type(0).show();
            else if (Setting.getHomeMenuKey() == 3) LiveActivity.start(this);
            else if (Setting.getHomeMenuKey() == 4) HistoryActivity.start(this);
            else if (Setting.getHomeMenuKey() == 5) SearchActivity.start(this);
            else if (Setting.getHomeMenuKey() == 6) PushActivity.start(this);
            else if (Setting.getHomeMenuKey() == 7) KeepActivity.start(this);
            else if (Setting.getHomeMenuKey() == 8) SettingActivity.start(this);
        }
        if (!isHomeFragment && KeyUtil.isMenuKey(event)) updateFilter((Class) mAdapter.get(mBinding.pager.getCurrentItem()));
        if (!isHomeFragment && KeyUtil.isBackKey(event) && event.isLongPress() && getFragment().goRoot()) setCoolDown();
        return super.dispatchKeyEvent(event);
    }

    @Override
    protected void onResume() {
        super.onResume();
        mClock.start();
        setTitleView();
        setHomeUI();
    }

    @Override
    protected void onPause() {
        super.onPause();
        mClock.stop();
    }

    @Override
    protected boolean handleBack() {
        return true;
    }

    @Override
    protected void onBackPress() {
        HomeFragment homeFragment = getHomeFragmentSafe();
        if (isVisible(mBinding.recycler) && mBinding.recycler.getSelectedPosition() > 0) {
            mBinding.recycler.scrollToPosition(0);
        } else if (mPageAdapter != null && homeFragment != null && homeFragment.inited && homeFragment.mBinding.progressLayout.isProgress()) {
            showHomeContent();
        } else if (mPageAdapter != null && homeFragment != null && homeFragment.inited && homeFragment.mPresenter != null && homeFragment.mPresenter.isDelete()) {
            homeFragment.setHistoryDelete(false);
        } else if (mPageAdapter != null && homeFragment != null && homeFragment.inited && homeFragment.mKeepPresenter != null && homeFragment.mKeepPresenter.isDelete()) {
            homeFragment.setKeepDelete(false);
        } else if (homeFragment != null && homeFragment.canBack()) {
            homeFragment.goBack();
        } else if (!confirm) {
            setConfirm();
        } else {
            finish();
        }
    }

    @Override
    public void onBackPressed() {
        boolean isHomeFragment = mBinding.pager.getCurrentItem() == 0;
        if (isHomeFragment) {
            super.onBackPressed();
            return;
        }
        Class item = (Class) mAdapter.get(mBinding.pager.getCurrentItem());
        if (item.getFilter() != null && item.getFilter()) getFragment().resetFilterOnBack();
        else if (getFragment().canBack()) getFragment().goBack();
        else if (!coolDown) super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        App.removeCallbacks(mRunnable, mCoolDownReset, mConfirmReset, mEnableTitleFocus);
        super.onDestroy();
        WallConfig.get().clear();
        LiveConfig.get().clear();
        VodConfig.get().clear();
        AppDatabase.backup();
        Server.get().stop();
        Source.get().exit();
    }

    class PageAdapter extends FragmentStatePagerAdapter {

        private final List<String> mPageKeys;

        public PageAdapter(@NonNull FragmentManager fm) {
            super(fm, BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT);
            this.mPageKeys = new ArrayList<>();
            submit();
        }

        public void submit() {
            List<String> pageKeys = getPageKeys();
            if (mPageKeys.equals(pageKeys)) return;
            mPageKeys.clear();
            mPageKeys.addAll(pageKeys);
            notifyDataSetChanged();
        }

        private List<String> getPageKeys() {
            List<String> pageKeys = new ArrayList<>();
            pageKeys.add("home");
            for (int i = 1; i < mAdapter.size(); i++) pageKeys.add(getPageKey(i));
            return pageKeys;
        }

        private String getPageKey(int position) {
            if (position == 0) return "home";
            Class type = (Class) mAdapter.get(position);
            Style style = type.getStyle();
            String styleKey = style == null ? "" : style.getType() + "@" + style.getRatio();
            String extendKey = App.gson().toJson(type.getExtend(false));
            String filterKey = App.gson().toJson(type.getFilters());
            String folderKey = "1".equals(type.getTypeFlag()) ? "1" : "0";
            return getHome().getKey() + "@" + type.getTypeId() + "@" + styleKey + "@" + folderKey + "@" + extendKey + "@" + filterKey;
        }

        private String getFragmentKey(@NonNull Fragment fragment) {
            if (fragment instanceof HomeFragment) return "home";
            if (!(fragment instanceof VodFragment) || fragment.getArguments() == null) return "";
            String key = fragment.getArguments().getString("key", "");
            String typeId = fragment.getArguments().getString("typeId", "");
            Style style = fragment.getArguments().getParcelable("style");
            String styleKey = style == null ? "" : style.getType() + "@" + style.getRatio();
            String extendKey = App.gson().toJson(fragment.getArguments().getSerializable("extend"));
            String filterKey = App.gson().toJson(fragment.getArguments().getParcelableArrayList("filters"));
            boolean folder = fragment.getArguments().getBoolean("folder");
            return key + "@" + typeId + "@" + styleKey + "@"
                    + (folder ? "1" : "0") + "@" + extendKey + "@" + filterKey;
        }

        @NonNull
        @Override
        public Fragment getItem(int position) {
            if (position == 0) return new HomeFragment();
            Class type = (Class) mAdapter.get(position);
            return VodFragment.newInstance(getHome().getKey(), type.getTypeId(), type.getStyle(), type.getExtend(false), new ArrayList<>(Filter.copy(type.getFilters())), "1".equals(type.getTypeFlag()), Boolean.TRUE.equals(type.getFilter()));
        }

        @Override
        public int getCount() {
            return mAdapter.size();
        }

        @Override
        public int getItemPosition(@NonNull Object object) {
            if (!(object instanceof Fragment)) return POSITION_NONE;
            int position = mPageKeys.indexOf(getFragmentKey((Fragment) object));
            return position < 0 ? POSITION_NONE : position;
        }

        @Override
        public void destroyItem(@NonNull ViewGroup container, int position, @NonNull Object object) {
            if (position == 0 || object instanceof HomeFragment) return;
            super.destroyItem(container, position, object);
        }

    }
}
