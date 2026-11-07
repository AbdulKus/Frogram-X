package org.thunderdog.challegram.voip;

import androidx.annotation.Keep;
import org.thunderdog.challegram.tool.UI;

/** Main-thread owner of the tgcalls group audio instance. */
@Keep
public final class GroupVoiceEngine {
  public interface Listener {
    void onPayload (int source, String payload);
    void onState (boolean connected);
    default void onSpeakers (int[] sources) { }
  }
  private final Listener listener;
  private long pointer;
  public GroupVoiceEngine (Listener listener) {
    this.listener = listener;
    System.loadLibrary("tgcallsjni");
    VoIP.initialize(UI.getAppContext());
    pointer = createNative();
    if (pointer == 0) throw new IllegalStateException("Group audio engine unavailable");
  }
  public void joinPayload () { if (pointer != 0) emitNative(pointer); }
  public void response (String payload) { if (pointer != 0) responseNative(pointer, payload); }
  public void mute (boolean muted) { if (pointer != 0) muteNative(pointer, muted); }
  public void setVolume (int source, double gain) { if (pointer != 0) volumeNative(pointer, source, gain); }
  public void stop () {
    long value = pointer;
    pointer = 0;
    if (value != 0) stopNative(value);
  }
  @Keep private void onPayload (int source, String payload) { UI.post(() -> { if (pointer != 0) listener.onPayload(source, payload); }); }
  @Keep private void onState (boolean connected) { UI.post(() -> { if (pointer != 0) listener.onState(connected); }); }
  @Keep private void onSpeakers (int[] sources) { UI.post(() -> { if (pointer != 0) listener.onSpeakers(sources); }); }
  private native long createNative ();
  private native void emitNative (long pointer);
  private native void responseNative (long pointer, String payload);
  private native void muteNative (long pointer, boolean muted);
  private native void volumeNative (long pointer, int source, double gain);
  private native void stopNative (long pointer);
}
