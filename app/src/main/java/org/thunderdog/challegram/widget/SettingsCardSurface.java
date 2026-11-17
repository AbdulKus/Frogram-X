package org.thunderdog.challegram.widget;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Screen;

import me.vkryl.core.ColorUtils;

/** The same restrained light-theme separation for profile and settings groups. */
public final class SettingsCardSurface {
  private final RectF bounds = new RectF();
  private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

  public void draw (Canvas canvas, View row, boolean first, boolean last, boolean foreground) {
    if (Theme.isDark() || row.getAlpha() <= 0f) return;
    float radius = Screen.dp(GlassStyle.RADIUS_DP);
    float top = row.getY(), bottom = top + row.getHeight();
    bounds.set(row.getX(), top - (first ? 0 : radius), row.getX() + row.getWidth(), bottom + (last ? 0 : radius));
    int save = canvas.save();
    // Joined rows expose only the group's outer sides, never a shadow/border seam.
    canvas.clipRect(bounds.left - Screen.dp(5f), top - (first ? Screen.dp(5f) : 0),
      bounds.right + Screen.dp(5f), bottom + (last ? Screen.dp(6f) : 0));
    paintSurface(canvas, row.getAlpha(), foreground, radius);
    canvas.restoreToCount(save);
  }

  public void drawGroup (Canvas canvas, RectF group, boolean foreground) {
    if (Theme.isDark() && foreground) return;
    bounds.set(group);
    if (!canvas.isHardwareAccelerated() && canvas.quickReject(bounds, Canvas.EdgeType.AA)) return;
    if (Theme.isDark()) {
      paint.clearShadowLayer();
      paint.setStyle(Paint.Style.FILL);
      paint.setColor(ProfileCardBackground.fillingColor());
      canvas.drawRoundRect(bounds, Screen.dp(GlassStyle.RADIUS_DP), Screen.dp(GlassStyle.RADIUS_DP), paint);
    } else {
      paintSurface(canvas, 1f, foreground, Screen.dp(GlassStyle.RADIUS_DP));
    }
  }

  private void paintSurface (Canvas canvas, float alpha, boolean foreground, float radius) {
    paint.setAlpha(Math.round(255 * alpha));
    if (foreground) {
      paint.clearShadowLayer();
      paint.setStyle(Paint.Style.STROKE);
      paint.setStrokeWidth(Screen.dp(.75f));
      paint.setColor(ColorUtils.alphaColor(.09f * alpha, Theme.getColor(ColorId.glassText)));
      canvas.drawRoundRect(bounds, radius, radius, paint);
    } else {
      paint.setStyle(Paint.Style.FILL);
      paint.setColor(ProfileCardBackground.fillingColor());
      paint.setAlpha(Math.round(255 * alpha));
      paint.setShadowLayer(Screen.dp(2f), 0, Screen.dp(1f), ColorUtils.alphaColor(.10f * alpha, 0xff000000));
      canvas.drawRoundRect(bounds, radius, radius, paint);
      paint.clearShadowLayer();
    }
  }
}
