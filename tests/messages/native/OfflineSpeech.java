package org.thunderdog.challegram.util;

/** Host-only JNI declaration fixture; Android code is compiled separately. */
public final class OfflineSpeech {
  private static native void resetNative();
  private static native void cancelNative();
  private static native void releaseModelNative();
  private static native byte[] transcribeNative(String model, float[] samples);
}
