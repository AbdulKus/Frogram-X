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

  public static int height (int inset) {
    return inset + org.thunderdog.challegram.tool.Screen.dp(12f);
  }

  public static Shader create (int color, int inset, int height) {
    float start = Math.max(0f, Math.min(.65f, inset * .65f / Math.max(1, height)));
    int[] colors = new int[9];
    float[] positions = new float[9];
    for (int i = 0; i < colors.length; i++) {
      float position = i / (float) (colors.length - 1);
      float t = Math.max(0f, (position - start) / (1f - start));
      colors[i] = ColorUtils.alphaColor(.76f * (1f - t * t * (3f - 2f * t)), color);
      positions[i] = position;
    }
    return new LinearGradient(0, 0, 0, Math.max(1, height), colors, positions, Shader.TileMode.CLAMP);
  }
}
