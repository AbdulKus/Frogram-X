package org.thunderdog.challegram.navigation;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.view.View;

import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.util.AlphaBlur;

/** Small foreground images prepared once, never a screenshot of the message list. */
final class GlassContentSnapshot {
  private Bitmap sharp, blurred;
  private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
  private final RectF target = new RectF();
  private int width, height, padding;

  boolean capture (View view) { return capture(view, view.getHeight()); }

  boolean capture (View view, int visibleHeight) {
    width = view.getWidth();
    height = Math.min(view.getHeight(), visibleHeight);
    if (width <= 0 || height <= 0) return false;
    padding = Screen.dp(8f);
    int paddedWidth = width + padding * 2, paddedHeight = height + padding * 2;
    float scale = Math.min(1f, (float) Math.sqrt(500000d / ((double) paddedWidth * paddedHeight)));
    int bitmapWidth = Math.max(1, Math.round(paddedWidth * scale)), bitmapHeight = Math.max(1, Math.round(paddedHeight * scale));
    sharp = Bitmap.createBitmap(bitmapWidth, bitmapHeight, Bitmap.Config.ARGB_8888);
    Canvas capture = new Canvas(sharp);
    capture.scale(scale, scale);
    capture.translate(padding, padding);
    capture.clipRect(0, 0, width, height);
    // Parent clip bounds are applied when presenting the image. A reply row can
    // grow from a one-pixel clip without needing a new bitmap for every frame.
    view.draw(capture);
    int blurWidth = Math.max(1, Math.round(paddedWidth / (float) Screen.dp(2f)));
    int blurHeight = Math.max(1, Math.round(paddedHeight / (float) Screen.dp(2f)));
    blurred = Bitmap.createBitmap(blurWidth, blurHeight, Bitmap.Config.ARGB_8888);
    paint.setAlpha(255);
    new Canvas(blurred).drawBitmap(sharp, null, new Rect(0, 0, blurWidth, blurHeight), paint);
    int[] pixels = new int[blurWidth * blurHeight];
    blurred.getPixels(pixels, 0, blurWidth, 0, 0, blurWidth, blurHeight);
    new AlphaBlur().blur(pixels, blurWidth, blurHeight);
    blurred.setPixels(pixels, 0, blurWidth, 0, 0, blurWidth, blurHeight);
    return true;
  }

  void draw (Canvas canvas, float left, float top, float visibility) {
    if (sharp == null || visibility <= 0f) return;
    visibility = Math.min(1f, visibility);
    target.set(left - padding, top - padding, left + width + padding, top + height + padding);
    // The halo grows as the foreground disappears; arrival reverses the same effect.
    paint.setAlpha(Math.round(255f * visibility * (1f - visibility)));
    canvas.drawBitmap(blurred, null, target, paint);
    paint.setAlpha(Math.round(255f * visibility * visibility));
    canvas.drawBitmap(sharp, null, target, paint);
  }
}
