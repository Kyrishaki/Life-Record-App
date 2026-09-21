package com.kyrishaki.liferecord;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import java.io.File;

public class MainActivity extends Activity {
    private WebView web;
    private MediaRecorder recorder;
    private File output;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        web = new WebView(this);
        web.getSettings().setJavaScriptEnabled(true);
        web.getSettings().setDomStorageEnabled(true);
        web.setWebViewClient(new WebViewClient());
        web.setWebChromeClient(new WebChromeClient());
        web.addJavascriptInterface(new NativeBridge(), "LifeRecordNative");
        setContentView(web);
        web.loadUrl("file:///android_asset/index.html");
    }

    private void emit(String name, String value) {
        web.post(() -> web.evaluateJavascript("window.nativeEvent(" + quote(name) + "," + quote(value) + ")", null));
    }
    private String quote(String s) { return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\""; }

    public class NativeBridge {
        @JavascriptInterface public void requestAndStart() {
            runOnUiThread(() -> {
                if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                    requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, 7);
                } else startRecording();
            });
        }
        @JavascriptInterface public void stop() { runOnUiThread(() -> stopRecording()); }
    }

    @Override public void onRequestPermissionsResult(int code, String[] p, int[] result) {
        super.onRequestPermissionsResult(code, p, result);
        if (code == 7 && result.length > 0 && result[0] == PackageManager.PERMISSION_GRANTED) startRecording();
        else emit("error", "Không thể truy cập micro. Hãy cấp quyền Microphone trong Cài đặt.");
    }

    private void startRecording() {
        try {
            output = new File(getFilesDir(), "recording-" + System.currentTimeMillis() + ".m4a");
            recorder = new MediaRecorder();
            recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            recorder.setAudioEncodingBitRate(96000);
            recorder.setAudioSamplingRate(44100);
            recorder.setOutputFile(output.getAbsolutePath());
            recorder.prepare(); recorder.start(); emit("recording", "started");
        } catch (Exception e) { recorder = null; emit("error", "Không khởi động được ghi âm: " + e.getMessage()); }
    }

    private void stopRecording() {
        if (recorder == null) return;
        try { recorder.stop(); emit("saved", output.getAbsolutePath()); }
        catch (RuntimeException e) { if (output != null) output.delete(); emit("error", "Bản ghi quá ngắn hoặc không có âm thanh."); }
        finally { recorder.release(); recorder = null; }
    }
    @Override protected void onDestroy() { stopRecording(); web.destroy(); super.onDestroy(); }
}
