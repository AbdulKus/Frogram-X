package org.thunderdog.challegram.widget;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;

import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.util.StatusBarScrim;

/** Floating settings/profile header with a clipped, low-resolution backdrop. */
public final class GlassPageHeader {
  private final ChatGlassDrawable glass;
  private ChatGlassDrawable player;
  private final View content, host;
  private boolean playerVisible;
  private final Paint fade = new Paint();
  private int lastBackground, lastHeight = -1, lastInset = -1, lastFade;

  public GlassPageHeader (View content, View host) {
    this.content = content; this.host = host;
    glass = createSurface();
  }

  public void draw (Canvas canvas, int width, int height, int inset) {
    playerVisible = false;
    int background = Theme.getColor(ColorId.background);
    if (background != lastBackground) {
      lastBackground = background;
      glass.invalidateBackdrop();
      if (player != null) player.invalidateBackdrop();
    }
    layout(width, height, inset);
    glass.draw(canvas);
    int color = StatusBarScrim.backgroundColor();
    if (height != lastHeight || inset != lastInset || color != lastFade) {
      lastHeight = height; lastInset = inset; lastFade = color;
      fade.setShader(StatusBarScrim.create(color, inset, StatusBarScrim.height(inset)));
    }
    canvas.drawRect(0, 0, width, StatusBarScrim.height(inset), fade);
  }

  public ChatGlassDrawable layout (int width, int height, int inset) {
    int edge = Screen.dp(GlassStyle.HEADER_EDGE_DP), side = Screen.dp(GlassStyle.SIDE_DP);
    glass.setBounds(side, inset + edge, Math.max(side, width - side), Math.max(inset + edge, height + Screen.dp(GlassStyle.HEADER_OVERFLOW_DP)));
    return glass;
  }

  private ChatGlassDrawable createSurface () {
    return new ChatGlassDrawable(content, host, ColorId.filling,
      canvas -> {
        if (content instanceof org.thunderdog.challegram.navigation.GlassPageContent) ((org.thunderdog.challegram.navigation.GlassPageContent) content).drawForGlass(canvas);
        else canvas.drawColor(Theme.getColor(ColorId.background));
      });
  }

  public void drawPlayer (Canvas canvas, int width, int top, int bottom) {
    playerVisible = true;
    layoutPlayer(width, top, bottom).draw(canvas);
  }

  public ChatGlassDrawable layoutPlayer (int width, int top, int bottom) {
    if (player == null) player = createSurface();
    int side = Screen.dp(GlassStyle.SIDE_DP), edge = Screen.dp(GlassStyle.HEADER_EDGE_DP);
    player.setBounds(side, top + edge, width - side, bottom - edge);
    return player;
  }

  public void invalidateBackdrop () { glass.invalidateBackdrop(); if (player != null) player.invalidateBackdrop(); }

  public int bottom () { return playerVisible && player != null ? Math.max(glass.getBounds().bottom, player.getBounds().bottom) : glass.getBounds().bottom; }

  public void setEnabled (boolean enabled) {
    glass.setEnabled(enabled);
    if (player != null) player.setEnabled(enabled);
  }

  public void release () { glass.release(); if (player != null) player.release(); }
}
