package org.thunderdog.challegram.widget;

import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.RippleDrawable;

import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.Theme;

import me.vkryl.core.ColorUtils;

/** Shared quiet, theme-derived surfaces for profile and settings groups. */
public final class ProfileCardBackground extends RippleDrawable {
  private int lastAccent;

  public ProfileCardBackground () {
    super(ColorStateList.valueOf(Color.TRANSPARENT), new Fill(), new ColorDrawable(Color.WHITE));
  }

  public static int fillingColor () {
    return ColorUtils.fromToArgb(Theme.getColor(ColorId.filling), Theme.getColor(ColorId.glassActive), Theme.isDark() ? .035f : .045f);
  }

  private static final class Fill extends ColorDrawable {
    @Override public void draw (Canvas canvas) {
      setColor(fillingColor());
      super.draw(canvas);
    }
  }

  @Override public void draw (Canvas canvas) {
    if (!canvas.isHardwareAccelerated()) {
      // A backdrop capture must not switch the live RippleDrawable between its
      // software and RenderThread animators. Sample its fill without advancing it.
      int color = fillingColor();
      boolean enabled = false, pressed = false;
      for (int state : getState()) {
        enabled |= state == android.R.attr.state_enabled;
        pressed |= state == android.R.attr.state_pressed;
      }
      if (enabled && pressed) color = ColorUtils.fromToArgb(color, Theme.getColor(ColorId.glassActive), .14f);
      canvas.drawRect(getBounds(), org.thunderdog.challegram.tool.Paints.fillingPaint(color));
      return;
    }
    int accent = Theme.getColor(ColorId.glassActive);
    if (accent != lastAccent) {
      lastAccent = accent;
      setColor(ColorStateList.valueOf(ColorUtils.alphaColor(.14f, accent)));
    }
    super.draw(canvas);
  }
}
