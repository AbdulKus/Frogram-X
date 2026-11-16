package org.thunderdog.challegram.widget;

import android.graphics.Color;
import android.graphics.drawable.Drawable;

import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Screen;

import me.vkryl.core.ColorUtils;

/** Shared spacing and row feedback for the floating filter and chat menus. */
public final class GlassMenuStyle {
  public static final float PADDING_DP = 6f, RADIUS_DP = 26f, GAP_DP = 8f;
  private GlassMenuStyle () { }

  public static Drawable rowBackground (boolean selected) {
    int accent = Theme.getColor(ColorId.glassActive);
    return Theme.createSimpleSelectorRoundRectDrawable(Screen.dp(16f),
      selected ? ColorUtils.alphaColor(.12f, accent) : Color.TRANSPARENT,
      ColorUtils.alphaColor(.14f, accent), Color.WHITE);
  }
}
