package com.fongmi.android.tv.model;

import com.github.catvod.net.HttpCallScope;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.fongmi.android.tv.Constant;
import com.fongmi.android.tv.bean.Result;
import com.fongmi.android.tv.utils.AppTaskScheduler;
import com.fongmi.android.tv.utils.TaskScope;
import com.fongmi.android.tv.utils.ThreadPools;

import java.util.concurrent.Callable;

public abstract class BaseSiteViewModel extends ViewModel {

    protected final MutableLiveData<Result> result;
    protected final TaskScope requests;

    protected static final String REQUEST_RESULT = "result";

    public BaseSiteViewModel() {
        this.result = new MutableLiveData<>();
        this.requests = new TaskScope(ThreadPools.search(), AppTaskScheduler.get(), error -> ThreadPools.log(error, "Site request failed."), false);
    }

    public LiveData<Result> result() {
        return result;
    }

    protected void executeAsync(String requestKey, long timeoutMs, Callable<Result> callable, java.util.function.Consumer<Result> poster, java.util.function.Function<Throwable, Result> fallback) {
        HttpCallScope calls = new HttpCallScope();
        requests.submit(requestKey, () -> calls.call(callable), timeoutMs, poster, fallback, calls::close);
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        requests.close();
    }
}
