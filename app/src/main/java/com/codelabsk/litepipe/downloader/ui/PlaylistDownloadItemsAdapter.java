package com.codelabsk.litepipe.downloader.ui;

import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.imageview.ShapeableImageView;
import com.codelabsk.litepipe.R;
import com.codelabsk.litepipe.util.ImageUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class PlaylistDownloadItemsAdapter extends RecyclerView.Adapter<PlaylistDownloadItemsAdapter.VH> {
	public static final Object PAYLOAD_SELECTION = new Object();

	@NonNull
	private final List<PlaylistDownloadItem> items = new ArrayList<>();
	@NonNull
	private final Callbacks callbacks;
	private boolean interactionEnabled = true;

	public PlaylistDownloadItemsAdapter(@NonNull Callbacks callbacks) {
		this.callbacks = callbacks;
		setHasStableIds(true);
	}

	public void replaceAll(@NonNull List<PlaylistDownloadItem> newItems) {
		DiffUtil.DiffResult diffResult = DiffUtil.calculateDiff(new DiffUtil.Callback() {
			@Override
			public int getOldListSize() {
				return items.size();
			}

			@Override
			public int getNewListSize() {
				return newItems.size();
			}

			@Override
			public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
				return items.get(oldItemPosition).getPlaylistIndex() == newItems.get(newItemPosition).getPlaylistIndex();
			}

			@Override
			public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
				return items.get(oldItemPosition).equals(newItems.get(newItemPosition));
			}
		});

		items.clear();
		items.addAll(newItems);
		diffResult.dispatchUpdatesTo(this);
	}

	public void setInteractionEnabled(boolean interactionEnabled) {
		this.interactionEnabled = interactionEnabled;
		if (!items.isEmpty()) {
			notifyItemRangeChanged(0, items.size());
		}
	}

	@Override
	public long getItemId(int position) {
		return items.get(position).getPlaylistIndex();
	}

	@NonNull
	@Override
	public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
		View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_playlist_download, parent, false);
		return new VH(view);
	}

	@Override
	public void onBindViewHolder(@NonNull VH holder, int position) {
		holder.bind(items.get(position), callbacks, interactionEnabled);
	}

	@Override
	public void onBindViewHolder(@NonNull VH holder, int position, @NonNull List<Object> payloads) {
		if (payloads.isEmpty()) {
			onBindViewHolder(holder, position);
		} else {
			for (Object payload : payloads) {
				if (payload == PAYLOAD_SELECTION) {
					holder.updateSelection(items.get(position), interactionEnabled);
				}
			}
		}
	}

	@Override
	public int getItemCount() {
		return items.size();
	}

	/**
	 * Contract for app logic.
	 */
	public interface Callbacks {
		void onItemToggled(int position, int playlistIndex, boolean selected);
	}

	public static final class VH extends RecyclerView.ViewHolder {
		private final MaterialCheckBox checkBox;
		private final ShapeableImageView thumbnail;
		private final TextView title;
		private final TextView meta;
		private final TextView status;

		VH(@NonNull View itemView) {
			super(itemView);
			checkBox = itemView.findViewById(R.id.playlist_item_checkbox);
			thumbnail = itemView.findViewById(R.id.playlist_item_thumbnail);
			title = itemView.findViewById(R.id.playlist_item_title);
			meta = itemView.findViewById(R.id.playlist_item_meta);
			status = itemView.findViewById(R.id.playlist_item_status);
		}

		void bind(@NonNull PlaylistDownloadItem item,
		          @NonNull Callbacks callbacks,
		          final boolean interactionEnabled) {
			title.setText(item.getTitle());
			String metaText = buildMetaLine(item);
			CharSequence statusText = getStatus(item);
			meta.setText(metaText);
			meta.setVisibility(!metaText.isEmpty() ? View.VISIBLE : View.GONE);
			status.setText(statusText);
			status.setTextColor(getStatusColor(item));
			status.setVisibility(shouldShowStatus(item) ? View.VISIBLE : View.GONE);
			ImageUtils.loadThumb(thumbnail, item.getThumbnailUrl());

			updateSelection(item, interactionEnabled);

			checkBox.setOnCheckedChangeListener((buttonView, isChecked) ->
							callbacks.onItemToggled(getBindingAdapterPosition(), item.getPlaylistIndex(), isChecked));
			itemView.setOnClickListener(v -> {
				if (!itemView.isEnabled()) return;
				checkBox.toggle();
			});
		}

		void updateSelection(@NonNull PlaylistDownloadItem item, boolean interactionEnabled) {
			boolean enabled = interactionEnabled && item.isSelectable();
			checkBox.setOnCheckedChangeListener(null);
			checkBox.setChecked(item.isSelected());
			checkBox.setEnabled(enabled);
			itemView.setEnabled(enabled);
			itemView.setAlpha(
							enabled || item.getBatchResultStatus() == PlaylistDownloadItem.BatchResultStatus.PREPARING
											? 1f
											: 0.7f);
		}

		@NonNull
		private String buildMetaLine(@NonNull PlaylistDownloadItem item) {
			List<String> parts = new ArrayList<>(2);
			if (item.getAuthor() != null && !item.getAuthor().isBlank()) {
				parts.add(item.getAuthor());
			}
			if (item.getDurationSeconds() > 0) {
				parts.add(formatDuration(item.getDurationSeconds()));
			}
			if (parts.isEmpty() && item.getFailureReason() != null && !item.getFailureReason().isBlank()) {
				return item.getFailureReason();
			}
			return String.join(" / ", parts);
		}

		@NonNull
		private String getStatus(@NonNull PlaylistDownloadItem item) {
			return switch (item.getBatchResultStatus()) {
				case PREPARING ->
								itemView.getContext().getString(R.string.playlist_download_status_preparing);
				case QUEUED -> itemView.getContext().getString(R.string.playlist_download_status_queued);
				case PARTIAL -> itemView.getContext().getString(R.string.playlist_download_status_partial);
				case FAILED -> itemView.getContext().getString(R.string.playlist_download_status_failed);
				case CANCELED ->
								itemView.getContext().getString(R.string.playlist_download_status_canceled);
				case NOT_STARTED -> switch (item.getAvailabilityStatus()) {
					case LOADING ->
									itemView.getContext().getString(R.string.playlist_download_status_loading);
					case READY -> itemView.getContext().getString(R.string.playlist_download_status_ready);
					case UNAVAILABLE ->
									itemView.getContext().getString(R.string.playlist_download_status_unavailable);
					case LOAD_FAILED ->
									itemView.getContext().getString(R.string.playlist_download_status_failed);
				};
			};
		}

		private boolean shouldShowStatus(@NonNull PlaylistDownloadItem item) {
			return item.getBatchResultStatus() != PlaylistDownloadItem.BatchResultStatus.NOT_STARTED
							|| item.getAvailabilityStatus() != PlaylistDownloadItem.AvailabilityStatus.READY;
		}

		private int getStatusColor(@NonNull PlaylistDownloadItem item) {
			if (item.getBatchResultStatus() == PlaylistDownloadItem.BatchResultStatus.FAILED) {
				return getThemeColor(androidx.appcompat.R.attr.colorError, android.R.color.holo_red_dark);
			}
			if (item.getBatchResultStatus() == PlaylistDownloadItem.BatchResultStatus.PARTIAL
							|| item.getBatchResultStatus() == PlaylistDownloadItem.BatchResultStatus.QUEUED
							|| item.getBatchResultStatus() == PlaylistDownloadItem.BatchResultStatus.PREPARING
							|| item.getAvailabilityStatus() == PlaylistDownloadItem.AvailabilityStatus.READY) {
				return getThemeColor(androidx.appcompat.R.attr.colorPrimary, android.R.color.holo_blue_dark);
			}
			return itemView.getContext().getColor(android.R.color.darker_gray);
		}

		private int getThemeColor(int attrId, int fallbackColorRes) {
			TypedValue value = new TypedValue();
			if (itemView.getContext().getTheme().resolveAttribute(attrId, value, true)) {
				return value.data;
			}
			return itemView.getContext().getColor(fallbackColorRes);
		}

		@NonNull
		private String formatDuration(long durationSeconds) {
			long hours = durationSeconds / 3600L;
			long minutes = (durationSeconds % 3600L) / 60L;
			long seconds = durationSeconds % 60L;
			if (hours > 0) {
				return String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds);
			}
			return String.format(Locale.US, "%d:%02d", minutes, seconds);
		}
	}
}
