package com.fongmi.android.tv.ui.adapter;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.bean.Class;
import com.fongmi.android.tv.bean.Result;
import com.fongmi.android.tv.databinding.AdapterTypeBinding;
import com.fongmi.android.tv.utils.ResUtil;

import java.util.ArrayList;
import java.util.List;

public class TypeAdapter extends RecyclerView.Adapter<TypeAdapter.ViewHolder> {

    private final OnClickListener mListener;
    private final List<Class> mItems;

    public TypeAdapter(OnClickListener listener) {
        this.mListener = listener;
        this.mItems = new ArrayList<>();
    }

    public interface OnClickListener {

        void onItemClick(int position, Class item);
    }

    private Class home() {
        Class type = new Class();
        type.setTypeName(ResUtil.getString(R.string.vod_home));
        type.setTypeId("home");
        return type;
    }

    public void clear() {
        mItems.clear();
        notifyDataSetChanged();
    }

    public void addAll(Result result) {
        String activated = getActivatedId();
        mItems.clear();
        if (result != null) {
            List<String> ids = new ArrayList<>();
            for (Class item : result.getTypes()) {
                if (item == null || ids.contains(item.getTypeId())) continue;
                ids.add(item.getTypeId());
                mItems.add(item);
            }
            if (result.getList().size() > 0) mItems.add(0, home());
        }
        setActivated(activated);
        notifyDataSetChanged();
    }

    private String getActivatedId() {
        for (Class item : mItems) if (item.isActivated()) return item.getTypeId();
        return "";
    }

    private void setActivated(String typeId) {
        if (mItems.isEmpty()) return;
        int position = 0;
        for (int i = 0; i < mItems.size(); i++) if (mItems.get(i).getTypeId().equals(typeId)) position = i;
        for (Class item : mItems) item.setActivated(false);
        mItems.get(position).setActivated(true);
    }

    public void setActivated(int position) {
        if (mItems.isEmpty()) return;
        if (position < 0 || position >= mItems.size()) return;
        int oldPosition = 0;
        for (int i = 0; i < mItems.size(); i++) if (mItems.get(i).isActivated()) oldPosition = i;
        for (Class item : mItems) item.setActivated(false);
        mItems.get(position).setActivated(true);
        if (oldPosition == position) {
            notifyItemChanged(position);
        } else {
            notifyItemChanged(oldPosition);
            notifyItemChanged(position);
        }
    }

    public Class get(int position) {
        return mItems.get(position);
    }

    public int indexOf(String typeId) {
        for (int i = 0; i < mItems.size(); i++) if (mItems.get(i).getTypeId().equals(typeId)) return i;
        return 0;
    }

    @Override
    public int getItemCount() {
        return mItems.size();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(AdapterTypeBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Class item = mItems.get(position);
        holder.binding.text.setText(item.getTypeName());
        holder.binding.text.setActivated(item.isActivated());
        holder.binding.text.setOnClickListener(v -> {
            int index = holder.getBindingAdapterPosition();
            if (index == RecyclerView.NO_POSITION) return;
            mListener.onItemClick(index, mItems.get(index));
        });
    }

    static class ViewHolder extends RecyclerView.ViewHolder {

        private final AdapterTypeBinding binding;

        ViewHolder(@NonNull AdapterTypeBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
