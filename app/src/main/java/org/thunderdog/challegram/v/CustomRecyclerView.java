/*
 * This file is a part of Telegram X
 * Copyright © 2014 (tgx-android@pm.me)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 *
 * File created on 24/08/2015 at 00:13
 */
package org.thunderdog.challegram.v;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import me.vkryl.android.animator.Animated;

public class CustomRecyclerView extends RecyclerView implements Animated {
  private Runnable backdropInvalidationListener;
  private boolean capturingGlass;
  private boolean glassOverlay;

  public void setGlassOverlay (boolean enabled) {
    glassOverlay = enabled;
  }

  @Override public void setClipToPadding (boolean clip) {
    super.setClipToPadding(clip && !glassOverlay);
  }


  public void setBackdropInvalidationListener (@Nullable Runnable listener) {
    backdropInvalidationListener = listener;
  }

  private void invalidateBackdrop () {
    if (!capturingGlass && backdropInvalidationListener != null) backdropInvalidationListener.run();
  }

  @Override public void onDescendantInvalidated (View child, View target) {
    super.onDescendantInvalidated(child, target);
    invalidateBackdrop();
  }

  @Override public android.view.ViewParent invalidateChildInParent (int[] location, android.graphics.Rect dirty) {
    invalidateBackdrop();
    return super.invalidateChildInParent(location, dirty);
  }

  @Override public void onScrolled (int dx, int dy) {
    super.onScrolled(dx, dy);
    invalidateBackdrop();
  }

  public void drawForGlass (android.graphics.Canvas canvas) {
    capturingGlass = true;
    try {
      if (getBackground() != null) getBackground().draw(canvas);
      for (int i = 0; i < getChildCount(); i++) drawGlassChild(canvas, getChildAt(i));
    } finally {
      capturingGlass = false;
    }
  }

  private static void drawGlassChild (android.graphics.Canvas canvas, View child) {
    if (child.getVisibility() != View.VISIBLE || child.getAlpha() <= 0f) return;
    int save = canvas.save();
    canvas.translate(child.getLeft(), child.getTop());
    canvas.concat(child.getMatrix());
    if (canvas.clipRect(0, 0, child.getWidth(), child.getHeight())) {
      if (child.getAlpha() < 1f) canvas.saveLayerAlpha(0, 0, child.getWidth(), child.getHeight(), Math.round(255f * child.getAlpha()));
      if (child.getBackground() != null) child.getBackground().draw(canvas);
      if (child instanceof org.thunderdog.challegram.component.dialogs.ChatView) {
        ((org.thunderdog.challegram.component.dialogs.ChatView) child).drawForGlass(canvas);
      } else if (child instanceof org.thunderdog.challegram.widget.BetterChatView) {
        ((org.thunderdog.challegram.widget.BetterChatView) child).drawForGlass(canvas);
      } else if (child instanceof org.thunderdog.challegram.widget.DoubleTextView) {
        ((org.thunderdog.challegram.widget.DoubleTextView) child).drawForGlass(canvas);
      } else if (child instanceof org.thunderdog.challegram.component.user.UserView) {
        ((org.thunderdog.challegram.component.user.UserView) child).drawForGlass(canvas);
      } else if (child instanceof android.view.ViewGroup) {
        android.view.ViewGroup group = (android.view.ViewGroup) child;
        canvas.translate(-group.getScrollX(), -group.getScrollY());
        for (int i = 0; i < group.getChildCount(); i++) drawGlassChild(canvas, group.getChildAt(i));
      }
    }
    canvas.restoreToCount(save);
  }

  public CustomRecyclerView (Context context) {
    super(context);
  }

  public CustomRecyclerView (Context context, AttributeSet attrs) {
    super(context, attrs);
  }

  public CustomRecyclerView (Context context, AttributeSet attrs, int defStyle) {
    super(context, attrs, defStyle);
  }

  public void requestLayoutAt (int adapterIndex) {
    LayoutManager manager = getLayoutManager();
    int savedScrollPosition, savedScrollOffset;
    if (manager instanceof LinearLayoutManager) {
      savedScrollPosition = ((LinearLayoutManager) manager).findFirstVisibleItemPosition();
      View view = savedScrollPosition == -1 ? null : manager.findViewByPosition(savedScrollPosition);
      savedScrollOffset = view != null ? manager.getDecoratedTop(view) - getPaddingTop() : 0;
    } else {
      savedScrollPosition = -1;
      savedScrollOffset = 0;
    }
    View view = manager.findViewByPosition(adapterIndex);
    if (view != null) {
      view.requestLayout();
      if (savedScrollPosition != RecyclerView.NO_POSITION && manager instanceof LinearLayoutManager) {
        ((LinearLayoutManager) manager).scrollToPositionWithOffset(savedScrollPosition, savedScrollOffset);
      }
    } else {
      getAdapter().notifyItemChanged(adapterIndex);
    }
  }

  public void invalidateViewAt (int adapterIndex) {
    View view = getLayoutManager().findViewByPosition(adapterIndex);
    if (view != null) {
      view.invalidate();
    } else {
      getAdapter().notifyItemChanged(adapterIndex);
    }
  }

  public void invalidateAll () {
    final LayoutManager manager = getLayoutManager();
    if (manager != null && manager instanceof LinearLayoutManager) {
      int first = ((LinearLayoutManager) manager).findFirstVisibleItemPosition();
      int last = ((LinearLayoutManager) manager).findLastVisibleItemPosition();
      for (int i = first; i <= last; i++) {
        View v = manager.findViewByPosition(i);
        if (v != null) {
          v.invalidate();
        }
      }
    }
    for (int i = 0; i < getChildCount(); i++) {
      View v = getChildAt(i);
      if (v != null) {
        v.invalidate();
      }
    }
  }

  public interface TouchInterceptor {
    boolean canInterceptAt (CustomRecyclerView v, float x, float y);
  }

  private @Nullable TouchInterceptor touchInterceptor;
  private boolean ignoreTouch;

  public void setTouchInterceptor (@Nullable TouchInterceptor touchInterceptor) {
    this.touchInterceptor = touchInterceptor;
  }

  @Override
  public boolean onInterceptTouchEvent (MotionEvent e) {
    if (scrollDisabled) {
      return interceptEvents;
    }
    if (touchDisabled) {
      switch (e.getAction()) {
        case MotionEvent.ACTION_DOWN: {
          return true;
        }
        case MotionEvent.ACTION_UP: {
          touchDisabled = false;
          return true;
        }
      }
      return false;
    }

    if (touchInterceptor != null && e.getAction() == MotionEvent.ACTION_DOWN && !touchInterceptor.canInterceptAt(this, e.getX(), e.getY())) {
      ignoreTouch = true;
      return true;
    }

    return super.onInterceptTouchEvent(e);
  }

  @Override
  public boolean onTouchEvent (MotionEvent e) {
    if (scrollDisabled) {
      return false;
    }
    if (touchDisabled) {
      switch (e.getAction()) {
        case MotionEvent.ACTION_DOWN: {
          touchDisabled = false;
          break;
        }
        case MotionEvent.ACTION_UP: {
          touchDisabled = false;
          return true;
        }
        default: {
          return false;
        }
      }
    }

    if (ignoreTouch) {
      ignoreTouch = false;
      return false;
    }

    return super.onTouchEvent(e);
  }

  private boolean touchDisabled;

  public void cancelTouchEvents () {
    touchDisabled = true;
  }

  private boolean scrollDisabled, interceptEvents;
  public void setScrollDisabled (boolean isDisabled) {
    this.scrollDisabled = isDisabled;
    this.interceptEvents = true;
  }

  public boolean isScrollDisabled () {
    return scrollDisabled;
  }

  public void setScrollDisabled (boolean isDisabled, boolean intercept) {
    this.scrollDisabled = isDisabled;
    this.interceptEvents = intercept;
  }

  public interface MeasureListener {
    void onMeasure (CustomRecyclerView v, int oldWidth, int oldHeight, int newWidth, int newHeight);
  }
  private @Nullable MeasureListener measureListener;
  public void setMeasureListener (@Nullable MeasureListener listener) {
    this.measureListener = listener;
  }

  private int oldWidth, oldHeight;

  @Override
  protected void onMeasure (int widthSpec, int heightSpec) {
    super.onMeasure(widthSpec, heightSpec);
    int newWidth = getMeasuredWidth();
    int newHeight = getMeasuredHeight();
    if (oldWidth != newWidth || oldHeight != newHeight) {
      int prevWidth = this.oldWidth;
      int prevHeight = this.oldHeight;
      oldWidth = newWidth;
      oldHeight = newHeight;
      if (measureListener != null) {
        measureListener.onMeasure(this, prevWidth, prevHeight, newWidth, newHeight);
      }
    }
  }

  private Runnable pendingAction;

  @Override
  public void runOnceViewBecomesReady (View view, Runnable action) {
    this.pendingAction = action;
  }

  @Override
  protected void onLayout (boolean changed, int left, int top, int right, int bottom) {
    super.onLayout(changed, left, top, right, bottom);
    invalidateBackdrop();
    if (pendingAction != null) {
      pendingAction.run();
      pendingAction = null;
    }
  }

  private int savedScrollPosition = RecyclerView.NO_POSITION;
  private int savedScrollTop;

  public void saveScrollPosition () {
    LayoutManager manager = getLayoutManager();
    if (!(manager instanceof LinearLayoutManager)) {
      savedScrollPosition = RecyclerView.NO_POSITION;
      return;
    }
    savedScrollPosition = ((LinearLayoutManager) manager).findFirstVisibleItemPosition();
    View view = manager.findViewByPosition(savedScrollPosition);
    savedScrollTop = view != null ? manager.getDecoratedTop(view) - getPaddingTop() : 0;
  }

  public void restoreScrollPosition () {
    if (savedScrollPosition != RecyclerView.NO_POSITION) {
      LayoutManager manager = getLayoutManager();
      if (manager instanceof LinearLayoutManager) {
        ((LinearLayoutManager) manager).scrollToPositionWithOffset(savedScrollPosition, savedScrollTop);
      }
      savedScrollPosition = RecyclerView.NO_POSITION;
      savedScrollTop = 0;
    }
  }
}
