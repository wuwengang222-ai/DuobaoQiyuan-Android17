package com.duobao.duology;

import android.app.Activity;
import android.app.AlertDialog;
import android.widget.FrameLayout;
import android.widget.Button;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.os.Build;
import android.view.ViewGroup;
import android.widget.Toast;
import android.annotation.SuppressLint;
import android.os.Bundle;
import android.os.SystemClock;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.ConsoleMessage;
import android.util.Log;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebSettings;
import androidx.webkit.WebViewAssetLoader;
import androidx.webkit.WebViewClientCompat;

import java.io.IOException;
import android.util.AtomicFile;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.io.FileOutputStream;

/** An offline Android wrapper. VMRP WASM must actually be provided at build time. */
public final class MainActivity extends Activity {
    private WebView view;
    private Button chapterOptions;
    private EndingFrameObserver endingFrameObserver;
    private volatile boolean wasmReadyForActivePage = false;
    private final DuologyStateMachine chapters = new DuologyStateMachine();
    private final StoryEndingDetector ending = new StoryEndingDetector();
    private volatile boolean awaitingSaveForTransition = false;
    private volatile long lastVisibleDialogueUptime = 0;
    private volatile int currentPageChapter = 1;

    @SuppressLint("SetJavaScriptEnabled")
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        // True Android immersive mode, not merely a large HTML canvas.
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                             WindowManager.LayoutParams.FLAG_FULLSCREEN);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            WindowManager.LayoutParams attrs = getWindow().getAttributes();
            attrs.layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            getWindow().setAttributes(attrs);
        }
        applyImmersiveMode();
        chapters.restore(getPreferences(MODE_PRIVATE).getBoolean("chapter1_finished", false));
        int current = chapters.chapter();
        WebViewAssetLoader loader = new WebViewAssetLoader.Builder()
                .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(this))
                .build();
        view = new WebView(this);
        endingFrameObserver = new EndingFrameObserver(view,
                text -> acceptDisplayedText(1, text, "OCR"));
        WebSettings settings = view.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setAllowFileAccessFromFileURLs(false);
        settings.setAllowUniversalAccessFromFileURLs(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        view.setWebChromeClient(new WebChromeClient() {
            @Override public boolean onConsoleMessage(ConsoleMessage message) {
                Log.i("DuobaoWebView", message.messageLevel() + " "
                        + message.sourceId() + ":" + message.lineNumber() + " " + message.message());
                return true;
            }
        });
        view.setWebViewClient(new WebViewClientCompat() {
            @Override public WebResourceResponse shouldInterceptRequest(WebView v, WebResourceRequest r) {
                String path = r.getUrl().getPath();
                if ("/assets/vmrp.wasm".equals(path)) {
                    try {
                        return new WebResourceResponse("application/wasm", null,
                                getAssets().open("vmrp.wasm"));
                    } catch (IOException ignored) { return null; }
                }
                return loader.shouldInterceptRequest(r.getUrl());
            }
        });
        view.addJavascriptInterface(new Object() {
            @JavascriptInterface public int currentChapter() { return chapters.chapter(); }
            @JavascriptInterface public void onCanvasActivityDetected(int chapter) {
                Log.i("DuobaoRuntime", "Canvas changed after engine startup; chapter=" + chapter
                        + " (only proves rendering changed, NOT playable MRP)");
            }
            @JavascriptInterface public void onGameCanvasPNG(int chapter, String dataUri) {
                if (chapter != 1 || currentPageChapter != 1 || chapters.chapter() != 1
                        || dataUri == null || dataUri.length() > 3_000_000) return;
                runOnUiThread(() -> {
                    if (endingFrameObserver != null && currentPageChapter == 1)
                        endingFrameObserver.submitCanvasPng(dataUri);
                });
            }
            @JavascriptInterface public void onRuntimeFailure(int chapter, String message) {
                Log.e("DuobaoRuntime", "VMRP runtime failure, chapter=" + chapter + ": " + message);
                runOnUiThread(() -> {
                    if (chapter == currentPageChapter) {
                        wasmReadyForActivePage = false;
                        if (endingFrameObserver != null) endingFrameObserver.stop();
                        Toast.makeText(MainActivity.this, "模拟器启动失败，请查看运行日志", Toast.LENGTH_LONG).show();
                    }
                });
            }
            @JavascriptInterface public void onEmulatorInitialized(int chapter) {
                Log.i("DuobaoRuntime", "VMRP WASM postRun reached, chapter=" + chapter
                        + " (this is NOT proof MRP scene is playable)");
                runOnUiThread(() -> {
                    if (chapter != currentPageChapter || chapters.chapter() != chapter) return;
                    wasmReadyForActivePage = true;
                    if (chapter == 1 && endingFrameObserver != null) endingFrameObserver.start();
                });
            }
            @JavascriptInterface public String loadSaveState(int chapter) {
                if (chapter != 1 && chapter != 2) return "{}";
                File file = new File(getFilesDir(), "chapter_" + chapter + "_fs.json");
                if (!file.exists() || file.length() > 5_000_000L) return "{}";
                try {
                    byte[] bytes = new AtomicFile(file).readFully();
                    return new String(bytes, StandardCharsets.UTF_8);
                } catch (IOException ignored) { return "{}"; }
            }
            @JavascriptInterface public boolean persistSaveState(int chapter, String json) {
                if (chapter != 1 && chapter != 2 || json == null || json.length() > 5_000_000) return false;
                // Do not trust data supplied by JS as file paths; store opaque JSON only.
                if (!json.startsWith("{\"files\":")) return false;
                AtomicFile target = new AtomicFile(new File(getFilesDir(), "chapter_" + chapter + "_fs.json"));
                FileOutputStream out = null;
                try {
                    out = target.startWrite();
                    out.write(json.getBytes(StandardCharsets.UTF_8));
                    target.finishWrite(out);
                    return true;
                } catch (IOException ignored) {
                    if (out != null) target.failWrite(out);
                    return false;
                }
            }
            @JavascriptInterface public void onDisplayedText(int chapter, String text) {
                if (chapter != 1 || currentPageChapter != 1) return;
                runOnUiThread(() -> acceptDisplayedText(chapter, text, "VMRP-dialog"));
            }
            @JavascriptInterface public void onContinueInput(int chapter) {
                if (chapter != 1 || currentPageChapter != 1) return;
                runOnUiThread(() -> {
                    if (chapters.chapter() != 1 || awaitingSaveForTransition) return;
                    // The next confirmation *after* the ending has really appeared, never
                    // map resource preload or menu exit. A short debounce avoids animation key repeat.
                    if (!ending.awaitingAcknowledgement()
                            || SystemClock.uptimeMillis() - lastVisibleDialogueUptime < 250L) return;
                    if (!ending.onEpilogueAcknowledged()) return;
                    awaitingSaveForTransition = true;
                    // The page must flush IDBFS before we switch to part2.
                    view.evaluateJavascript("window.duobaoFlushAndSwitch && window.duobaoFlushAndSwitch()", null);
                });
            }
            @JavascriptInterface public void onChapterSaveFlushed(int chapter, boolean success) {
                if (chapter != 1) return;
                runOnUiThread(() -> {
                    if (!awaitingSaveForTransition || chapters.chapter() != 1) return;
                    if (!success) {
                        awaitingSaveForTransition = false;
                        ending.retryAfterSaveFailure();
                        // Do not discard a save if it failed to persist.
                        view.evaluateJavascript("alert('存档失败，尚未切换第二部。请重试。')", null);
                        return;
                    }
                    // Persist completion BEFORE changing active chapter, so a failed
                    // preference write cannot strand the next startup in part 1.
                    boolean durable = getPreferences(MODE_PRIVATE).edit()
                            .putBoolean("chapter1_finished", true).commit();
                    if (!durable) {
                        awaitingSaveForTransition = false;
                        ending.retryAfterSaveFailure();
                        view.evaluateJavascript("alert('章节状态写入失败，暂不切换。')", null);
                        return;
                    }
                    if (chapters.onEvent(1, DuologyStateMachine.Event.STORY_COMPLETE)) {
                        Log.i("DuobaoRuntime", "Chapter 1 save flushed, transitioning to chapter=2");
                        loadChapter(2);
                    }
                });
            }
        }, "DuobaoNative");
        FrameLayout root = new FrameLayout(this);
        root.addView(view, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        // Optional manual fallback: never claim it is automatic end detection.
        chapterOptions = new Button(this);
        chapterOptions.setText("章节");
        chapterOptions.setTextSize(12);
        chapterOptions.setAllCaps(false);
        chapterOptions.setOnClickListener(v -> {
            Log.i("DuobaoRuntime", "Chapter menu control tapped");
            showChapterMenu();
        });
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                (int) (60 * getResources().getDisplayMetrics().density),
                (int) (44 * getResources().getDisplayMetrics().density),
                Gravity.END | Gravity.TOP);
        // Keep chapter menu away from the Android status-bar gesture region,
        // especially on compact 320x640 emulator profiles (the old control
        // occupied y=0..44 and tapping its center hit the system status bar).
        lp.topMargin = (int) (6 * getResources().getDisplayMetrics().density);
        root.addView(chapterOptions, lp);
        setContentView(root);
        loadChapter(current);
    }
    /** Hide system status/navigation bars; a swipe temporarily reveals them. */
    private void applyImmersiveMode() {
        View decor = getWindow().getDecorView();
        // Also retain the legacy flags for Android 8–10.
        decor.setSystemUiVisibility(
              View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            | View.SYSTEM_UI_FLAG_FULLSCREEN
            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowInsetsController controller = decor.getWindowInsetsController();
            if (controller != null) {
                controller.setSystemBarsBehavior(
                    WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
                controller.hide(WindowInsets.Type.statusBars() |
                                WindowInsets.Type.navigationBars());
            }
        }
    }

    @Override public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) applyImmersiveMode();
    }

    private void showChapterMenu() {
        Log.i("DuobaoRuntime", "Opening chapter options dialog; chapter=" + chapters.chapter());
        if (chapters.chapter() == 2) {
            new AlertDialog.Builder(this).setTitle("已进入第二部")
                .setMessage("两部游戏的存档分别保存。按“保存进度”会写入第二部当前进度。")
                .setPositiveButton("保存进度", (dialog, which) -> requestSave())
                .setNegativeButton("关闭", null).show();
            return;
        }
        new AlertDialog.Builder(this).setTitle("夺宝奇缘双部曲")
            .setMessage("正常情况下应在第一部结局后自动衔接。若旧引擎自绘字幕无法被识别，"
                + "请在亲自看完第一部结局后，使用“手动进入第二部”备用功能。")
            .setPositiveButton("保存进度", (dialog, which) -> requestSave())
            .setNeutralButton("手动进入第二部", (dialog, which) -> confirmManualHandoff())
            .setNegativeButton("继续游戏", null).show();
    }
    private void requestSave() {
        if (view == null) return;
        view.evaluateJavascript("Boolean(window.duobaoFlush && window.duobaoFlush())", result ->
            Toast.makeText(this, "true".equals(result) ? "进度已保存" : "保存未成功", Toast.LENGTH_SHORT).show());
    }
    private void confirmManualHandoff() {
        Log.i("DuobaoRuntime", "Manual chapter transition confirmation requested");
        new AlertDialog.Builder(this).setTitle("仅通关后使用")
            .setMessage("此功能不是自动结局识别。确认你已经看完第一部结局，程序将先保存第一部进度，"
                + "再进入第二部；否则请取消。")
            .setPositiveButton("已通关，进入第二部", (dialog, which) -> {
                Log.i("DuobaoRuntime", "Manual chapter confirmation pressed");
                if (chapters.chapter() != 1 || awaitingSaveForTransition || view == null) return;
                awaitingSaveForTransition = true;
                view.evaluateJavascript("window.duobaoFlushAndSwitch && window.duobaoFlushAndSwitch()", null);
            })
            .setNegativeButton("取消", null).show();
    }
    /** Called only with live on-screen text or a live framebuffer OCR sample. */
    private void acceptDisplayedText(int chapter, String shown, String source) {
        if (chapter != 1 || currentPageChapter != 1 || chapters.chapter() != 1
                || shown == null || shown.isEmpty()) return;
        int before = ending.markersSeen();
        ending.onTextDisplayed(shown);
        int after = ending.markersSeen();
        if (after > before) Log.i("DuobaoEnding", "Ending marker progress " + after + "/5 via " + source);
        // OCR may repeat an identical frame many times; avoid making the
        // acknowledgement clock tick indefinitely for a frozen ending screen.
        if (after > before || "VMRP-dialog".equals(source))
            lastVisibleDialogueUptime = SystemClock.uptimeMillis();
    }

    private void loadChapter(int chapter) {
        if (endingFrameObserver != null) endingFrameObserver.stop();
        wasmReadyForActivePage = false;
        currentPageChapter = chapter;
        if (chapterOptions != null) chapterOptions.setText(chapter == 1 ? "章节 1" : "章节 2");
        ending.reset();
        awaitingSaveForTransition = false;
        // Same-origin local files only; no network permission is requested.
        String url = "https://appassets.androidplatform.net/assets/index.html?chapter=" + chapter
                + "&f=./games/part" + chapter + ".mrp";
        view.loadUrl(url);
    }
    @Override protected void onPause() {
        if (endingFrameObserver != null) endingFrameObserver.stop();
        super.onPause();
        if (view != null) view.evaluateJavascript("window.duobaoFlush && window.duobaoFlush()", null);
    }
    @Override protected void onResume() {
        super.onResume();
        if (wasmReadyForActivePage && currentPageChapter == 1 && endingFrameObserver != null)
            endingFrameObserver.start();
    }
    @Override public void onBackPressed() {
        if (view != null) view.evaluateJavascript("window.duobaoFlush && window.duobaoFlush()", null);
        super.onBackPressed();
    }
    @Override protected void onDestroy() {
        if (endingFrameObserver != null) { endingFrameObserver.close(); endingFrameObserver = null; }
        if (view != null) { view.removeJavascriptInterface("DuobaoNative"); view.destroy(); view = null; }
        super.onDestroy();
    }
}
