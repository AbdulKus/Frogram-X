package org.thunderdog.challegram.navigation;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
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
    private final ArrayList<Layer> top = new ArrayList<>(), bottom = new ArrayList<>();
    private final ArrayList<Surface> bottomSurfaces = new ArrayList<>();
    private final RectF bounds = new RectF();
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
      prepared = true;
      return true;
    }

    private void refresh (View root) {
      for (HiddenView entry : hidden) entry.refreshAlpha();
      for (Layer layer : top) capture(layer, root);
      for (Layer layer : bottom) capture(layer, root);
      for (Surface entry : bottomSurfaces) entry.update(this, root);
      updateBounds(root);
    }

    private void updateBounds (View root) {
      position(surface, root, bounds, surface.getHeight());
      float x = bounds.left, y = bounds.top;
      glass.getNavigationBounds(bounds);
      bounds.offset(x, y);
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

    private void restore () {
      for (HiddenView entry : hidden) entry.restore();
      for (ChatGlassDrawable drawable : drawables) drawable.freezeForNavigation(false);
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
  private final RectF shell = new RectF(), playerBounds = new RectF();
  private final Path clip = new Path();
  private final Paint status = new Paint(Paint.ANTI_ALIAS_FLAG);
  private Scene from, to;
  private float progress;
  private boolean prepared;
  private GlassNavigationMotion.Geometry geometry;
  private ViewTreeObserver observer;

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
    progress = 0f;
    prepared = false;
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
    } catch (RuntimeException | OutOfMemoryError error) {
      android.util.Log.w("GlassNavigation", "Foreground update unavailable", error);
      finish();
      return true;
    }
    geometry.retarget(to.bounds.bottom, progress);
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
    canvas.drawRect(0, 0, getWidth(), header.getEffectiveTopOffset() + Screen.dp(24f), status);
    from.glass.drawNavigationSurface(canvas, shell, 1f);
    to.glass.drawNavigationSurface(canvas, shell, factor);
    int save = canvas.save();
    clip.reset();
    clip.addRoundRect(shell, Screen.dp(26f), Screen.dp(26f), Path.Direction.CW);
    canvas.clipPath(clip);
    from.draw(canvas, from.top, GlassNavigationMotion.outgoing(progress));
    to.draw(canvas, to.top, geometry.incoming(progress));
    canvas.restoreToCount(save);
    from.drawBottom(canvas, GlassNavigationMotion.outgoing(progress));
    to.drawBottom(canvas, geometry.incoming(progress));
    Scene playerScene = from.playerGlass != null ? from : to;
    float playerAlpha = Math.max(from.player != null && from.player.view.getVisibility() == VISIBLE ? from.player.alpha : 0f,
      to.player != null && to.player.view.getVisibility() == VISIBLE ? to.player.alpha : 0f);
    if (playerScene.playerGlass != null && playerAlpha > 0f) {
      playerBounds.set(shell.left, shell.bottom + Screen.dp(6f), shell.right, shell.bottom + Screen.dp(6f) + HeaderView.getPlayerSize());
      playerScene.playerGlass.drawNavigationSurface(canvas, playerBounds, playerAlpha);
      save = canvas.save();
      clip.reset();
      clip.addRoundRect(playerBounds, Screen.dp(18f), Screen.dp(18f), Path.Direction.CW);
      canvas.clipPath(clip);
      if (playerAlpha < 1f) canvas.saveLayerAlpha(playerBounds, Math.round(playerAlpha * 255f));
      canvas.translate(0, playerBounds.top);
      header.getFilling().drawFloatingPlayer(canvas, HeaderView.getPlayerSize());
      canvas.restoreToCount(save);
    }
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
