/*
 * This file is a part of Frogram X.
 *
 * It bridges Telegram's tgcalls Android video source to the WebRTC camera
 * implementation bundled with the application.
 */
package org.telegram.messenger.voip;

import android.content.Context;

import androidx.annotation.Keep;

import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.voip.VoIPEglContext;
import org.webrtc.Camera1Enumerator;
import org.webrtc.Camera2Enumerator;
import org.webrtc.CameraEnumerator;
import org.webrtc.CameraVideoCapturer;
import org.webrtc.CapturerObserver;
import org.webrtc.ContextUtils;
import org.webrtc.SurfaceTextureHelper;

@Keep
public final class VideoCameraCapturer {
  private static final int VIDEO_STATE_INACTIVE = 0;
  private static final int VIDEO_STATE_PAUSED = 1;
  private static final int VIDEO_STATE_ACTIVE = 2;

  private static final int CAPTURE_WIDTH = 1280;
  private static final int CAPTURE_HEIGHT = 720;
  private static final int CAPTURE_FPS = 30;

  private long nativePtr;
  private boolean useFrontCamera = true;
  private boolean started;
  private boolean destroyed;
  private int requestedState = VIDEO_STATE_INACTIVE;

  private CameraVideoCapturer capturer;
  private SurfaceTextureHelper surfaceTextureHelper;

  @Keep
  public VideoCameraCapturer () { }

  @Keep
  private synchronized void init (long nativePtr, boolean useFrontCamera) {
    if (destroyed) {
      return;
    }
    stopAndDisposeCapturer();
    this.nativePtr = nativePtr;
    this.useFrontCamera = useFrontCamera;

    Context context = ContextUtils.getApplicationContext();
    if (context == null) {
      Log.e(Log.TAG_VOIP, "Video camera initialization failed: application context is unavailable");
      return;
    }

    CameraEnumerator enumerator = Camera2Enumerator.isSupported(context)
      ? new Camera2Enumerator(context)
      : new Camera1Enumerator(false);
    String deviceName = findCamera(enumerator, useFrontCamera);
    if (deviceName == null) {
      Log.e(Log.TAG_VOIP, "Video camera initialization failed: no camera found");
      return;
    }

    capturer = enumerator.createCapturer(deviceName, new CameraVideoCapturer.CameraEventsHandler() {
      @Override
      public void onCameraError (String errorDescription) {
        Log.e(Log.TAG_VOIP, "Video camera error: %s", errorDescription);
      }

      @Override
      public void onCameraDisconnected () {
        Log.w(Log.TAG_VOIP, "Video camera disconnected");
      }

      @Override
      public void onCameraFreezed (String errorDescription) {
        Log.e(Log.TAG_VOIP, "Video camera frozen: %s", errorDescription);
      }

      @Override
      public void onCameraOpening (String cameraName) {
        Log.v(Log.TAG_VOIP, "Opening video camera: %s", cameraName);
      }

      @Override
      public void onFirstFrameAvailable () {
        Log.v(Log.TAG_VOIP, "First local video frame is available");
      }

      @Override
      public void onCameraClosed () {
        Log.v(Log.TAG_VOIP, "Video camera closed");
      }
    });
    if (capturer == null) {
      Log.e(Log.TAG_VOIP, "Video camera initialization failed: capturer is unavailable");
      return;
    }

    surfaceTextureHelper = SurfaceTextureHelper.create("FrogramVideoCamera", VoIPEglContext.getSharedContext());
    if (surfaceTextureHelper == null) {
      Log.e(Log.TAG_VOIP, "Video camera initialization failed: EGL surface is unavailable");
      capturer.dispose();
      capturer = null;
      return;
    }

    CapturerObserver observer = nativeGetJavaVideoCapturerObserver(nativePtr);
    capturer.initialize(surfaceTextureHelper, context, observer);
    if (requestedState == VIDEO_STATE_ACTIVE) {
      startCapture();
    }
  }

  @Keep
  private synchronized void onStateChanged (long nativePtr, int state) {
    if (destroyed || this.nativePtr != nativePtr) {
      return;
    }
    requestedState = state;
    if (state == VIDEO_STATE_ACTIVE) {
      startCapture();
    } else if (state == VIDEO_STATE_PAUSED || state == VIDEO_STATE_INACTIVE) {
      stopCapture();
    }
  }

  @Keep
  private synchronized void onAspectRatioRequested (float aspectRatio) {
    if (capturer == null || !started || aspectRatio <= 0f) {
      return;
    }
    if (aspectRatio < 1f) {
      capturer.changeCaptureFormat(CAPTURE_HEIGHT, CAPTURE_WIDTH, CAPTURE_FPS);
    } else {
      capturer.changeCaptureFormat(CAPTURE_WIDTH, CAPTURE_HEIGHT, CAPTURE_FPS);
    }
  }

  @Keep
  private synchronized void onDestroy () {
    destroyed = true;
    requestedState = VIDEO_STATE_INACTIVE;
    stopAndDisposeCapturer();
    nativePtr = 0;
  }

  private void startCapture () {
    if (capturer == null || started) {
      return;
    }
    try {
      capturer.startCapture(CAPTURE_WIDTH, CAPTURE_HEIGHT, CAPTURE_FPS);
      started = true;
    } catch (Throwable t) {
      Log.e(Log.TAG_VOIP, "Unable to start video capture", t);
    }
  }

  private void stopCapture () {
    if (capturer == null || !started) {
      return;
    }
    try {
      capturer.stopCapture();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    } catch (Throwable t) {
      Log.e(Log.TAG_VOIP, "Unable to stop video capture", t);
    }
    started = false;
  }

  private void stopAndDisposeCapturer () {
    stopCapture();
    if (capturer != null) {
      try {
        capturer.dispose();
      } catch (Throwable t) {
        Log.e(Log.TAG_VOIP, "Unable to dispose video capturer", t);
      }
      capturer = null;
    }
    if (surfaceTextureHelper != null) {
      try {
        surfaceTextureHelper.dispose();
      } catch (Throwable t) {
        Log.e(Log.TAG_VOIP, "Unable to dispose video surface", t);
      }
      surfaceTextureHelper = null;
    }
  }

  private static String findCamera (CameraEnumerator enumerator, boolean front) {
    String fallback = null;
    for (String deviceName : enumerator.getDeviceNames()) {
      if (fallback == null) {
        fallback = deviceName;
      }
      if ((front && enumerator.isFrontFacing(deviceName)) || (!front && enumerator.isBackFacing(deviceName))) {
        return deviceName;
      }
    }
    return fallback;
  }

  private static native CapturerObserver nativeGetJavaVideoCapturerObserver (long nativePtr);
}
