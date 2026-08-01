package com.fongmi.android.tv.player;

import com.fongmi.android.tv.bean.Result;

import java.util.Objects;

public final class PlaybackRequest {

    private String key;
    private String flag;
    private String id;
    private String token;

    public void set(String key, String flag, String id, String token) {
        this.key = key;
        this.flag = flag;
        this.id = id;
        this.token = token;
    }

    public String getToken() {
        return token;
    }

    public boolean matches(Result result) {
        return result != null
                && Objects.equals(result.getKey(), key)
                && Objects.equals(result.getRequestFlag(), flag)
                && Objects.equals(result.getRequestId(), id)
                && Objects.equals(result.getRequestToken(), token);
    }

    public void clear() {
        key = null;
        flag = null;
        id = null;
        token = null;
    }
}
