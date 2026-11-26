package org.thunderdog.challegram.util;

/** Marks software-canvas backdrop sampling passes so receivers and icons use cheap paths. */
public final class GlassRenderState {
  private static int depth;
  private static Thread renderThread;

  private GlassRenderState () { }

  public static void begin () {
    Thread current = Thread.currentThread();
    if (depth == 0) {
      renderThread = current;
    }
    if (renderThread == current) {
      depth++;
    }
  }

  public static boolean isActive () {
    return depth > 0 && renderThread == Thread.currentThread();
  }

  public static void end () {
    if (depth > 0 && renderThread == Thread.currentThread()) {
      depth--;
      if (depth == 0) {
        renderThread = null;
      }
    }
  }
}
