package org.thunderdog.challegram.widget;

import android.graphics.Bitmap;
import android.graphics.SurfaceTexture;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.SystemClock;
import android.view.PixelCopy;
import android.view.Surface;
import android.view.TextureView;

/** Asynchronous, double-buffered video samples for software glass captures. */
public final class GlassVideoSnapshot {
  private final Handler handler = new Handler(Looper.getMainLooper());
  private final boolean copyInBackground;
  private HandlerThread copyThread;
  private Handler copyHandler;
  private SurfaceTexture source;
  private Surface surface;
  private Bitmap frame, spare;
  private long generation, capturedRevision = -1, lastRequestTime;
  private boolean pending;

  public GlassVideoSnapshot () { this(Build.VERSION.SDK_INT < 34); }

  GlassVideoSnapshot (boolean copyInBackground) { this.copyInBackground = copyInBackground; }

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
    Surface requestSurface = surface;
    pending = true;
    lastRequestTime = now;
    Runnable request = () -> {
      try {
        PixelCopy.request(requestSurface, destination, result -> {
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
        // Decoder/PiP transitions can remove the source before the copy starts.
        handler.post(() -> { if (requestGeneration == generation) pending = false; });
      }
    };
    if (copyInBackground) {
      // Older PixelCopy implementations perform the readback in request(), even
      // though they deliver the result through a callback. Keep that off the UI.
      if (copyThread == null) {
        copyThread = new HandlerThread("GlassVideoCopy");
        copyThread.start();
        copyHandler = new Handler(copyThread.getLooper());
      }
      copyHandler.post(request);
    } else {
      request.run();
    }
    return frame;
  }

  public void reset () {
    generation++;
    if (surface != null) surface.release();
    if (copyThread != null) copyThread.quitSafely();
    copyThread = null;
    copyHandler = null;
    source = null;
    surface = null;
    pending = false;
    frame = spare = null;
    capturedRevision = -1;
    lastRequestTime = 0;
  }
}
