package org.thunderdog.challegram.util;

import org.thunderdog.challegram.tool.UI;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/** One bounded on-device inference at a time, with a checksum-verified model. */
@android.annotation.TargetApi(21)
public final class OfflineSpeech {
  private static final String MODEL_URL = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-base.bin";
  private static final String MODEL_SHA256 = "60ed5bc3dd14eea856493d334349b405782ddcaf0028d4b5df4088345fba2efe";
  private static final long MODEL_SIZE = 147951465;
  private static final ExecutorService WORKER = Executors.newSingleThreadExecutor();
  private static final AtomicBoolean BUSY = new AtomicBoolean();
  private static volatile Job running;
  private static final Object NATIVE_LOCK = new Object();
  private static volatile boolean loaded;

  private static native void resetNative ();
  private static native void cancelNative ();
  private static native byte[] transcribeNative (String model, float[] audio);

  private static File model () { return new File(UI.getAppContext().getNoBackupFilesDir(), "speech/ggml-base.bin"); }
  public static boolean hasModel () { return model().length() == MODEL_SIZE; }

  public interface Listener {
    void onProgress (int percent);
    void onResult (String text, boolean failed);
  }

  public static final class Job {
    final AtomicBoolean cancelled = new AtomicBoolean();
    public void cancel () {
      cancelled.set(true);
      synchronized (NATIVE_LOCK) {
        if (running == this && loaded) cancelNative();
      }
    }
  }

  public static Job transcribe (String mediaPath, Listener listener) {
    Job job = new Job();
    if (!BUSY.compareAndSet(false, true)) {
      UI.post(() -> { if (!job.cancelled.get()) listener.onResult("", true); });
      return job;
    }
    WORKER.execute(() -> {
      synchronized (NATIVE_LOCK) { running = job; }
      String result = "";
      boolean failed = true;
      try {
        if (!loaded) { System.loadLibrary("frogram_whisper"); loaded = true; }
        synchronized (NATIVE_LOCK) { resetNative(); }
        File file = ensureModel(job, listener);
        UI.post(() -> { if (!job.cancelled.get()) listener.onProgress(-1); });
        if (!job.cancelled.get()) {
          float[] samples = SpeechAudioDecoder.decode(mediaPath, job.cancelled);
          if (!job.cancelled.get()) {
            byte[] bytes = transcribeNative(file.getAbsolutePath(), samples);
            if (bytes != null) { result = new String(bytes, StandardCharsets.UTF_8).trim(); failed = false; }
          }
        }
      } catch (Exception | LinkageError | OutOfMemoryError ignored) {
        // Report to the originating dialog without crashing the chat.
      } finally {
        synchronized (NATIVE_LOCK) { running = null; }
        BUSY.set(false);
      }
      final String text = result;
      final boolean error = failed;
      UI.post(() -> { if (!job.cancelled.get()) listener.onResult(text, error); });
    });
    return job;
  }

  private static File ensureModel (Job job, Listener listener) throws Exception {
    File target = model();
    if (hasModel() && MODEL_SHA256.equals(hash(target))) return target;
    File directory = target.getParentFile();
    if (!directory.isDirectory() && !directory.mkdirs()) throw new IOException("Cannot create model directory");
    File temporary = new File(directory, "model.part");
    HttpURLConnection connection = (HttpURLConnection) new URL(MODEL_URL).openConnection();
    connection.setConnectTimeout(15000);
    connection.setReadTimeout(15000);
    connection.setInstanceFollowRedirects(true);
    try {
      if (connection.getResponseCode() != 200) throw new IOException("Model download failed");
      long total = 0;
      int previous = -1;
      try (InputStream input = connection.getInputStream(); FileOutputStream output = new FileOutputStream(temporary)) {
        byte[] buffer = new byte[65536];
        int count;
        while ((count = input.read(buffer)) != -1) {
          if (job.cancelled.get()) throw new IOException("Cancelled");
          total += count;
          if (total > MODEL_SIZE) throw new IOException("Model too large");
          output.write(buffer, 0, count);
          int percent = (int) (100 * total / MODEL_SIZE);
          if (percent != previous) {
            previous = percent;
            UI.post(() -> { if (!job.cancelled.get()) listener.onProgress(percent); });
          }
        }
        output.getFD().sync();
      }
      if (job.cancelled.get() || total != MODEL_SIZE || !MODEL_SHA256.equals(hash(temporary))) throw new IOException("Model verification failed");
      if (!temporary.renameTo(target)) throw new IOException("Model installation failed");
      return target;
    } finally {
      connection.disconnect();
      if (temporary.exists()) temporary.delete();
    }
  }

  private static String hash (File file) throws Exception {
    MessageDigest digest = MessageDigest.getInstance("SHA-256");
    try (InputStream input = new FileInputStream(file)) {
      byte[] buffer = new byte[65536];
      int count;
      while ((count = input.read(buffer)) != -1) digest.update(buffer, 0, count);
    }
    StringBuilder result = new StringBuilder();
    for (byte value : digest.digest()) result.append(String.format(java.util.Locale.US, "%02x", value & 255));
    return result.toString();
  }
}
