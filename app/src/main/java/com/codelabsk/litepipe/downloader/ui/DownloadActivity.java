package com.codelabsk.litepipe.downloader.ui;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.PopupMenu;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.media3.common.util.UnstableApi;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.imageview.ShapeableImageView;
import com.google.android.material.progressindicator.LinearProgressIndicator;

import com.codelabsk.litepipe.R;
import com.codelabsk.litepipe.downloader.core.history.DownloadHistoryRepository;
import com.codelabsk.litepipe.downloader.core.history.DownloadRecord;
import com.codelabsk.litepipe.downloader.core.history.DownloadStatus;
import com.codelabsk.litepipe.downloader.core.history.DownloadType;
import com.codelabsk.litepipe.downloader.service.DownloadService;
import com.codelabsk.litepipe.extractor.YoutubeExtractor;
import com.codelabsk.litepipe.ui.OfflinePlayerActivity;
import com.codelabsk.litepipe.util.DownloadStorageUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.inject.Inject;

import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
@UnstableApi
@SuppressLint({"UnstableApi", "UnusedResources"})
public class DownloadActivity extends AppCompatActivity {

	@Inject
	DownloadHistoryRepository historyRepository;
	@Inject
	YoutubeExtractor youtubeExtractor;

	private DownloadRecordsAdapter adapter;
	private DownloadService downloadService;
	private boolean isBound;
	private String filterParentId = null;
	private String filterTitle = null;
	private MaterialToolbar toolbar;
	private MaterialCheckBox selectAllCheckbox;
	private View selectionHeader;
	private final ExecutorService diffExecutor = Executors.newSingleThreadExecutor();
	private final Handler mainHandler = new Handler(Looper.getMainLooper());

	private final Set<String> selectedIds = new HashSet<>();

	private final ServiceConnection connection = new ServiceConnection() {
		@Override
		public void onServiceConnected(ComponentName name, IBinder service) {
			downloadService = ((DownloadService.DownloadBinder) service).getService();
			isBound = true;
		}

		@Override
		public void onServiceDisconnected(ComponentName name) {
			downloadService = null;
			isBound = false;
		}
	};

	@Override
	protected void onCreate(@Nullable Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		EdgeToEdge.enable(this);
		setContentView(R.layout.activity_download);

		filterParentId = getIntent().getStringExtra("parent_id");
		filterTitle = getIntent().getStringExtra("folder_name");
		toolbar = findViewById(R.id.toolbar);
		setSupportActionBar(toolbar);
		toolbar.setNavigationOnClickListener(v -> {
			if (!selectedIds.isEmpty()) {
				clearSelection();
			} else {
				finish();
			}
		});

		selectAllCheckbox = findViewById(R.id.toolbar_checkbox);
		selectionHeader = findViewById(R.id.selection_header);

		final View root = findViewById(R.id.root);
		ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
			var systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
			v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
			return insets;
		});

		RecyclerView recyclerView = findViewById(R.id.recyclerView);
		recyclerView.setLayoutManager(new LinearLayoutManager(this));
		recyclerView.setItemAnimator(null);

		adapter = new DownloadRecordsAdapter(actions, filterParentId == null);
		recyclerView.setAdapter(adapter);

		if (selectAllCheckbox != null) {
			selectAllCheckbox.setOnClickListener(v -> {
				if (selectAllCheckbox.isChecked()) {
					loadRecordsForSelection();
				} else {
					selectedIds.clear();
					updateUIState();
					adapter.refreshCurrentList();
				}
			});
		}

		updateUIState();
	}

	private void loadRecordsForSelection() {
		diffExecutor.execute(() -> {
			List<DownloadRecord> currentItems = getCurrentlyDisplayedRecords();
			mainHandler.post(() -> {
				for (DownloadRecord r : currentItems) selectedIds.add(r.getTaskId());
				updateUIState();
				adapter.refreshCurrentList();
			});
		});
	}

	private List<DownloadRecord> getCurrentlyDisplayedRecords() {
		List<DownloadRecord> all = historyRepository.getAllSorted();
		List<DownloadRecord> verifiedList = new ArrayList<>();
		List<String> toRemove = new ArrayList<>();

		Set<String> videoIdsInList = new HashSet<>();
		for (DownloadRecord r : all) {
			if (r.getType() == DownloadType.VIDEO) {
				videoIdsInList.add(getShortVideoId(r.getVideoId()));
			}
		}

		for (DownloadRecord record : all) {
			if (record.getStatus() == DownloadStatus.COMPLETED && record.getType() != DownloadType.PLAYLIST) {
				if (!DownloadStorageUtils.exists(this, record.getOutputPath())) {
					toRemove.add(record.getTaskId());
					continue;
				}
			}
			
			if (record.getType() == DownloadType.SUBTITLE && videoIdsInList.contains(getShortVideoId(record.getVideoId()))) {
				continue;
			}
			
			verifiedList.add(record);
		}

		if (!toRemove.isEmpty()) {
			historyRepository.removeBatch(toRemove);
		}

		List<DownloadRecord> filtered = new ArrayList<>();
		if (filterParentId == null) {
			Map<String, Integer> childrenCounts = new HashMap<>();
			for (DownloadRecord r : verifiedList) {
				if (r.getParentId() != null) {
					childrenCounts.merge(r.getParentId(), 1, Integer::sum);
				}
			}

			List<String> emptyPlaylists = new ArrayList<>();
			for (DownloadRecord r : verifiedList) {
				if (r.getParentId() == null) {
					if (r.getType() == DownloadType.PLAYLIST) {
						Integer countVal = childrenCounts.get(r.getTaskId());
						int count = (countVal != null) ? countVal : 0;
						if (count == 0 && r.getStatus() != DownloadStatus.RUNNING && r.getStatus() != DownloadStatus.QUEUED && r.getStatus() != DownloadStatus.MERGING) {
							emptyPlaylists.add(r.getTaskId());
							continue;
						}
						r.setItemCount(count);
					}
					filtered.add(r);
				}
			}
			if (!emptyPlaylists.isEmpty()) {
				historyRepository.removeBatch(emptyPlaylists);
			}
		} else {
			for (DownloadRecord r : verifiedList) {
				if (Objects.equals(r.getParentId(), filterParentId)) {
					filtered.add(r);
				}
			}
		}
		return filtered;
	}

	private void clearSelection() {
		selectedIds.clear();
		updateUIState();
		adapter.refreshCurrentList();
	}

	private void updateUIState() {
		boolean isSelecting = !selectedIds.isEmpty();

		if (!isSelecting) {
			toolbar.setTitle(filterTitle != null ? filterTitle : getString(R.string.downloads_default_title));
			toolbar.setNavigationIcon(R.drawable.ic_arrow_back);
			if (selectionHeader != null) selectionHeader.setVisibility(View.GONE);
		} else {
			toolbar.setTitle("");
			toolbar.setNavigationIcon(R.drawable.ic_close);
			if (selectionHeader != null) {
				selectionHeader.setVisibility(View.VISIBLE);
				diffExecutor.execute(() -> {
					List<DownloadRecord> currentItems = getCurrentlyDisplayedRecords();
					mainHandler.post(() -> selectAllCheckbox.setChecked(!currentItems.isEmpty() && selectedIds.size() >= currentItems.size()));
				});
			}
		}
		invalidateOptionsMenu();
	}

	@Override
	public boolean onPrepareOptionsMenu(Menu menu) {
		boolean selecting = !selectedIds.isEmpty();
		setMenuVisible(menu, R.id.action_pause_all, !selecting);
		setMenuVisible(menu, R.id.action_resume_all, !selecting);
		setMenuVisible(menu, R.id.action_delete_all, !selecting);
		setMenuVisible(menu, R.id.action_clear_history, !selecting);
		setMenuVisible(menu, R.id.action_pause_selected, selecting);
		setMenuVisible(menu, R.id.action_resume_selected, selecting);
		setMenuVisible(menu, R.id.action_retry_selected, selecting);
		setMenuVisible(menu, R.id.action_cancel_selected, selecting);
		setMenuVisible(menu, R.id.action_delete_selected, selecting);
		return super.onPrepareOptionsMenu(menu);
	}

	private void setMenuVisible(Menu menu, int id, boolean visible) {
		MenuItem item = menu.findItem(id);
		if (item != null) item.setVisible(visible);
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.download_history_menu, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(@NonNull MenuItem item) {
		int id = item.getItemId();
		if (id == R.id.action_pause_all) {
			performBatch(DownloadStatus.RUNNING, tid -> downloadService.pause(tid));
		} else if (id == R.id.action_resume_all) {
			performBatch(DownloadStatus.PAUSED, tid -> downloadService.resume(tid));
		} else if (id == R.id.action_delete_all) {
			diffExecutor.execute(() -> {
				List<DownloadRecord> records = getCurrentlyDisplayedRecords();
				mainHandler.post(() -> confirmDelete(records, true));
			});
		} else if (id == R.id.action_pause_selected) {
			for (String tid : selectedIds) downloadService.pause(tid);
			clearSelection();
		} else if (id == R.id.action_resume_selected || id == R.id.action_retry_selected) {
			for (String tid : selectedIds) downloadService.resume(tid);
			clearSelection();
		} else if (id == R.id.action_cancel_selected) {
			for (String tid : selectedIds) downloadService.cancel(tid);
			clearSelection();
		} else if (id == R.id.action_delete_selected) {
			List<DownloadRecord> targets = new ArrayList<>();
			for (String tid : selectedIds) {
				DownloadRecord r = historyRepository.findByTaskId(tid);
				if (r != null) targets.add(r);
			}
			confirmDelete(targets, false);
		} else if (id == R.id.action_clear_history) {
			showClearHistoryDialog();
		}
		return super.onOptionsItemSelected(item);
	}

	private void performBatch(DownloadStatus filter, java.util.function.Consumer<String> action) {
		if (isBound && downloadService != null) {
			diffExecutor.execute(() -> {
				List<DownloadRecord> list = getCurrentlyDisplayedRecords();
				mainHandler.post(() -> {
					for (int i = list.size() - 1; i >= 0; i--) {
						DownloadRecord r = list.get(i);
						if (r.getStatus() == filter || (filter == DownloadStatus.RUNNING && r.getStatus() == DownloadStatus.QUEUED)) {
							action.accept(r.getTaskId());
						}
					}
				});
			});
		}
	}

	private void confirmDelete(List<DownloadRecord> targets, boolean isAll) {
		View view = LayoutInflater.from(this).inflate(R.layout.dialog_delete_record, null);
		MaterialCheckBox cb = view.findViewById(R.id.checkbox_delete_file);
		cb.setChecked(true);
		if (isAll) cb.setText(R.string.delete_local_files);

		new MaterialAlertDialogBuilder(this)
						.setTitle(isAll ? "Delete All" : "Delete Selected")
						.setView(view)
						.setPositiveButton("Delete", (d, w) -> {
							List<String> idsToRemove = new ArrayList<>();
							for (DownloadRecord r : targets) {
								if (isBound && downloadService != null) {
									downloadService.cancel(r.getTaskId());
								}
								if (cb.isChecked()) {
									DownloadStorageUtils.delete(DownloadActivity.this, r.getOutputPath());
								}
								idsToRemove.add(r.getTaskId());
							}
							historyRepository.removeBatch(idsToRemove);
							clearSelection();
							loadRecords();
						}).setNegativeButton("Cancel", null).show();
	}

	private final DownloadRecordsAdapter.Actions actions = new DownloadRecordsAdapter.Actions() {
		@Override
		public void onOpen(DownloadRecord r) {
			openRecordFile(r);
		}

		@Override
		public void onCancel(DownloadRecord r) {
			if (isBound) downloadService.cancel(r.getTaskId());
		}

		@Override
		public void onPause(DownloadRecord r) {
			if (isBound) downloadService.pause(r.getTaskId());
		}

		@Override
		public void onResume(DownloadRecord r) {
			if (isBound) downloadService.resume(r.getTaskId());
		}

		@Override
		public void onRetry(DownloadRecord r) {
			if (isBound) downloadService.resume(r.getTaskId());
		}

		@Override
		public void onRedownload(DownloadRecord r) {
			String url = "https://m.youtube.com/watch?v=" + getShortVideoId(r.getVideoId());
			new DownloadDialog(url, DownloadActivity.this, youtubeExtractor).show();
		}

		@Override
		public void onDelete(DownloadRecord r) {
			showDeleteDialog(r);
		}

		@Override
		public void onToggleSelection(DownloadRecord r) {
			toggleSelection(r.getTaskId());
		}

		@Override
		public void onLongClick(DownloadRecord r) {
			toggleSelection(r.getTaskId());
		}

		@Override
		public boolean isSelected(DownloadRecord r) {
			return selectedIds.contains(r.getTaskId());
		}

		@Override
		public boolean isInSelectionMode() {
			return !selectedIds.isEmpty();
		}

		@Override
		public void onOpenFolder(DownloadRecord folderRecord) {
			Intent i = new Intent(DownloadActivity.this, DownloadActivity.class);
			i.putExtra("parent_id", folderRecord.getTaskId());
			i.putExtra("folder_name", folderRecord.getTitle());
			startActivity(i);
		}

		@Override
		public void onDeleteFolder(DownloadRecord folderRecord) {
			new MaterialAlertDialogBuilder(DownloadActivity.this).setTitle("Delete Folder").setMessage("Delete folder content?")
							.setPositiveButton("Delete", (d, w) -> diffExecutor.execute(() -> {
                                List<DownloadRecord> all = historyRepository.getAllSorted();
                                List<String> idsToRemove = new ArrayList<>();
                                for (DownloadRecord r : all) {
                                    if (Objects.equals(r.getParentId(), folderRecord.getTaskId())) {
                                        if (isBound && downloadService != null) downloadService.cancel(r.getTaskId());
                                        DownloadStorageUtils.delete(DownloadActivity.this, r.getOutputPath());
                                        idsToRemove.add(r.getTaskId());
                                    }
                                }
                                idsToRemove.add(folderRecord.getTaskId());
                                historyRepository.removeBatch(idsToRemove);
                                mainHandler.post(DownloadActivity.this::loadRecords);
                            })).setNegativeButton("Cancel", null).show();
		}
	};

	private void toggleSelection(String id) {
		if (selectedIds.contains(id)) selectedIds.remove(id);
		else selectedIds.add(id);
		updateUIState();
		adapter.refreshCurrentList();
	}

	private void showDeleteDialog(DownloadRecord record) {
		List<DownloadRecord> list = new ArrayList<>();
		list.add(record);
		confirmDelete(list, false);
	}

	private void showClearHistoryDialog() {
		new MaterialAlertDialogBuilder(this).setTitle("Clear Finished").setMessage("Remove finished items from list?")
						.setPositiveButton("Clear", (d, w) -> diffExecutor.execute(() -> {
                            List<String> toRemove = new ArrayList<>();
                            for (DownloadRecord r : historyRepository.getAllSorted()) {
                                DownloadStatus s = r.getStatus();
                                if (s == DownloadStatus.COMPLETED || s == DownloadStatus.FAILED || s == DownloadStatus.CANCELED)
                                    toRemove.add(r.getTaskId());
                            }
                            historyRepository.removeBatch(toRemove);
                            mainHandler.post(DownloadActivity.this::loadRecords);
                        })).setNegativeButton("Cancel", null).show();
	}

	private void openRecordFile(DownloadRecord record) {
		if (!DownloadStorageUtils.exists(this, record.getOutputPath())) {
			Toast.makeText(this, R.string.file_not_found, Toast.LENGTH_SHORT).show();
			loadRecords();
			return;
		}
		final Uri uri = DownloadStorageUtils.getOpenUri(this, record.getOutputPath());
		if (uri == null) {
			Toast.makeText(this, R.string.file_not_found, Toast.LENGTH_SHORT).show();
			loadRecords();
			return;
		}
		
        if (record.getType() == DownloadType.VIDEO) {
            final Intent intent = new Intent(this, OfflinePlayerActivity.class);
            intent.setAction("PLAY_LOCAL_VIDEO");
            intent.putExtra("uri", uri);
            intent.putExtra("title", record.getFileName());
            intent.putExtra("video_id", record.getVideoId());
            startActivity(intent);
            return;
        }

		final String type = DownloadStorageUtils.getMimeType(this, record.getOutputPath(), record.getFileName());
		final Intent intent = new Intent(Intent.ACTION_VIEW);
		intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
		intent.setDataAndType(uri, type != null ? type : "*/*");
		try {
			startActivity(intent);
		} catch (Exception e) {
			Toast.makeText(this, R.string.application_not_found, Toast.LENGTH_SHORT).show();
		}
	}

	@Override
	protected void onResume() {
		super.onResume();
		loadRecords();
	}

	@Override
	protected void onStart() {
		super.onStart();
		ContextCompat.registerReceiver(this, receiver, new IntentFilter(DownloadService.ACTION_DOWNLOAD_RECORD_UPDATED), ContextCompat.RECEIVER_NOT_EXPORTED);
		bindService(new Intent(this, DownloadService.class), connection, BIND_AUTO_CREATE);
	}

	@Override
	protected void onStop() {
		super.onStop();
		try {
			unregisterReceiver(receiver);
		} catch (Exception ignored) {
		}
		if (isBound) {
			unbindService(connection);
			isBound = false;
		}
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		diffExecutor.shutdownNow();
	}

	private void loadRecords() {
		diffExecutor.execute(() -> {
			List<DownloadRecord> list = getCurrentlyDisplayedRecords();
			adapter.updateItemsAsync(list, () -> findViewById(R.id.emptyView).setVisibility(adapter.getItemCount() == 0 ? View.VISIBLE : View.GONE));
		});
	}

	private final BroadcastReceiver receiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			String tid = intent.getStringExtra(DownloadService.EXTRA_TASK_ID);
			if (tid != null) {
				DownloadRecord r = historyRepository.findByTaskId(tid);
				if (r != null) {
					RecyclerView rv = findViewById(R.id.recyclerView);
					for (int i = 0; i < rv.getChildCount(); i++) {
						RecyclerView.ViewHolder holder = rv.getChildViewHolder(rv.getChildAt(i));
						if (holder instanceof DownloadRecordsAdapter.ItemVH vh) {
							if (tid.equals(vh.currentTaskId)) {
								vh.updateProgressUI(r);
								return;
							}
						}
					}
				}
			}
			loadRecords();
		}
	};

	private static String getShortVideoId(String videoId) {
		if (videoId == null) return "";
		int idx = videoId.indexOf(':');
		return idx == -1 ? videoId : videoId.substring(0, idx);
	}

	private static class DownloadRecordsAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
		private static final int TYPE_ITEM = 0, TYPE_FOLDER = 1;
		private final List<Object> displayItems = new ArrayList<>();
		private final Actions actions;
		private final boolean useGrouping;
		private final Handler mainHandler = new Handler(Looper.getMainLooper());

		DownloadRecordsAdapter(Actions a, boolean g) {
			this.actions = a;
			this.useGrouping = g;
		}

		void updateItemsAsync(List<DownloadRecord> records, Runnable onDone) {
			List<Object> newList = new ArrayList<>();
			if (!useGrouping) {
				newList.addAll(records);
			} else {
				for (DownloadRecord r : records) {
					if (r.getType() == DownloadType.PLAYLIST) {
						newList.add(new FolderHeader(r));
					} else {
						newList.add(r);
					}
				}
			}

			List<Object> oldList = new ArrayList<>(displayItems);
			DiffUtil.DiffResult diffResult = DiffUtil.calculateDiff(new DiffUtil.Callback() {
				@Override
				public int getOldListSize() { return oldList.size(); }
				@Override
				public int getNewListSize() { return newList.size(); }
				@Override
				public boolean areItemsTheSame(int op, int np) {
					Object o = oldList.get(op), n = newList.get(np);
					if (o instanceof DownloadRecord or && n instanceof DownloadRecord nr) return or.getTaskId().equals(nr.getTaskId());
					if (o instanceof FolderHeader of && n instanceof FolderHeader nf) return of.record.getTaskId().equals(nf.record.getTaskId());
					return false;
				}
				@Override
				public boolean areContentsTheSame(int op, int np) {
					Object o = oldList.get(op), n = newList.get(np);
					if (o instanceof DownloadRecord or && n instanceof DownloadRecord nr) 
						return Objects.equals(or, nr) && actions.isSelected(or) == actions.isSelected(nr);
					if (o instanceof FolderHeader of && n instanceof FolderHeader nf)
						return of.record.getUpdatedAt() == nf.record.getUpdatedAt() && of.record.getItemCount() == nf.record.getItemCount();
					return false;
				}
			});

			mainHandler.post(() -> {
				displayItems.clear();
				displayItems.addAll(newList);
				diffResult.dispatchUpdatesTo(this);
				if (onDone != null) onDone.run();
			});
		}

		@SuppressLint("NotifyDataSetChanged")
		void refreshCurrentList() {
			notifyDataSetChanged();
		}

		@Override
		public int getItemViewType(int p) {
			return displayItems.get(p) instanceof FolderHeader ? TYPE_FOLDER : TYPE_ITEM;
		}

		@NonNull
		@Override
		public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup p, int vt) {
			LayoutInflater inf = LayoutInflater.from(p.getContext());
			if (vt == TYPE_FOLDER) return new FolderVH(inf.inflate(R.layout.item_download_folder, p, false));
			return new ItemVH(inf.inflate(R.layout.item_download_record, p, false));
		}

		@Override
		public void onBindViewHolder(@NonNull RecyclerView.ViewHolder h, int p) {
			if (h instanceof FolderVH fvh) fvh.bind((FolderHeader) displayItems.get(p), actions);
			else ((ItemVH) h).bind((DownloadRecord) displayItems.get(p), actions);
		}

		@Override
		public int getItemCount() {
			return displayItems.size();
		}

		static class FolderHeader {
			DownloadRecord record;
			FolderHeader(DownloadRecord r) { record = r; }
		}

		static class FolderVH extends RecyclerView.ViewHolder {
			TextView title, subtitle;
			ShapeableImageView icon;
			ImageButton more;

			FolderVH(View v) {
				super(v);
				title = v.findViewById(R.id.title);
				subtitle = v.findViewById(R.id.subtitle);
				icon = v.findViewById(R.id.thumbnail);
				more = v.findViewById(R.id.more);
			}

			void bind(FolderHeader f, Actions a) {
				title.setText(f.record.getTitle());
				subtitle.setText(itemView.getContext().getString(R.string.videos_count, f.record.getItemCount()));
				if (f.record.getThumbnailUrl() != null) {
					Glide.with(itemView.getContext())
									.load(f.record.getThumbnailUrl())
									.diskCacheStrategy(DiskCacheStrategy.ALL)
									.into(icon);
				}
				itemView.setOnClickListener(v -> a.onOpenFolder(f.record));
				more.setOnClickListener(v -> {
					PopupMenu p = new PopupMenu(v.getContext(), v);
					p.getMenu().add("Delete");
					p.setOnMenuItemClickListener(i -> {
						a.onDeleteFolder(f.record);
						return true;
					});
					p.show();
				});
			}
		}

		static class ItemVH extends RecyclerView.ViewHolder {
			ShapeableImageView thumb;
			TextView title, statusChip, typeChip, size;
			LinearProgressIndicator progress;
			ImageButton more;
			MaterialCheckBox checkBox;
			String currentTaskId;
			DownloadRecord currentRecord;

			ItemVH(View v) {
				super(v);
				thumb = v.findViewById(R.id.thumbnail);
				title = v.findViewById(R.id.title);
				statusChip = v.findViewById(R.id.status_chip);
				typeChip = v.findViewById(R.id.type_chip);
				size = v.findViewById(R.id.size_downloaded);
				progress = v.findViewById(R.id.progress);
				more = v.findViewById(R.id.more);
				checkBox = v.findViewById(R.id.checkbox);
			}

			void bind(DownloadRecord r, Actions a) {
				this.currentRecord = r;
				this.currentTaskId = r.getTaskId();
				title.setText(r.getFileName());
				updateProgressUI(r);
				if (r.getThumbnailUrl() != null) {
					Glide.with(itemView.getContext())
									.load(r.getThumbnailUrl())
									.diskCacheStrategy(DiskCacheStrategy.ALL)
									.into(thumb);
				}

				boolean selecting = a.isInSelectionMode();
				checkBox.setVisibility(selecting ? View.VISIBLE : View.GONE);
				checkBox.setOnCheckedChangeListener(null);
				checkBox.setChecked(a.isSelected(r));
				checkBox.setOnCheckedChangeListener((bv, is) -> a.onToggleSelection(r));
				itemView.setOnClickListener(v -> {
					if (selecting) a.onToggleSelection(r);
					else if (r.getStatus() == DownloadStatus.COMPLETED) a.onOpen(r);
				});
				itemView.setOnLongClickListener(v -> {
					if (!selecting) {
						a.onLongClick(r);
						return true;
					}
					return false;
				});
				more.setVisibility(selecting ? View.GONE : View.VISIBLE);
				more.setOnClickListener(v -> {
					PopupMenu p = new PopupMenu(v.getContext(), v);
					Menu m = p.getMenu();
					DownloadStatus s = currentRecord.getStatus();
					if (s == DownloadStatus.COMPLETED) {
						m.add(0, 0, 0, "Open");
						m.add(0, 6, 1, "Redownload");
					} else if (s == DownloadStatus.RUNNING || s == DownloadStatus.QUEUED || s == DownloadStatus.MERGING) {
						m.add(0, 1, 0, "Pause");
						m.add(0, 3, 1, "Cancel");
					} else if (s == DownloadStatus.PAUSED) {
						m.add(0, 2, 0, "Resume");
						m.add(0, 3, 1, "Cancel");
					}
					if (s == DownloadStatus.FAILED || s == DownloadStatus.CANCELED) {
						m.add(0, 7, 0, "Retry");
						m.add(0, 6, 1, "Redownload");
					}
					m.add(0, 4, 2, "Delete");
					m.add(0, 5, 3, "Copy Video ID");
					p.setOnMenuItemClickListener(item -> {
						switch (item.getItemId()) {
							case 0: a.onOpen(currentRecord); break;
							case 1: a.onPause(currentRecord); break;
							case 2: a.onResume(currentRecord); break;
							case 3: a.onCancel(currentRecord); break;
							case 4: a.onDelete(currentRecord); break;
							case 5:
								ClipboardManager cm = (ClipboardManager) v.getContext().getSystemService(Context.CLIPBOARD_SERVICE);
								cm.setPrimaryClip(ClipData.newPlainText("vid", getShortVideoId(currentRecord.getVideoId())));
								Toast.makeText(v.getContext(), "ID Copied", Toast.LENGTH_SHORT).show();
								break;
							case 6: a.onRedownload(currentRecord); break;
							case 7: a.onRetry(currentRecord); break;
						}
						return true;
					});
					p.show();
				});
			}

			private String getStatusString(Context context, DownloadStatus status) {
				return switch (status) {
					case QUEUED -> context.getString(R.string.status_queued);
					case RUNNING -> context.getString(R.string.status_downloading);
					case MERGING -> context.getString(R.string.status_merging);
					case COMPLETED -> context.getString(R.string.status_completed);
					case FAILED -> context.getString(R.string.status_failed);
					case CANCELED -> context.getString(R.string.status_cancelled);
					case PAUSED -> context.getString(R.string.status_paused);
				};
			}

			private String getTypeString(Context context, DownloadType type, String quality) {
				return switch (type) {
					case VIDEO -> context.getString(R.string.type_video) + (quality != null ? " (" + quality + ")" : "");
					case AUDIO -> context.getString(R.string.type_audio);
					case SUBTITLE -> context.getString(R.string.type_subtitle);
					case THUMBNAIL -> context.getString(R.string.type_thumbnail);
					case PLAYLIST -> context.getString(R.string.type_playlist);
				};
			}

			void updateProgressUI(DownloadRecord r) {
				this.currentRecord = r;
				Context context = itemView.getContext();
				DownloadStatus s = r.getStatus();
				
				statusChip.setText(getStatusString(context, s));
				typeChip.setText(getTypeString(context, r.getType(), r.getQuality()));
				typeChip.setVisibility(View.VISIBLE);

				int statusBgRes, statusTextColorRes;
				switch (s) {
					case COMPLETED -> {
						statusBgRes = R.drawable.bg_download_chip_completed;
						statusTextColorRes = R.color.chip_completed_text;
					}
					case FAILED, CANCELED -> {
						statusBgRes = R.drawable.bg_download_chip_error;
						statusTextColorRes = R.color.chip_error_text;
					}
					case PAUSED -> {
						statusBgRes = R.drawable.bg_download_chip_paused;
						statusTextColorRes = R.color.chip_paused_text;
					}
					default -> {
						statusBgRes = R.drawable.bg_download_chip_active;
						statusTextColorRes = R.color.chip_active_text;
					}
				}

				statusChip.setBackgroundResource(statusBgRes);
				statusChip.setTextColor(ContextCompat.getColor(context, statusTextColorRes));

				typeChip.setBackgroundResource(R.drawable.bg_download_chip_type);
				typeChip.setTextColor(ContextCompat.getColor(context, R.color.chip_type_text));

				String sizeText;
				if (r.getType() == DownloadType.SUBTITLE) {
					sizeText = "";
				} else if (r.getTotalSize() > 0) {
					sizeText = context.getString(R.string.download_progress_with_total, formatMB(r.getDownloadedSize()), formatMB(r.getTotalSize()), r.getProgress());
					if (s == DownloadStatus.RUNNING && r.getSpeed() > 0) {
						sizeText += " • " + calculateETA(r, r.getSpeed());
					}
				} else {
					sizeText = context.getString(R.string.download_progress_simple, formatMB(r.getDownloadedSize()));
				}
				size.setText(sizeText);
				
				boolean isActivelyDownloading = (s == DownloadStatus.RUNNING || s == DownloadStatus.QUEUED || s == DownloadStatus.MERGING);
                progress.setVisibility(isActivelyDownloading ? View.VISIBLE : View.GONE);
				
				if (s == DownloadStatus.RUNNING) {
					progress.setIndeterminate(false);
					progress.setProgressCompat(r.getProgress(), true);
				} else if (s == DownloadStatus.QUEUED || s == DownloadStatus.MERGING) {
					progress.setIndeterminate(true);
				}
			}
			
			private String formatMB(long bytes) {
				return String.format(Locale.getDefault(), "%.1f", bytes / 1048576.0);
			}

			private String calculateETA(DownloadRecord record, long speedBytesPerSec) {
				if (speedBytesPerSec <= 0 || record.getTotalSize() <= 0) return "--:--";
				long remainingBytes = record.getTotalSize() - record.getDownloadedSize();
				if (remainingBytes <= 0) return "0s";
				long seconds = remainingBytes / speedBytesPerSec;
				if (seconds < 60) return seconds + "s";
				if (seconds < 3600) return (seconds / 60) + "m";
				return (seconds / 3600) + "h " + ((seconds % 3600) / 60) + "m";
			}
		}

		interface Actions {
			void onOpen(DownloadRecord r);
			void onCancel(DownloadRecord r);
			void onPause(DownloadRecord r);
			void onResume(DownloadRecord r);
			void onRetry(DownloadRecord r);
			void onRedownload(DownloadRecord r);
			void onDelete(DownloadRecord r);
			void onOpenFolder(DownloadRecord folderRecord);
			void onDeleteFolder(DownloadRecord folderRecord);
			void onLongClick(DownloadRecord r);
			void onToggleSelection(DownloadRecord r);
			boolean isSelected(DownloadRecord r);
			boolean isInSelectionMode();
		}
	}
}
