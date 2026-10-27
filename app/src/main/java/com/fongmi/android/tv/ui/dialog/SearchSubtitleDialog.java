package com.fongmi.android.tv.ui.dialog;

import android.content.DialogInterface;
import android.content.Context;
import android.os.Bundle;
import android.os.Parcelable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.view.KeyEvent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.activity.ComponentDialog;
import androidx.activity.OnBackPressedCallback;
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
    private boolean focusResults;
    private Parcelable searchLayout;
    private SubtitleSearchState.Screen renderedScreen;

    public static SearchSubtitleDialog create() {
        return new SearchSubtitleDialog();
    }

    public SearchSubtitleDialog title(String title) {
        this.currentTitle = title;
        Bundle arguments = getArguments() == null ? new Bundle() : getArguments();
        arguments.putString("subtitle_title", title);
        setArguments(arguments);
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
            if (actionId == EditorInfo.IME_ACTION_SEARCH || event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER) {
                if (event == null || event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) onSearch(binding.search);
                return true;
            }
            return false;
        });
        binding.search.setOnClickListener(this::onSearch);
        binding.previous.setOnClickListener(this::onPrevious);
        binding.next.setOnClickListener(this::onNext);
        binding.retry.setOnClickListener(view -> { focusResults = true; viewModel.retry(); });
        binding.backResults.setOnClickListener(view -> returnToSearch());
        ((ComponentDialog) requireDialog()).getOnBackPressedDispatcher().addCallback(getViewLifecycleOwner(), new OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() {
                if (!returnToSearch()) dismiss();
            }
        });
        binding.source.setOnClickListener(view -> {
            viewModel.cancelSubtitleResolve();
            new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.subtitle_sources).setSingleChoiceItems(sourceNames(), currentSource().ordinal(), (dialog,index) -> {
                    dialog.dismiss(); focusResults = true; searchLayout = null; viewModel.source(SubtitleSource.values()[index]);
                }).show();
        });
        binding.preference.setOnClickListener(view -> {
            viewModel.cancelSubtitleResolve();
            new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.subtitle_sort).setSingleChoiceItems(preferenceNames(), currentPreference().ordinal(), (dialog,index) -> {
                    dialog.dismiss(); viewModel.preference(SubtitlePreference.values()[index]);
                }).show();
        });
        binding.websites.setOnClickListener(view -> {
            String query = binding.input.getText().toString().trim();
            if (query.isEmpty()) { Notify.show(R.string.search_subtitle_empty); return; }
            viewModel.cancelSubtitleResolve();
            new MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.subtitle_websites)
                    .setItems(new String[]{"SubHD", "R3字幕網"}, (dialog,index) ->
                            SubtitleWebsiteDialog.show(requireContext(), index == 0 ? SubtitleWebsites.subhd(query)
                                    : SubtitleWebsites.r3(Trans.toTraditional(query)))).show();
        });

        if (currentTitle == null && getArguments() != null) currentTitle = getArguments().getString("subtitle_title");
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

    @Override public int getTheme() { return R.style.SubtitleSearchDialog; }

    private void onSearch(View view) {
        String query = binding.input.getText().toString().trim();
        focusResults = !query.isEmpty();
        searchLayout = null;
        binding.input.clearFocus();
        binding.search.requestFocus();
        InputMethodManager keyboard = (InputMethodManager) requireContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (keyboard != null) keyboard.hideSoftInputFromWindow(binding.input.getWindowToken(), 0);
        viewModel.searchResult(query, 1);
        if (query.isEmpty()) Notify.show(R.string.search_subtitle_empty);
    }

    private void onPrevious(View view) { focusResults = true; viewModel.previous(); }
    private void onNext(View view) { focusResults = true; viewModel.next(); }

    private boolean returnToSearch() {
        focusResults = true;
        return viewModel.backToSearch();
    }

    private void render(SubtitleSearchState state) {
        binding.source.setText(sourceNames()[state.source.ordinal()]);
        binding.preference.setText(preferenceNames()[state.preference.ordinal()]);
        java.util.List<String> unavailable = new java.util.ArrayList<>();
        for (String value : state.unavailable) {
            try { unavailable.add(sourceNames()[SubtitleSource.valueOf(value).ordinal()]); }
            catch (IllegalArgumentException ignored) { unavailable.add(value); }
        }
        java.util.List<String> status = new java.util.ArrayList<>();
        if (state.screen == SubtitleSearchState.Screen.SEARCH && !state.items.isEmpty()) status.add(getString(R.string.subtitle_page, state.page));
        if (!unavailable.isEmpty()) status.add(getString(R.string.subtitle_partial,android.text.TextUtils.join("、",unavailable)));
        boolean failed = state.phase == SubtitleSearchState.Phase.ERROR;
        if (failed) status.add(getString(R.string.error_subtitle_search_failed));
        binding.status.setText(android.text.TextUtils.join(" · ", status));
        binding.status.setVisibility(status.isEmpty() ? View.GONE : View.VISIBLE);
        binding.loading.setVisibility(state.loading() ? View.VISIBLE : View.GONE);
        binding.previous.setEnabled(state.canPrevious());
        binding.next.setEnabled(state.canNext());
        boolean files = state.screen == SubtitleSearchState.Screen.FILES;
        binding.previous.setVisibility(files ? View.GONE : View.VISIBLE);
        binding.next.setVisibility(files ? View.GONE : View.VISIBLE);
        binding.backResults.setVisibility(files ? View.VISIBLE : View.GONE);
        binding.retry.setVisibility(failed ? View.VISIBLE : View.GONE);
        adapter.replace(state.items);
        boolean showItems = !state.loading() && !state.items.isEmpty();
        binding.recycler.setVisibility(showItems ? View.VISIBLE : View.GONE);
        binding.empty.setVisibility(!state.loading() && state.items.isEmpty() ? View.VISIBLE : View.GONE);
        binding.empty.setText(failed ? R.string.error_subtitle_search_failed
                : state.query.isEmpty() ? R.string.search_subtitle_empty : R.string.no_results);
        if (!files && renderedScreen == SubtitleSearchState.Screen.FILES && searchLayout != null) {
            binding.recycler.getLayoutManager().onRestoreInstanceState(searchLayout);
            searchLayout = null;
        }
        renderedScreen = state.screen;
        if (showItems && focusResults) {
            focusResults = false;
            androidx.recyclerview.widget.RecyclerView list = binding.recycler;
            list.post(() -> {
                if (binding != null && binding.recycler == list && !binding.input.hasFocus()
                        && viewModel.searchResults().getValue() == state) list.requestFocus();
            });
        }
    }

    @Override
    public void onItemClick(Subtitle item, int position) {
        if (item.isExternal()) {
            viewModel.cancelSubtitleResolve();
            SubtitleWebsiteDialog.show(requireContext(),item.getUrl());
        } else if (Boolean.TRUE.equals(item.getIsZip())) {
            if (renderedScreen == SubtitleSearchState.Screen.SEARCH) searchLayout = binding.recycler.getLayoutManager().onSaveInstanceState();
            focusResults = true;
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
        resize();
    }

    @Override public void onConfigurationChanged(@NonNull android.content.res.Configuration configuration) {
        super.onConfigurationChanged(configuration);
        resize();
    }

    private void resize() {
        if (getDialog() != null && getDialog().getWindow() != null) {
            android.util.DisplayMetrics metrics = getResources().getDisplayMetrics();
            int margin = ResUtil.dp2px(24);
            getDialog().getWindow().setLayout(Math.max(1, Math.min(ResUtil.dp2px(600), metrics.widthPixels - margin)),
                    Math.max(1, Math.min(ResUtil.dp2px(600), metrics.heightPixels - margin)));
            View sheet = getDialog().findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (sheet != null) {
                ViewGroup.LayoutParams params = sheet.getLayoutParams();
                params.height = ViewGroup.LayoutParams.MATCH_PARENT;
                sheet.setLayoutParams(params);
            }
        }
    }

    public interface Listener {
        void onSubtitleSelected(Subtitle subtitle);
    }
}
