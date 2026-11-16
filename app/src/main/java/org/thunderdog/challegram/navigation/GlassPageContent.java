package org.thunderdog.challegram.navigation;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Views;
import org.thunderdog.challegram.v.CustomRecyclerView;
import org.thunderdog.challegram.widget.RootFrameLayout;

import me.vkryl.android.widget.FrameLayoutFix;

/** Extends painting above reserved headers without changing page/list coordinates. */
public final class GlassPageContent extends FrameLayoutFix implements RootFrameLayout.InsetsChangeListener {
  private final Runnable invalidation;
  private RootFrameLayout root;
  private int overflow;
  private boolean capturing, notifying;
  private final java.util.Set<RecyclerView> lists = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());

  /** Opening the list's clip must not let a row's drawColor erase other rows. */
  private static final class RowClips implements RecyclerView.OnChildAttachStateChangeListener, View.OnLayoutChangeListener {
    private final java.util.WeakHashMap<View, Rect> originals = new java.util.WeakHashMap<>();
    private final Rect bounds = new Rect();

    private void clip (View view) {
      if (view.getWidth() <= 0 || view.getHeight() <= 0) return;
      bounds.set(0, 0, view.getWidth(), view.getHeight());
      Rect original = originals.get(view);
      if (original != null && !bounds.intersect(original)) bounds.setEmpty();
      view.setClipBounds(bounds);
    }

    @Override public void onChildViewAttachedToWindow (@NonNull View view) {
      if (!originals.containsKey(view)) originals.put(view, view.getClipBounds());
      view.addOnLayoutChangeListener(this);
      clip(view);
    }

    @Override public void onChildViewDetachedFromWindow (@NonNull View view) {
      view.removeOnLayoutChangeListener(this);
      view.setClipBounds(originals.remove(view));
    }

    @Override public void onLayoutChange (View view, int l, int t, int r, int b, int oldL, int oldT, int oldR, int oldB) { clip(view); }
  }

  public GlassPageContent (Context context, View content, Runnable invalidation) {
    super(context);
    this.invalidation = invalidation;
    setClipChildren(false);
    setClipToPadding(false);
    setWillNotDraw(false);
    setLayoutParams(FrameLayoutFix.newParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
    addView(content);
  }

  private void updateInset (int inset) {
    int next = HeaderView.getSize(false) + inset;
    if (overflow != next) {
      overflow = next;
      // These cancel: the original content keeps its previous screen position
      // and viewport height. Only this wrapper extends to the window's top.
      Views.setTopMargin(this, -next);
      setPadding(0, next, 0, 0);
      invalidation.run();
    }
  }

  @Override protected void onAttachedToWindow () {
    super.onAttachedToWindow();
    root = Views.findAncestor(this, RootFrameLayout.class, true);
    if (root != null) { root.addInsetsChangeListener(this); updateInset(root.getTopInset()); }
  }

  @Override protected void onDetachedFromWindow () {
    if (root != null) root.removeInsetsChangeListener(this);
    root = null;
    super.onDetachedFromWindow();
  }

  @Override public void onInsetsChanged (RootFrameLayout view, Rect effective, Rect withoutIme, Rect system, Rect systemWithoutIme, boolean update) {
    updateInset(effective.top);
  }

  public static int extraLayoutSpace (View view) {
    while (view != null) {
      if (view instanceof GlassPageContent) return ((GlassPageContent) view).overflow;
      view = view.getParent() instanceof View ? (View) view.getParent() : null;
    }
    return 0;
  }

  private boolean openScrollClipping (View view) {
    if (view instanceof RecyclerView) {
      RecyclerView list = (RecyclerView) view;
      RecyclerView.LayoutManager manager = list.getLayoutManager();
      if (!(manager instanceof LinearLayoutManager) || ((LinearLayoutManager) manager).getOrientation() != RecyclerView.VERTICAL) return false;
      if (lists.add(list)) {
        RowClips clips = new RowClips();
        list.addOnChildAttachStateChangeListener(clips);
        for (int i = 0; i < list.getChildCount(); i++) clips.onChildViewAttachedToWindow(list.getChildAt(i));
      }
      if (list.getClipChildren()) list.setClipChildren(false);
      if (list.getClipToPadding()) list.setClipToPadding(false);
      return true;
    }
    if (!(view instanceof ViewGroup)) return false;
    ViewGroup group = (ViewGroup) view;
    boolean scrolls = false;
    for (int i = 0; i < group.getChildCount(); i++) scrolls |= openScrollClipping(group.getChildAt(i));
    if (scrolls) {
      if (group.getClipChildren()) group.setClipChildren(false);
      if (group.getClipToPadding()) group.setClipToPadding(false);
    }
    return scrolls;
  }

  @Override protected void onLayout (boolean changed, int l, int t, int r, int b) {
    super.onLayout(changed, l, t, r, b);
    openScrollClipping(getChildAt(0));
  }

  @Override protected void onDraw (@NonNull Canvas canvas) {
    canvas.drawRect(0, 0, getWidth(), getHeight(), org.thunderdog.challegram.tool.Paints.fillingPaint(Theme.getColor(ColorId.background)));
  }

  @Override public void onDescendantInvalidated (View child, View target) {
    super.onDescendantInvalidated(child, target);
    if (!insideGlassPanel(target)) invalidateBackdropFromContent();
  }

  private boolean insideGlassPanel (View view) {
    while (view != null && view != this) {
      if (view instanceof org.thunderdog.challegram.widget.GlassPanelLayout) return true;
      view = view.getParent() instanceof View ? (View) view.getParent() : null;
    }
    return false;
  }

  @Override public android.view.ViewParent invalidateChildInParent (int[] location, Rect dirty) {
    invalidateBackdropFromContent();
    return super.invalidateChildInParent(location, dirty);
  }

  private void invalidateBackdropFromContent () {
    if (capturing || notifying) return;
    notifying = true;
    try { invalidation.run(); } finally { notifying = false; }
  }

  public void drawForGlass (Canvas canvas) {
    capturing = true;
    try { drawBackdrop(canvas, this); } finally { capturing = false; }
  }

  public static void drawViewForGlass (Canvas canvas, View view) { drawBackdrop(canvas, view); }

  private static void drawBackdrop (Canvas canvas, View view) {
    if (view instanceof org.thunderdog.challegram.widget.GlassPanelLayout) return;
    if (view.getVisibility() != VISIBLE || view.getAlpha() <= 0f) return;
    int alphaSave = canvas.save();
    if (view.getAlpha() < 1f) canvas.saveLayerAlpha(null, Math.round(view.getAlpha() * 255f));
    if (view instanceof GlassPageContent) canvas.drawColor(Theme.getColor(ColorId.background));
    if (view instanceof CustomRecyclerView) {
      ((CustomRecyclerView) view).drawForGlass(canvas);
    } else if (view instanceof ViewGroup && !(view instanceof RecyclerView)) {
      if (view.getBackground() != null) view.getBackground().draw(canvas);
      ViewGroup group = (ViewGroup) view;
      for (int i = 0; i < group.getChildCount(); i++) {
        View child = group.getChildAt(i);
        int save = canvas.save();
        canvas.translate(child.getLeft() - view.getScrollX(), child.getTop() - view.getScrollY());
        canvas.concat(child.getMatrix());
        drawBackdrop(canvas, child);
        canvas.restoreToCount(save);
      }
    } else if (!canvas.quickReject(0, 0, view.getWidth(), view.getHeight(), Canvas.EdgeType.AA)) {
      view.draw(canvas);
    }
    canvas.restoreToCount(alphaSave);
  }
}
