/*
 * This file is a part of Frogram X.
 */
package org.thunderdog.challegram.voip;

import org.thunderdog.challegram.Log;
import org.webrtc.EglBase;

/**
 * Owns the root EGL context shared by camera capture and call renderers.
 */
public final class VoIPEglContext {
  private static EglBase root;
  private static boolean initializationAttempted;

  private VoIPEglContext () { }

  public static synchronized EglBase.Context getSharedContext () {
    if (!initializationAttempted) {
      initializationAttempted = true;
      try {
        root = EglBase.create();
      } catch (Throwable t) {
        Log.e(Log.TAG_VOIP, "Unable to create the shared call EGL context", t);
      }
    }
    return root != null ? root.getEglBaseContext() : null;
  }
}
