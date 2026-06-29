package com.hhst.youtubelite.ui;

import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.webkit.WebStorage;
import android.webkit.WebView;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.hhst.youtubelite.Constants;
import com.hhst.youtubelite.R;
import com.hhst.youtubelite.util.UpdateManager;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import javax.inject.Inject;

import dagger.hilt.android.AndroidEntryPoint;
import okio.BufferedSink;
import okio.Okio;
import okio.Source;

@AndroidEntryPoint
public class AboutActivity extends AppCompatActivity {
	private static final String TAG = "AboutActivity";
	
	@Inject
	UpdateManager updateManager;
	
	private TextView checkUpdateText;
	private View checkUpdateLayout;

	@Override
	protected void onCreate(@Nullable Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		EdgeToEdge.enable(this);
		setContentView(R.layout.activity_about);
		
		View mainView = findViewById(android.R.id.content);
		ViewCompat.setOnApplyWindowInsetsListener(mainView, (v, insets) -> {
			Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
			v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
			return insets;
		});

		MaterialToolbar toolbar = findViewById(R.id.toolbar);
		if (toolbar != null) {
			toolbar.setNavigationOnClickListener(v -> finish());
		}

		ImageView iconView = findViewById(R.id.app_icon);
		TextView nameView = findViewById(R.id.app_name);
		TextView versionView = findViewById(R.id.app_version);
		TextView descriptionView = findViewById(R.id.app_description);
		View sourceCodeLayout = findViewById(R.id.source_code_layout);
		checkUpdateLayout = findViewById(R.id.check_update_layout);
		checkUpdateText = findViewById(R.id.check_update_text);
		View clearCacheLayout = findViewById(R.id.clear_cache_layout);
		View exportLogLayout = findViewById(R.id.export_log_layout);

		try {
			PackageManager pm = getPackageManager();
			PackageInfo pi = pm.getPackageInfo(getPackageName(), 0);
			iconView.setImageResource(R.drawable.app_logo);
			nameView.setText(R.string.app_name);
			versionView.setText(getString(R.string.version, pi.versionName));
		} catch (Exception e) {
			Log.e(TAG, "Failed to load app info", e);
		}

		descriptionView.setText(R.string.app_description);
		sourceCodeLayout.setOnClickListener(v -> {
			Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(getString(R.string.source_link)));
			startActivity(intent);
		});

		checkUpdateLayout.setOnClickListener(v -> updateManager.checkForUpdates(this, true));
		clearCacheLayout.setOnClickListener(v -> showClearCacheDialog());
		exportLogLayout.setOnClickListener(v -> exportLogs());
	}

	private void showClearCacheDialog() {
		new MaterialAlertDialogBuilder(this)
				.setTitle(R.string.clear_cache)
				.setMessage(R.string.clear_cache_confirmation)
				.setPositiveButton(R.string.clear, (dialog, which) -> clearAppCache())
				.setNegativeButton(android.R.string.cancel, null)
				.show();
	}

	private void clearAppCache() {
		new Thread(() -> {
			try {

				runOnUiThread(() -> {
					WebView webView = new WebView(AboutActivity.this);
					webView.clearCache(true);
					WebStorage.getInstance().deleteAllData();
				});

				deleteDir(getCacheDir());
				deleteDir(getExternalCacheDir());

				runOnUiThread(() -> Toast.makeText(AboutActivity.this, R.string.cache_cleared, Toast.LENGTH_SHORT).show());
			} catch (Exception e) {
				Log.e(TAG, "Failed to clear cache", e);
			}
		}).start();
	}

	private boolean deleteDir(File dir) {
		if (dir != null && dir.isDirectory()) {
			String[] children = dir.list();
			if (children != null) {
				for (String child : children) {
					boolean success = deleteDir(new File(dir, child));
					if (!success) return false;
				}
			}
			return dir.delete();
		} else if (dir != null && dir.isFile()) return dir.delete();
		else return false;
	}

	private void exportLogs() {
		new Thread(() -> {
			try {
				String version = "unknown";
				try {
					version = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
				} catch (Exception ignored) {
				}

				String time = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
				File destFile = new File(getExternalCacheDir(), "litube_error_log_" + time + ".txt");
				File srcFile = new File(getFilesDir(), Constants.LOGGING_FILENAME);

				String header = String.format(Locale.US, "--------- Device Info ---------\nDevice: %s\nModel: %s\nAndroid: %s\nApp Version: %s\n-------------------------------\n\n", Build.DEVICE, Build.MODEL, Build.VERSION.RELEASE, version);

				try (BufferedSink sink = Okio.buffer(Okio.sink(destFile))) {
					sink.writeString(header, StandardCharsets.UTF_8);
					if (srcFile.exists()) {
						try (Source source = Okio.source(srcFile)) {
							sink.writeAll(source);
						}
					}
				}

				Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".provider", destFile);
				Intent intent = new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

				runOnUiThread(() -> startActivity(Intent.createChooser(intent, getString(R.string.export_error_log))));

			} catch (Exception e) {
				Log.e(TAG, "Log export error", e);
				runOnUiThread(() -> Toast.makeText(this, R.string.failed_to_export_log, Toast.LENGTH_SHORT).show());
			}
		}).start();
	}
}
