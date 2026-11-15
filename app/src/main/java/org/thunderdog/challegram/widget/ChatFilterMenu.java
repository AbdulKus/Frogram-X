package org.thunderdog.challegram.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Path;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;

import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.util.ViewCoordinateMapper;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.animator.BoolAnimator;
import me.vkryl.android.animator.FactorAnimator;
import me.vkryl.android.widget.FrameLayoutFix;

/** Floating filter surface. Held gestures and ordinary accessible clicks share the same rows. */
public class ChatFilterMenu extends FrameLayoutFix implements FactorAnimator.Target {
  private final ScrollView scroll;
  private final LinearLayout items;
  private final Path clip = new Path();
  private final BoolAnimator visible = new BoolAnimator(0, this, AnimatorUtils.DECELERATE_INTERPOLATOR, 160L);
  private final ViewCoordinateMapper coordinates = new ViewCoordinateMapper();
  private final float[] rootPoint = new float[2], localPoint = new float[2];
  private ChatGlassDrawable surface;
  private boolean freezeOnDraw;
  private Runnable dismissListener;
  private int highlighted = -1;
  private boolean tracking;
  private final Runnable autoScroll = this::scrollTowardsPointer;

  public ChatFilterMenu (Context context) {
    super(context);
    setPadding(Screen.dp(6f), Screen.dp(6f), Screen.dp(6f), Screen.dp(6f));
    setElevation(Screen.dp(8f));
    setOutlineProvider(new android.view.ViewOutlineProvider() {
      @Override public void getOutline (View view, android.graphics.Outline outline) {
        outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), Screen.dp(26f));
      }
    });
    setAlpha(0f);
    setVisibility(INVISIBLE);
    scroll = new ScrollView(context);
    scroll.setVerticalScrollBarEnabled(false);
    scroll.setOverScrollMode(OVER_SCROLL_NEVER);
    items = new LinearLayout(context);
    items.setOrientation(LinearLayout.VERTICAL);
    scroll.addView(items, new ScrollView.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
    addView(scroll, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
  }

  public void setGlassSurface (ChatGlassDrawable surface) {
    this.surface = surface;
    setBackground(surface);
  }

  public void setDismissListener (Runnable listener) { dismissListener = listener; }

  public void addItem (View item) { items.addView(item); }
  public int getItemCount () { return items.getChildCount(); }
  public View getItemAt (int index) { return items.getChildAt(index); }
  public boolean isBubbleVisible () { return visible.getValue(); }

  public void setBubbleVisible (boolean show) {
    stopTracking();
    freezeOnDraw = show;
    if (show) {
      setVisibility(VISIBLE);
      scroll.scrollTo(0, 0);
    }
    visible.setDuration(show ? 160L : 100L);
    visible.setValue(show, getWindowToken() != null);
  }

  @Override public void onFactorChanged (int id, float factor, float fraction, FactorAnimator animator) {
    setAlpha(factor);
    setScaleX(.94f + .06f * factor);
    setScaleY(.94f + .06f * factor);
  }

  @Override public void onFactorChangeFinished (int id, float factor, FactorAnimator animator) {
    freezeOnDraw = false;
    if (surface != null) surface.freezeForNavigation(false);
    if (!visible.getValue()) {
      setVisibility(INVISIBLE);
      if (dismissListener != null) dismissListener.run();
    }
  }

  @Override protected void dispatchDraw (Canvas canvas) {
    // The background has acquired its bounds and first sample in View.draw().
    // Reuse that sample during the short scale animation instead of replaying
    // several chat rows each frame as the pivot changes the window position.
    if (freezeOnDraw && surface != null) {
      surface.freezeForNavigation(true);
      freezeOnDraw = false;
    }
    int save = canvas.save();
    clip.reset();
    clip.addRoundRect(0, 0, getWidth(), getHeight(), Screen.dp(26f), Screen.dp(26f), Path.Direction.CW);
    canvas.clipPath(clip);
    super.dispatchDraw(canvas);
    canvas.restoreToCount(save);
  }

  @Override protected void onMeasure (int widthSpec, int heightSpec) {
    int margin = Screen.dp(16f);
    int maxWidth = Math.max(0, MeasureSpec.getSize(widthSpec) - margin);
    int maxHeight = Math.max(0, MeasureSpec.getSize(heightSpec) - margin);
    int horizontalPadding = getPaddingLeft() + getPaddingRight();
    int contentLimit = Math.max(0, Math.min(Screen.dp(268f), maxWidth) - horizontalPadding);
    int contentWidth = 0;
    for (int i = 0; i < items.getChildCount(); i++) {
      View item = items.getChildAt(i);
      if (item.getVisibility() == GONE) continue;
      int height = item.getLayoutParams().height;
      item.measure(MeasureSpec.makeMeasureSpec(contentLimit, MeasureSpec.AT_MOST),
        MeasureSpec.makeMeasureSpec(Math.max(0, height), height >= 0 ? MeasureSpec.EXACTLY : MeasureSpec.UNSPECIFIED));
      contentWidth = Math.max(contentWidth, item.getMeasuredWidth());
    }
    // An exact outer width also stretches the single ScrollView/content child;
    // a minimum width alone leaves a blank strip beside MATCH_PARENT rows.
    int width = Math.min(maxWidth, contentWidth + horizontalPadding);
    super.onMeasure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
      MeasureSpec.makeMeasureSpec(maxHeight, MeasureSpec.AT_MOST));
  }

  private int findItem (float x, float y) {
    if (x < scroll.getLeft() || x >= scroll.getRight() || y < scroll.getTop() || y >= scroll.getBottom()) return -1;
    localPoint[0] = x;
    localPoint[1] = y;
    if (!coordinates.mapPoint(this, items, localPoint)) return -1;
    for (int i = 0; i < items.getChildCount(); i++) {
      View item = items.getChildAt(i);
      if (item.getVisibility() == VISIBLE && item.isEnabled() &&
          localPoint[0] >= item.getLeft() && localPoint[0] < item.getRight() &&
          localPoint[1] >= item.getTop() && localPoint[1] < item.getBottom()) return i;
    }
    return -1;
  }

  private int updateSelection () {
    localPoint[0] = rootPoint[0];
    localPoint[1] = rootPoint[1];
    int item = coordinates.mapPoint(getRootView(), this, localPoint) ? findItem(localPoint[0], localPoint[1]) : -1;
    if (highlighted != item) {
      if (highlighted != -1) getItemAt(highlighted).setPressed(false);
      highlighted = item;
      if (item != -1) getItemAt(item).setPressed(true);
    }
    return highlighted;
  }

  public int trackPointer (View source, float x, float y) {
    rootPoint[0] = x;
    rootPoint[1] = y;
    tracking = coordinates.mapPoint(source, getRootView(), rootPoint);
    removeCallbacks(autoScroll);
    int selected = tracking ? updateSelection() : -1;
    if (tracking && scrollDirection() != 0) postOnAnimation(autoScroll);
    return selected;
  }

  private int scrollDirection () {
    localPoint[0] = rootPoint[0];
    localPoint[1] = rootPoint[1];
    if (!coordinates.mapPoint(getRootView(), this, localPoint) ||
        localPoint[0] < scroll.getLeft() || localPoint[0] >= scroll.getRight() ||
        localPoint[1] < scroll.getTop() || localPoint[1] >= scroll.getBottom()) return 0;
    int edge = Screen.dp(24f);
    if (localPoint[1] < scroll.getTop() + edge && scroll.canScrollVertically(-1)) return -1;
    if (localPoint[1] >= scroll.getBottom() - edge && scroll.canScrollVertically(1)) return 1;
    return 0;
  }

  private void scrollTowardsPointer () {
    if (!tracking) return;
    int direction = scrollDirection();
    if (direction != 0) {
      scroll.scrollBy(0, direction * Screen.dp(5f));
      updateSelection();
      postOnAnimation(autoScroll);
    }
  }

  public void stopTracking () {
    tracking = false;
    removeCallbacks(autoScroll);
    if (highlighted != -1) getItemAt(highlighted).setPressed(false);
    highlighted = -1;
  }

  @Override protected void onDetachedFromWindow () {
    stopTracking();
    visible.setValue(false, false);
    setVisibility(INVISIBLE);
    super.onDetachedFromWindow();
  }
}
