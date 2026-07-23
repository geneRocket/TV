package com.fongmi.android.tv.ui.presenter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.leanback.widget.Presenter;

import com.fongmi.android.tv.Setting;
import com.fongmi.android.tv.bean.Func;
import com.fongmi.android.tv.databinding.AdapterFuncBinding;
import com.fongmi.android.tv.databinding.AdapterFuncHorizontalBinding;

public class FuncPresenter extends Presenter {

    private final OnClickListener mListener;

    public FuncPresenter(OnClickListener listener) {
        this.mListener = listener;
    }

    public interface OnClickListener {
        void onItemClick(Func item);
    }

    @Override
    public Presenter.ViewHolder onCreateViewHolder(ViewGroup parent) {
        if (Setting.getHomeUI() == 1) return new HoriViewHolder(AdapterFuncHorizontalBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
        return new ViewHolder(AdapterFuncBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(Presenter.ViewHolder viewHolder, Object object) {
        if (viewHolder instanceof ViewHolder) bindView((ViewHolder) viewHolder, object);
        else if (viewHolder instanceof HoriViewHolder) bindView((HoriViewHolder) viewHolder, object);
    }

    private void bindView(ViewHolder viewHolder, Object object) {
        Func item = (Func) object;
        ViewHolder holder = viewHolder;
        bindRoot(holder.binding.getRoot(), item);
        holder.binding.text.setText(item.getText());
        holder.binding.icon.setImageResource(item.getDrawable());
        setOnClickListener(holder, view -> mListener.onItemClick(item));
    }

    private void bindView(HoriViewHolder viewHolder, Object object) {
        Func item = (Func) object;
        HoriViewHolder holder = viewHolder;
        bindRoot(holder.binding.getRoot(), item);
        holder.binding.text.setText(item.getText());
        holder.binding.icon.setImageResource(item.getDrawable());
        setOnClickListener(holder, view -> mListener.onItemClick(item));
    }

    private void bindRoot(View view, Func item) {
        view.setId(item.getId() > 0 ? item.getId() : View.NO_ID);
        view.setNextFocusLeftId(item.getNextFocusLeft() > 0 ? item.getNextFocusLeft() : View.NO_ID);
        view.setNextFocusRightId(item.getNextFocusRight() > 0 ? item.getNextFocusRight() : View.NO_ID);
        view.setContentDescription(item.getText());
    }

    @Override
    public void onUnbindViewHolder(Presenter.ViewHolder viewHolder) {
    }

    public static class ViewHolder extends Presenter.ViewHolder {

        private final AdapterFuncBinding binding;

        public ViewHolder(@NonNull AdapterFuncBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }

    public static class HoriViewHolder extends Presenter.ViewHolder {

        private final AdapterFuncHorizontalBinding binding;

        public HoriViewHolder(@NonNull AdapterFuncHorizontalBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
