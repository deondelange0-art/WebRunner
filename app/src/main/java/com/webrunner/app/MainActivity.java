package com.webrunner.app;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.view.View;
import android.webkit.JavascriptInterface;
import android.webkit.PermissionRequest;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONArray;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private WebView webView;
    private TextToSpeech tts;
    private SpeechRecognizer speechRecognizer;
    private boolean listening = false;
    private File dataDir;

    private Button btnConsole;
    private View consolePanel;
    private TextView consoleText;
    private final StringBuilder log = new StringBuilder();
    private boolean consoleVisible = false;

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        webView = findViewById(R.id.webview);
        btnConsole = findViewById(R.id.btn_console);
        consolePanel = findViewById(R.id.console_panel);
        consoleText = findViewById(R.id.console_text);

        dataDir = new File(getFilesDir(), "js_data");
        if (!dataDir.exists()) dataDir.mkdirs();

        SharedPreferences prefs = getSharedPreferences(ToolsActivity.PREFS, MODE_PRIVATE);
        if (prefs.getBoolean(ToolsActivity.KEY_DEV, false)) {
            btnConsole.setVisibility(View.VISIBLE);
        }

        btnConsole.setOnClickListener(v -> toggleConsole());
        findViewById(R.id.btn_close_console).setOnClickListener(v -> toggleConsole());

        initTts();
        initSpeech();
        setupWebView();

        String filePath = getIntent().getStringExtra("file_path");
        if (filePath != null) {
            log("Loading: " + filePath);
            webView.loadUrl("file://" + filePath);
        } else {
            finish();
        }
    }

    private void toggleConsole() {
        consoleVisible = !consoleVisible;
        consolePanel.setVisibility(consoleVisible ? View.VISIBLE : View.GONE);
        if (consoleVisible) consoleText.setText(log.toString());
    }

    private void log(String msg) {
        String time = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date());
        log.append("[").append(time).append("] ").append(msg).append("\n");
        if (consoleVisible) {
            runOnUiThread(() -> consoleText.setText(log.toString()));
        }
    }

    private void initTts() {
        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                tts.setLanguage(Locale.getDefault());
            }
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
                log("Speech error: " + error);
                runOnUiThread(() -> webView.evaluateJavascript(
                        "window.onSpeechError&&window.onSpeechError('Error "+error+"');", null));
            }

            @Override
            public void onResults(Bundle results) {
                listening = false;
                ArrayList<String> list = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                String text = (list != null && !list.isEmpty()) ? list.get(0) : "";
                log("Speech result: " + text);
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

        // ===== Text-to-Speech =====
        @JavascriptInterface
        public void speak(String text) {
            log("speak: " + text);
            if (tts != null) tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "id");
        }

        @JavascriptInterface
        public void stopSpeaking() {
            log("stopSpeaking");
            if (tts != null) tts.stop();
        }

        @JavascriptInterface
        public boolean setLanguage(String code) {
            log("setLanguage: " + code);
            if (tts == null) return false;
            try {
                Locale locale;
                if (code.contains("-")) {
                    String[] parts = code.split("-");
                    locale = new Locale(parts[0], parts[1]);
                } else {
                    locale = new Locale(code);
                }
                int result = tts.setLanguage(locale);
                return result != TextToSpeech.LANG_MISSING_DATA
                        && result != TextToSpeech.LANG_NOT_SUPPORTED;
            } catch (Exception e) {
                log("setLanguage error: " + e.getMessage());
                return false;
            }
        }

        @JavascriptInterface
        public void setSpeechRate(float rate) {
            log("setSpeechRate: " + rate);
            if (tts != null) tts.setSpeechRate(rate);
        }

        @JavascriptInterface
        public void setPitch(float pitch) {
            log("setPitch: " + pitch);
            if (tts != null) tts.setPitch(pitch);
        }

        @JavascriptInterface
        public boolean isSpeaking() {
            return tts != null && tts.isSpeaking();
        }

        // ===== Microphone =====
        @JavascriptInterface
        public void startListening() {
            log("startListening");
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
    log("stopListening");
    runOnUiThread(() -> {
        if (speechRecognizer != null) {
            try { speechRecognizer.stopListening(); } catch (Exception ignored) {}
            try { speechRecognizer.cancel(); } catch (Exception ignored) {}
        }
        listening = false;
        // Tell the page that listening has stopped
        webView.evaluateJavascript(
            "window.onSpeechStopped&&window.onSpeechStopped();", null);
    });
}

        // ===== File storage =====
        @JavascriptInterface
        public boolean saveFile(String name, String data) {
            log("saveFile: " + name);
            try {
                if (name.contains("..") || name.contains("/")) return false;
                File f = new File(dataDir, name);
                try (FileOutputStream out = new FileOutputStream(f)) {
                    out.write(data.getBytes(StandardCharsets.UTF_8));
                }
                return true;
            } catch (Exception e) {
                log("saveFile error: " + e.getMessage());
                return false;
            }
        }

        @JavascriptInterface
        public String loadFile(String name) {
            log("loadFile: " + name);
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
            } catch (Exception e) {
                log("loadFile error: " + e.getMessage());
                return null;
            }
        }

        @JavascriptInterface
        public boolean deleteFile(String name) {
            log("deleteFile: " + name);
            try {
                if (name.contains("..") || name.contains("/")) return false;
                File f = new File(dataDir, name);
                return f.exists() && f.delete();
            } catch (Exception e) { return false; }
        }

        @JavascriptInterface
        public String listFiles() {
            log("listFiles");
            try {
                File[] files = dataDir.listFiles();
                JSONArray arr = new JSONArray();
                if (files != null) for (File f : files) if (f.isFile()) arr.put(f.getName());
                return arr.toString();
            } catch (Exception e) { return "[]"; }
        }

        @JavascriptInterface
        public String getDataPath() {
            log("getDataPath");
            return dataDir.getAbsolutePath();
        }
    }

    @Override
    public void onBackPressed() {
        if (consoleVisible) {
            toggleConsole();
        } else if (webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        if (tts != null) { tts.stop(); tts.shutdown(); }
        if (speechRecognizer != null) speechRecognizer.destroy();
        super.onDestroy();
    }
}
