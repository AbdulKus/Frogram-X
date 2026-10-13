package org.thunderdog.challegram.navigation;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewTreeObserver;

import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.util.GlassNavigationMotion;
import org.thunderdog.challegram.widget.ChatGlassDrawable;

import java.util.ArrayList;
import java.util.function.IntSupplier;

import me.vkryl.core.ColorUtils;

/** Stationary navigation chrome; the actual pages keep their usual slide animation. */
public final class GlassNavigationTransition extends View implements ViewTreeObserver.OnPreDrawListener {
  public static final class Scene {
    private final View page, surface;
    private final ChatGlassDrawable glass;
    private final ArrayList<ChatGlassDrawable> drawables = new ArrayList<>();
    private final ArrayList<HiddenView> hidden = new ArrayList<>();
    private final ArrayList<CachedView> cachedViews = new ArrayList<>();
    private final ArrayList<Layer> top = new ArrayList<>(), bottom = new ArrayList<>();
    private final ArrayList<Surface> bottomSurfaces = new ArrayList<>();
    private final RectF bounds = new RectF();
    private final RectF playerBounds = new RectF(), playerContentBounds = new RectF();
    private final int[] position = new int[2];
    private ChatGlassDrawable playerGlass;
    private HiddenView player;
    private boolean prepared;

    public Scene (View page, View surface, ChatGlassDrawable glass) {
      this.page = page;
      this.surface = surface;
      this.glass = glass;
      hide(surface);
      freeze(glass);
    }

    public Scene freeze (ChatGlassDrawable drawable) {
      if (drawable != null && !drawables.contains(drawable)) drawables.add(drawable);
      return this;
    }

    public Scene hide (View view) {
      if (view != null && findHidden(view) == null) hidden.add(new HiddenView(view));
      return this;
    }

    /** Cache only scrolling content: the page's overflow and live chrome stay intact. */
    public Scene cache (View view) {
      if (view != null) {
        for (CachedView entry : cachedViews) if (entry.view == view) return this;
        cachedViews.add(new CachedView(view));
      }
      return this;
    }

    private HiddenView findHidden (View view) {
      for (HiddenView entry : hidden) if (entry.view == view) return entry;
      return null;
    }

    public Scene top (View view, IntSupplier height) {
      if (view != null) { hide(view); top.add(new Layer(findHidden(view), height)); }
      return this;
    }

    public Scene bottom (View view) {
      if (view != null) { hide(view); bottom.add(new Layer(findHidden(view), view::getHeight)); }
      return this;
    }

    public Scene bottomSurface (View view, ChatGlassDrawable drawable) {
      if (view != null && drawable != null) { hide(view); freeze(drawable); bottomSurfaces.add(new Surface(view, drawable)); }
      return this;
    }

    public Scene player (View view, ChatGlassDrawable drawable) {
      if (view != null) { hide(view); player = findHidden(view); playerGlass = drawable; freeze(drawable); }
      return this;
    }

    private void position (View view, View root, RectF rect, int height) {
      root.getLocationInWindow(position);
      int rootX = position[0], rootY = position[1];
      view.getLocationInWindow(position);
      float x = position[0] - rootX - Math.round(page.getTranslationX()), y = position[1] - rootY - Math.round(page.getTranslationY());
      rect.set(x, y, x + view.getWidth(), y + height);
    }

    private boolean prepare (View root) {
      if (prepared) return true;
      if (surface.getWidth() == 0 || surface.getHeight() == 0) return false;
      // View backgrounds acquire their bounds during draw; prepare them before freezing.
      if (surface.getBackground() != null) surface.getBackground().setBounds(0, 0, surface.getWidth(), surface.getHeight());
      if (player != null && player.view.getBackground() != null) player.view.getBackground().setBounds(0, 0, player.view.getWidth(), player.view.getHeight());
      for (Surface entry : bottomSurfaces) entry.update(this, root);
      for (ChatGlassDrawable drawable : drawables) drawable.freezeForNavigation(true);
      updateBounds(root);
      refresh(root);
      if (root.isHardwareAccelerated()) for (CachedView entry : cachedViews) entry.prepare();
      prepared = true;
      return true;
    }

    private void refresh (View root) {
      for (HiddenView entry : hidden) entry.refreshAlpha();
      for (Surface entry : bottomSurfaces) entry.update(this, root);
      updateBounds(root);
    }

    private void captureForeground (View root, float topVisibility, float bottomVisibility) {
      // Incoming controls stay hidden until the shell has finished expanding.
      // Capturing them during that expansion repeats software drawing, bitmap
      // allocation and blur whenever a pinned/reply row changes height.
      if (topVisibility > 0f) for (Layer layer : top) capture(layer, root);
      if (bottomVisibility > 0f) for (Layer layer : bottom) capture(layer, root);
    }

    private void updateBounds (View root) {
      position(surface, root, bounds, surface.getHeight());
      float x = bounds.left, y = bounds.top;
      glass.getNavigationBounds(bounds);
      bounds.offset(x, y);
      if (player != null && playerGlass != null && player.view.getHeight() > 0) {
        position(player.view, root, playerContentBounds, player.view.getHeight());
        playerGlass.getNavigationBounds(playerBounds);
        playerBounds.offset(playerContentBounds.left, playerContentBounds.top);
      }
    }

    private void capture (Layer layer, View root) {
      View view = layer.hidden.view;
      int height = Math.min(view.getHeight(), layer.height.getAsInt());
      layer.visible = view.getVisibility() == VISIBLE && layer.hidden.alpha > 0f && height > 0;
      if (!layer.visible) return;
      position(view, root, layer.bounds, height);
      layer.clipped = view.getClipBounds(layer.clip);
      if (!layer.captured || layer.capturedHeight != height) {
        layer.captured = layer.snapshot.capture(view, height);
        layer.capturedHeight = height;
      }
    }

    private void hideViews () {
      for (HiddenView entry : hidden) entry.hide();
    }

    private void refreshBackdrop () {
      glass.refreshNavigationBackdrop();
      if (playerGlass != null && player.view.getVisibility() == VISIBLE && player.alpha > 0f) playerGlass.refreshNavigationBackdrop();
    }

    private void drawBackdrop (Canvas canvas, RectF area, boolean playerSurface) {
      ChatGlassDrawable drawable = playerSurface ? playerGlass : glass;
      if (drawable != null) drawable.drawNavigationBackdrop(canvas, area, page.getTranslationX(), page.getWidth(), page.getAlpha());
    }

    private void restore () {
      for (HiddenView entry : hidden) entry.restore();
      for (ChatGlassDrawable drawable : drawables) drawable.freezeForNavigation(false);
      for (CachedView entry : cachedViews) entry.restore();
      prepared = false;
      for (Layer layer : top) layer.captured = false;
      for (Layer layer : bottom) layer.captured = false;
    }

    private void draw (Canvas canvas, ArrayList<Layer> layers, float alpha) {
      if (alpha <= 0f) return;
      for (Layer layer : layers) {
        if (!layer.visible || !layer.captured) continue;
        int save = canvas.save();
        if (layer.clipped) canvas.clipRect(layer.bounds.left + layer.clip.left, layer.bounds.top + layer.clip.top,
          layer.bounds.left + layer.clip.right, layer.bounds.top + layer.clip.bottom);
        layer.snapshot.draw(canvas, layer.bounds.left, layer.bounds.top, alpha * layer.hidden.alpha);
        canvas.restoreToCount(save);
      }
    }

    private void drawBottom (Canvas canvas, float alpha) {
      if (alpha <= 0f) return;
      for (Surface entry : bottomSurfaces) if (entry.view.getVisibility() == VISIBLE) entry.glass.drawNavigationSurface(canvas, entry.bounds, alpha);
      draw(canvas, bottom, alpha);
    }
  }

  private static final class CachedView {
    final View view;
    int originalLayerType;
    boolean active;
    CachedView (View view) { this.view = view; }
    void prepare () {
      if (active || view.getWidth() <= 0 || view.getHeight() <= 0 || view.getVisibility() != VISIBLE || view.getAlpha() <= 0f) return;
      originalLayerType = view.getLayerType();
      if (originalLayerType != LAYER_TYPE_NONE) return;
      active = true;
      view.setLayerType(LAYER_TYPE_HARDWARE, null);
    }
    void restore () {
      if (!active) return;
      view.setLayerType(originalLayerType, null);
      active = false;
    }
  }

  private static final class Surface {
    final View view;
    final ChatGlassDrawable glass;
    final RectF bounds = new RectF();
    Surface (View view, ChatGlassDrawable glass) { this.view = view; this.glass = glass; }
    void update (Scene scene, View root) {
      if (view.getBackground() != null) view.getBackground().setBounds(0, 0, view.getWidth(), view.getHeight());
      scene.position(view, root, bounds, view.getHeight());
      float x = bounds.left, y = bounds.top;
      glass.getNavigationBounds(bounds);
      bounds.offset(x, y);
    }
  }

  private static final class HiddenView {
    final View view;
    float alpha;
    boolean hidden;
    HiddenView (View view) { this.view = view; alpha = view.getAlpha(); }
    void refreshAlpha () { if (!hidden || view.getAlpha() != 0f) alpha = view.getAlpha(); }
    void hide () {
      // Keep animator updates (reply/player visibility) rather than restoring stale state.
      refreshAlpha();
      hidden = true;
      if (view.getAlpha() != 0f) view.setAlpha(0f);
    }
    void restore () { if (hidden) { view.setAlpha(alpha); hidden = false; } }
  }

  private static final class Layer {
    final HiddenView hidden;
    final IntSupplier height;
    final RectF bounds = new RectF();
    final GlassContentSnapshot snapshot = new GlassContentSnapshot();
    final Rect clip = new Rect();
    int capturedHeight;
    boolean captured, visible, clipped;
    Layer (HiddenView hidden, IntSupplier height) { this.hidden = hidden; this.height = height; }
  }

  private final HeaderView header;
  private final RectF shell = new RectF(), playerBounds = new RectF(), playerContentBounds = new RectF();
  private final Path clip = new Path();
  private final Paint status = new Paint(Paint.ANTI_ALIAS_FLAG);
  private Scene from, to;
  private boolean forward;
  private float progress;
  private boolean prepared;
  private GlassNavigationMotion.Geometry geometry;
  private ViewTreeObserver observer;
  private long lastBackdropUpdate;

  public GlassNavigationTransition (Context context, HeaderView header) {
    super(context);
    this.header = header;
    setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    setVisibility(INVISIBLE);
  }

  public void begin (ViewController<?> left, ViewController<?> right, boolean forward) {
    finish();
    Scene leftScene = left.createGlassNavigationScene(), rightScene = right.createGlassNavigationScene();
    if (leftScene == null || rightScene == null) return;
    from = forward ? leftScene : rightScene;
    to = forward ? rightScene : leftScene;
    this.forward = forward;
    progress = 0f;
    prepared = false;
    lastBackdropUpdate = 0;
    int color = ChatGlassDrawable.surfaceColor(ColorId.filling);
    int fadeHeight = header.getEffectiveTopOffset() + Screen.dp(24f);
    status.setShader(new LinearGradient(0, 0, 0, Math.max(1, fadeHeight),
      new int[] {ColorUtils.alphaColor(.65f, color), ColorUtils.alphaColor(.32f, color), ColorUtils.alphaColor(0f, color)},
      new float[] {0f, .55f, 1f}, Shader.TileMode.CLAMP));
    observer = getViewTreeObserver();
    observer.addOnPreDrawListener(this);
    setVisibility(VISIBLE);
    header.setGlassNavigationActive(true);
  }

  public boolean isActive () { return from != null; }
  public float getProgress () { return progress; }

  public void setProgress (float progress) {
    this.progress = Math.max(0f, Math.min(1f, progress));
    invalidate();
  }

  @Override public boolean onPreDraw () {
    if (!isActive()) return true;
    if (!prepared) {
      try {
        if (!from.prepare(this) || !to.prepare(this)) return true;
        prepared = true;
        geometry = new GlassNavigationMotion.Geometry(from.bounds.bottom, to.bounds.bottom);
      } catch (RuntimeException | OutOfMemoryError error) {
        android.util.Log.w("GlassNavigation", "Foreground capture unavailable", error);
        finish();
        return true;
      }
    }
    try {
      from.refresh(this);
      to.refresh(this);
      geometry.retarget(to.bounds.bottom, progress);
      float outgoing = GlassNavigationMotion.outgoing(progress);
      from.captureForeground(this, outgoing, outgoing);
      to.captureForeground(this, geometry.incoming(progress), GlassNavigationMotion.incomingBottom(progress));
      long now = SystemClock.uptimeMillis();
      if (now - lastBackdropUpdate >= 32L || progress == 0f || progress == 1f) {
        from.refreshBackdrop();
        to.refreshBackdrop();
        lastBackdropUpdate = now;
      }
    } catch (RuntimeException | OutOfMemoryError error) {
      android.util.Log.w("GlassNavigation", "Foreground update unavailable", error);
      finish();
      return true;
    }
    header.setGlassNavigationReveal(GlassNavigationMotion.outgoing(progress), geometry.incoming(progress));
    from.hideViews();
    to.hideViews();
    return true;
  }

  @Override protected void onDraw (Canvas canvas) {
    if (!prepared) return;
    float factor = GlassNavigationMotion.geometry(progress);
    shell.set(from.bounds.left + (to.bounds.left - from.bounds.left) * factor,
      from.bounds.top + (to.bounds.top - from.bounds.top) * factor,
      from.bounds.right + (to.bounds.right - from.bounds.right) * factor,
      geometry.value(progress));
    int save = canvas.save();
    clip.reset();
    clip.addRoundRect(shell, Screen.dp(26f), Screen.dp(26f), Path.Direction.CW);
    canvas.clipPath(clip);
    drawBackdrops(canvas, shell, false);
    canvas.restoreToCount(save);
    // Chrome clips its fill itself and paints the full stroke outside that clip,
    // just like the settled drawable. An outer clip would cut the stroke in half.
    from.glass.drawNavigationChrome(canvas, shell, to.glass, factor);
    // Settled surfaces draw their background first, then this status fade in
    // View.onDraw. Keep that same order where the fade overlaps the glass.
    canvas.drawRect(0, 0, getWidth(), header.getEffectiveTopOffset() + Screen.dp(24f), status);
    save = canvas.save();
    canvas.clipPath(clip);
    from.draw(canvas, from.top, GlassNavigationMotion.outgoing(progress));
    to.draw(canvas, to.top, geometry.incoming(progress));
    canvas.restoreToCount(save);
    from.drawBottom(canvas, GlassNavigationMotion.outgoing(progress));
    to.drawBottom(canvas, GlassNavigationMotion.incomingBottom(progress));
    Scene playerScene = !from.playerBounds.isEmpty() ? from : to;
    float playerAlpha = Math.max(from.player != null && from.player.view.getVisibility() == VISIBLE ? from.player.alpha : 0f,
      to.player != null && to.player.view.getVisibility() == VISIBLE ? to.player.alpha : 0f);
    if (playerScene.playerGlass != null && playerAlpha > 0f) {
      Scene playerFrom = from.playerBounds.isEmpty() ? playerScene : from;
      Scene playerTo = to.playerBounds.isEmpty() ? playerScene : to;
      // Use the actual player panel and content origins, including the half-dp
      // stroke inset. Reconstructing them from header height shifts the endpoint.
      playerBounds.set(shell.left + blend(playerFrom.playerBounds.left - playerFrom.bounds.left, playerTo.playerBounds.left - playerTo.bounds.left, factor),
        shell.bottom + blend(playerFrom.playerBounds.top - playerFrom.bounds.bottom, playerTo.playerBounds.top - playerTo.bounds.bottom, factor),
        shell.right + blend(playerFrom.playerBounds.right - playerFrom.bounds.right, playerTo.playerBounds.right - playerTo.bounds.right, factor), 0);
      playerBounds.bottom = playerBounds.top + blend(playerFrom.playerBounds.height(), playerTo.playerBounds.height(), factor);
      playerContentBounds.set(playerBounds.left + blend(playerFrom.playerContentBounds.left - playerFrom.playerBounds.left, playerTo.playerContentBounds.left - playerTo.playerBounds.left, factor),
        playerBounds.top + blend(playerFrom.playerContentBounds.top - playerFrom.playerBounds.top, playerTo.playerContentBounds.top - playerTo.playerBounds.top, factor),
        playerBounds.right + blend(playerFrom.playerContentBounds.right - playerFrom.playerBounds.right, playerTo.playerContentBounds.right - playerTo.playerBounds.right, factor),
        playerBounds.bottom + blend(playerFrom.playerContentBounds.bottom - playerFrom.playerBounds.bottom, playerTo.playerContentBounds.bottom - playerTo.playerBounds.bottom, factor));
      save = canvas.save();
      if (playerAlpha < 1f) canvas.saveLayerAlpha(0, playerContentBounds.top, getWidth(), playerContentBounds.bottom, Math.round(playerAlpha * 255f));
      int backgroundSave = canvas.save();
      clip.reset();
      float radius = Math.min(Screen.dp(26f), playerBounds.height() / 2f);
      clip.addRoundRect(playerBounds, radius, radius, Path.Direction.CW);
      canvas.clipPath(clip);
      drawBackdrops(canvas, playerBounds, true);
      canvas.restoreToCount(backgroundSave);
      playerScene.playerGlass.drawNavigationChrome(canvas, playerBounds,
        to.playerGlass != null ? to.playerGlass : playerScene.playerGlass, factor);
      clip.reset();
      clip.addRoundRect(playerContentBounds.left + Screen.dp(8f), playerContentBounds.top,
        playerContentBounds.right - Screen.dp(8f), playerContentBounds.bottom, Screen.dp(18f), Screen.dp(18f), Path.Direction.CW);
      canvas.clipPath(clip);
      canvas.translate(playerContentBounds.left, playerContentBounds.top);
      header.getFilling().drawFloatingPlayer(canvas, Math.round(playerContentBounds.height()));
      canvas.restoreToCount(save);
    }
  }

  private static float blend (float from, float to, float factor) { return from + (to - from) * factor; }

  private void drawBackdrops (Canvas canvas, RectF area, boolean playerSurface) {
    // The right-hand page stays in front both while opening and while closing.
    (forward ? from : to).drawBackdrop(canvas, area, playerSurface);
    (forward ? to : from).drawBackdrop(canvas, area, playerSurface);
  }

  public void finish () {
    if (observer != null && observer.isAlive()) observer.removeOnPreDrawListener(this);
    observer = null;
    if (from != null) from.restore();
    if (to != null) to.restore();
    from = to = null;
    prepared = false;
    geometry = null;
    header.setGlassNavigationActive(false);
    setVisibility(INVISIBLE);
  }

  @Override protected void onDetachedFromWindow () {
    finish();
    super.onDetachedFromWindow();
  }
}
