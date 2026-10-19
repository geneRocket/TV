package com.fongmi.android.tv.model;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

/** Configuration state shared only by a main activity and its home fragment. */
public class HomeConfigViewModel extends ViewModel {
    public enum State { IDLE, LOADING, READY, FAILED }

    private final MutableLiveData<State> state = new MutableLiveData<>(State.IDLE);

    public LiveData<State> state() {
        return state;
    }

    public void loading() {
        state.setValue(State.LOADING);
    }

    public void ready() {
        state.setValue(State.READY);
    }

    public void failed() {
        state.setValue(State.FAILED);
    }
}
