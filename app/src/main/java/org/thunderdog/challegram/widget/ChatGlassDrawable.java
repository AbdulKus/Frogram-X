package org.thunderdog.challegram.widget;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.thunderdog.challegram.U;
import org.thunderdog.challegram.component.chat.WallpaperView;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Screen;

/** Frosted chat wallpaper, never a capture of message text or another window. */
public final class ChatGlassDrawable extends Drawable {
  private final WallpaperView wallpaper;
  private final View host;
  private final int colorId;
  private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
  private final RectF panel = new RectF();
  private final Path clip = new Path();
  private final int[] hostPosition = new int[2];
  private final int[] wallpaperPosition = new int[2];
  private Bitmap sample;
  private Canvas sampleCanvas;
  private Shader sheen;
  private int alpha = 255;

  public ChatGlassDrawable (WallpaperView wallpaper, View host, int colorId) {
    this.wallpaper = wallpaper;
    this.host = host;
    this.colorId = colorId;
  }

  @Override protected void onBoundsChange (Rect bounds) {
    panel.set(bounds);
    panel.inset(Screen.dp(.5f), Screen.dp(.5f));
    clip.reset();
    float radius = Math.min(Screen.dp(26f), panel.height() / 2f);
    clip.addRoundRect(panel, radius, radius, Path.Direction.CW);
    sheen = new LinearGradient(0, panel.top, 0, panel.bottom,
      new int[] {0x28ffffff, 0x06ffffff, 0x00000000}, null, Shader.TileMode.CLAMP);
  }

  @Override public void draw (@NonNull Canvas canvas) {
    if (panel.isEmpty()) return;
    float radius = Math.min(Screen.dp(26f), panel.height() / 2f);
    int save = canvas.save();
    canvas.clipPath(clip);
    paint.setShader(null);
    paint.setColor(Theme.getColor(colorId));
    paint.setAlpha(alpha);
    canvas.drawRect(panel, paint);

    if (wallpaper.getWidth() > 0 && wallpaper.getHeight() > 0) {
      // Two small reusable surfaces at most, about 1/64 of the panel's pixels.
      // The existing native blur works on every supported Android version.
      int width = Math.max(8, (int) Math.ceil(panel.width() / 8f));
      int height = Math.max(8, (int) Math.ceil(panel.height() / 8f));
      if (sample == null || sample.getWidth() != width || sample.getHeight() != height) {
        release();
        sample = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        sampleCanvas = new Canvas(sample);
      }
      sample.eraseColor(Theme.getColor(colorId));
      host.getLocationInWindow(hostPosition);
      wallpaper.getLocationInWindow(wallpaperPosition);
      float x = hostPosition[0] - wallpaperPosition[0] + panel.left;
      float y = Math.max(0, hostPosition[1] - wallpaperPosition[1] + panel.top);
      int sampleSave = sampleCanvas.save();
      sampleCanvas.scale(width / panel.width(), height / panel.height());
      sampleCanvas.translate(-x, -y);
      wallpaper.drawForGlass(sampleCanvas);
      sampleCanvas.restoreToCount(sampleSave);
      U.blurBitmap(sample, 3, 1);
      paint.setColor(Color.WHITE);
      paint.setAlpha(alpha);
      canvas.drawBitmap(sample, null, panel, paint);
    }
    // Keep theme foreground colours legible even over a high-contrast photo.
    paint.setColor(Theme.getColor(colorId));
    paint.setAlpha(Math.round(alpha * (Theme.isDark() ? .86f : .82f)));
    canvas.drawRect(panel, paint);
    paint.setShader(sheen);
    paint.setAlpha(alpha);
    canvas.drawRect(panel, paint);
    paint.setShader(null);
    canvas.restoreToCount(save);

    paint.setStyle(Paint.Style.STROKE);
    paint.setStrokeWidth(Screen.dp(1f));
    paint.setColor(Theme.isDark() ? 0x38ffffff : 0x90ffffff);
    paint.setAlpha(Math.round(Color.alpha(paint.getColor()) * alpha / 255f));
    canvas.drawRoundRect(panel, radius, radius, paint);
    paint.setStyle(Paint.Style.FILL);
  }

  public void release () {
    // Do not recycle a bitmap that may still be referenced by a display list.
    sampleCanvas = null;
    sample = null;
  }

  @Override public void setAlpha (int alpha) { this.alpha = alpha; invalidateSelf(); }
  @Override public void setColorFilter (@Nullable ColorFilter filter) { }
  @Override public int getOpacity () { return PixelFormat.TRANSLUCENT; }
}
