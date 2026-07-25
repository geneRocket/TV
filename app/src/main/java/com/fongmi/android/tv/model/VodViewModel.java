package com.fongmi.android.tv.model;

import androidx.lifecycle.MutableLiveData;

import com.fongmi.android.tv.Constant;
import com.fongmi.android.tv.bean.History;
import com.fongmi.android.tv.bean.Result;
import com.fongmi.android.tv.bean.Vod;
import com.fongmi.android.tv.repository.HistoryRepository;
import com.fongmi.android.tv.repository.SpiderRepository;

public class VodViewModel extends BaseSiteViewModel {

    private final MutableLiveData<Vod> vod;
    private final MutableLiveData<History> history;

    public VodViewModel() {
        super();
        this.vod = new MutableLiveData<>();
        this.history = new MutableLiveData<>();
    }

    public MutableLiveData<Vod> getVod() {
        return vod;
    }

    public MutableLiveData<History> getHistory() {
        return history;
    }

    public void detailContent(String key, String id) {
        executeAsync(REQUEST_RESULT, Constant.TIMEOUT_VOD, () -> SpiderRepository.get().detailContent(key, id), data -> {
            Result result = data == null ? Result.empty() : data;
            if (result.getList().isEmpty()) {
                vod.postValue(null);
            } else {
                vod.postValue(result.getList().get(0));
            }
        }, error -> Result.empty());
    }

    public void checkHistory(String key) {
        history.postValue(HistoryRepository.get().find(key));
    }
}
