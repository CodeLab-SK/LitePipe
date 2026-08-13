package com.codelabsk.litepipe.ui;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SearchView;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.codelabsk.litepipe.R;
import com.codelabsk.litepipe.extension.Constant;
import com.codelabsk.litepipe.extension.Extension;
import com.codelabsk.litepipe.extension.ExtensionManager;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public class SettingsActivity extends AppCompatActivity {

    @Inject ExtensionManager extensionManager;
    
    private View scrollView;
    private RecyclerView resultsRecycler;
    private SearchAdapter searchAdapter;
    private final List<SearchResult> allSettings = new ArrayList<>();

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_settings);

        View mainView = findViewById(android.R.id.content);
        ViewCompat.setOnApplyWindowInsetsListener(mainView, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        scrollView = findViewById(R.id.settings_scroll_view);
        resultsRecycler = findViewById(R.id.search_results_recycler);
        resultsRecycler.setLayoutManager(new LinearLayoutManager(this));
        searchAdapter = new SearchAdapter();
        resultsRecycler.setAdapter(searchAdapter);

        prepareSearchData();

        SearchView searchView = findViewById(R.id.search_view);
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {

            @Override
            public boolean onQueryTextSubmit(String query) {
                performSearch(query);
                return true;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                performSearch(newText);
                return true;
            }
        });
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (resultsRecycler.getVisibility() == View.VISIBLE) {
                    closeSearch(searchView);
                } else {
                    setEnabled(false);
                    getOnBackPressedDispatcher().onBackPressed();
                }
            }
        });

        findViewById(R.id.category_general).setOnClickListener(v -> 
            openSubSettings(0, R.string.general));

        findViewById(R.id.category_shorts).setOnClickListener(v -> 
            openSubSettings(1, R.string.shorts));

        findViewById(R.id.category_player).setOnClickListener(v -> 
            openSubSettings(2, R.string.player));

        findViewById(R.id.category_sponsorblock).setOnClickListener(v -> 
            openSubSettings(3, R.string.sponsorblock));

        findViewById(R.id.category_download).setOnClickListener(v -> 
            openSubSettings(4, R.string.download));

        findViewById(R.id.reset_layout).setOnClickListener(v -> showResetConfirmation());
    }

    private void closeSearch(SearchView searchView) {
        searchView.setQuery("", false);
        searchView.clearFocus();
        scrollView.setVisibility(View.VISIBLE);
        resultsRecycler.setVisibility(View.GONE);
    }

    private void prepareSearchData() {
        List<Extension> tree = Extension.defaultExtensionTree();
        for (int i = 0; i < tree.size(); i++) {
            Extension category = tree.get(i);
            flattenExtension(i, category.description(), category.children(), null, getString(category.description()));
        }
    }

    private void flattenExtension(int categoryIndex, int categoryTitleRes, List<Extension> children, String parentKey, String parentPath) {
        if (children == null) return;
        for (Extension child : children) {
            String searchTitle = getString(child.description()).toLowerCase();
            String searchHelp = child.helpText() != 0 ? getString(child.helpText()).toLowerCase() : "";
            
            allSettings.add(new SearchResult(child, categoryIndex, categoryTitleRes, parentKey, searchTitle, searchHelp, parentPath));
            
            String currentPath = parentPath + " > " + getString(child.description());
            if (child.children() != null && !child.children().isEmpty()) {
                flattenExtension(categoryIndex, categoryTitleRes, child.children(), child.key(), currentPath);
            }
        }
    }

    private void performSearch(String query) {
        if (query == null || query.trim().isEmpty()) {
            scrollView.setVisibility(View.VISIBLE);
            resultsRecycler.setVisibility(View.GONE);
            return;
        }

        scrollView.setVisibility(View.GONE);
        resultsRecycler.setVisibility(View.VISIBLE);

        List<SearchResult> filtered = new ArrayList<>();
        String lowerQuery = query.toLowerCase();

        for (SearchResult result : allSettings) {
            if (result.searchTitle().contains(lowerQuery) || result.searchHelp().contains(lowerQuery)) {
                filtered.add(result);
            }
        }
        searchAdapter.updateResults(filtered);
    }

    private void showResetConfirmation() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.reset_extension_title)
                .setMessage(R.string.reset_extension_message)
                .setPositiveButton(R.string.confirm, (d, w) -> extensionManager.resetToDefault())
                .setNegativeButton(R.string.cancel, (d, w) -> d.dismiss())
                .show();
    }

    private void openSubSettings(int index, int titleRes) {
        Intent intent = new Intent(this, SubSettingsActivity.class);
        intent.putExtra(SubSettingsActivity.EXTRA_CATEGORY_INDEX, index);
        intent.putExtra(SubSettingsActivity.EXTRA_TITLE_RES, titleRes);
        startActivity(intent);
    }

    private record SearchResult(Extension extension, int categoryIndex, int categoryTitleRes, String parentKey, String searchTitle, String searchHelp, String parentPath) {}

    private class SearchAdapter extends RecyclerView.Adapter<SearchAdapter.ViewHolder> {
        private final List<SearchResult> results = new ArrayList<>();

        @SuppressLint("NotifyDataSetChanged")
        void updateResults(List<SearchResult> newResults) {
            results.clear();
            results.addAll(newResults);
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_setting_toggle, parent, false);
            return new ViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            SearchResult result = results.get(position);
            Extension ext = result.extension;
            holder.title.setText(ext.description());
            
            boolean hasChildren = ext.children() != null && !ext.children().isEmpty();
            boolean isNavBarOrder = Constant.NAV_BAR_ORDER.equals(ext.key());
            boolean isActionBarOrder = Constant.ACTION_BAR_ORDER.equals(ext.key());
            
            // Show the path context (where this setting lives)
            holder.description.setText(result.parentPath());
            holder.description.setVisibility(View.VISIBLE);

            boolean isToggle = ext.key() != null && !hasChildren && !isNavBarOrder && !isActionBarOrder;
            boolean isSpecial = isToggle && (
                Constant.DOWNLOAD_LOCATION.equals(ext.key()) ||
                Constant.DOWNLOAD_MAX_CONCURRENT.equals(ext.key()) ||
                Constant.DEFAULT_QUALITY.equals(ext.key()) ||
                Constant.DEFAULT_PLAYBACK_SPEED.equals(ext.key()) ||
                Constant.DOUBLE_TAP_SEEK_AMOUNT.equals(ext.key())
            );

            if (isToggle && !isSpecial) {
                holder.checkbox.setVisibility(View.VISIBLE);
                holder.checkbox.setChecked(extensionManager.isEnabled(ext.key()));
                // Only toggle when the switch is clicked
                holder.checkbox.setOnClickListener(v -> extensionManager.setEnabled(ext.key(), holder.checkbox.isChecked()));
            } else {
                holder.checkbox.setVisibility(View.GONE);
                holder.checkbox.setOnClickListener(null);
            }
            
            holder.actionButton.setVisibility(View.GONE);

            // Clicking the row opens the page and scrolls to the item
            holder.itemView.setOnClickListener(v -> {
                Intent intent = new Intent(SettingsActivity.this, SubSettingsActivity.class);
                intent.putExtra(SubSettingsActivity.EXTRA_CATEGORY_INDEX, result.categoryIndex);
                intent.putExtra(SubSettingsActivity.EXTRA_TITLE_RES, result.categoryTitleRes);
                
                if (hasChildren || isNavBarOrder || isActionBarOrder) {
                    intent.putExtra(SubSettingsActivity.EXTRA_EXTENSION_KEY, ext.key());
                } else if (result.parentKey != null) {
                    intent.putExtra(SubSettingsActivity.EXTRA_EXTENSION_KEY, result.parentKey);
                    intent.putExtra(SubSettingsActivity.EXTRA_SCROLL_TO_KEY, ext.key());
                } else {
                    intent.putExtra(SubSettingsActivity.EXTRA_SCROLL_TO_KEY, ext.key());
                }
                startActivity(intent);
            });
        }

        @Override
        public int getItemCount() {
            return results.size();
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            TextView title, description;
            SwitchMaterial checkbox;
            View actionButton;
            ViewHolder(View itemView) {
                super(itemView);
                title = itemView.findViewById(R.id.title);
                description = itemView.findViewById(R.id.setting_description);
                checkbox = itemView.findViewById(R.id.checkbox);
                actionButton = itemView.findViewById(R.id.action_button);
            }
        }
    }
}