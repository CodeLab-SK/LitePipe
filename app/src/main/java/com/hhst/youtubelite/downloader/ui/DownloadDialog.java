package com.hhst.youtubelite.downloader.ui;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.CheckBox;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.media3.common.util.UnstableApi;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.hhst.youtubelite.R;
import com.hhst.youtubelite.downloader.core.Task;
import com.hhst.youtubelite.downloader.service.DownloadService;
import com.hhst.youtubelite.extractor.StreamDetails;
import com.hhst.youtubelite.extractor.VideoDetails;
import com.hhst.youtubelite.extractor.YoutubeExtractor;
import com.hhst.youtubelite.gallery.GalleryActivity;
import com.tencent.mmkv.MMKV;

import org.schabi.newpipe.extractor.MediaFormat;
import org.schabi.newpipe.extractor.stream.AudioStream;
import org.schabi.newpipe.extractor.stream.SubtitlesStream;
import org.schabi.newpipe.extractor.stream.VideoStream;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;

@UnstableApi
public class DownloadDialog {
    private static final String KEY_THREAD_COUNT = "download_thread_count";
    private static final String KEY_LAST_MODE = "last_download_mode";
    private static final String KEY_LAST_VIDEO_RES = "last_download_res";
    private static final String KEY_LAST_AUDIO_TRACK = "last_download_audio_track";
    private static final String KEY_LAST_VIDEO_AUDIO_TRACK = "last_download_video_audio_track";
    private static final String KEY_LAST_THUMB_SEL = "last_download_thumb_sel";
    private static final String KEY_LAST_SUB_SEL = "last_download_sub_sel";
    private static final String KEY_LAST_SUB_LANG = "last_download_sub_lang";
    private static final String KEY_LAST_AUDIO_LANG = "last_audio_lang";

    private final Context context;
    private static final ExecutorService dialogExecutor = Executors.newFixedThreadPool(2);
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private final View dialogView;
    private final MMKV kv = MMKV.defaultMMKV();

    private VideoDetails videoDetails;
    private StreamDetails streamDetails;
    private boolean thumbSel, subtitleSel;
    private String mode;
    private VideoStream videoSelStream;
    private AudioStream audioSelStream;
    private AudioStream videoAudioStream;
    private SubtitlesStream subtitleSelStream;
    private int threadCount = 4;
    private DownloadService downloadService;
    private boolean isBound = false;

    private final ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            downloadService = ((DownloadService.DownloadBinder) service).getService();
            isBound = true;
        }
        @Override
        public void onServiceDisconnected(ComponentName name) {
            isBound = false;
        }
    };

    public DownloadDialog(String url, Context context, YoutubeExtractor youtubeExtractor) {
        this.context = context;
        this.dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_download, new FrameLayout(context), false);

        mode = kv.decodeString(KEY_LAST_MODE, "video");
        thumbSel = kv.decodeBool(KEY_LAST_THUMB_SEL, false);
        subtitleSel = kv.decodeBool(KEY_LAST_SUB_SEL, false);

        Intent serviceIntent = new Intent(context, DownloadService.class);
        context.startService(serviceIntent);
        context.bindService(serviceIntent, connection, Context.BIND_AUTO_CREATE);

        dialogExecutor.execute(() -> {
            try {
                videoDetails = youtubeExtractor.getVideoInfo(url);
                updateUI();
            } catch (Exception e) {
                Log.e("DownloadDialog", "Video info fetch failed", e);
            }
        });

        dialogExecutor.execute(() -> {
            try {
                streamDetails = youtubeExtractor.getStreamInfo(url);
                if (streamDetails != null && streamDetails.getAudioStreams() != null) {
                    sortAudioStreams(streamDetails.getAudioStreams());
                }
                updateUI();
            } catch (Exception e) {
                mainHandler.post(() -> Toast.makeText(context, R.string.failed_to_load_video_details, Toast.LENGTH_SHORT).show());
            }
        });
    }

    private void updateUI() {
        mainHandler.post(() -> {
            ProgressBar progressBar = dialogView.findViewById(R.id.loadingBar);
            if (videoDetails != null) {
                if (progressBar != null) progressBar.setVisibility(View.GONE);
                ImageView imageView = dialogView.findViewById(R.id.download_image);
                EditText editText = dialogView.findViewById(R.id.download_edit_text);

                if (imageView != null) {
                    Glide.with(context)
                            .load(videoDetails.getThumbnail())
                            .diskCacheStrategy(DiskCacheStrategy.ALL)
                            .into(imageView);

                    imageView.setOnClickListener(v -> {
                        Intent intent = new Intent(context, GalleryActivity.class);
                        ArrayList<String> urls = new ArrayList<>();
                        urls.add(videoDetails.getThumbnail());
                        intent.putStringArrayListExtra("thumbnails", urls);
                        intent.putExtra("filename", videoDetails.getTitle());
                        context.startActivity(intent);
                    });
                }

                if (editText != null && editText.getText().toString().isEmpty()) {
                    editText.setText(String.format("%s-%s", videoDetails.getTitle(), videoDetails.getAuthor()));
                }
            }

            if (streamDetails != null) {
                dialogExecutor.execute(() -> restorePreferences(
                        dialogView.findViewById(R.id.button_video),
                        dialogView.findViewById(R.id.button_audio)
                ));
            }
        });
    }

    public void show() {
        EditText editText = dialogView.findViewById(R.id.download_edit_text);
        Button videoButton = dialogView.findViewById(R.id.button_video);
        Button thumbnailButton = dialogView.findViewById(R.id.button_thumbnail);
        Button audioButton = dialogView.findViewById(R.id.button_audio);
        Button subtitleButton = dialogView.findViewById(R.id.button_subtitle);
        Button downloadButton = dialogView.findViewById(R.id.button_download);
        SeekBar threadsSeekBar = dialogView.findViewById(R.id.threads_seekbar);
        TextView threadsCountText = dialogView.findViewById(R.id.threads_count);

        updateButtonStates(videoButton, audioButton);
        updateAuxButtonState(thumbnailButton, thumbSel);
        updateAuxButtonState(subtitleButton, subtitleSel);

        AlertDialog dialog = new MaterialAlertDialogBuilder(context)
                .setTitle(context.getString(R.string.download))
                .setView(dialogView)
                .create();

        threadCount = kv.decodeInt(KEY_THREAD_COUNT, 4);
        if (threadsSeekBar != null) {
            threadsSeekBar.setProgress(threadCount - 1);
            threadsSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar s, int p, boolean f) {
                    threadCount = p + 1;
                    if (threadsCountText != null) threadsCountText.setText(String.valueOf(threadCount));
                    kv.encode(KEY_THREAD_COUNT, threadCount);
                }
                @Override public void onStartTrackingTouch(SeekBar s) {}
                @Override public void onStopTrackingTouch(SeekBar s) {}
            });
        }
        if (threadsCountText != null) threadsCountText.setText(String.valueOf(threadCount));

        if (videoButton != null) {
            videoButton.setOnClickListener(v -> {
                if (streamDetails != null) {
                    if ("video".equals(mode)) {
                        mode = "none";
                    } else {
                        mode = "video";
                    }
                    updateButtonStates(videoButton, audioButton);
                    if ("video".equals(mode)) showVideoQualityDialog(videoButton);
                } else Toast.makeText(context, "Loading streams...", Toast.LENGTH_SHORT).show();
            });
        }

        if (audioButton != null) {
            audioButton.setOnClickListener(v -> {
                if (streamDetails != null) {
                    if ("audio".equals(mode)) {
                        mode = "none";
                    } else {
                        mode = "audio";
                    }
                    updateButtonStates(videoButton, audioButton);
                    if ("audio".equals(mode)) showAudioSelectionDialog(audioButton);
                } else Toast.makeText(context, "Loading streams...", Toast.LENGTH_SHORT).show();
            });
        }

        if (subtitleButton != null) {
            subtitleButton.setOnClickListener(v -> {
                if (streamDetails != null) {
                    if (subtitleSel) {
                        subtitleSel = false;
                        subtitleSelStream = null;
                        updateAuxButtonState(subtitleButton, false);
                    } else {
                        showSubtitleSelectionDialog(subtitleButton);
                    }
                } else Toast.makeText(context, "Loading streams...", Toast.LENGTH_SHORT).show();
            });
        }

        if (thumbnailButton != null) {
            thumbnailButton.setOnClickListener(v -> {
                thumbSel = !thumbSel;
                updateAuxButtonState(thumbnailButton, thumbSel);
            });
        }

        if (downloadButton != null) {
            downloadButton.setOnClickListener(v -> {
                if (videoDetails == null) return;
                kv.encode(KEY_LAST_MODE, mode);
                kv.encode(KEY_LAST_THUMB_SEL, thumbSel);
                kv.encode(KEY_LAST_SUB_SEL, subtitleSel);
                if (videoSelStream != null) kv.encode(KEY_LAST_VIDEO_RES, videoSelStream.getResolution());
                if (audioSelStream != null) kv.encode(KEY_LAST_AUDIO_TRACK, getTrackId(audioSelStream));
                if (videoAudioStream != null) kv.encode(KEY_LAST_VIDEO_AUDIO_TRACK, getTrackId(videoAudioStream));
                if (subtitleSelStream != null) kv.encode(KEY_LAST_SUB_LANG, subtitleSelStream.getLanguageTag());

                String fName = videoDetails.getTitle();
                if (editText != null && !editText.getText().toString().isEmpty()) {
                    fName = editText.getText().toString();
                }
                String fileName = sanitizeFileName(fName);
                List<Task> tasks = getTasks(fileName);
                if (tasks.isEmpty()) {
                    Toast.makeText(context, "Please select at least one item to download", Toast.LENGTH_SHORT).show();
                    return;
                }
                if (isBound && downloadService != null) downloadService.download(tasks);
                dialog.dismiss();
            });
        }

        View cancelBtn = dialogView.findViewById(R.id.button_cancel);
        if (cancelBtn != null) cancelBtn.setOnClickListener(v -> dialog.dismiss());

        dialog.setOnDismissListener(di -> {
            if (isBound) {
                context.unbindService(connection);
                isBound = false;
            }
            Activity activity = getActivity(context);
            if (activity instanceof DownloadReceiverActivity) {
                activity.finish();
            }
        });
        dialog.show();
    }

    private void updateButtonStates(Button vBtn, Button aBtn) {
        int primary = getThemeAttrColor(androidx.appcompat.R.attr.colorPrimary);
        int onPrimary = getThemeAttrColor(com.google.android.material.R.attr.colorOnPrimary);
        int surfaceVariant = getThemeAttrColor(com.google.android.material.R.attr.colorSurfaceVariant);
        int onSurfaceVariant = getThemeAttrColor(com.google.android.material.R.attr.colorOnSurfaceVariant);

        if ("video".equals(mode)) {
            if (vBtn != null) { setBtnStyle(vBtn, primary, onPrimary); }
            if (aBtn != null) { setBtnStyle(aBtn, surfaceVariant, onSurfaceVariant); }
        } else if ("audio".equals(mode)) {
            if (aBtn != null) { setBtnStyle(aBtn, primary, onPrimary); }
            if (vBtn != null) { setBtnStyle(vBtn, surfaceVariant, onSurfaceVariant); }
        } else {
            if (vBtn != null) { setBtnStyle(vBtn, surfaceVariant, onSurfaceVariant); }
            if (aBtn != null) { setBtnStyle(aBtn, surfaceVariant, onSurfaceVariant); }
        }
    }

    private void setBtnStyle(Button btn, int bg, int text) {
        btn.setBackgroundTintList(ColorStateList.valueOf(bg));
        btn.setTextColor(text);
    }

    private void updateAuxButtonState(Button btn, boolean selected) {
        if (btn == null) return;
        int primary = getThemeAttrColor(androidx.appcompat.R.attr.colorPrimary);
        int onPrimary = getThemeAttrColor(com.google.android.material.R.attr.colorOnPrimary);
        int surfaceVariant = getThemeAttrColor(com.google.android.material.R.attr.colorSurfaceVariant);
        int onSurfaceVariant = getThemeAttrColor(com.google.android.material.R.attr.colorOnSurfaceVariant);

        if (selected) setBtnStyle(btn, primary, onPrimary);
        else setBtnStyle(btn, surfaceVariant, onSurfaceVariant);
    }

    private int getThemeAttrColor(int attr) {
        TypedValue typedValue = new TypedValue();
        if (context.getTheme().resolveAttribute(attr, typedValue, true)) {
            return typedValue.data;
        }
        return Color.BLACK;
    }

    private void restorePreferences(Button vBtn, Button aBtn) {
        if (streamDetails == null) return;

        mode = kv.decodeString(KEY_LAST_MODE, "video");
        thumbSel = kv.decodeBool(KEY_LAST_THUMB_SEL, false);
        subtitleSel = kv.decodeBool(KEY_LAST_SUB_SEL, false);

        String lastRes = kv.decodeString(KEY_LAST_VIDEO_RES, "");
        String lastAudioTrack = kv.decodeString(KEY_LAST_AUDIO_TRACK, "");
        String lastVideoAudioTrack = kv.decodeString(KEY_LAST_VIDEO_AUDIO_TRACK, "");
        String lastSubLang = kv.decodeString(KEY_LAST_SUB_LANG, "");

        for (VideoStream vs : streamDetails.getVideoStreams()) {
            if (vs.getFormat() == MediaFormat.MPEG_4 && vs.getResolution().equals(lastRes)) {
                videoSelStream = vs;
                break;
            }
        }
        if (videoSelStream == null && !streamDetails.getVideoStreams().isEmpty()) {
            videoSelStream = streamDetails.getVideoStreams().get(0);
        }

        for (AudioStream as : streamDetails.getAudioStreams()) {
            if (Objects.equals(getTrackId(as), lastAudioTrack)) {
                audioSelStream = as;
                break;
            }
        }
        if (audioSelStream == null && !streamDetails.getAudioStreams().isEmpty()) {
            audioSelStream = streamDetails.getAudioStreams().get(0);
        }

        for (AudioStream as : streamDetails.getAudioStreams()) {
            if (Objects.equals(getTrackId(as), lastVideoAudioTrack)) {
                videoAudioStream = as;
                break;
            }
        }
        if (videoAudioStream == null && !streamDetails.getAudioStreams().isEmpty()) {
            videoAudioStream = streamDetails.getAudioStreams().get(0);
        }

        if (subtitleSel && !streamDetails.getSubtitles().isEmpty()) {
            for (SubtitlesStream s : streamDetails.getSubtitles()) {
                if (s.getLanguageTag().equals(lastSubLang)) {
                    subtitleSelStream = s;
                    break;
                }
            }
            if (subtitleSelStream == null) subtitleSelStream = streamDetails.getSubtitles().get(0);
        }

        mainHandler.post(() -> {
            updateButtonStates(vBtn, aBtn);
            updateAuxButtonState(dialogView.findViewById(R.id.button_thumbnail), thumbSel);
            updateAuxButtonState(dialogView.findViewById(R.id.button_subtitle), subtitleSel);
        });
    }

    private String getTrackId(AudioStream s) {
        String lang = s.getAudioLocale() != null ? s.getAudioLocale().getLanguage() : "und";
        return lang + "_" + s.getAudioTrackName() + "_" + s.getAverageBitrate();
    }

    private void showVideoQualityDialog(Button btn) {
        @SuppressLint("InflateParams") View v = LayoutInflater.from(context).inflate(R.layout.quality_selector, null);
        AlertDialog d = new MaterialAlertDialogBuilder(context).setTitle(R.string.video_quality).setView(v).create();
        View cancel = v.findViewById(R.id.button_cancel);
        if (cancel != null) cancel.setOnClickListener(v1 -> {
            mode = "none";
            updateButtonStates(btn, dialogView.findViewById(R.id.button_audio));
            d.dismiss();
        });
        setupVideoContainer(v, d);
        d.show();
    }

    private void setupVideoContainer(View viewRoot, AlertDialog d) {
        LinearLayout container = viewRoot.findViewById(R.id.quality_container);
        ProgressBar loading = viewRoot.findViewById(R.id.loadingBar2);
        if (streamDetails == null || container == null) return;

        if (loading != null) loading.setVisibility(View.GONE);
        final CheckBox[] refs = new CheckBox[1];
        long audioSize = 0;
        if (!streamDetails.getAudioStreams().isEmpty()) {
            AudioStream firstAudio = streamDetails.getAudioStreams().get(0);
            if (firstAudio.getItagItem() != null) {
                audioSize = firstAudio.getItagItem().getContentLength();
            }
        }

        for (VideoStream s : streamDetails.getVideoStreams()) {
            if (s.getFormat() != MediaFormat.MPEG_4) continue;
            CheckBox cb = new CheckBox(context);
            long totalSize = audioSize;
            if (s.getItagItem() != null) {
                totalSize += s.getItagItem().getContentLength();
            }
            cb.setText(String.format(Locale.US, "%s (%s)", s.getResolution(), formatSize(totalSize)));
            cb.setOnCheckedChangeListener((view, is) -> {
                if (is) {
                    if (refs[0] != null) refs[0].setChecked(false);
                    videoSelStream = s;
                    refs[0] = cb;
                }
            });
            container.addView(cb);
            if (videoSelStream != null && videoSelStream.getResolution().equals(s.getResolution())) {
                cb.setChecked(true);
                refs[0] = cb;
            }
        }

        View confirm = viewRoot.findViewById(R.id.button_confirm);
        if (confirm != null) confirm.setOnClickListener(v1 -> {
            List<AudioStream> uniqueAudio = getUniqueAudioStreams(streamDetails.getAudioStreams());
            if (videoSelStream != null && uniqueAudio.size() > 1) {
                showAudioTrackSelectionForVideo(uniqueAudio, d::dismiss);
            } else {
                if (!streamDetails.getAudioStreams().isEmpty()) {
                    videoAudioStream = streamDetails.getAudioStreams().get(0);
                }
                d.dismiss();
            }
        });
    }

    private void showAudioTrackSelectionForVideo(List<AudioStream> streams, Runnable onSelected) {
        @SuppressLint("InflateParams") View v = LayoutInflater.from(context).inflate(R.layout.quality_selector, null);
        AlertDialog d = new MaterialAlertDialogBuilder(context).setTitle("Select Audio Track").setView(v).setCancelable(false).create();
        setupAudioTrackSelection(v, d, streams, onSelected);
        d.show();
    }

    private void setupAudioTrackSelection(View viewRoot, AlertDialog d, List<AudioStream> displayTracks, Runnable onSelected) {
        LinearLayout container = viewRoot.findViewById(R.id.quality_container);
        View loading = viewRoot.findViewById(R.id.loadingBar2);
        if (container == null) return;
        if (loading != null) loading.setVisibility(View.GONE);
        
        final CheckBox[] refs = new CheckBox[1];
        
        for (int i = 0; i < displayTracks.size(); i++) {
            AudioStream s = displayTracks.get(i);
            CheckBox cb = new CheckBox(context);
            cb.setText(getAudioTrackLabel(s, i, false));
            cb.setOnCheckedChangeListener((v1, is) -> {
                if (is) {
                    if (refs[0] != null) refs[0].setChecked(false);
                    videoAudioStream = s;
                    refs[0] = cb;
                }
            });
            container.addView(cb);
            if (videoAudioStream != null && videoAudioStream.getContent().equals(s.getContent())) {
                cb.setChecked(true);
                refs[0] = cb;
            }
        }
        View cancel = viewRoot.findViewById(R.id.button_cancel);
        if (cancel != null) cancel.setOnClickListener(v1 -> d.dismiss());
        View confirm = viewRoot.findViewById(R.id.button_confirm);
        if (confirm != null) confirm.setOnClickListener(v1 -> {
            if (onSelected != null) onSelected.run();
            d.dismiss();
        });
    }

    private void showAudioSelectionDialog(Button btn) {
        @SuppressLint("InflateParams") View v = LayoutInflater.from(context).inflate(R.layout.quality_selector, null);
        AlertDialog d = new MaterialAlertDialogBuilder(context).setTitle(R.string.audio_track).setView(v).create();
        View cancel = v.findViewById(R.id.button_cancel);
        if (cancel != null) cancel.setOnClickListener(v1 -> {
            mode = "none";
            updateButtonStates(dialogView.findViewById(R.id.button_video), btn);
            d.dismiss();
        });
        setupAudioContainer(v, d);
        d.show();
    }

    private void setupAudioContainer(View dView, AlertDialog d) {
        LinearLayout container = dView.findViewById(R.id.quality_container);
        ProgressBar loading = dView.findViewById(R.id.loadingBar2);
        if (streamDetails == null || container == null) return;

        if (loading != null) loading.setVisibility(View.GONE);
        
        final List<AudioStream> displayTracks = getUniqueAudioStreams(streamDetails.getAudioStreams());
        final CheckBox[] refs = new CheckBox[1];
        
        for (int i = 0; i < displayTracks.size(); i++) {
            AudioStream s = displayTracks.get(i);
            if (s.getFormat() != MediaFormat.M4A) continue;
            CheckBox cb = new CheckBox(context);
            cb.setText(getAudioTrackLabel(s, i, true));
            cb.setOnCheckedChangeListener((v, is) -> {
                if (is) {
                    if (refs[0] != null) refs[0].setChecked(false);
                    audioSelStream = s;
                    refs[0] = cb;
                }
            });
            container.addView(cb);
            if (audioSelStream != null && audioSelStream.getContent().equals(s.getContent())) {
                cb.setChecked(true);
                refs[0] = cb;
            }
        }
        View confirm = dView.findViewById(R.id.button_confirm);
        if (confirm != null) confirm.setOnClickListener(v1 -> d.dismiss());
    }

    @NonNull
    private String getAudioTrackLabel(AudioStream track, int i, boolean showSize) {
        String name = track.getAudioTrackName();
        String lang = track.getAudioLocale() != null ? track.getAudioLocale().getDisplayLanguage() : null;
        boolean isOriginal = name != null && name.toLowerCase(Locale.ROOT).contains("original");

        StringBuilder sb = new StringBuilder();
        if (isOriginal) {
            if (lang != null && !lang.isEmpty()) sb.append(lang).append(" (Original)");
            else sb.append("Original");
        } else if (lang != null && !lang.isEmpty()) {
            sb.append(lang);
            if (name != null && !name.isEmpty() && !name.equalsIgnoreCase(lang) && !name.toLowerCase(Locale.ROOT).contains("original")) {
                String cleanName = name.replaceAll("(?i)\\b" + Pattern.quote(lang) + "\\b", "").replaceAll("[()]", "").trim();
                if (!cleanName.isEmpty()) {
                    sb.append(" (").append(cleanName).append(")");
                }
            }
        } else if (name != null && !name.isEmpty()) {
            sb.append(name);
        } else {
            sb.append("Audio Track ").append(i + 1);
        }
        
        if (showSize && track.getItagItem() != null) {
            sb.append(" (").append(formatSize(track.getItagItem().getContentLength())).append(")");
        }
        return sb.toString();
    }

    private List<AudioStream> getUniqueAudioStreams(List<AudioStream> streams) {
        if (streams == null) return new ArrayList<>();
        final Map<String, AudioStream> uniqueMap = new LinkedHashMap<>();
        for (AudioStream s : streams) {
            String langCode = s.getAudioLocale() != null ? s.getAudioLocale().getLanguage() : "und";
            String name = s.getAudioTrackName();
            boolean isOriginal = name != null && name.toLowerCase(Locale.ROOT).contains("original");
            String key = langCode + "_" + name + "_" + isOriginal;
            
            AudioStream existing = uniqueMap.get(key);
            if (existing == null || s.getAverageBitrate() > existing.getAverageBitrate()) {
                uniqueMap.put(key, s);
            }
        }
        return new ArrayList<>(uniqueMap.values());
    }

    private void sortAudioStreams(List<AudioStream> audioStreams) {
        if (audioStreams == null || audioStreams.isEmpty()) return;
        final String savedLanguage = kv.decodeString(KEY_LAST_AUDIO_LANG, "und");
        audioStreams.sort((first, second) -> {
            final int originalComparison = Boolean.compare(
                    isOriginal(second),
                    isOriginal(first));
            if (originalComparison != 0) return originalComparison;

            final int savedLanguageComparison = Boolean.compare(
                    matchesLanguage(second, savedLanguage),
                    matchesLanguage(first, savedLanguage));
            if (savedLanguageComparison != 0) return savedLanguageComparison;

            return Long.compare(second.getAverageBitrate(), first.getAverageBitrate());
        });
    }

    private boolean isOriginal(AudioStream s) {
        return s.getAudioTrackName() != null && s.getAudioTrackName().toLowerCase(Locale.ROOT).contains("original");
    }

    private boolean matchesLanguage(AudioStream s, String lang) {
        String sLang = s.getAudioLocale() != null ? s.getAudioLocale().getLanguage() : "und";
        return sLang.equalsIgnoreCase(lang);
    }

    private void showSubtitleSelectionDialog(Button btn) {
        @SuppressLint("InflateParams") View v = LayoutInflater.from(context).inflate(R.layout.quality_selector, null);
        AlertDialog d = new MaterialAlertDialogBuilder(context).setTitle(R.string.subtitles).setView(v).create();
        View cancel = v.findViewById(R.id.button_cancel);
        if (cancel != null) cancel.setOnClickListener(v1 -> d.dismiss());
        setupSubtitleContainer(v, d, btn);
        d.show();
    }

    private void setupSubtitleContainer(View viewRoot, AlertDialog d, Button btn) {
        LinearLayout container = viewRoot.findViewById(R.id.quality_container);
        View loading = viewRoot.findViewById(R.id.loadingBar2);
        if (streamDetails == null || container == null) return;
        if (loading != null) loading.setVisibility(View.GONE);

        final CheckBox[] refs = new CheckBox[1];
        for (SubtitlesStream s : streamDetails.getSubtitles()) {
            CheckBox cb = new CheckBox(context);
            cb.setText(s.getDisplayLanguageName());
            cb.setOnCheckedChangeListener((v1, is) -> {
                if (is) {
                    if (refs[0] != null) refs[0].setChecked(false);
                    subtitleSelStream = s;
                    refs[0] = cb;
                } else if (refs[0] == cb) {
                    subtitleSelStream = null;
                    refs[0] = null;
                }
            });
            container.addView(cb);
            if (subtitleSelStream != null && subtitleSelStream.getLanguageTag().equals(s.getLanguageTag())) {
                cb.setChecked(true);
                refs[0] = cb;
            }
        }
        View confirm = viewRoot.findViewById(R.id.button_confirm);
        if (confirm != null) confirm.setOnClickListener(v1 -> {
            subtitleSel = refs[0] != null;
            updateAuxButtonState(btn, subtitleSel);
            d.dismiss();
        });
    }

    private String sanitizeFileName(String f) {
        return f.replaceAll("[<>:\"/|?*]", "_");
    }

    private String formatSize(long bytes) {
        return bytes <= 0 ? "Unknown" : String.format(Locale.US, "%.1f MB", bytes / 1048576.0);
    }

    @NonNull
    private List<Task> getTasks(String f) {
        List<Task> t = new ArrayList<>();
        File d = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), context.getString(R.string.app_name));
        if (!d.exists() && !d.mkdirs()) {
            Log.e("DownloadDialog", "Failed to create directory: " + d.getAbsolutePath());
        }

        if ("video".equals(mode) && videoSelStream != null) {
            AudioStream audio = (videoAudioStream != null) ? videoAudioStream : (streamDetails != null && !streamDetails.getAudioStreams().isEmpty() ? streamDetails.getAudioStreams().get(0) : null);
            if (audio != null) {
                t.add(new Task(videoDetails.getId() + ":v", videoSelStream, audio, null, null, f, d, threadCount, videoDetails.getTitle(), videoDetails.getThumbnail(), videoSelStream.getResolution(), null, null));
            }
        } else if ("audio".equals(mode) && audioSelStream != null) {
            t.add(new Task(videoDetails.getId() + ":a", null, audioSelStream, null, null, f, d, threadCount, videoDetails.getTitle(), videoDetails.getThumbnail(), null, null, null));
        }

        if (subtitleSel && subtitleSelStream != null) {
            t.add(new Task(videoDetails.getId() + ":s", null, null, subtitleSelStream, null, f, d, threadCount, videoDetails.getTitle(), videoDetails.getThumbnail(), null, null, null));
        }

        if (thumbSel)
            t.add(new Task(videoDetails.getId() + ":t", null, null, null, videoDetails.getThumbnail(), f, d, threadCount, videoDetails.getTitle(), videoDetails.getThumbnail(), null, null, null));

        return t;
    }

    private static Activity getActivity(Context context) {
        if (context instanceof Activity) {
            return (Activity) context;
        } else if (context instanceof ContextWrapper) {
            return getActivity(((ContextWrapper) context).getBaseContext());
        }
        return null;
    }
}
