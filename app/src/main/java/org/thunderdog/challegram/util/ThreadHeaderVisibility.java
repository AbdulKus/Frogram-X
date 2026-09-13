package org.thunderdog.challegram.util;

/** Sticky post visibility in list coordinates, independent of preview animation. */
public final class ThreadHeaderVisibility {
  private ThreadHeaderVisibility () { }

  public static boolean shouldShow (boolean requested, boolean postAttached, float postBottom, int boundary, int hysteresis) {
    if (!postAttached) return true;
    return postBottom <= boundary + (requested ? hysteresis : 0);
  }
}
