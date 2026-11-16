package org.thunderdog.challegram.widget;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;

import org.thunderdog.challegram.tool.Screen;

/** Bounded circular header feedback and capsule-shaped folder feedback. */
public final class GlassRippleDrawable extends RippleDrawable {
  public static final float TAB_INSET_X_DP = 4f, TAB_INSET_Y_DP = 7f;
  private final GradientDrawable mask;
  private final boolean compact;
  private int visibleWidth = -1;
  private boolean alignRight;

  /** The touch view reserves the expanded label; feedback follows the visible tab. */
  public void setVisibleWidth (int width, boolean rtl) {
    if (visibleWidth == width && alignRight == rtl) return;
    visibleWidth = width;
    alignRight = rtl;
    updateMask(getBounds());
  }

  public GlassRippleDrawable (int color, boolean compact) {
    this(color, compact, new GradientDrawable());
  }

  private GlassRippleDrawable (int color, boolean compact, GradientDrawable mask) {
    super(ColorStateList.valueOf(color), null, mask);
    this.mask = mask;
    this.compact = compact;
    mask.setColor(Color.WHITE);
  }

  @Override protected void onBoundsChange (Rect bounds) {
    super.onBoundsChange(bounds);
    updateMask(bounds);
  }

  private void updateMask (Rect bounds) {
    // LayerDrawable may assign bounds during construction, before our fields exist.
    if (mask == null) return;
    int extent = visibleWidth >= 0 ? Math.min(bounds.width(), Math.max(0, visibleWidth)) : bounds.width();
    int start = bounds.left + (alignRight ? bounds.width() - extent : 0);
    int width = compact ? Math.min(bounds.width(), Screen.dp(GlassStyle.CONTROL_SIZE_DP)) : Math.max(0, extent - Screen.dp(TAB_INSET_X_DP) * 2);
    int height = compact ? Math.min(bounds.height(), Screen.dp(GlassStyle.CONTROL_SIZE_DP)) : Math.max(0, bounds.height() - Screen.dp(TAB_INSET_Y_DP) * 2);
    int left = start + (extent - width) / 2, top = bounds.top + (bounds.height() - height) / 2;
    mask.setCornerRadius(Math.min(width, height) / 2f);
    mask.setBounds(left, top, left + width, top + height);
    setHotspotBounds(left, top, left + width, top + height);
  }
}
