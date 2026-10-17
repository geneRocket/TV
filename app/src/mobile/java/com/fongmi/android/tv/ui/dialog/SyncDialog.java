package com.fongmi.android.tv.ui.dialog;

import android.content.res.TypedArray;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.Constant;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.Setting;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.bean.Config;
import com.fongmi.android.tv.repository.ConfigRepository;
import com.fongmi.android.tv.bean.Device;
import com.fongmi.android.tv.repository.DeviceRepository;
import com.fongmi.android.tv.bean.History;
import com.fongmi.android.tv.repository.HistoryRepository;
import com.fongmi.android.tv.bean.Keep;
import com.fongmi.android.tv.repository.KeepRepository;
import com.fongmi.android.tv.databinding.DialogDeviceBinding;
import com.fongmi.android.tv.event.ScanEvent;
import com.fongmi.android.tv.impl.Callback;
import com.fongmi.android.tv.server.SyncPulls;
import com.fongmi.android.tv.ui.activity.ScanActivity;
import com.fongmi.android.tv.ui.adapter.DeviceAdapter;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.ResUtil;
import com.fongmi.android.tv.utils.ScanTask;
import com.github.catvod.net.OkHttp;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

import okhttp3.Call;
import okhttp3.FormBody;
import okhttp3.OkHttpClient;
import okhttp3.Response;

public class SyncDialog extends BaseDialog implements DeviceAdapter.OnClickListener, ScanTask.Listener {

    private final FormBody.Builder body;
    private final OkHttpClient client;
    private final TypedArray mode;
    private final Runnable refreshDevices = this::onRefresh;
    private DialogDeviceBinding binding;
    private DeviceAdapter adapter;
    private ScanTask scanTask;
    private Call activeCall;
    private SyncPulls.Ticket activePull;
    private String syncConfigUrl = "";
    private String type;

    public static SyncDialog create() {
        return new SyncDialog();
    }

    public SyncDialog() {
        client = OkHttp.client(Constant.TIMEOUT_SYNC);
        mode = ResUtil.getTypedArray(R.array.cast_mode);
        body = new FormBody.Builder();
    }

    public SyncDialog history() {
        Config config = Config.vod();
        syncConfigUrl = config.getUrl();
        body.add("device", DeviceRepository.get().current().toString());
        body.add("config", config.toString());
        body.add("targets", App.gson().toJson(HistoryRepository.get().all(config.getId())));
        return type("history");
    }

    public SyncDialog keep() {
        body.add("device", DeviceRepository.get().current().toString());
        body.add("targets", App.gson().toJson(KeepRepository.get().vod()));
        body.add("configs", App.gson().toJson(ConfigRepository.get().urls()));
        return type("keep");
    }

    public void show(FragmentActivity activity) {
        for (Fragment f : activity.getSupportFragmentManager().getFragments()) if (f instanceof BottomSheetDialogFragment) return;
        show(activity.getSupportFragmentManager(), null);
    }

    private SyncDialog type(String type) {
        this.type = type;
        return this;
    }

    @Override
    protected ViewBinding getBinding(@NonNull LayoutInflater inflater, @Nullable ViewGroup container) {
        return binding = DialogDeviceBinding.inflate(inflater, container, false);
    }

    @Override
    protected void initView() {
        binding.mode.setVisibility(View.VISIBLE);
        EventBus.getDefault().register(this);
        scanTask = ScanTask.create(this);
        setRecyclerView();
        getDevice();
        setMode();
    }

    @Override
    protected void initEvent() {
        binding.mode.setOnClickListener(v -> onMode());
        binding.scan.setOnClickListener(v -> onScan());
        binding.refresh.setOnClickListener(v -> onRefresh());
    }

    private void setRecyclerView() {
        binding.recycler.setHasFixedSize(true);
        binding.recycler.setAdapter(adapter = new DeviceAdapter(this));
    }

    private void getDevice() {
        adapter.addAll(DeviceRepository.get().all());
        if (adapter.getItemCount() == 0) App.post(refreshDevices, 1000);
    }

    private void setMode() {
        int index = Setting.getSyncMode();
        binding.mode.setImageResource(mode.getResourceId(index, 0));
        binding.mode.setTag(String.valueOf(index));
    }

    private void onMode() {
        int index = Setting.getSyncMode();
        Setting.putSyncMode(index = index == mode.length() - 1 ? 0 : ++index);
        binding.mode.setImageResource(mode.getResourceId(index, 0));
        binding.mode.setTag(String.valueOf(index));
    }

    private void onScan() {
        ScanActivity.start(getActivity());
    }

    private void onRefresh() {
        if (binding == null || adapter == null) return;
        if (scanTask != null) scanTask.start(adapter.getIps());
        adapter.clear();
    }

    private void onSuccess() {
        dismiss();
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onScanEvent(ScanEvent event) {
        if (scanTask != null) scanTask.start(event.getAddress());
    }

    @Override
    public void onFind(List<Device> devices) {
        if (binding != null && devices.size() > 0) adapter.addAll(devices);
    }

    @Override
    public void onItemClick(Device item) {
        enqueue(OkHttp.newCall(client, String.format(Locale.getDefault(), "%s/action?do=sync&mode=%s&type=%s", item.getIp(), binding.mode.getTag().toString(), type), body.build()));
    }

    @Override
    public boolean onLongClick(Device item) {
        String mode = binding.mode.getTag().toString();
        if (mode.equals("0")) return false;
        SyncPulls.Ticket pull = mode.equals("2") ? SyncPulls.get().begin(item.getIp(), type, syncConfigUrl) : null;
        enqueue(OkHttp.newCall(client, String.format(Locale.getDefault(), "%s/action?do=sync&mode=%s&type=%s&force=true", item.getIp(), binding.mode.getTag().toString(), type), body.build()), pull);
        return true;
    }

    private void enqueue(Call call) {
        enqueue(call, null);
    }

    private void enqueue(Call call, SyncPulls.Ticket pull) {
        Call previous = activeCall;
        if (previous != null) previous.cancel();
        if (activePull != null) activePull.close();
        activePull = pull;
        activeCall = call;
        call.enqueue(getCallback(call, pull));
    }

    private Callback getCallback(Call request, SyncPulls.Ticket pull) {
        return new Callback() {
            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) {
                try (Response ignored = response) {
                    if (!response.isSuccessful()) {
                        onFailure(call, new IOException(response.code() + " " + response.message()));
                        return;
                    }
                    App.post(() -> { if (binding != null && activeCall == request) onSuccess(); });
                } finally {
                    if (pull != null) pull.close();
                }
            }

            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                if (pull != null) pull.close();
                App.post(() -> { if (binding != null && activeCall == request) Notify.show(e.getMessage()); });
            }
        };
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (scanTask != null) scanTask.stop();
        scanTask = null;
        Call call = activeCall;
        activeCall = null;
        if (activePull != null) activePull.close();
        activePull = null;
        if (call != null) call.cancel();
        App.removeCallbacks(refreshDevices);
        binding = null;
        EventBus.getDefault().unregister(this);
    }
}
