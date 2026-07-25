package com.fongmi.android.tv.ui.presenter;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.leanback.widget.Presenter;

import com.fongmi.android.tv.Product;
import com.fongmi.android.tv.bean.Episode;
import com.fongmi.android.tv.databinding.AdapterEpisodeBinding;

public class EpisodePresenter extends Presenter {

    private final OnClickListener mListener;
    private int nextFocusDown;
    private int nextFocusUp;
    private int numColumns;
    private int numRows;

    public EpisodePresenter(OnClickListener listener) {
        this.mListener = listener;
    }

    public interface OnClickListener {
        void onItemClick(Episode item);
    }

    public boolean setNextFocusDown(int nextFocus) {
        if (this.nextFocusDown == nextFocus) return false;
        this.nextFocusDown = nextFocus;
        return true;
    }

    public boolean setNextFocusUp(int nextFocus) {
        if (this.nextFocusUp == nextFocus) return false;
        this.nextFocusUp = nextFocus;
        return true;
    }

    public int getNumColumns() {
        return this.numColumns;
    }

    public void setNumColumns(int numColumns) {
        this.numColumns = numColumns;
    }

    public int getNumRows() {
        return this.numRows;
    }

    public void setNumRows(int numRows) {
        this.numRows = numRows;
    }

    @Override
    public Presenter.ViewHolder onCreateViewHolder(ViewGroup parent) {
        return new ViewHolder(AdapterEpisodeBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(Presenter.ViewHolder viewHolder, Object object) {
        Episode item = (Episode) object;
        ViewHolder holder = (ViewHolder) viewHolder;
        holder.binding.text.setActivated(item.isActivated());
        holder.binding.text.setText(item.getDesc().concat(item.getName()));
        holder.binding.text.setNextFocusUpId(numColumns > 0 ? (item.getIndex() < numColumns ? nextFocusUp : 0) : nextFocusUp);
        holder.binding.text.setNextFocusDownId(numColumns > 0 ? (item.getIndex() >= (numRows - 1) * numColumns ? nextFocusDown : 0) : nextFocusDown);
        holder.item = item;
    }

    @Override
    public void onUnbindViewHolder(Presenter.ViewHolder viewHolder) {
        ((ViewHolder) viewHolder).item = null;
    }

    public class ViewHolder extends Presenter.ViewHolder {

        private final AdapterEpisodeBinding binding;
        private Episode item;

        public ViewHolder(@NonNull AdapterEpisodeBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
            this.binding.text.setMaxEms(Product.getEms());
            this.binding.getRoot().setOnClickListener(view -> {
                if (item != null) mListener.onItemClick(item);
            });
        }
    }
}
