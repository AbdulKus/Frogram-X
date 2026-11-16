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
    // LayerDrawable may assign bounds during construction, before our fields exist.
    if (mask == null) return;
    int width = compact ? Math.min(bounds.width(), Screen.dp(44f)) : Math.max(0, bounds.width() - Screen.dp(TAB_INSET_X_DP) * 2);
    int height = compact ? Math.min(bounds.height(), Screen.dp(44f)) : Math.max(0, bounds.height() - Screen.dp(TAB_INSET_Y_DP) * 2);
    int left = bounds.left + (bounds.width() - width) / 2, top = bounds.top + (bounds.height() - height) / 2;
    mask.setCornerRadius(Math.min(width, height) / 2f);
    mask.setBounds(left, top, left + width, top + height);
    setHotspotBounds(left, top, left + width, top + height);
  }
}
