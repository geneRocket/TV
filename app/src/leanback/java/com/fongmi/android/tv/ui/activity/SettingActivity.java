package com.fongmi.android.tv.ui.activity;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.view.View;

import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.BuildConfig;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.Setting;
import com.fongmi.android.tv.Updater;
import com.fongmi.android.tv.api.config.LiveConfig;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.api.config.WallConfig;
import com.fongmi.android.tv.bean.Config;
import com.fongmi.android.tv.bean.Live;
import com.fongmi.android.tv.bean.Site;
import com.fongmi.android.tv.databinding.ActivitySettingBinding;
import com.fongmi.android.tv.db.AppDatabase;
import com.fongmi.android.tv.event.RefreshEvent;
import com.fongmi.android.tv.impl.BackupCallback;
import com.fongmi.android.tv.impl.Callback;
import com.fongmi.android.tv.impl.ConfigCallback;
import com.fongmi.android.tv.impl.DohCallback;
import com.fongmi.android.tv.impl.LiveCallback;
import com.fongmi.android.tv.impl.ProxyCallback;
import com.fongmi.android.tv.impl.SiteCallback;
import com.fongmi.android.tv.player.Source;
import com.fongmi.android.tv.ui.base.BaseActivity;
import com.fongmi.android.tv.ui.dialog.BackupDialog;
import com.fongmi.android.tv.ui.dialog.ConfigDialog;
import com.fongmi.android.tv.ui.dialog.DohDialog;
import com.fongmi.android.tv.ui.dialog.HistoryDialog;
import com.fongmi.android.tv.ui.dialog.LiveDialog;
import com.fongmi.android.tv.ui.dialog.ProxyDialog;
import com.fongmi.android.tv.ui.dialog.SiteDialog;
import com.fongmi.android.tv.utils.FileUtil;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.ResUtil;
import com.fongmi.android.tv.utils.UrlUtil;
import com.github.catvod.bean.Doh;
import com.github.catvod.net.OkHttp;
import com.permissionx.guolindev.PermissionX;

import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class SettingActivity extends BaseActivity implements BackupCallback, ConfigCallback, SiteCallback, LiveCallback, DohCallback, ProxyCallback {

    private ActivitySettingBinding mBinding;
    private String[] backup;
    private int type;

    public static void start(Activity activity) {
        activity.startActivity(new Intent(activity, SettingActivity.class));
    }

    private int getDohIndex() {
        return Math.max(0, VodConfig.get().getDoh().indexOf(Doh.objectFrom(Setting.getDoh())));
    }

    private String[] getDohList() {
        List<String> list = new ArrayList<>();
        for (Doh item : VodConfig.get().getDoh()) list.add(item.getName());
        return list.toArray(new String[0]);
    }

    private int safeIndex(int index, String[] items) {
        if (items == null || items.length == 0) return 0;
        return Math.max(0, Math.min(index, items.length - 1));
    }

    private int nextIndex(int index, String[] items) {
        if (items == null || items.length == 0) return 0;
        index = safeIndex(index, items);
        return index == items.length - 1 ? 0 : index + 1;
    }

    @Override
    protected ViewBinding getBinding() {
        return mBinding = ActivitySettingBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void initView() {
        mBinding.vod.requestFocus();
        mBinding.vodUrl.setText(getVodConfigDesc());
        mBinding.liveUrl.setText(getLiveConfigDesc());
        mBinding.wallUrl.setText(WallConfig.getDesc());
        String[] doh = getDohList();
        mBinding.dohText.setText(doh.length == 0 ? "" : doh[safeIndex(getDohIndex(), doh)]);
        mBinding.versionText.setText(BuildConfig.VERSION_NAME);
        mBinding.proxyText.setText(UrlUtil.scheme(Setting.getProxy()));
        mBinding.backupText.setText((backup = ResUtil.getStringArray(R.array.select_backup))[safeIndex(Setting.getBackupMode(), backup)]);
        mBinding.aboutText.setText(BuildConfig.FLAVOR_mode + "-" + BuildConfig.FLAVOR_api + "-" + BuildConfig.FLAVOR_abi);
        setCacheText();
    }

    private void setCacheText() {
        FileUtil.getCacheSize(new Callback() {
            @Override
            public void success(String result) {
                mBinding.cacheText.setText(result);
            }
        });
    }

    @Override
    protected void initEvent() {
        mBinding.vod.setOnClickListener(this::onVod);
        mBinding.live.setOnClickListener(this::onLive);
        mBinding.wall.setOnClickListener(this::onWall);
        mBinding.proxy.setOnClickListener(this::onProxy);
        mBinding.cache.setOnClickListener(this::onCache);
        mBinding.cache.setOnLongClickListener(this::onCacheLongClick);
        mBinding.backup.setOnClickListener(this::onBackup);
        mBinding.restore.setOnClickListener(this::onRestore);
        mBinding.player.setOnClickListener(this::onPlayer);
        mBinding.danmu.setOnClickListener(this::onDanmu);
        mBinding.version.setOnClickListener(this::onVersion);
        mBinding.vod.setOnLongClickListener(this::onVodEdit);
        mBinding.vodHome.setOnClickListener(this::onVodHome);
        mBinding.live.setOnLongClickListener(this::onLiveEdit);
        mBinding.liveHome.setOnClickListener(this::onLiveHome);
        mBinding.wall.setOnLongClickListener(this::onWallEdit);
        mBinding.backup.setOnLongClickListener(this::onBackupMode);
        mBinding.vodHistory.setOnClickListener(this::onVodHistory);
        mBinding.version.setOnLongClickListener(this::onVersionDev);
        mBinding.liveHistory.setOnClickListener(this::onLiveHistory);
        mBinding.wallDefault.setOnClickListener(this::setWallDefault);
        mBinding.wallRefresh.setOnClickListener(this::setWallRefresh);
        mBinding.custom.setOnClickListener(this::onCustom);
        mBinding.doh.setOnClickListener(this::setDoh);
        mBinding.about.setOnClickListener(this::onAbout);
    }

    @Override
    public void setConfig(Config config) {
        if (config.getUrl().startsWith("file") && !PermissionX.isGranted(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
            PermissionX.init(this).permissions(Manifest.permission.WRITE_EXTERNAL_STORAGE).request((allGranted, grantedList, deniedList) -> {
                if (allGranted) load(config);
            });
        } else {
            load(config);
        }
    }

    @Override
    public void setConfigs(List<Config> configs) {
        if (configs == null || configs.isEmpty()) return;
        boolean needStorage = false;
        for (Config config : configs) if (config.getUrl().startsWith("file")) needStorage = true;
        if (needStorage && !PermissionX.isGranted(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
            PermissionX.init(this).permissions(Manifest.permission.WRITE_EXTERNAL_STORAGE).request((allGranted, grantedList, deniedList) -> {
                if (allGranted) load(configs);
            });
        } else {
            load(configs);
        }
    }

    private void load(Config config) {
        type = config.getType();
        switch (config.getType()) {
            case 0:
                Notify.progress(this);
                VodConfig.load(config, getCallback(), true);
                Setting.putVodConfigDesc(config.getDesc());
                Setting.putVodConfigUrls(config.getUrl());
                mBinding.vodUrl.setText(config.getDesc());
                break;
            case 1:
                Notify.progress(this);
                LiveConfig.load(config, getCallback(), true);
                Setting.putLiveConfigDesc(config.getDesc());
                Setting.putLiveConfigUrls(config.getUrl());
                mBinding.liveUrl.setText(config.getDesc());
                break;
            case 2:
                Notify.progress(this);
                WallConfig.load(config, getCallback());
                mBinding.wallUrl.setText(config.getDesc());
                break;
        }
    }

    private void load(List<Config> configs) {
        Config first = configs.get(0);
        type = first.getType();
        switch (first.getType()) {
            case 0:
                Notify.progress(this);
                VodConfig.load(configs, getCallback(), false, true);
                Setting.putVodConfigDesc(getConfigsDesc(configs));
                Setting.putVodConfigUrls(getConfigsUrls(configs));
                mBinding.vodUrl.setText(getVodConfigDesc());
                break;
            case 1:
                Notify.progress(this);
                LiveConfig.load(configs, getCallback(), true);
                Setting.putLiveConfigDesc(getConfigsDesc(configs));
                Setting.putLiveConfigUrls(getConfigsUrls(configs));
                mBinding.liveUrl.setText(getLiveConfigDesc());
                break;
            case 2:
                Notify.progress(this);
                WallConfig.load(first, getCallback());
                mBinding.wallUrl.setText(first.getDesc());
                break;
        }
    }

    private Callback getCallback() {
        return new Callback() {
            @Override
            public void success(String result) {
                Notify.show(result);
                success();
            }

            @Override
            public void success() {
                setConfig();
            }

            @Override
            public void error(String msg) {
                Notify.show(msg);
                setConfig();
            }
        };
    }

    private void setConfig() {
        switch (type) {
            case 0:
                Notify.dismiss();
                RefreshEvent.history();
                RefreshEvent.config();
                RefreshEvent.video();
                break;
            case 1:
                Notify.dismiss();
                RefreshEvent.config();
                break;
            case 2:
                Notify.dismiss();
                RefreshEvent.config();
                break;
        }
    }

    private String getVodConfigDesc() {
        String desc = Setting.getVodConfigDesc();
        return desc.isEmpty() ? VodConfig.getDesc() : desc;
    }

    private String getLiveConfigDesc() {
        String desc = Setting.getLiveConfigDesc();
        return desc.isEmpty() ? LiveConfig.getDesc() : desc;
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

    @Override
    public void setSite(Site item) {
        VodConfig.get().setHome(item);
        RefreshEvent.video();
    }

    @Override
    public void onChanged() {
    }

    @Override
    public void setLive(Live item) {
        LiveConfig.get().setHome(item);
    }

    private void onVod(View view) {
        type = 0;
        HistoryDialog.create(this).type(type).add(() -> ConfigDialog.create(this).type(type).returnToHistory().show()).show();
    }

    private void onLive(View view) {
        type = 1;
        HistoryDialog.create(this).type(type).add(() -> ConfigDialog.create(this).type(type).returnToHistory().show()).show();
    }

    private void onWall(View view) {
        ConfigDialog.create(this).type(type = 2).show();
    }

    private boolean onVodEdit(View view) {
        ConfigDialog.create(this).type(type = 0).edit().show();
        return true;
    }

    private boolean onLiveEdit(View view) {
        ConfigDialog.create(this).type(type = 1).edit().show();
        return true;
    }

    private boolean onWallEdit(View view) {
        ConfigDialog.create(this).type(type = 2).edit().show();
        return true;
    }

    private void onVodHome(View view) {
        SiteDialog.create(this).action().show();
    }

    private void onLiveHome(View view) {
        LiveDialog.create(this).action().show();
    }

    private void onVodHistory(View view) {
        HistoryDialog.create(this).type(type = 0).show();
    }

    private void onLiveHistory(View view) {
        HistoryDialog.create(this).type(type = 1).show();
    }

    private void onPlayer(View view) {
        SettingPlayerActivity.start(this);
    }

    private void onDanmu(View view) {
        SettingDanmuActivity.start(this);
    }

    private void onVersion(View view) {
        Updater.get().force().release().start(this);
    }

    private boolean onVersionDev(View view) {
        Updater.get().force().dev().start(this);
        return true;
    }

    private void setWallDefault(View view) {
        WallConfig.refresh(Setting.getWall() == 4 ? 1 : Setting.getWall() + 1);
    }

    private void setWallRefresh(View view) {
        Notify.progress(this);
        WallConfig.get().load(new Callback() {
            @Override
            public void success() {
                Notify.dismiss();
                setCacheText();
            }
        });
    }

    private void onCustom(View view) {
        SettingCustomActivity.start(this);
    }

    private void onAbout(View view) {
        mBinding.aboutText.setText(BuildConfig.FLAVOR_mode + "-" + BuildConfig.FLAVOR_api + "-" + BuildConfig.FLAVOR_abi);
    }

    private void setDoh(View view) {
        DohDialog.create(this).index(getDohIndex()).show();
    }

    @Override
    public void setDoh(Doh doh) {
        Source.get().stop();
        OkHttp.get().setDoh(doh);
        Notify.progress(getActivity());
        Setting.putDoh(doh.toString());
        mBinding.dohText.setText(doh.getName());
        VodConfig.load(Config.vod(), getCallback());
    }

    private void onProxy(View view) {
        ProxyDialog.create(this).show();
    }

    @Override
    public void setProxy(String proxy) {
        Source.get().stop();
        Setting.putProxy(proxy);
        OkHttp.selector().clear();
        OkHttp.get().setProxy(proxy);
        Notify.progress(getActivity());
        VodConfig.load(Config.vod(), getCallback());
        mBinding.proxyText.setText(UrlUtil.scheme(proxy));
    }

    private void onCache(View view) {
        FileUtil.clearCache(new Callback() {
            @Override
            public void success() {
                VodConfig.get().getConfig().json("").save();
                setCacheText();
            }
        });
    }

    private boolean onCacheLongClick(View view) {
        FileUtil.clearCache(new Callback() {
            @Override
            public void success() {
                setCacheText();
                Config config = VodConfig.get().getConfig().json("").save();
                if (!config.isEmpty()) setConfig(config);
            }
        });
        return true;
    }

    @Override
    public void restore(File file) {
        PermissionX.init(this).permissions(Manifest.permission.WRITE_EXTERNAL_STORAGE).request((allGranted, grantedList, deniedList) -> {
            if (allGranted) AppDatabase.restore(file, new Callback() {
                @Override
                public void success() {
                    Notify.progress(getActivity());
                    App.post(() -> {
                        AppDatabase.reset();
                        initConfig();
                    }, 3000);
                }
            });
        });
    }

    private void onRestore(View view) {
        PermissionX.init(this).permissions(Manifest.permission.WRITE_EXTERNAL_STORAGE).request((allGranted, grantedList, deniedList) -> {
            if (allGranted) BackupDialog.create(this).show();
        });
    }

    private void initConfig() {
        LiveConfig.reload(new Callback());
        VodConfig.reload(getCallback());
    }

    private void onBackup(View view) {
        PermissionX.init(this).permissions(Manifest.permission.WRITE_EXTERNAL_STORAGE).request((allGranted, grantedList, deniedList) -> {
            if (allGranted) AppDatabase.backup(new Callback() {
                @Override
                public void success(String path) {
                    Notify.show(R.string.backed);
                }
            });
        });
    }

    private boolean onBackupMode(View view) {
        int index = nextIndex(Setting.getBackupMode(), backup);
        Setting.putBackupMode(index);
        mBinding.backupText.setText(backup[index]);
        return true;
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onRefreshEvent(RefreshEvent event) {
        super.onRefreshEvent(event);
        switch (event.getType()) {
            case CONFIG:
                setCacheText();
                mBinding.vodUrl.setText(getVodConfigDesc());
                mBinding.liveUrl.setText(getLiveConfigDesc());
                mBinding.wallUrl.setText(WallConfig.getDesc());
                break;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        RefreshEvent.history();
    }
}
