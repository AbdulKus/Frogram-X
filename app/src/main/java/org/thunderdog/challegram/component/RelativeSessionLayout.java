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
 * File created on 08/12/2016
 */
package org.thunderdog.challegram.component;

import android.content.Context;
import android.graphics.Canvas;
import android.widget.RelativeLayout;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.user.RemoveHelper;

public class RelativeSessionLayout extends RelativeLayout implements RemoveHelper.RemoveDelegate {
  private RemoveHelper helper;
  private boolean cardStyle;
  private int lastIconColor;

  public void setCardStyle () {
    if (cardStyle) return;
    cardStyle = true;
    boolean rtl = org.thunderdog.challegram.core.Lang.rtl();
    setPadding(org.thunderdog.challegram.tool.Screen.dp(rtl ? 20f : 18f), getPaddingTop(),
      org.thunderdog.challegram.tool.Screen.dp(rtl ? 18f : 20f), getPaddingBottom());
    android.view.View icon = findViewById(R.id.session_icon);
    RelativeLayout.LayoutParams iconParams = (RelativeLayout.LayoutParams) icon.getLayoutParams();
    iconParams.width = iconParams.height = org.thunderdog.challegram.tool.Screen.dp(28f);
    icon.setPadding(0, 0, 0, 0);
    if (icon instanceof android.widget.ImageView) ((android.widget.ImageView) icon).setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER);
    icon.setLayoutParams(iconParams);
    android.view.View title = findViewById(R.id.session_title);
    RelativeLayout.LayoutParams titleParams = (RelativeLayout.LayoutParams) title.getLayoutParams();
    titleParams.leftMargin = rtl ? 0 : org.thunderdog.challegram.tool.Screen.dp(18f);
    titleParams.rightMargin = rtl ? org.thunderdog.challegram.tool.Screen.dp(18f) : 0;
    title.setLayoutParams(titleParams);
    for (int id : new int[] {R.id.session_device, R.id.session_location}) {
      android.view.View text = findViewById(id);
      RelativeLayout.LayoutParams p = (RelativeLayout.LayoutParams) text.getLayoutParams();
      p.addRule(rtl ? ALIGN_RIGHT : ALIGN_LEFT, R.id.session_title);
      text.setLayoutParams(p);
    }
  }

  @Override protected void onLayout (boolean changed, int l, int t, int r, int b) {
    super.onLayout(changed, l, t, r, b);
    if (!cardStyle) return;
    android.widget.TextView title = findViewById(R.id.session_title), detail = findViewById(R.id.session_device);
    android.view.View icon = findViewById(R.id.session_icon);
    float center = (title.getTop() + title.getBaseline() + title.getPaint().ascent() +
      detail.getTop() + detail.getBaseline() + detail.getPaint().descent()) / 2f;
    int top = Math.round(center - icon.getHeight() / 2f);
    icon.layout(icon.getLeft(), top, icon.getRight(), top + icon.getHeight());
  }

  @Override protected void dispatchDraw (Canvas canvas) {
    if (cardStyle) {
      android.view.View icon = findViewById(R.id.session_icon);
      if (icon instanceof android.widget.ImageView) {
        int color = org.thunderdog.challegram.theme.Theme.isDark() ?
          org.thunderdog.challegram.theme.Theme.getColor(org.thunderdog.challegram.theme.ColorId.icon) :
          me.vkryl.core.ColorUtils.fromToArgb(org.thunderdog.challegram.theme.Theme.getColor(org.thunderdog.challegram.theme.ColorId.textLight),
            org.thunderdog.challegram.theme.Theme.getColor(org.thunderdog.challegram.theme.ColorId.glassActive), .08f);
        if (lastIconColor != color) { lastIconColor = color; ((android.widget.ImageView) icon).setColorFilter(color); }
      }
    }
    super.dispatchDraw(canvas);
  }

  public RelativeSessionLayout (Context context) {
    super(context);
    helper = new RemoveHelper(this, R.drawable.baseline_remove_circle_24);
  }

  public void draw (Canvas c) {
    helper.save(c);
    super.draw(c);
    helper.restore(c);
    helper.draw(c);
  }

  @Override
  public void setRemoveDx (float dx) {
    helper.setDx(dx);
  }

  @Override
  public void onRemoveSwipe () {
    helper.onSwipe();
  }
}
