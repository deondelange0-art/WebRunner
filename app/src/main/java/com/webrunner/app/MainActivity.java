package com.webrunner.app;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.webkit.JavascriptInterface;
import android.webkit.PermissionRequest;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONArray;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private WebView webView;
    private TextToSpeech tts;
    private SpeechRecognizer speechRecognizer;
    private boolean listening = false;
    private File dataDir;

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        webView = findViewById(R.id.webview);
        dataDir = new File(getFilesDir(), "js_data");
        if (!dataDir.exists()) dataDir.mkdirs();

        initTts();
        initSpeech();
        setupWebView();

        String filePath = getIntent().getStringExtra("file_path");
        String safUri = getIntent().getStringExtra("saf_uri");

        if (safUri != null) {
            webView.loadUrl(safUri);
        } else if (filePath != null) {
            webView.loadUrl("file://" + filePath);
        } else {
            finish();
        }
    }

    private void initTts() {
        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) tts.setLanguage(Locale.getDefault());
        });
    }

    private void initSpeech() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) return;
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
        speechRecognizer.setRecognitionListener(new RecognitionListener() {
            @Override public void onReadyForSpeech(Bundle p) {}
            @Override public void onBeginningOfSpeech() {}
            @Override public void onRmsChanged(float v) {}
            @Override public void onBufferReceived(byte[] b) {}
            @Override public void onEndOfSpeech() {}
            @Override public void onPartialResults(Bundle b) {}
            @Override public void onEvent(int i, Bundle b) {}

            @Override
            public void onError(int error) {
                listening = false;
                runOnUiThread(() -> webView.evaluateJavascript(
                        "window.onSpeechError&&window.onSpeechError('Error "+error+"');", null));
            }

            @Override
            public void onResults(Bundle results) {
                listening = false;
                ArrayList<String> list = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                String text = (list != null && !list.isEmpty()) ? list.get(0) : "";
                String safe = text.replace("'", "\\'");
                runOnUiThread(() -> webView.evaluateJavascript(
                        "window.onSpeechResult&&window.onSpeechResult('"+safe+"');", null));
            }
        });
    }

    private void setupWebView() {
        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(true);
        s.setAllowContentAccess(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);

        webView.addJavascriptInterface(new Bridge(), "Android");
        webView.setWebViewClient(new WebViewClient());
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onPermissionRequest(PermissionRequest request) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    request.grant(request.getResources());
                }
            }
        });
    }

    public class Bridge {
        @JavascriptInterface
        public void speak(String text) {
            if (tts != null) tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "id");
        }

        @JavascriptInterface
        public void startListening() {
            runOnUiThread(() -> {
                if (speechRecognizer == null || listening) return;
                Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
                i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
                listening = true;
                speechRecognizer.startListening(i);
            });
        }

        @JavascriptInterface
        public void stopListening() {
            runOnUiThread(() -> {
                if (speechRecognizer != null && listening) {
                    speechRecognizer.stopListening();
                    listening = false;
                }
            });
        }

        @JavascriptInterface
        public boolean saveFile(String name, String data) {
            try {
                if (name.contains("..") || name.contains("/")) return false;
                File f = new File(dataDir, name);
                try (FileOutputStream out = new FileOutputStream(f)) {
                    out.write(data.getBytes(StandardCharsets.UTF_8));
                }
                return true;
            } catch (Exception e) { return false; }
        }

        @JavascriptInterface
        public String loadFile(String name) {
            try {
                if (name.contains("..") || name.contains("/")) return null;
                File f = new File(dataDir, name);
                if (!f.exists()) return null;
                StringBuilder sb = new StringBuilder();
                try (BufferedReader r = new BufferedReader(
                        new InputStreamReader(new FileInputStream(f), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = r.readLine()) != null) sb.append(line).append("\n");
                }
                return sb.toString().trim();
            } catch (Exception e) { return null; }
        }

        @JavascriptInterface
        public String listFiles() {
            try {
                File[] files = dataDir.listFiles();
                JSONArray arr = new JSONArray();
                if (files != null) for (File f : files) if (f.isFile()) arr.put(f.getName());
                return arr.toString();
            } catch (Exception e) { return "[]"; }
        }
    }

    @Override
    public void onBackPressed() {
        if (webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        if (tts != null) { tts.stop(); tts.shutdown(); }
        if (speechRecognizer != null) speechRecognizer.destroy();
        super.onDestroy();
    }
}
