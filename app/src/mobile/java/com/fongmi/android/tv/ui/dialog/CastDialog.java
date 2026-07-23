package com.fongmi.android.tv.ui.dialog;

import android.content.Context;
import android.net.wifi.WifiManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewbinding.ViewBinding;

import com.android.cast.dlna.dmc.DLNACastManager;
import com.android.cast.dlna.dmc.OnDeviceRegistryListener;
import com.android.cast.dlna.dmc.control.DeviceControl;
import com.android.cast.dlna.dmc.control.OnDeviceControlListener;
import com.android.cast.dlna.dmc.control.ServiceActionCallback;
import com.fongmi.android.tv.App;
import com.fongmi.android.tv.Constant;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.bean.CastVideo;
import com.fongmi.android.tv.bean.Config;
import com.fongmi.android.tv.bean.Device;
import com.fongmi.android.tv.repository.DeviceRepository;
import com.fongmi.android.tv.bean.History;
import com.fongmi.android.tv.databinding.DialogDeviceBinding;
import com.fongmi.android.tv.event.ScanEvent;
import com.fongmi.android.tv.server.Server;
import com.fongmi.android.tv.ui.activity.ScanActivity;
import com.fongmi.android.tv.ui.adapter.DeviceAdapter;
import com.fongmi.android.tv.utils.DLNADevice;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.ScanTask;
import com.github.catvod.net.OkHttp;
import com.github.catvod.utils.Path;
import com.github.catvod.utils.Util;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import org.fourthline.cling.support.lastchange.EventedValue;
import org.fourthline.cling.support.model.TransportState;
import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.io.IOException;
import java.util.List;

import kotlin.Unit;
import okhttp3.Call;
import okhttp3.FormBody;
import okhttp3.OkHttpClient;
import okhttp3.Response;

public class CastDialog extends BaseDialog implements DeviceAdapter.OnClickListener, ScanTask.Listener, OnDeviceRegistryListener, OnDeviceControlListener, ServiceActionCallback<Unit>, okhttp3.Callback {

    private final FormBody.Builder body;
    private final OkHttpClient client;

    private DialogDeviceBinding binding;
    private WifiManager.MulticastLock lock;
    private DeviceAdapter adapter;
    private DeviceControl control;
    private Listener listener;
    private CastVideo video;
    private ScanTask scanTask;
    private volatile Call castCall;
    private boolean fm;

    public static CastDialog create() {
        return new CastDialog();
    }

    public CastDialog() {
        body = new FormBody.Builder();
        body.add("device", DeviceRepository.get().current().toString());
        body.add("config", Config.vod().toString());
        client = OkHttp.client(Constant.TIMEOUT_SYNC);
    }

    public CastDialog history(History history) {
        String id = history.getVodId();
        String fd = history.getVodId();
        if (fd.startsWith("/")) fd = Server.get().getAddress() + "/file" + fd.replace(Path.rootPath(), "");
        if (fd.startsWith("file")) fd = Server.get().getAddress() + "/" + fd.replace(Path.rootPath(), "").replace("://", "");
        if (fd.contains("127.0.0.1")) fd = fd.replace("127.0.0.1", Util.getIp());
        body.add("history", history.toString().replace(id, fd));
        return this;
    }

    public CastDialog video(CastVideo video) {
        this.video = video;
        return this;
    }

    public CastDialog fm(boolean fm) {
        this.fm = fm;
        return this;
    }

    public void show(FragmentActivity activity) {
        for (Fragment f : activity.getSupportFragmentManager().getFragments()) if (f instanceof BottomSheetDialogFragment) return;
        show(activity.getSupportFragmentManager(), null);
        this.listener = (Listener) activity;
    }

    @Override
    protected ViewBinding getBinding(@NonNull LayoutInflater inflater, @Nullable ViewGroup container) {
        return binding = DialogDeviceBinding.inflate(inflater, container, false);
    }

    @Override
    protected void initView() {
        binding.scan.setVisibility(fm ? View.VISIBLE : View.GONE);
        EventBus.getDefault().register(this);
        scanTask = ScanTask.create(this);
        setRecyclerView();
        getDevice();
        initDLNA();
    }

    @Override
    protected void initEvent() {
        binding.scan.setOnClickListener(v -> onScan());
        binding.refresh.setOnClickListener(v -> onRefresh());
    }

    private void setRecyclerView() {
        binding.recycler.setHasFixedSize(true);
        binding.recycler.setAdapter(adapter = new DeviceAdapter(this));
    }

    private void getDevice() {
        if (fm) adapter.addAll(DeviceRepository.get().all());
        adapter.addAll(DLNADevice.get().getAll());
    }

    private void initDLNA() {
        DLNACastManager.INSTANCE.registerDeviceListener(this);
        DLNACastManager.INSTANCE.search(null);
        acquireLock();
    }

    private void acquireLock() {
        try {
            WifiManager wm = (WifiManager) App.get().getSystemService(Context.WIFI_SERVICE);
            if (wm == null) return;
            lock = wm.createMulticastLock("TV");
            lock.setReferenceCounted(true);
            lock.acquire();
        } catch (Exception ignored) {
        }
    }

    private void releaseLock() {
        try {
            if (lock != null && lock.isHeld()) lock.release();
        } catch (Exception ignored) {
        }
    }

    private void onScan() {
        ScanActivity.start(getActivity());
    }

    private void onRefresh() {
        if (fm && scanTask != null) scanTask.start(adapter.getIps());
        DLNACastManager.INSTANCE.search(null);
        adapter.clear();
    }

    private void onCasted() {
        if (!isViewActive() || listener == null) return;
        // 在投屏前先暂停当前播放的视频，避免与DLNA协议冲突导致绿屏
        listener.onCasted();
        dismiss();
    }

    private boolean isViewActive() {
        return isAdded() && !isRemoving() && binding != null;
    }

    private void postIfActive(Runnable task) {
        App.post(() -> {
            if (isViewActive()) task.run();
        });
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onScanEvent(ScanEvent event) {
        if (scanTask != null) scanTask.start(event.getAddress());
    }

    @Override
    public void onFind(List<Device> devices) {
        if (devices.size() > 0) postIfActive(() -> adapter.addAll(devices));
    }

    @Override
    public void onDeviceAdded(@NonNull org.fourthline.cling.model.meta.Device<?, ?, ?> device) {
        postIfActive(() -> adapter.addAll(DLNADevice.get().add(device)));
    }

    @Override
    public void onDeviceRemoved(@NonNull org.fourthline.cling.model.meta.Device<?, ?, ?> device) {
        postIfActive(() -> adapter.remove(DLNADevice.get().remove(device)));
    }

    @Override
    public void onConnected(@NonNull org.fourthline.cling.model.meta.Device<?, ?, ?> device) {
        control.setAVTransportURI(video.getUrl(), video.getName(), this);
    }

    @Override
    public void onDisconnected(@NonNull org.fourthline.cling.model.meta.Device<?, ?, ?> device) {
        postIfActive(() -> Notify.show(R.string.device_offline));
    }

    @Override
    public void onSuccess(Unit unit) {
        postIfActive(() -> {
            if (control != null) control.play("1", null);
            onCasted();
        });
    }

    @Override
    public void onFailure(@NonNull String s) {
        postIfActive(() -> Notify.show(s));
    }

    @Override
    public void onFailure(@NonNull Call call, @NonNull IOException e) {
        postIfActive(() -> Notify.show(e.getMessage()));
    }

    @Override
    public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
        try (Response ignored = response) {
            boolean success = response.body() != null && "OK".equals(response.body().string());
            if (success) postIfActive(this::onCasted);
            else postIfActive(() -> Notify.show(R.string.device_offline));
        }
    }

    @Override
    public void onItemClick(Device item) {
        if (item.isDLNA()) control = DLNACastManager.INSTANCE.connectDevice(DLNADevice.get().find(item), this);
        else {
            Call previous = castCall;
            if (previous != null) previous.cancel();
            castCall = OkHttp.newCall(client, item.getIp().concat("/action?do=cast"), body.build());
            castCall.enqueue(this);
        }
    }

    @Override
    public void onDestroyView() {
        if (scanTask != null) scanTask.stop();
        scanTask = null;
        Call call = castCall;
        castCall = null;
        if (call != null) call.cancel();
        binding = null;
        listener = null;
        control = null;
        releaseLock();
        DLNADevice.get().disconnect();
        EventBus.getDefault().unregister(this);
        DLNACastManager.INSTANCE.unregisterListener(this);
        DLNACastManager.INSTANCE.unbindCastService(App.get());
        super.onDestroyView();
    }

    @Override
    public void onAvTransportStateChanged(@NonNull TransportState state) {
    }

    @Override
    public void onEventChanged(@NonNull EventedValue<?> event) {
    }

    @Override
    public void onRendererVolumeChanged(int volume) {
    }

    @Override
    public void onRendererVolumeMuteChanged(boolean mute) {
    }

    @Override
    public boolean onLongClick(Device item) {
        return false;
    }

    public interface Listener {

        void onCasted();
    }
}
