package com.fongmi.android.tv.ui.dialog;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.Constant;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.bean.Device;
import com.fongmi.android.tv.repository.DeviceRepository;
import com.fongmi.android.tv.event.ScanEvent;
import com.fongmi.android.tv.utils.ScanTask;
import com.fongmi.android.tv.databinding.DialogDeviceBinding;
import com.fongmi.android.tv.impl.Callback;
import com.fongmi.android.tv.server.Server;
import com.fongmi.android.tv.ui.activity.ScanActivity;
import com.fongmi.android.tv.ui.adapter.DeviceAdapter;
import com.fongmi.android.tv.utils.Notify;
import com.github.catvod.net.OkHttp;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.io.File;
import java.io.IOException;
import java.util.List;

import okhttp3.Call;
import okhttp3.FormBody;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.RequestBody;
import okhttp3.Response;

public class TransmitDialog extends BaseDialog implements DeviceAdapter.OnClickListener, ScanTask.Listener {

    private RequestBody requestBody;
    private final OkHttpClient client;
    private final Runnable refreshDevices = this::onRefresh;
    private DialogDeviceBinding binding;
    private DeviceAdapter adapter;
    private ScanTask scanTask;
    private Call activeCall;
    private String type;

    public static TransmitDialog create() {
        return new TransmitDialog();
    }

    public TransmitDialog() {
        client = OkHttp.client(Constant.TIMEOUT_TRANSMIT);
    }

    public TransmitDialog apk(String path) {
        type = "apk";
        File file = new File(path);
        MediaType mediaType = MediaType.parse("multipart/form-data");
        MultipartBody.Builder body = new MultipartBody.Builder();
        body.setType(MultipartBody.FORM);
        body.addFormDataPart("name", file.getName());
        body.addFormDataPart("files-0", file.getName(), RequestBody.create(mediaType, file));
        requestBody = body.build();
        return this;
    }

    public TransmitDialog vodConfig() {
        type = "vod_config";
        FormBody.Builder body = new FormBody.Builder();
        if (VodConfig.getUrl() != null) body.add("url", VodConfig.getUrl());
        requestBody = body.build();
        return this;
    }

    public TransmitDialog wallConfig(String path) {
        type = "wall_config";
        File file = new File(path);
        MediaType mediaType = MediaType.parse("multipart/form-data");
        MultipartBody.Builder body = new MultipartBody.Builder();
        body.setType(MultipartBody.FORM);
        body.addFormDataPart("name", file.getName());
        body.addFormDataPart("files-0", file.getName(), RequestBody.create(mediaType, file));
        requestBody = body.build();
        return this;
    }

    public TransmitDialog pushRetore(String path) {
        type = "push_restore";
        File file = new File(path);
        MediaType mediaType = MediaType.parse("multipart/form-data");
        MultipartBody.Builder body = new MultipartBody.Builder();
        body.setType(MultipartBody.FORM);
        body.addFormDataPart("name", file.getName());
        body.addFormDataPart("files-0", file.getName(), RequestBody.create(mediaType, file));
        requestBody = body.build();
        return this;
    }

    public TransmitDialog pullRetore() {
        type = "pull_restore";
        FormBody.Builder body = new FormBody.Builder();
        body.add("ip", Server.get().getAddress());
        requestBody = body.build();
        return this;
    }

    public void show(Fragment fragment) {
        show(fragment.getActivity());
    }

    public void show(FragmentActivity activity) {
        for (Fragment f : activity.getSupportFragmentManager().getFragments()) if (f instanceof BottomSheetDialogFragment) return;
        show(activity.getSupportFragmentManager(), null);
    }

    @Override
    protected ViewBinding getBinding(@NonNull LayoutInflater inflater, @Nullable ViewGroup container) {
        return binding = DialogDeviceBinding.inflate(inflater, container, false);
    }

    @Override
    protected void initView() {
        EventBus.getDefault().register(this);
        scanTask = ScanTask.create(this);
        setRecyclerView();
        getDevice();
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
        adapter.addAll(DeviceRepository.get().all());
        if (adapter.getItemCount() == 0) App.post(refreshDevices, 1000);
    }

    private void onRefresh() {
        if (binding == null || adapter == null) return;
        if (scanTask != null) scanTask.start(adapter.getIps());
        adapter.clear();
    }

    private void onScan() {
        ScanActivity.start(getActivity());
    }

    private void onSuccess() {
        dismiss();
        Notify.dismiss();
    }

    private void onError(Exception e) {
        Notify.show(e.getMessage());
        Notify.dismiss();
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
        Notify.progress(getContext());
        enqueue(OkHttp.newCall(client, item.getIp().concat("/action?do=transmit&type=").concat(type), requestBody));
    }

    @Override
    public boolean onLongClick(Device item) {
        Notify.progress(getContext());
        enqueue(OkHttp.newCall(client, item.getIp().concat("/action?do=transmit&type=").concat(type), requestBody));
        return true;
    }

    private void enqueue(Call call) {
        Call previous = activeCall;
        if (previous != null) previous.cancel();
        activeCall = call;
        call.enqueue(getCallback(call));
    }

    private Callback getCallback(Call request) {
        return new Callback() {
            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) {
                try (Response ignored = response) {
                    App.post(() -> { if (binding != null && activeCall == request) onSuccess(); });
                }
            }

            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                App.post(() -> { if (binding != null && activeCall == request) onError(e); });
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
        if (call != null) call.cancel();
        App.removeCallbacks(refreshDevices);
        binding = null;
        EventBus.getDefault().unregister(this);
    }
}
