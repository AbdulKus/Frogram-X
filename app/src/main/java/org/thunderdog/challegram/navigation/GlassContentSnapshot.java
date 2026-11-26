package org.thunderdog.challegram.navigation;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Picture;
import android.graphics.RectF;
import android.os.Build;
import android.view.View;

import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.util.AlphaBlur;

/** Small foreground images prepared once, never a screenshot of the message list. */
final class GlassContentSnapshot {
  private Bitmap sharp, blurred;
  private HardwareSnapshot hardware;
  private Picture picture;
  private final AlphaBlur blur = new AlphaBlur();
  private int[] pixels = new int[0];
  private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
  private final RectF target = new RectF();
  private int width, height, padding;

  boolean capture (View view) { return capture(view, view.getHeight()); }

  boolean capture (View view, int visibleHeight) { return capture(view, visibleHeight, true); }

  boolean capture (View view, int visibleHeight, boolean useHardware) {
    org.thunderdog.challegram.util.PerformanceTrace.begin("Frogram.glass.foreground");
    try {
      return captureContents(view, visibleHeight, useHardware);
    } finally {
      org.thunderdog.challegram.util.PerformanceTrace.end();
    }
  }

  private boolean captureContents (View view, int visibleHeight, boolean useHardware) {
    width = view.getWidth();
    height = Math.min(view.getHeight(), visibleHeight);
    if (width <= 0 || height <= 0) return false;
    padding = Screen.dp(8f);
    int paddedWidth = width + padding * 2, paddedHeight = height + padding * 2;
    // Record once so the sharp foreground and the small halo share commands.
    // Headers can keep the sharp content on the GPU. Small composer controls
    // use textures: a separate GPU layer for every icon costs more to composite.
    picture = new Picture();
    Canvas recording = picture.beginRecording(paddedWidth, paddedHeight);
    try {
      recording.translate(padding, padding);
      recording.clipRect(0, 0, width, height);
      view.draw(recording);
    } finally {
      picture.endRecording();
    }
    sharp = null;
    if (useHardware && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && view.isHardwareAccelerated()) {
      if (hardware == null) hardware = new HardwareSnapshot();
      hardware.record(picture, paddedWidth, paddedHeight);
    } else {
      hardware = null;
      prepareSoftwareForeground();
    }
    int blurWidth = Math.max(1, Math.round(paddedWidth / (float) Screen.dp(3.5f)));
    int blurHeight = Math.max(1, Math.round(paddedHeight / (float) Screen.dp(3.5f)));
    if (blurred == null || blurred.getWidth() != blurWidth || blurred.getHeight() != blurHeight) {
      blurred = Bitmap.createBitmap(blurWidth, blurHeight, Bitmap.Config.ARGB_8888);
    } else {
      blurred.eraseColor(0);
    }
    Canvas halo = new Canvas(blurred);
    halo.scale(blurWidth / (float) paddedWidth, blurHeight / (float) paddedHeight);
    halo.drawPicture(picture);
    if (pixels.length < blurWidth * blurHeight) pixels = new int[blurWidth * blurHeight];
    blurred.getPixels(pixels, 0, blurWidth, 0, 0, blurWidth, blurHeight);
    blur.blur(pixels, blurWidth, blurHeight);
    blurred.setPixels(pixels, 0, blurWidth, 0, 0, blurWidth, blurHeight);
    return true;
  }

  void draw (Canvas canvas, float left, float top, float visibility) {
    if (picture == null || visibility <= 0f) return;
    visibility = Math.min(1f, visibility);
    target.set(left - padding, top - padding, left + width + padding, top + height + padding);
    // The halo grows as the foreground disappears; arrival reverses the same effect.
    paint.setAlpha(Math.round(255f * visibility * (1f - visibility)));
    canvas.drawBitmap(blurred, null, target, paint);
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && hardware != null && canvas.isHardwareAccelerated()) {
      int save = canvas.save();
      canvas.translate(left - padding, top - padding);
      hardware.draw(canvas, visibility * visibility);
      canvas.restoreToCount(save);
    } else {
      prepareSoftwareForeground();
      paint.setAlpha(Math.round(255f * visibility * visibility));
      canvas.drawBitmap(sharp, null, target, paint);
    }
  }

  private void prepareSoftwareForeground () {
    if (sharp != null) return;
    int paddedWidth = width + padding * 2, paddedHeight = height + padding * 2;
    float scale = Math.min(1f, (float) Math.sqrt(500000d / ((double) paddedWidth * paddedHeight)));
    sharp = Bitmap.createBitmap(Math.max(1, Math.round(paddedWidth * scale)),
      Math.max(1, Math.round(paddedHeight * scale)), Bitmap.Config.ARGB_8888);
    Canvas canvas = new Canvas(sharp);
    canvas.scale(scale, scale);
    canvas.drawPicture(picture);
  }

  @android.annotation.TargetApi(Build.VERSION_CODES.Q)
  private static final class HardwareSnapshot {
    private final android.graphics.RenderNode node = new android.graphics.RenderNode("Frogram navigation foreground");

    void record (Picture picture, int width, int height) {
      node.setPosition(0, 0, width, height);
      node.setUseCompositingLayer(true, null);
      Canvas canvas = node.beginRecording(width, height);
      try {
        canvas.drawPicture(picture);
      } finally {
        node.endRecording();
      }
    }

    void draw (Canvas canvas, float alpha) {
      node.setAlpha(alpha);
      canvas.drawRenderNode(node);
    }
  }
}
