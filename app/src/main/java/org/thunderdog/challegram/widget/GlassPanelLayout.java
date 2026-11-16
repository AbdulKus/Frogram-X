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

  public GlassPanelLayout (Context context, View source) {
    super(context);
    setWillNotDraw(false);
    glass = new ChatGlassDrawable(source, this, ColorId.filling,
      canvas -> GlassPageContent.drawViewForGlass(canvas, source));
  }

  @Override protected void onDraw (Canvas canvas) {
    int side = Screen.dp(GlassStyle.SIDE_DP), edge = Screen.dp(GlassStyle.HEADER_EDGE_DP);
    glass.setBounds(side, edge, getWidth() - side, getHeight() - edge);
    glass.draw(canvas);
  }

  public void invalidateBackdrop () { glass.invalidateBackdrop(); }
  public void setGlassEnabled (boolean enabled) { glass.setEnabled(enabled); }
  public void release () { glass.release(); }
}
