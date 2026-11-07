package org.thunderdog.challegram.widget;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.PixelCopy;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.Window;

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
  private boolean navigationFrozen;
  private final RectF navigationSampleBounds = new RectF();
  private final Path navigationClip = new Path();
  private final Matrix navigationSheenMatrix = new Matrix();
  private final Shader navigationSheen = new LinearGradient(0, 0, 0, 1,
    new int[] {0x28ffffff, 0x06ffffff, 0x00000000}, null, Shader.TileMode.CLAMP);
  private ViewTreeObserver observer;
  private final ViewTreeObserver.OnPreDrawListener prepareFrame;
  private final int[] messagesPosition = new int[2];
  private final int[] changedPosition = new int[2];
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
  private int backdropDimColor;
  private boolean customShape;

  public void setShape (Path path) {
    clip.set(path);
    customShape = true;
    host.invalidate();
  }

  public void setBackdropDimAmount (float amount) {
    backdropDimColor = ColorUtils.alphaColor(amount, Color.BLACK);
    invalidateBackdrop();
  }

  public void setWindowBackdrop (Window window) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      backdropWindow = window;
      invalidateBackdrop();
    }
  }

  public static int surfaceColor (int colorId) {
    int color = Theme.getColor(colorId);
    return Theme.isDark() ? color : ColorUtils.fromToArgb(color, Theme.headerColor(), .18f);
  }

  private Window backdropWindow;
  private final Rect requestedWindowBounds = new Rect();
  private boolean windowCapturePending;
  private boolean windowCaptureFailed;
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
      if (!released && enabled && !navigationFrozen && host.isShown() && hasVisibleAlpha() && prepareBackdrop()) host.invalidate();
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

  public void freezeForNavigation (boolean frozen) {
    if (navigationFrozen == frozen) return;
    // Detached captures support offscreen preparation. Attached hidden controls
    // must not replay the list just because another screen starts a transition.
    if (frozen && (host.getWindowToken() == null || (host.isShown() && hasVisibleAlpha()))) prepareBackdrop();
    navigationFrozen = frozen;
    if (!frozen) invalidateBackdrop();
  }

  public void getNavigationBounds (RectF bounds) {
    bounds.set(panel);
  }

  public void refreshNavigationBackdrop () {
    // Page translation cancels in the source coordinates. Only changed contents
    // or geometry cause a new small capture; sliding reuses the existing texture.
    prepareBackdrop();
  }

  public void drawNavigationBackdrop (Canvas canvas, RectF bounds, float pageX, int pageWidth, float opacity) {
    if (bounds.isEmpty() || opacity <= 0f) return;
    int save = canvas.save();
    canvas.clipRect(pageX, bounds.top, pageX + pageWidth, bounds.bottom);
    paint.setShader(null);
    paint.setColor(Color.WHITE);
    paint.setAlpha(Math.round(opacity * 255f));
    if (sample != null && !panel.isEmpty()) {
      float scaleX = bounds.width() / panel.width(), scaleY = bounds.height() / panel.height();
      navigationSampleBounds.set(bounds.left + pageX + (sampleBounds.left - panel.left) * scaleX,
        bounds.top + (sampleBounds.top - panel.top) * scaleY,
        bounds.left + pageX + (sampleBounds.right - panel.left) * scaleX,
        bounds.top + (sampleBounds.bottom - panel.top) * scaleY);
      canvas.drawBitmap(sample, null, navigationSampleBounds, paint);
    } else {
      paint.setColor(surfaceColor(colorId));
      paint.setAlpha(Math.round(opacity * 255f));
      canvas.drawRect(bounds, paint);
    }
    canvas.restoreToCount(save);
  }

  public void drawNavigationChrome (Canvas canvas, RectF bounds, ChatGlassDrawable destination, float factor) {
    int color = ColorUtils.fromToArgb(surfaceColor(colorId), surfaceColor(destination.colorId), factor);
    drawNavigationChrome(canvas, bounds, 1f, color, sample != null || destination.sample != null);
  }

  /** Render a cached backdrop into the shared shell without relayout or recapture. */
  public void drawNavigationSurface (Canvas canvas, RectF bounds, float opacity) {
    if (bounds.isEmpty() || opacity <= 0f) return;
    int save = canvas.save();
    float radius = Math.min(Screen.dp(26f), bounds.height() / 2f);
    navigationClip.reset();
    navigationClip.addRoundRect(bounds, radius, radius, Path.Direction.CW);
    canvas.clipPath(navigationClip);
    paint.setShader(null);
    if (sample != null && !panel.isEmpty()) {
      float scaleX = bounds.width() / panel.width(), scaleY = bounds.height() / panel.height();
      navigationSampleBounds.set(bounds.left + (sampleBounds.left - panel.left) * scaleX,
        bounds.top + (sampleBounds.top - panel.top) * scaleY,
        bounds.left + (sampleBounds.right - panel.left) * scaleX,
        bounds.top + (sampleBounds.bottom - panel.top) * scaleY);
      paint.setColor(Color.WHITE);
      paint.setAlpha(Math.round(opacity * 255f));
      canvas.drawBitmap(sample, null, navigationSampleBounds, paint);
    }
    canvas.restoreToCount(save);
    drawNavigationChrome(canvas, bounds, opacity, surfaceColor(colorId), sample != null);
  }

  private void drawNavigationChrome (Canvas canvas, RectF bounds, float opacity, int color, boolean sampled) {
    int save = canvas.save();
    float radius = Math.min(Screen.dp(26f), bounds.height() / 2f);
    navigationClip.reset();
    navigationClip.addRoundRect(bounds, radius, radius, Path.Direction.CW);
    canvas.clipPath(navigationClip);
    paint.setShader(null);
    paint.setColor(color);
    paint.setAlpha(Math.round(opacity * 255f * (sampled ? (Theme.isDark() ? .54f : .58f) : .82f)));
    canvas.drawRect(bounds, paint);
    navigationSheenMatrix.setScale(1f, bounds.height());
    navigationSheenMatrix.postTranslate(0, bounds.top);
    navigationSheen.setLocalMatrix(navigationSheenMatrix);
    paint.setShader(navigationSheen);
    paint.setAlpha(Math.round(opacity * 255f));
    canvas.drawRect(bounds, paint);
    paint.setShader(null);
    canvas.restoreToCount(save);
    paint.setStyle(Paint.Style.STROKE);
    paint.setStrokeWidth(Screen.dp(1f));
    paint.setColor(Theme.isDark() ? 0x38ffffff : ColorUtils.alphaColor(.28f, ColorUtils.fromToArgb(Theme.headerColor(), Theme.textAccentColor(), .25f)));
    paint.setAlpha(Math.round(Color.alpha(paint.getColor()) * opacity));
    canvas.drawRoundRect(bounds, radius, radius, paint);
    paint.setStyle(Paint.Style.FILL);
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
    if (sample == null || (!navigationFrozen && geometryDirty)) prepareBackdrop();
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
    if (host.isPressed()) {
      paint.setColor(Theme.textAccentColor());
      paint.setAlpha(Math.round(alpha * .12f));
      canvas.drawRect(panel, paint);
    }
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

  /** Ignore animated rows outside the captured area, including the blur overscan. */
  public void invalidateBackdrop (@Nullable View changed) {
    if (released || !enabled) return;
    if (changed == null || sample == null || geometryDirty || changed.getWindowToken() == null ||
        !changed.getMatrix().isIdentity()) {
      // Transformed rows can expose their previous position as well as the new
      // one. Layout/scroll changes also use the full invalidation path.
      invalidateBackdrop();
      return;
    }
    if (host.getWindowToken() == changed.getWindowToken()) {
      host.getLocationInWindow(hostPosition);
      changed.getLocationInWindow(changedPosition);
    } else {
      host.getLocationOnScreen(hostPosition);
      changed.getLocationOnScreen(changedPosition);
    }
    int left = changedPosition[0] - hostPosition[0], top = changedPosition[1] - hostPosition[1];
    if (left < sampleBounds.right && left + changed.getWidth() > sampleBounds.left &&
        top < sampleBounds.bottom && top + changed.getHeight() > sampleBounds.top) invalidateBackdrop();
  }

  /** Content-only changes (video frames) already have their transformed window bounds. */
  public void invalidateBackdropRegion (RectF region) {
    if (released || !enabled || region.isEmpty()) return;
    if (sample == null || geometryDirty) {
      invalidateBackdrop();
      return;
    }
    host.getLocationInWindow(hostPosition);
    if (host.getWindowToken() != sourceView.getWindowToken()) {
      host.getLocationOnScreen(hostPosition);
      sourceView.getLocationOnScreen(wallpaperPosition);
      sourceView.getLocationInWindow(changedPosition);
      hostPosition[0] -= wallpaperPosition[0] - changedPosition[0];
      hostPosition[1] -= wallpaperPosition[1] - changedPosition[1];
    }
    if (region.left < hostPosition[0] + sampleBounds.right && region.right > hostPosition[0] + sampleBounds.left &&
        region.top < hostPosition[1] + sampleBounds.bottom && region.bottom > hostPosition[1] + sampleBounds.top) invalidateBackdrop();
  }

  private boolean prepareBackdrop () {
    org.thunderdog.challegram.util.PerformanceTrace.begin("Frogram.glass.backdrop");
    try {
      return prepareBackdropContents();
    } finally {
      org.thunderdog.challegram.util.PerformanceTrace.end();
    }
  }

  private boolean prepareBackdropContents () {
    if (backdropWindow != null && !windowCaptureFailed && !released && enabled && !panel.isEmpty()) {
      prepareWindowBackdrop();
      return false;
    }
    if (released || !enabled || panel.isEmpty() || (sourceView.getWidth() == 0 && (messages == null || messages.getWidth() == 0))) return false;
    boolean sameWindow = host.getWindowToken() == sourceView.getWindowToken();
    if (sameWindow) {
      host.getLocationInWindow(hostPosition);
      sourceView.getLocationInWindow(wallpaperPosition);
    } else {
      host.getLocationOnScreen(hostPosition);
      sourceView.getLocationOnScreen(wallpaperPosition);
    }
    int relativeX = hostPosition[0] - wallpaperPosition[0];
    int relativeY = hostPosition[1] - wallpaperPosition[1];
    int color = Theme.getColor(colorId);
    int messagesX = 0, messagesY = 0;
    if (messages != null) {
      if (sameWindow) messages.getLocationInWindow(messagesPosition);
      else messages.getLocationOnScreen(messagesPosition);
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
    if (backdropDimColor != 0) sampleCanvas.drawColor(backdropDimColor);
    return blurCapture(capture, width, height);
  }

  // The dialog is its own window. Sample the exact rectangle behind it in the
  // activity's surface, including overlays and any transformed message views.
  private void prepareWindowBackdrop () {
    if (windowCapturePending || host.getWindowToken() == null) return;
    View decor = backdropWindow.getDecorView();
    if (decor.getWindowToken() == null || decor.getWidth() == 0 || decor.getHeight() == 0) return;
    decor.getLocationOnScreen(wallpaperPosition);
    host.getLocationOnScreen(hostPosition);
    int x = hostPosition[0] - wallpaperPosition[0], y = hostPosition[1] - wallpaperPosition[1];
    Rect region = new Rect(getBounds());
    region.inset(-Screen.dp(32), -Screen.dp(32));
    region.offset(x, y);
    if (!region.intersect(0, 0, decor.getWidth(), decor.getHeight())) return;
    Rect local = new Rect(region);
    local.offset(-x, -y);
    // Position can change as the keyboard or a dialog's content changes size.
    if (sample != null && region.equals(requestedWindowBounds) &&
        sampleBounds.left == local.left && sampleBounds.top == local.top &&
        sampleBounds.right == local.right && sampleBounds.bottom == local.bottom) return;
    requestedWindowBounds.set(region);
    windowCapturePending = true;
    int step = Math.max(Screen.dp(6), Math.max(region.width(), region.height()) / 92);
    int width = Math.max(1, (region.width() + step - 1) / step);
    int height = Math.max(1, (region.height() + step - 1) / step);
    Bitmap bitmap = Bitmap.createBitmap(width * 2, height * 2, Bitmap.Config.ARGB_8888);
    try {
      PixelCopy.request(backdropWindow, region, bitmap, result -> {
        windowCapturePending = false;
        if (released) {
          bitmap.recycle();
          return;
        }
        if (result == PixelCopy.SUCCESS) {
          new Canvas(bitmap).drawColor(backdropDimColor);
          sampleBounds.set(local);
          if (sample == null || sample.getWidth() != width || sample.getHeight() != height) {
            sample = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            frames.clear();
          }
          blurCapture(bitmap, width, height);
          geometryDirty = false;
        } else {
          windowCaptureFailed = true;
        }
        bitmap.recycle();
        invalidateBackdrop();
      }, new Handler(Looper.getMainLooper()));
    } catch (IllegalArgumentException | IllegalStateException e) {
      windowCapturePending = false;
      windowCaptureFailed = true;
      bitmap.recycle();
    }
  }

  private boolean blurCapture (Bitmap bitmap, int width, int height) {
    if (pixels.length < width * height) pixels = new int[width * height];
    if (capturePixels.length < width * height * 4) capturePixels = new int[width * height * 4];
    bitmap.getPixels(capturePixels, 0, width * 2, 0, 0, width * 2, height * 2);
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
  @Override public boolean isStateful () { return true; }
  @Override protected boolean onStateChange (int[] state) { invalidateSelf(); return true; }
  @Override public void setColorFilter (@Nullable ColorFilter filter) { }
  @Override public int getOpacity () { return PixelFormat.TRANSLUCENT; }
}
