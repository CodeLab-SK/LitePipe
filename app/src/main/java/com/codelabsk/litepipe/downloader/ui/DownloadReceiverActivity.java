package com.codelabsk.litepipe.downloader.ui;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.media3.common.util.UnstableApi;

import com.codelabsk.litepipe.extractor.YoutubeExtractor;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.inject.Inject;

import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
@UnstableApi
public class DownloadReceiverActivity extends AppCompatActivity {

    @Inject
    YoutubeExtractor youtubeExtractor;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        Intent intent = getIntent();
        if (intent != null && Intent.ACTION_SEND.equals(intent.getAction()) && "text/plain".equals(intent.getType())) {
            String text = intent.getStringExtra(Intent.EXTRA_TEXT);
            String url = extractUrl(text);
            
            if (url != null) {
                DownloadDialog dialog = new DownloadDialog(url, this, youtubeExtractor);
                dialog.show();
            } else {
                Toast.makeText(this, "No valid link found", Toast.LENGTH_SHORT).show();
                finish();
            }
        } else {
            finish();
        }
    }

    private String extractUrl(String text) {
        if (text == null) return null;
        Matcher m = Pattern.compile("https?://[\\w./?=&%#-]+", Pattern.CASE_INSENSITIVE).matcher(text);
        return m.find() ? m.group() : null;
    }
}
