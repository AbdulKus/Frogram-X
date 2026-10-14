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
 * File created on 23/04/2015 at 17:44
 */
package org.thunderdog.challegram.navigation;

import android.content.Context;
import android.graphics.Canvas;
import android.view.MotionEvent;
import android.view.ViewConfiguration;

import org.thunderdog.challegram.BaseActivity;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.UI;

import me.vkryl.android.animator.FactorAnimator;
import me.vkryl.android.widget.FrameLayoutFix;

public class InterceptLayout extends FrameLayoutFix implements FactorAnimator.StartHelper {
  public InterceptLayout (Context context) {
    super(context);
    setOnTouchListener((BaseActivity) context);
  }

  public boolean isBlocked () {
    final BaseActivity context = UI.getUiContext();
    return context != null && context.getGestureController() != null && context.getGestureController().isDispatching();
  }

  @Override
  public boolean onTouchEvent (MotionEvent event) {
    return super.onTouchEvent(event) || event.getAction() == MotionEvent.ACTION_DOWN;
  }

  @Override
  public void draw (Canvas c) {
    if (((BaseActivity) getContext()).isPasscodeShowing()) {
      c.drawColor(Theme.headerColor());
    } else {
      super.draw(c);
    }
  }

  private boolean disallowIntercept;

  private BackHeaderButton capturedHeaderBack;
  private ViewController<?> capturedBackTarget;
  private float backDownX, backDownY;
  private boolean backTouchMoved;
  private final int[] backPosition = new int[2];

  private void clearHeaderBackTouch () {
    if (capturedHeaderBack != null) capturedHeaderBack.setPressed(false);
    capturedHeaderBack = null;
    capturedBackTarget = null;
    backTouchMoved = false;
  }

  @Override
  public boolean dispatchTouchEvent (MotionEvent event) {
    int action = event.getActionMasked();
    BaseActivity context = (BaseActivity) getContext();
    NavigationController navigation = context.navigation();
    if (action == MotionEvent.ACTION_DOWN) {
      clearHeaderBackTouch();
      // The old interception swallowed a tap even after the new page looked
      // ready. Keep just this back tap and finish the opening before returning.
      if (!isBlocked() && !context.isPasscodeShowing() && navigation != null && navigation.isOpeningGlassPage()) {
        BackHeaderButton back = navigation.getHeaderView().getBackButton();
        if (back != null && back.isShown() && back.isEnabled() && back.getAlpha() > 0f) {
          back.getLocationOnScreen(backPosition);
          float x = event.getRawX() - backPosition[0], y = event.getRawY() - backPosition[1];
          if (x >= 0f && x < back.getWidth() && y >= 0f && y < back.getHeight()) {
            capturedHeaderBack = back;
            capturedBackTarget = navigation.getCurrentStackItem();
            backDownX = event.getRawX();
            backDownY = event.getRawY();
            back.drawableHotspotChanged(x, y);
            back.setPressed(true);
            navigation.getHeaderView().onBackTouchDown(event);
          }
        }
      }
    }
    if (capturedHeaderBack != null) {
      float dx = event.getRawX() - backDownX, dy = event.getRawY() - backDownY;
      int slop = ViewConfiguration.get(context).getScaledTouchSlop();
      if (event.getPointerCount() > 1 || dx * dx + dy * dy > slop * slop) {
        backTouchMoved = true;
        capturedHeaderBack.setPressed(false);
      }
      if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
        boolean commit = action == MotionEvent.ACTION_UP && !backTouchMoved;
        ViewController<?> expected = capturedBackTarget;
        clearHeaderBackTouch();
        if (commit && navigation != null) navigation.requestHeaderBack(expected);
      }
      return true;
    }
    return super.dispatchTouchEvent(event);
  }

  @Override protected void onDetachedFromWindow () {
    clearHeaderBackTouch();
    super.onDetachedFromWindow();
  }

  /*@Override
  public void requestDisallowInterceptTouchEvent (boolean disallowIntercept) {
    this.disallowIntercept = disallowIntercept;
    super.requestDisallowInterceptTouchEvent(disallowIntercept);
  }*/

  public boolean onInterceptTouchEvent (MotionEvent event) {
    if (disallowIntercept) {
      return false;
    }

    final BaseActivity context = ((BaseActivity) getContext());

    if (isBlocked() || context.isAnimating(true)) {
      return true;
    }

    DrawerController drawer = context.getDrawer();
    if (drawer != null && drawer.isVisible() && event.getAction() == MotionEvent.ACTION_DOWN) {
      if (Lang.rtl()) {
        if (event.getX() < getMeasuredWidth() - drawer.getWidth() + drawer.getShadowWidth()) {
          context.processTouchEvent(event);
          return true;
        }
      } else {
        if (event.getX() >= drawer.getWidth()) {
          context.processTouchEvent(event);
          return true;
        }
      }
    }

    return context.processTouchEvent(event) && event.getAction() != MotionEvent.ACTION_DOWN;
  }

  private FactorAnimator scheduledAnimator;
  private float scheduledAnimationFactor;

  @Override
  public void startAnimatorOnLayout (FactorAnimator animator, float toFactor) {
    scheduledAnimator = animator;
    scheduledAnimationFactor = toFactor;
  }

  @Override
  protected void onLayout (boolean changed, int left, int top, int right, int bottom) {
    super.onLayout(changed, left, top, right, bottom);
    if (scheduledAnimator != null) {
      scheduledAnimator.animateTo(scheduledAnimationFactor);
      scheduledAnimator = null;
    }
  }

  @Override
  protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
    super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    UI.getContext(getContext()).applyContentTranslation(this);
  }
}
