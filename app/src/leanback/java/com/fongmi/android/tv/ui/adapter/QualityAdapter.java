package com.fongmi.android.tv.ui.adapter;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.fongmi.android.tv.bean.Result;
import com.fongmi.android.tv.databinding.AdapterQualityBinding;

public class QualityAdapter extends RecyclerView.Adapter<QualityAdapter.ViewHolder> {

    private final OnClickListener mListener;
    private int nextFocusDown;
    private Result mResult;
    private int position;

    public QualityAdapter(OnClickListener listener) {
        this.mListener = listener;
        this.mResult = Result.empty();
        setHasStableIds(true);
    }

    public interface OnClickListener {

        void onItemClick(Result result);
    }

    public boolean setNextFocusDown(int nextFocusDown) {
        if (this.nextFocusDown == nextFocusDown) return false;
        this.nextFocusDown = nextFocusDown;
        return true;
    }

    public int getPosition() {
        return position;
    }

    public void addAll(Result result) {
        mResult = result;
        notifyDataSetChanged();
    }

    @Override
    public int getItemCount() {
        return mResult.getUrl().getValues().size();
    }

    @Override
    public long getItemId(int position) {
        return (mResult.getUrl().n(position) + "@" + mResult.getUrl().v(position)).hashCode();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(AdapterQualityBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.binding.text.setNextFocusDownId(nextFocusDown);
        holder.binding.text.setText(mResult.getUrl().n(position));
        holder.binding.text.setOnClickListener(v -> onItemClick(holder));
        holder.binding.text.setActivated(mResult.getUrl().getPosition() == position);
    }

    private void onItemClick(@NonNull ViewHolder holder) {
        int position = holder.getBindingAdapterPosition();
        if (position == RecyclerView.NO_POSITION) return;
        int oldPosition = mResult.getUrl().getPosition();
        this.position = position;
        mResult.getUrl().set(position);
        mListener.onItemClick(mResult);
        if (oldPosition == position) {
            if (position >= 0 && position < getItemCount()) notifyItemChanged(position);
        } else {
            if (oldPosition >= 0 && oldPosition < getItemCount()) notifyItemChanged(oldPosition);
            if (position >= 0 && position < getItemCount()) notifyItemChanged(position);
        }
    }

    static class ViewHolder extends RecyclerView.ViewHolder {

        private final AdapterQualityBinding binding;

        ViewHolder(@NonNull AdapterQualityBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
