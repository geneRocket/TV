package com.fongmi.android.tv.ui.dialog;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.bean.Subtitle;
import com.fongmi.android.tv.bean.SubtitleData;
import com.fongmi.android.tv.databinding.DialogSearchSubtitleBinding;
import com.fongmi.android.tv.ui.adapter.SearchSubtitleAdapter;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.ResUtil;
import com.fongmi.android.tv.viewmodel.SubtitleViewModel;

public class SearchSubtitleDialog extends BaseDialog implements SearchSubtitleAdapter.OnClickListener {

    private DialogSearchSubtitleBinding binding;
    private SearchSubtitleAdapter adapter;
    private SubtitleViewModel viewModel;
    private Listener listener;
    private String currentTitle;
    private int currentPage;
    private boolean isZip;

    public static SearchSubtitleDialog create() {
        return new SearchSubtitleDialog();
    }

    public SearchSubtitleDialog title(String title) {
        this.currentTitle = title;
        return this;
    }

    public SearchSubtitleDialog listener(Listener listener) {
        this.listener = listener;
        return this;
    }

    public void show(FragmentActivity activity) {
        for (Fragment f : activity.getSupportFragmentManager().getFragments()) if (f instanceof SearchSubtitleDialog) return;
        if (activity instanceof Listener && this.listener == null) {
            this.listener = (Listener) activity;
        }
        show(activity.getSupportFragmentManager(), null);
    }

    @Override
    protected ViewBinding getBinding(@NonNull LayoutInflater inflater, @Nullable ViewGroup container) {
        return binding = DialogSearchSubtitleBinding.inflate(inflater, container, false);
    }

    @Override
    protected void initView() {
        binding.recycler.setHasFixedSize(true);
        binding.recycler.setItemAnimator(null);
        binding.recycler.setLayoutManager(new LinearLayoutManager(getContext()));
        binding.recycler.setAdapter(adapter = new SearchSubtitleAdapter(this));
        binding.recycler.setVisibility(View.GONE);
        binding.loading.setVisibility(View.GONE);
        binding.empty.setVisibility(View.GONE);
        binding.previous.setEnabled(false);
        binding.next.setEnabled(false);
        currentPage = 1;
        isZip = false;
    }

    @Override
    protected void initEvent() {
        viewModel = new ViewModelProvider(this).get(SubtitleViewModel.class);
        viewModel.searchResult.observe(this, result -> {
            if (result == null) {
                binding.loading.setVisibility(View.GONE);
                binding.empty.setVisibility(View.VISIBLE);
                binding.recycler.setVisibility(View.GONE);
                binding.empty.setText(R.string.error_subtitle_search_failed);
                Notify.show(R.string.error_subtitle_search_failed);
            } else {
                onSearchResult(result);
            }
        });
        binding.input.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) onSearch(binding.search);
            return true;
        });
        binding.search.setOnClickListener(this::onSearch);
        binding.previous.setOnClickListener(this::onPrevious);
        binding.next.setOnClickListener(this::onNext);
        binding.download.setOnClickListener(this::onDownload);

        if (currentTitle != null && !currentTitle.isEmpty()) {
            binding.input.setText(currentTitle);
            onSearch(binding.search);
        }
    }

    @Override
    protected boolean transparent() {
        return false;
    }

    private void onSearch(View view) {
        String title = binding.input.getText().toString().trim();
        if (title.isEmpty()) {
            binding.empty.setVisibility(View.VISIBLE);
            binding.recycler.setVisibility(View.GONE);
            binding.empty.setText(R.string.search_subtitle_empty);
            Notify.show(R.string.search_subtitle_empty);
            return;
        }
        currentPage = 1;
        adapter.clear();
        binding.empty.setVisibility(View.GONE);
        binding.recycler.setVisibility(View.GONE);
        binding.loading.setVisibility(View.VISIBLE);
        binding.previous.setEnabled(false);
        binding.next.setEnabled(false);
        viewModel.searchResult(title, currentPage);
    }

    private void onPrevious(View view) {
        if (currentPage > 1) {
            currentPage--;
            adapter.clear();
            binding.empty.setVisibility(View.GONE);
            binding.loading.setVisibility(View.VISIBLE);
            binding.previous.setEnabled(false);
            binding.next.setEnabled(false);
            String title = binding.input.getText().toString().trim();
            viewModel.searchResult(title, currentPage);
        }
    }

    private void onNext(View view) {
        currentPage++;
        adapter.clear();
        binding.empty.setVisibility(View.GONE);
        binding.loading.setVisibility(View.VISIBLE);
        binding.previous.setEnabled(false);
        binding.next.setEnabled(false);
        String title = binding.input.getText().toString().trim();
        viewModel.searchResult(title, currentPage);
    }

    private void onDownload(View view) {
        // Download subtitle file if needed
    }

    private void onSearchResult(SubtitleData data) {
        binding.loading.setVisibility(View.GONE);
        binding.previous.setEnabled(currentPage > 1);
        binding.next.setEnabled(data.getSubtitleList().size() > 0);
        if (data.getSubtitleList().isEmpty()) {
            binding.empty.setVisibility(View.VISIBLE);
            binding.recycler.setVisibility(View.GONE);
        } else {
            isZip = data.getIsZip();
            adapter.addAll(data.getSubtitleList());
            binding.recycler.setVisibility(View.VISIBLE);
            binding.empty.setVisibility(View.GONE);
            binding.recycler.post(() -> binding.recycler.requestFocus());
        }
    }

    @Override
    public void onItemClick(Subtitle item, int position) {
        if (isZip) {
            binding.loading.setVisibility(View.VISIBLE);
            binding.recycler.setVisibility(View.GONE);
            binding.previous.setEnabled(false);
            binding.next.setEnabled(false);
            adapter.clear();
            viewModel.getSearchResultSubtitleUrls(item);
        } else {
            if (listener != null) listener.onSubtitleSelected(item);
            dismiss();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        setDimAmount(0.5f);
        if (getDialog() != null && getDialog().getWindow() != null) getDialog().getWindow().setLayout(ResUtil.dp2px(600), ResUtil.dp2px(600));
    }

    public interface Listener {
        void onSubtitleSelected(Subtitle subtitle);
    }
}
