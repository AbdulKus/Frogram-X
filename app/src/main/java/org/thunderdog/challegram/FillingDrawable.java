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
 * File created on 22/01/2017
 */
package org.thunderdog.challegram;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.theme.ThemeDelegate;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;

import me.vkryl.core.ColorUtils;

public class FillingDrawable extends Drawable {
  @ColorId
  private int colorId;

  @Nullable
  private ThemeDelegate forcedTheme;

  private float cornerRadius;
  private float topCornerRadius;
  private float bottomCornerRadius;
  private Path roundedPath;

  public FillingDrawable (int colorId) {
    this.colorId = colorId;
  }

  public FillingDrawable (int colorId, float radius) {
    this.colorId = colorId;
    this.cornerRadius = radius;
  }

  public FillingDrawable (int colorId, float topRadius, float bottomRadius) {
    this.colorId = colorId;
    this.topCornerRadius = topRadius;
    this.bottomCornerRadius = bottomRadius;
  }

  public final void setForcedTheme (ThemeDelegate forcedTheme) {
    if (this.forcedTheme != forcedTheme) {
      this.forcedTheme = forcedTheme;
      invalidateSelf();
    }
  }

  public void setCornerRadius (float radius) {
    if (this.cornerRadius != radius || topCornerRadius != 0 || bottomCornerRadius != 0) {
      this.cornerRadius = radius;
      this.topCornerRadius = 0;
      this.bottomCornerRadius = 0;
      invalidateSelf();
    }
  }

  @ColorId
  public final int getColorId () {
    return colorId;
  }

  public final void setColorId (int colorId) {
    if (this.colorId != colorId) {
      this.colorId = colorId;
      invalidateSelf();
    }
  }

  public void setAlphaFactor (float alpha) {
    if (this.alpha != alpha) {
      this.alpha = alpha;
      invalidateSelf();
    }
  }

  protected int getFillingColor () {
    return ColorUtils.alphaColor(alpha, forcedTheme != null ? forcedTheme.getColor(colorId) : Theme.getColor(colorId));
  }

  @Override
  public final void draw (@NonNull Canvas c) {
    if (colorId != 0) {
      if (topCornerRadius != 0 || bottomCornerRadius != 0) {
        RectF rectF = Paints.getRectF();
        rectF.set(getBounds());
        float topRadius = Screen.dp(topCornerRadius);
        float bottomRadius = Screen.dp(bottomCornerRadius);
        float[] radii = {
          topRadius, topRadius,
          topRadius, topRadius,
          bottomRadius, bottomRadius,
          bottomRadius, bottomRadius
        };
        if (roundedPath == null) {
          roundedPath = new Path();
        } else {
          roundedPath.reset();
        }
        roundedPath.addRoundRect(rectF, radii, Path.Direction.CW);
        c.drawPath(roundedPath, Paints.fillingPaint(getFillingColor()));
      } else if (cornerRadius != 0) {
        RectF rectF = Paints.getRectF();
        rectF.set(getBounds());
        float radius = Screen.dp(cornerRadius);
        c.drawRoundRect(rectF, radius, radius, Paints.fillingPaint(getFillingColor()));
      } else {
        c.drawRect(getBounds(), Paints.fillingPaint(getFillingColor()));
      }
    }
  }

  private float alpha = 1f;

  @Override
  public final void setAlpha (int alpha) { }

  @Override
  public final void setColorFilter (ColorFilter colorFilter) { }

  @Override
  @SuppressWarnings("deprecation")
  public final int getOpacity () {
    return PixelFormat.UNKNOWN;
  }

  public static void changeColor (View view, @ColorId int newColorId) {
    if (view != null) {
      Drawable drawable = view.getBackground();
      if (drawable instanceof FillingDrawable) {
        FillingDrawable fillingDrawable = (FillingDrawable) drawable;
        if (fillingDrawable.colorId != newColorId) {
          fillingDrawable.colorId = newColorId;
          view.invalidate();
        }
      }
    }
  }
}
