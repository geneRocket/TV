package com.fongmi.android.tv.repository;

import com.fongmi.android.tv.bean.Device;

import java.util.List;

/** Data-access boundary for saved and local peer devices. */
public final class DeviceRepository {

    private static final DeviceRepository INSTANCE = new DeviceRepository();

    public static DeviceRepository get() {
        return INSTANCE;
    }

    private DeviceRepository() {
    }

    public Device current() {
        return Device.get();
    }

    public Device fromJson(String value) {
        return Device.objectFrom(value);
    }

    public List<Device> all() {
        return Device.getAll();
    }

    public void deleteAll() {
        Device.delete();
    }
}
