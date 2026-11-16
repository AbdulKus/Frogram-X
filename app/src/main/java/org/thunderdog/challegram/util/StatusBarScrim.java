package org.thunderdog.challegram.util;

import android.graphics.LinearGradient;
import android.graphics.Shader;
import me.vkryl.core.ColorUtils;

/** Protect system icons across the whole status inset, then fade into the scene. */
public final class StatusBarScrim {
  private StatusBarScrim () { }
  public static int backgroundColor () {
    return org.thunderdog.challegram.theme.Theme.isDark() ? android.graphics.Color.BLACK : android.graphics.Color.WHITE;
  }

  public static Shader create (int color, int inset, int height) {
    float edge = Math.max(.01f, Math.min(.65f, inset / (float) Math.max(1, height)));
    return new LinearGradient(0, 0, 0, Math.max(1, height),
      new int[] {ColorUtils.alphaColor(.86f, color), ColorUtils.alphaColor(.76f, color),
        ColorUtils.alphaColor(.38f, color), ColorUtils.alphaColor(0f, color)},
      new float[] {0f, edge, .78f, 1f}, Shader.TileMode.CLAMP);
  }
}
