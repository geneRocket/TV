package com.fongmi.android.tv.ui.adapter;

import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.fongmi.android.tv.bean.Button;
import com.fongmi.android.tv.databinding.AdapterButtonsBinding;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ButtonsAdapter extends RecyclerView.Adapter<ButtonsAdapter.ViewHolder> {

    private List<Button> mItems;
    private int upFocus;
    private int downFocus;

    public ButtonsAdapter() {
        this.mItems = Button.sortedAll();
        this.upFocus = -1;
        this.downFocus = -1;
        setHasStableIds(true);
    }

    @Override
    public int getItemCount() {
        return mItems.size();
    }

    @Override
    public long getItemId(int position) {
        return mItems.get(position).getId();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(AdapterButtonsBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Button item = mItems.get(position);
        holder.binding.text.setText(item.getName());
        holder.binding.check.setChecked(getChecked(item));
        holder.binding.select.setOnLongClickListener(v -> onItemLongClick(holder));
        holder.binding.select.setOnClickListener(v -> onItemClick(holder));
        holder.binding.text.setGravity(Gravity.START);
        holder.binding.down.setOnClickListener(v -> onDownClick(holder));
        holder.binding.up.setOnClickListener(v -> onUpClick(holder));
        if (upFocus == position) holder.binding.up.requestFocus();
        else if (downFocus == position) holder.binding.down.requestFocus();
    }

    private boolean getChecked(Button item) {
        Map<Integer, Button> map = Button.getButtonsMap();
        if (map.containsKey(item.getId())) return true;
        return false;
    }

    private void onItemClick(@NonNull ViewHolder holder) {
        int position = holder.getBindingAdapterPosition();
        if (position == RecyclerView.NO_POSITION) return;
        Button item = mItems.get(position);
        boolean checked = getChecked(item);
        Map<Integer, Button> map = Button.getButtonsMap();
        if (checked) map.remove(item.getId());
        else map.put(item.getId(), item);
        save(mItems, map);
        notifyItemChanged(position);
    }

    private boolean onItemLongClick(@NonNull ViewHolder holder) {
        int position = holder.getBindingAdapterPosition();
        if (position == RecyclerView.NO_POSITION) return false;
        Button item = mItems.get(position);
        boolean checked = getChecked(item);
        Map<Integer, Button> map = new LinkedHashMap<>();
        if (!checked) map = Button.getMap(mItems);
        Button.save(map);
        notifyItemRangeChanged(0, getItemCount());
        return true;
    }

    private void onDownClick(@NonNull ViewHolder holder) {
        int position = holder.getBindingAdapterPosition();
        if (position == RecyclerView.NO_POSITION) return;
        if (position == getItemCount() - 1) return;
        List<Button> buttonList = Button.sortedAll();
        Button button = buttonList.get(position);
        buttonList.remove(position);
        buttonList.add(position + 1, button);
        Map<Integer, Button> map = Button.getMap(buttonList);
        Button.saveSorted(map);
        mItems = Button.sortedAll();
        save(mItems, Button.getButtonsMap());
        downFocus = position + 1;
        upFocus = -1;
        notifyItemMoved(position, position + 1);
        notifyItemRangeChanged(position, 2);
    }

    private void onUpClick(@NonNull ViewHolder holder) {
        int position = holder.getBindingAdapterPosition();
        if (position == RecyclerView.NO_POSITION) return;
        if (position == 0) return;
        List<Button> buttonList = Button.sortedAll();
        Button button = buttonList.get(position);
        buttonList.remove(position);
        buttonList.add(position - 1, button);
        Map<Integer, Button> map = Button.getMap(buttonList);
        Button.saveSorted(map);
        mItems = Button.sortedAll();
        save(mItems, Button.getButtonsMap());
        upFocus = position - 1;
        downFocus = -1;
        notifyItemMoved(position, position - 1);
        notifyItemRangeChanged(position - 1, 2);
    }

    private void save(List<Button> sortedItems, Map<Integer, Button> btnsMap) {
        List<Button> btns = new ArrayList<>();
        for(int i=0; i<sortedItems.size(); i++) {
            if (btnsMap.containsKey(sortedItems.get(i).getId())) btns.add(sortedItems.get(i));
        }
        Button.save(Button.getMap(btns));
    }


    static class ViewHolder extends RecyclerView.ViewHolder {

        private final AdapterButtonsBinding binding;

        ViewHolder(@NonNull AdapterButtonsBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }

}
