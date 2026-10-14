package org.thunderdog.challegram.util;

import android.os.Build;
import android.os.Trace;

/** Static, non-identifying sections for system traces; no logging or recording by default. */
public final class PerformanceTrace {
  private PerformanceTrace () { }

  public static void begin (String section) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) Trace.beginSection(section);
  }

  public static void end () {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) Trace.endSection();
  }
}
