package com.fongmi.android.tv.ui.dialog;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewbinding.ViewBinding;
import androidx.viewpager2.adapter.FragmentStateAdapter;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.bean.Episode;
import com.fongmi.android.tv.databinding.DialogEpisodeGridBinding;
import com.fongmi.android.tv.ui.fragment.EpisodeFragment;
import com.fongmi.android.tv.ui.presenter.EpisodePageLabels;
import com.fongmi.android.tv.utils.ResUtil;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.tabs.TabLayoutMediator;

import java.util.ArrayList;
import java.util.List;

public class EpisodeGridDialog extends BaseDialog {

    private DialogEpisodeGridBinding binding;
    private List<Episode> episodes = new ArrayList<>();
    private TabLayoutMediator mediator;
    private final List<String> titles;
    private boolean download;
    private boolean reverse;
    private int spanCount;
    private int itemCount;

    public static EpisodeGridDialog create() {
        return new EpisodeGridDialog();
    }

    public EpisodeGridDialog() {
        this.titles = new ArrayList<>();
        this.spanCount = 5;
        this.download = false;
    }

    public EpisodeGridDialog reverse(boolean reverse) {
        this.reverse = reverse;
        arguments().putBoolean("reverse", reverse);
        return this;
    }

    public EpisodeGridDialog download(boolean download) {
        this.download = download;
        arguments().putBoolean("download", download);
        return this;
    }

    public EpisodeGridDialog episodes(List<Episode> episodes) {
        this.episodes = episodes == null ? new ArrayList<>() : new ArrayList<>(episodes);
        arguments().putParcelableArrayList("episodes", new ArrayList<>(this.episodes));
        return this;
    }

    private Bundle arguments() {
        if (getArguments() == null) setArguments(new Bundle());
        return getArguments();
    }

    @Override public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Bundle args = getArguments();
        if (args == null) return;
        ArrayList<Episode> items = args.getParcelableArrayList("episodes");
        episodes = items == null ? new ArrayList<>() : items;
        reverse = args.getBoolean("reverse");
        download = args.getBoolean("download");
    }

    public void show(FragmentActivity activity) {
        for (Fragment f : activity.getSupportFragmentManager().getFragments()) if (f instanceof BottomSheetDialogFragment) return;
        show(activity.getSupportFragmentManager(), null);
    }

    @Override
    protected ViewBinding getBinding(@NonNull LayoutInflater inflater, @Nullable ViewGroup container) {
        return binding = DialogEpisodeGridBinding.inflate(inflater, container, false);
    }

    @Override
    protected void initView() {
        binding.episode.setText(download ? R.string.detail_download : R.string.detail_episode);
        setSpanCount();
        setTitles();
        setPager();
    }

    private void setSpanCount() {
        int total = 0;
        spanCount = 5;
        int row = ResUtil.isLand(getActivity()) ? 5 : 10;
        for (Episode item : episodes) total += item.getName().length();
        int offset = (int) Math.ceil((double) total / Math.max(1, episodes.size()));
        if (offset >= 12) spanCount = 1;
        else if (offset >= 8) spanCount = 2;
        else if (offset >= 4) spanCount = 3;
        else if (offset >= 2) spanCount = 4;
        itemCount = spanCount * row;
    }

    private void setTitles() {
        titles.clear();
        titles.addAll(EpisodePageLabels.create(episodes.size(), itemCount, reverse));
    }

    private void setPager() {
        binding.pager.setAdapter(new PageAdapter(this));
        mediator = new TabLayoutMediator(binding.tabs, binding.pager, (tab, position) -> tab.setText(titles.get(position)));
        mediator.attach();
        setCurrentPage();
    }

    private void setCurrentPage() {
        for (int i = 0; i < episodes.size(); i++) {
            if (episodes.get(i).isActivated()) {
                binding.pager.setCurrentItem(i / itemCount);
                break;
            }
        }
    }

    class PageAdapter extends FragmentStateAdapter {

        public PageAdapter(@NonNull Fragment fragment) {
            super(fragment);
        }

        @NonNull
        @Override
        public Fragment createFragment(int position) {
            return EpisodeFragment.newInstance(spanCount, episodes.subList(position * itemCount, Math.min(position * itemCount + itemCount, episodes.size())), download);
        }

        @Override
        public int getItemCount() {
            return titles.size();
        }
    }

    @Override public void onDestroyView() {
        if (mediator != null) mediator.detach();
        mediator = null;
        binding.pager.setAdapter(null);
        binding = null;
        super.onDestroyView();
    }
}
