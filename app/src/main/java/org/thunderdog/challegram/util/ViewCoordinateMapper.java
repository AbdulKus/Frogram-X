package org.thunderdog.challegram.util;

import android.graphics.Matrix;
import android.graphics.RectF;
import android.view.View;
import android.view.ViewParent;

/** Reusable mapping between views in one tree, including scroll and animated transforms. */
public final class ViewCoordinateMapper {
  private final Matrix source = new Matrix(), target = new Matrix(), mapping = new Matrix();

  private static void toRoot (View view, Matrix matrix) {
    ViewParent parent = view.getParent();
    if (parent instanceof View) {
      View parentView = (View) parent;
      toRoot(parentView, matrix);
      matrix.preTranslate(-parentView.getScrollX(), -parentView.getScrollY());
    }
    matrix.preTranslate(view.getLeft(), view.getTop());
    if (!view.getMatrix().isIdentity()) matrix.preConcat(view.getMatrix());
  }

  private boolean prepare (View from, View to) {
    if (from.getRootView() != to.getRootView()) return false;
    source.reset();
    target.reset();
    toRoot(from, source);
    toRoot(to, target);
    if (!target.invert(mapping)) return false;
    mapping.preConcat(source);
    return true;
  }

  public boolean mapPoint (View from, View to, float[] point) {
    if (!prepare(from, to)) return false;
    mapping.mapPoints(point);
    return true;
  }

  public boolean mapRect (View from, View to, RectF bounds) {
    if (!prepare(from, to)) return false;
    mapping.mapRect(bounds);
    return true;
  }
}
