package com.fongmi.android.tv.ui.dialog;

import android.content.DialogInterface;
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
import com.fongmi.android.tv.subtitle.SubtitleSearchState;
import com.fongmi.android.tv.subtitle.SubtitleSource;
import com.fongmi.android.tv.subtitle.SubtitlePreference;
import com.fongmi.android.tv.subtitle.SubtitleWebsites;
import com.fongmi.android.tv.databinding.DialogSearchSubtitleBinding;
import com.fongmi.android.tv.ui.adapter.SearchSubtitleAdapter;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.ResUtil;
import com.fongmi.android.tv.viewmodel.SubtitleViewModel;
import com.github.catvod.utils.Trans;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

public class SearchSubtitleDialog extends BaseDialog implements SearchSubtitleAdapter.OnClickListener {

    private DialogSearchSubtitleBinding binding;
    private SearchSubtitleAdapter adapter;
    private SubtitleViewModel viewModel;
    private Listener listener;
    private String currentTitle;

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
    }

    @Override
    protected void initEvent() {
        viewModel = new ViewModelProvider(this).get(SubtitleViewModel.class);
        viewModel.searchResults().observe(getViewLifecycleOwner(), this::render);
        binding.input.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) onSearch(binding.search);
            return true;
        });
        binding.search.setOnClickListener(this::onSearch);
        binding.previous.setOnClickListener(this::onPrevious);
        binding.next.setOnClickListener(this::onNext);
        binding.source.setOnClickListener(view -> new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.subtitle_sources).setSingleChoiceItems(sourceNames(), currentSource().ordinal(), (dialog,index) -> {
                    dialog.dismiss(); viewModel.source(SubtitleSource.values()[index]);
                }).show());
        binding.preference.setOnClickListener(view -> new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.subtitle_sort).setSingleChoiceItems(preferenceNames(), currentPreference().ordinal(), (dialog,index) -> {
                    dialog.dismiss(); viewModel.preference(SubtitlePreference.values()[index]);
                }).show());
        binding.websites.setOnClickListener(view -> {
            String query = binding.input.getText().toString().trim();
            if (query.isEmpty()) { Notify.show(R.string.search_subtitle_empty); return; }
            viewModel.cancelSubtitleResolve();
            new MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.subtitle_websites)
                    .setItems(new String[]{"SubHD", "R3字幕網"}, (dialog,index) ->
                            SubtitleWebsiteDialog.show(requireContext(), index == 0 ? SubtitleWebsites.subhd(query)
                                    : SubtitleWebsites.r3(Trans.toTraditional(query)))).show();
        });

        if (viewModel.searchResults().getValue() != null) {
            binding.input.setText(viewModel.searchResults().getValue().query);
        } else if (currentTitle != null && !currentTitle.isEmpty()) {
            binding.input.setText(currentTitle);
            onSearch(binding.search);
        }
    }

    @Override
    protected boolean transparent() {
        return false;
    }

    private void onSearch(View view) {
        String query = binding.input.getText().toString().trim();
        viewModel.searchResult(query, 1);
        if (query.isEmpty()) Notify.show(R.string.search_subtitle_empty);
    }

    private void onPrevious(View view) { viewModel.previous(); }
    private void onNext(View view) { viewModel.next(); }

    private void render(SubtitleSearchState state) {
        binding.source.setText(sourceNames()[state.source.ordinal()]);
        binding.preference.setText(preferenceNames()[state.preference.ordinal()]);
        binding.status.setVisibility(state.unavailable.isEmpty() ? View.GONE : View.VISIBLE);
        java.util.List<String> unavailable = new java.util.ArrayList<>();
        for (String value : state.unavailable) {
            try { unavailable.add(sourceNames()[SubtitleSource.valueOf(value).ordinal()]); }
            catch (IllegalArgumentException ignored) { unavailable.add(value); }
        }
        binding.status.setText(getString(R.string.subtitle_partial,android.text.TextUtils.join("、",unavailable)));
        binding.loading.setVisibility(state.loading() ? View.VISIBLE : View.GONE);
        binding.previous.setEnabled(state.canPrevious());
        binding.next.setEnabled(state.canNext());
        adapter.clear();
        adapter.addAll(state.items);
        boolean showItems = !state.loading() && !state.items.isEmpty();
        binding.recycler.setVisibility(showItems ? View.VISIBLE : View.GONE);
        binding.empty.setVisibility(!state.loading() && state.items.isEmpty() ? View.VISIBLE : View.GONE);
        boolean failed = state.phase == SubtitleSearchState.Phase.ERROR;
        binding.empty.setText(failed ? R.string.error_subtitle_search_failed : R.string.search_subtitle_empty);
        if (failed) Notify.show(R.string.error_subtitle_search_failed);
        if (showItems) binding.recycler.post(() -> {
            if (getView() != null) binding.recycler.requestFocus();
        });
    }

    @Override
    public void onItemClick(Subtitle item, int position) {
        if (item.isExternal()) {
            viewModel.cancelSubtitleResolve();
            SubtitleWebsiteDialog.show(requireContext(),item.getUrl());
        } else if (Boolean.TRUE.equals(item.getIsZip())) {
            viewModel.getSearchResultSubtitleUrls(item);
        } else {
            viewModel.getSubtitleUrl(item, subtitle -> {
                if (!isAdded() || getDialog() == null || !getDialog().isShowing()) return;
                if (listener != null) listener.onSubtitleSelected(subtitle);
                dismiss();
            });
        }
    }

    private String[] sourceNames() { return new String[]{getString(R.string.subtitle_source_all), "ASSRT", "SubHD", "Subtitles.website"}; }
    private String[] preferenceNames() {
        return new String[]{getString(R.string.subtitle_pref_chinese),getString(R.string.subtitle_pref_cantonese),
                getString(R.string.subtitle_pref_traditional),getString(R.string.subtitle_pref_bilingual),getString(R.string.subtitle_pref_all)};
    }
    private SubtitleSource currentSource() {
        SubtitleSearchState value=viewModel.searchResults().getValue(); return value == null ? SubtitleSource.ALL : value.source;
    }
    private SubtitlePreference currentPreference() {
        SubtitleSearchState value=viewModel.searchResults().getValue(); return value == null ? SubtitlePreference.CHINESE : value.preference;
    }

    @Override
    public void onDismiss(@NonNull DialogInterface dialog) {
        if (viewModel != null) viewModel.cancelSubtitleResolve();
        super.onDismiss(dialog);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
        adapter = null;
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
