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
import android.view.ViewTreeObserver;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.thunderdog.challegram.util.GlassFrameCache;
import org.thunderdog.challegram.util.GlassSampling;
import me.vkryl.core.ColorUtils;
import org.thunderdog.challegram.component.chat.WallpaperView;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.v.MessagesRecyclerView;

/** A small in-memory backdrop of this chat, excluding the overlaid controls. */
public final class ChatGlassDrawable extends Drawable implements View.OnAttachStateChangeListener {
  private final WallpaperView wallpaper;
  private View sourceView;
  private VideoLayer backdrop;
  private final View host;
  private final int colorId;
  private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
  private final RectF panel = new RectF();
  private final RectF sampleBounds = new RectF();
  private final Path clip = new Path();
  private final int[] hostPosition = new int[2];
  private final int[] wallpaperPosition = new int[2];
  private MessagesRecyclerView messages;
  public interface VideoLayer { void draw (Canvas canvas); }
  private VideoLayer videoLayer;

  public void setVideoLayer (VideoLayer layer) { videoLayer = layer; }

  public void setBackdrop (View source, VideoLayer drawer) {
    sourceView = source;
    backdrop = drawer;
    messages = null;
    invalidateBackdrop();
  }

  public void refresh () {
    if (released) return;
    if (host.getWindowToken() != null && (observer == null || !observer.isAlive() || observer != host.getViewTreeObserver())) {
      onViewAttachedToWindow(host);
    }
    backdropDirty = true;
    host.invalidate();
  }
  private boolean backdropDirty = true;
  private boolean geometryDirty = true;
  private boolean released;
  private int lastX = Integer.MIN_VALUE, lastY, lastColor;
  private int lastMessagesX, lastMessagesY;
  private boolean enabled = true;
  private ViewTreeObserver observer;
  private final ViewTreeObserver.OnPreDrawListener prepareFrame;
  private final int[] messagesPosition = new int[2];
  private final GlassFrameCache frames = new GlassFrameCache();
  private float topInset;

  public void setTopInset (float inset) {
    if (topInset != inset) {
      topInset = inset;
      onBoundsChange(getBounds());
      invalidateSelf();
      host.invalidate();
    }
  }
  private int[] pixels = new int[0];
  private int[] capturePixels = new int[0];
  private Bitmap sample, capture;
  private boolean customShape;

  public void setShape (Path path) {
    clip.set(path);
    customShape = true;
    host.invalidate();
  }

  public static int surfaceColor (int colorId) {
    int color = Theme.getColor(colorId);
    return Theme.isDark() ? color : ColorUtils.fromToArgb(color, Theme.headerColor(), .18f);
  }

  private Canvas sampleCanvas;
  private Shader sheen;
  private int alpha = 255;

  public ChatGlassDrawable (WallpaperView wallpaper, View host, int colorId) {
    this(wallpaper, host, colorId, null);
  }

  /** The source paints content only, without drawing live Android view display lists. */
  public ChatGlassDrawable (View sourceView, View host, int colorId, @Nullable VideoLayer backdrop) {
    this.sourceView = sourceView;
    this.wallpaper = sourceView instanceof WallpaperView ? (WallpaperView) sourceView : null;
    this.backdrop = backdrop;
    this.host = host;
    this.colorId = colorId;
    prepareFrame = () -> {
      if (!released && enabled && host.isShown() && hasVisibleAlpha() && prepareBackdrop()) host.invalidate();
      return true;
    };
    host.addOnAttachStateChangeListener(this);
    if (host.getWindowToken() != null) onViewAttachedToWindow(host);
  }

  private boolean hasVisibleAlpha () {
    View view = host;
    while (true) {
      if (view.getAlpha() <= 0f) return false;
      if (!(view.getParent() instanceof View)) return true;
      view = (View) view.getParent();
    }
  }

  @Override public void onViewAttachedToWindow (View view) {
    if (released) return;
    if (observer != null && observer.isAlive()) observer.removeOnPreDrawListener(prepareFrame);
    sample = null;
    capture = null;
    sampleCanvas = null;
    frames.clear();
    observer = host.getViewTreeObserver();
    observer.addOnPreDrawListener(prepareFrame);
    invalidateBackdrop();
  }

  @Override public void onViewDetachedFromWindow (View view) {
    if (observer != null && observer.isAlive()) observer.removeOnPreDrawListener(prepareFrame);
    observer = null;
  }

  public void setEnabled (boolean enabled) {
    this.enabled = enabled;
    if (enabled) invalidateBackdrop();
  }

  public void setMessages (MessagesRecyclerView messages) {
    this.messages = messages;
  }

  @Override protected void onBoundsChange (Rect bounds) {
    backdropDirty = true;
    geometryDirty = true;
    customShape = false;
    panel.set(bounds);
    panel.top = Math.min(panel.bottom, panel.top + topInset);
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
    // Normally prepared by pre-draw. The fallback covers the very first bounds assignment.
    // Source invalidation during this draw belongs to the next pre-draw; never
    // replay the message list twice in the same frame.
    if (sample == null || geometryDirty) prepareBackdrop();
    if (sample != null) {
      paint.setColor(Color.WHITE);
      paint.setAlpha(alpha);
      canvas.drawBitmap(sample, null, sampleBounds, paint);
    }
    // Keep theme foreground colours legible even over a high-contrast photo.
    paint.setColor(surfaceColor(colorId));
    paint.setAlpha(Math.round(alpha * (sample != null ? (Theme.isDark() ? .54f : .58f) : .82f)));
    canvas.drawRect(panel, paint);
    paint.setShader(sheen);
    paint.setAlpha(alpha);
    canvas.drawRect(panel, paint);
    paint.setShader(null);
    canvas.restoreToCount(save);

    paint.setStyle(Paint.Style.STROKE);
    paint.setStrokeWidth(Screen.dp(1f));
    paint.setColor(Theme.isDark() ? 0x38ffffff : ColorUtils.alphaColor(.28f, ColorUtils.fromToArgb(Theme.headerColor(), Theme.textAccentColor(), .25f)));
    paint.setAlpha(Math.round(Color.alpha(paint.getColor()) * alpha / 255f));
    if (customShape) canvas.drawPath(clip, paint); else canvas.drawRoundRect(panel, radius, radius, paint);
    paint.setStyle(Paint.Style.FILL);
  }

  public void invalidateBackdrop () {
    if (!released && enabled) {
      backdropDirty = true;
      host.invalidate();
    }
  }

  private boolean prepareBackdrop () {
    if (released || !enabled || panel.isEmpty() || (sourceView.getWidth() == 0 && (messages == null || messages.getWidth() == 0))) return false;
    host.getLocationInWindow(hostPosition);
    sourceView.getLocationInWindow(wallpaperPosition);
    int relativeX = hostPosition[0] - wallpaperPosition[0];
    int relativeY = hostPosition[1] - wallpaperPosition[1];
    int color = Theme.getColor(colorId);
    int messagesX = 0, messagesY = 0;
    if (messages != null) {
      messages.getLocationInWindow(messagesPosition);
      messagesX = messagesPosition[0] - wallpaperPosition[0];
      messagesY = messagesPosition[1] - wallpaperPosition[1];
    }
    if (sample != null && !backdropDirty && relativeX == lastX && relativeY == lastY && lastColor == color &&
        messagesX == lastMessagesX && messagesY == lastMessagesY) return false;
    backdropDirty = false;
    geometryDirty = false;
    lastX = relativeX;
    lastY = relativeY;
    lastColor = color;
    lastMessagesX = messagesX;
    lastMessagesY = messagesY;
    // Include the full blur kernel outside the visible edge. Otherwise a glyph
    // entering the island is clamped across the kernel and abruptly turns it dark.
    // Capture the reserved composer bounds, independent of its animated reply inset.
    Rect bounds = getBounds();
    float scale = Math.max(Screen.dp(7f), Math.max(bounds.width(), bounds.height()) / 84f);
    int innerWidth = Math.max(1, Math.min(84, (int) Math.ceil(bounds.width() / scale)));
    int innerHeight = Math.max(1, Math.min(84, (int) Math.ceil(bounds.height() / scale)));
    int width = innerWidth + 12, height = innerHeight + 12;
    sampleBounds.set(bounds);
    sampleBounds.inset(-6f * bounds.width() / innerWidth, -6f * bounds.height() / innerHeight);
    if (sample == null || sample.getWidth() != width || sample.getHeight() != height) {
      sample = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
      capture = Bitmap.createBitmap(width * 2, height * 2, Bitmap.Config.ARGB_8888);
      sampleCanvas = new Canvas(capture);
      frames.clear();
    }
    capture.eraseColor(Theme.getColor(colorId));

    float x = relativeX + sampleBounds.left;
    float y = relativeY + sampleBounds.top;
    int sampleSave = sampleCanvas.save();
    sampleCanvas.scale(capture.getWidth() / sampleBounds.width(), capture.getHeight() / sampleBounds.height());
    sampleCanvas.translate(-x, -y);
    if (backdrop != null) backdrop.draw(sampleCanvas);
    else if (wallpaper != null) wallpaper.drawForGlass(sampleCanvas);
    if (messages != null && messages.getWidth() > 0) {
      sampleCanvas.translate(messagesX, messagesY);
      messages.drawForGlass(sampleCanvas);
      if (videoLayer != null) videoLayer.draw(sampleCanvas);
    }
    sampleCanvas.restoreToCount(sampleSave);
    if (pixels.length < width * height) pixels = new int[width * height];
    if (capturePixels.length < width * height * 4) capturePixels = new int[width * height * 4];
    capture.getPixels(capturePixels, 0, width * 2, 0, 0, width * 2, height * 2);
    GlassSampling.downsample2x(capturePixels, pixels, width, height);
    if (!frames.update(pixels, width, height, false)) return false;
    sample.setPixels(frames.pixels(), 0, width, 0, 0, width, height);
    return true;
  }

  public void release () {
    released = true;
    onViewDetachedFromWindow(host);
    host.removeOnAttachStateChangeListener(this);
    // Do not recycle a bitmap that may still be referenced by a display list.
    sampleCanvas = null;
    sample = null;
    capture = null;
  }

  @Override public void setAlpha (int alpha) { this.alpha = alpha; invalidateSelf(); }
  @Override public void setColorFilter (@Nullable ColorFilter filter) { }
  @Override public int getOpacity () { return PixelFormat.TRANSLUCENT; }
}
