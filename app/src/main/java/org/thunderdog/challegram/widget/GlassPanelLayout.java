package org.thunderdog.challegram.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.view.View;

import org.thunderdog.challegram.navigation.GlassPageContent;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.tool.Screen;

import me.vkryl.android.widget.FrameLayoutFix;

/** A floating secondary panel; excluded from captures to avoid sampling glass again. */
public final class GlassPanelLayout extends FrameLayoutFix {
  private final ChatGlassDrawable glass;
  private float sectionBacking;

  public GlassPanelLayout (Context context, View source) {
    super(context);
    setWillNotDraw(false);
    glass = new ChatGlassDrawable(source, this, ColorId.filling,
      canvas -> GlassPageContent.drawViewForGlass(canvas, source));
  }

  @Override protected void onDraw (Canvas canvas) {
    if (sectionBacking > 0f) canvas.drawRect(0, 0, getWidth(), getHeight(), org.thunderdog.challegram.tool.Paints.fillingPaint(
      me.vkryl.core.ColorUtils.alphaColor(sectionBacking, org.thunderdog.challegram.theme.Theme.getColor(ColorId.background))));
    int side = Screen.dp(GlassStyle.SIDE_DP), edge = Screen.dp(GlassStyle.HEADER_EDGE_DP);
    glass.setBounds(side, edge, getWidth() - side, getHeight() - edge);
    glass.draw(canvas);
  }

  /** Covers moving section-date labels in the side gutters until this panel pins. */
  public void setSectionBacking (float factor) {
    if (sectionBacking != factor) { sectionBacking = factor; invalidate(); }
  }

  public void invalidateBackdrop () { glass.invalidateBackdrop(); }
  public void setGlassEnabled (boolean enabled) { glass.setEnabled(enabled); }
  public void release () { glass.release(); }
}
