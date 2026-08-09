package com.codelabsk.litepipe.util;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.content.FileProvider;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.codelabsk.litepipe.R;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;

import javax.inject.Inject;
import javax.inject.Singleton;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

@Singleton
public class UpdateManager {
    private static final String TAG = "UpdateManager";
    private static final String GITHUB_RELEASE_API = "https://api.github.com/repos/CodeLab-SK/LitePipe/releases/latest";

    private final OkHttpClient client;
    private final Gson gson;

    @Inject
    public UpdateManager(OkHttpClient client, Gson gson) {
        this.client = client;
        this.gson = gson;
    }

    public void checkForUpdates(Activity activity, boolean manual) {
        Request request = new Request.Builder()
                .url(GITHUB_RELEASE_API)
                .header("Accept", "application/vnd.github.v3+json")
                .header("User-Agent", "LitePipe-App")
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                Log.e(TAG, "Update check network failure", e);
                if (manual) {
                    activity.runOnUiThread(() -> Toast.makeText(activity, R.string.failed_to_check_for_updates, Toast.LENGTH_SHORT).show());
                }
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) {
                try (response) {
                    if (!response.isSuccessful()) throw new IOException("Unexpected code " + response);

                    String body = Objects.requireNonNull(response.body()).string();
                    JsonObject json = gson.fromJson(body, JsonObject.class);
                    
                    if (json == null || !json.has("tag_name")) return;
                    
                    String latest = json.get("tag_name").getAsString();
                    String current = activity.getPackageManager().getPackageInfo(activity.getPackageName(), 0).versionName;

                    if (isNewerVersion(current, latest)) {
                        String downloadUrl = null;
                        if (json.has("assets")) {
                            JsonArray assets = json.getAsJsonArray("assets");
                            downloadUrl = findBestApk(assets);
                        }
                        
                        if (downloadUrl == null) downloadUrl = json.get("html_url").getAsString();
                        
                        String finalDownloadUrl = downloadUrl;
                        activity.runOnUiThread(() -> showUpdateDialog(activity, latest, finalDownloadUrl));
                    } else if (manual) {
                        activity.runOnUiThread(() -> Toast.makeText(activity, R.string.no_updates_available, Toast.LENGTH_SHORT).show());
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Update check error", e);
                }
            }
        });
    }

    private String findBestApk(JsonArray assets) {
        String arch = getDeviceArchitecture();
        String universalUrl = null;
        String archSpecificUrl = null;

        for (int i = 0; i < assets.size(); i++) {
            JsonObject asset = assets.get(i).getAsJsonObject();
            String name = asset.get("name").getAsString().toLowerCase();
            String url = asset.get("browser_download_url").getAsString();

            if (!name.endsWith(".apk")) continue;

            if (name.contains(arch)) {
                archSpecificUrl = url;
            } else if (name.contains("universal")) {
                universalUrl = url;
            } else if (universalUrl == null) {
                // Fallback to the first APK found if no universal/arch-specific yet
                universalUrl = url;
            }
        }
        return archSpecificUrl != null ? archSpecificUrl : universalUrl;
    }

    private String getDeviceArchitecture() {
        String abi = Build.SUPPORTED_ABIS[0].toLowerCase();
        if (abi.contains("arm64")) return "arm64-v8a";
        if (abi.contains("armeabi")) return "armeabi-v7a";
        if (abi.contains("x86_64")) return "x86_64";
        if (abi.contains("x86")) return "x86";
        return abi;
    }

    private void showUpdateDialog(Activity activity, String version, String downloadUrl) {
        // Ensure dialog is only shown if the activity is still active
        if (activity.isFinishing() || activity.isDestroyed()) return;
        
        new MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.update_dialog_title)
                .setMessage(activity.getString(R.string.update_dialog_message, version))
                .setPositiveButton(R.string.update, (dialog, which) -> {
                    if (downloadUrl.endsWith(".apk")) {
                        downloadAndInstall(activity, downloadUrl);
                    } else {
                        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(downloadUrl));
                        activity.startActivity(intent);
                    }
                })
                .setNegativeButton(R.string.close, null)
                .show();
    }

    private void downloadAndInstall(Activity activity, String url) {
        ProgressDialog progressDialog = new ProgressDialog(activity);
        progressDialog.setMessage(activity.getString(R.string.downloading_update));
        progressDialog.setIndeterminate(false);
        progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        progressDialog.setCancelable(false);
        progressDialog.show();

        Request request = new Request.Builder().url(url).build();
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                activity.runOnUiThread(() -> {
                    progressDialog.dismiss();
                    Toast.makeText(activity, R.string.failed_to_check_for_updates, Toast.LENGTH_SHORT).show();
                });
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (!response.isSuccessful()) {
                    activity.runOnUiThread(progressDialog::dismiss);
                    return;
                }

                File file = new File(activity.getExternalCacheDir(), "update.apk");
                long totalBytes = response.body().contentLength();
                
                try (InputStream is = response.body().byteStream();
                     FileOutputStream fos = new FileOutputStream(file)) {
                    
                    byte[] buffer = new byte[8192];
                    long downloaded = 0;
                    int read;
                    while ((read = is.read(buffer)) != -1) {
                        fos.write(buffer, 0, read);
                        downloaded += read;
                        long finalDownloaded = downloaded;
                        activity.runOnUiThread(() -> {
                            if (totalBytes > 0) {
                                progressDialog.setProgress((int) (finalDownloaded * 100 / totalBytes));
                            }
                        });
                    }
                    fos.flush();
                }

                activity.runOnUiThread(() -> {
                    progressDialog.dismiss();
                    installApk(activity, file);
                });
            }
        });
    }

    private void installApk(Activity activity, File file) {
        Uri uri = FileProvider.getUriForFile(activity, activity.getPackageName() + ".provider", file);
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(uri, "application/vnd.android.package-archive");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        activity.startActivity(intent);
    }

    private boolean isNewerVersion(String current, String latest) {
        if (current == null || latest == null) return false;

        String c = current.startsWith("v") ? current.substring(1) : current;
        String l = latest.startsWith("v") ? latest.substring(1) : latest;

        String[] currentParts = c.split("\\.");
        String[] latestParts = l.split("\\.");
        int length = Math.max(currentParts.length, latestParts.length);

        for (int i = 0; i < length; i++) {
            int cPart = i < currentParts.length ? parseSafeInt(currentParts[i]) : 0;
            int lPart = i < latestParts.length ? parseSafeInt(latestParts[i]) : 0;
            if (lPart > cPart) return true;
            if (lPart < cPart) return false;
        }
        return false;
    }

    private int parseSafeInt(String part) {
        if (part == null) return 0;
        String digits = part.replaceAll("\\D", "");
        if (digits.isEmpty()) return 0;
        try {
            return Integer.parseInt(digits);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}