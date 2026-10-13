package org.thunderdog.challegram.widget;

import android.graphics.Bitmap;
import android.graphics.SurfaceTexture;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.PixelCopy;
import android.view.Surface;
import android.view.TextureView;

/** Asynchronous, double-buffered video samples for software glass captures. */
public final class GlassVideoSnapshot {
  private final Handler handler = new Handler(Looper.getMainLooper());
  private SurfaceTexture source;
  private Surface surface;
  private Bitmap frame, spare;
  private long generation, capturedRevision = -1, lastRequestTime;
  private boolean pending;

  /** UI thread only. Drawing always uses the completed sample, never waits for the GPU. */
  public Bitmap frame (TextureView texture, long revision, Runnable onReady) {
    SurfaceTexture nextSource = texture.getSurfaceTexture();
    if (!texture.isAvailable() || nextSource == null) return null;
    if (source != nextSource) {
      reset();
      source = nextSource;
      surface = new Surface(source);
    }
    long now = SystemClock.uptimeMillis();
    if (pending || capturedRevision == revision || (lastRequestTime != 0 && now - lastRequestTime < 33L)) return frame;
    if (spare == null) spare = Bitmap.createBitmap(128, 128, Bitmap.Config.ARGB_8888);
    Bitmap destination = spare;
    long requestGeneration = generation;
    pending = true;
    lastRequestTime = now;
    try {
      PixelCopy.request(surface, destination, result -> {
        if (requestGeneration != generation) return;
        pending = false;
        if (result == PixelCopy.SUCCESS) {
          spare = frame;
          frame = destination;
          capturedRevision = revision;
          onReady.run();
        }
      }, handler);
    } catch (IllegalArgumentException | IllegalStateException ignored) {
      // Decoder/PiP transitions can remove the source between availability and request.
      pending = false;
    }
    return frame;
  }

  public void reset () {
    generation++;
    if (surface != null) surface.release();
    source = null;
    surface = null;
    pending = false;
    frame = spare = null;
    capturedRevision = -1;
    lastRequestTime = 0;
  }
}
