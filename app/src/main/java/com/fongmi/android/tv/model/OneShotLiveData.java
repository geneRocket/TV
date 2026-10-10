package com.fongmi.android.tv.model;

import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;
import java.util.concurrent.atomic.AtomicBoolean;

/** A command has one consumer; replacing its observer cannot replay it. */
public final class OneShotLiveData<T> extends MutableLiveData<T> {
    private final AtomicBoolean pending = new AtomicBoolean();
    @Override public void observe(LifecycleOwner owner, Observer<? super T> observer) {
        super.observe(owner, value -> { if (pending.compareAndSet(true, false)) observer.onChanged(value); });
    }
    public void discard() { pending.set(false); super.setValue(null); }
    @Override public void setValue(T value) { pending.set(true); super.setValue(value); }
}
