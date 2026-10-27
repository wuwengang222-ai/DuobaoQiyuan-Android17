package com.duobao.duology;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Base64;
import android.graphics.Canvas;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.webkit.WebView;

import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions;

/**
 * An experimental, on-device, offline Chinese OCR path for text drawn straight
 * into the MRP framebuffer rather than delivered through VMRP's dialog API.
 * No arbitrary file read is ever interpreted as evidence of a displayed ending.
 *
 * Limitations: recognition of the original tiny bitmap font is UNVERIFIED;
 * this does not prove end-to-end automatic switching works.
 */
public final class EndingFrameObserver {
    public interface TextSink { void onTextSeen(String visibleOcrText); }

    private static final long INTERVAL_MS = 1450L;
    private static final int MAX_LONG_EDGE = 1600;
    private final WebView webView;
    private final TextSink sink;
    private final TextRecognizer recognizer = TextRecognition.getClient(
            new ChineseTextRecognizerOptions.Builder().build());
    private final Handler ui = new Handler(Looper.getMainLooper());
    private boolean enabled = false;
    private boolean busy = false;
    private int epoch = 0;
    private long lastDirectFrameUptime = 0L;

    public EndingFrameObserver(WebView view, TextSink sink) {
        webView = view;
        this.sink = sink;
    }

    public void start() {
        if (enabled) return;
        enabled = true;
        lastDirectFrameUptime = 0L;
        epoch++;
        ui.postDelayed(sampler, INTERVAL_MS);
    }

    public void stop() {
        enabled = false;
        epoch++;
        ui.removeCallbacks(sampler);
    }

    public void close() { stop(); recognizer.close(); }

    /** Receives pixels copied from the VMRP Canvas itself, no network transmission. */
    public void submitCanvasPng(String dataUri) {
        if (!enabled || busy || dataUri == null || dataUri.length() > 3_000_000
                || !dataUri.startsWith("data:image/png;base64,")) return;
        Bitmap bitmap = null;
        try {
            byte[] decoded = Base64.decode(dataUri.substring(22), Base64.DEFAULT);
            if (decoded.length == 0 || decoded.length > 2_000_000) return;
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(decoded, 0, decoded.length, bounds);
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0
                    || bounds.outWidth > 1600 || bounds.outHeight > 1600) return;
            bitmap = BitmapFactory.decodeByteArray(decoded, 0, decoded.length);
            if (bitmap == null) return;
            lastDirectFrameUptime = SystemClock.uptimeMillis();
            processImage(bitmap);
        } catch (IllegalArgumentException | OutOfMemoryError ex) {
            if (bitmap != null) bitmap.recycle();
            Log.w("DuobaoOCR", "Invalid game canvas PNG", ex);
        }
    }

    private void processImage(Bitmap image) {
        if (busy) { image.recycle(); return; }
        busy = true;
        final int capturedEpoch = epoch;
        try {
            recognizer.process(InputImage.fromBitmap(image, 0))
                .addOnSuccessListener(result -> {
                    if (enabled && capturedEpoch == epoch) {
                        String shown = result.getText();
                        if (shown != null && !shown.isEmpty()) sink.onTextSeen(shown);
                    }
                })
                .addOnFailureListener(ex -> Log.w("DuobaoOCR", "OCR unavailable", ex))
                .addOnCompleteListener(task -> { image.recycle(); busy = false; });
        } catch (RuntimeException e) {
            busy = false;
            image.recycle();
            Log.w("DuobaoOCR", "OCR failed to start", e);
        }
    }

    private final Runnable sampler = new Runnable() {
        @Override public void run() {
            if (!enabled) return;
            if (!busy && SystemClock.uptimeMillis() - lastDirectFrameUptime > 6000
                    && webView.getWidth() > 0 && webView.getHeight() > 0 &&
                    webView.isShown()) {
                Bitmap image;
                try {
                    final int w = webView.getWidth(), h = webView.getHeight();
                    final double scale = Math.min(1.0, (double) MAX_LONG_EDGE / Math.max(w, h));
                    final int width = Math.max(1, (int) (w * scale));
                    final int height = Math.max(1, (int) (h * scale));
                    image = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
                    Canvas canvas = new Canvas(image);
                    canvas.scale((float) scale, (float) scale);
                    webView.draw(canvas);
                } catch (RuntimeException e) {
                    Log.w("DuobaoOCR", "Could not sample framebuffer", e);
                    ui.postDelayed(this, INTERVAL_MS);
                    return;
                }
                processImage(image);
            }
            ui.postDelayed(this, INTERVAL_MS);
        }
    };
}
