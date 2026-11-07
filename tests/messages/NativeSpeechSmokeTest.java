import java.io.File;
import java.lang.reflect.Method;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/** Host JNI smoke test. Args: libfrogram_whisper.so, GGML model, samples/jfk.wav. */
public final class NativeSpeechSmokeTest {
  public static void main(String[] args) throws Exception {
    System.load(new File(args[0]).getAbsolutePath());
    Class<?> engine = Class.forName("org.thunderdog.challegram.util.OfflineSpeech");
    Method reset = engine.getDeclaredMethod("resetNative");
    Method transcribe = engine.getDeclaredMethod("transcribeNative", String.class, float[].class);
    Method cancel = engine.getDeclaredMethod("cancelNative");
    Method release = engine.getDeclaredMethod("releaseModelNative");
    reset.setAccessible(true); transcribe.setAccessible(true); cancel.setAccessible(true); release.setAccessible(true);
    float[] samples;
    try (AudioInputStream audio = AudioSystem.getAudioInputStream(new File(args[2]))) {
      if (audio.getFormat().getSampleRate() != 16000 || audio.getFormat().getChannels() != 1 || audio.getFormat().isBigEndian()) throw new AssertionError("Expected 16 kHz mono little-endian WAV");
      byte[] pcm = audio.readAllBytes();
      samples = new float[pcm.length / 2];
      for (int i=0; i<samples.length; i++) samples[i] = (short)((pcm[2*i]&255) | (pcm[2*i+1] << 8)) / 32768f;
    }
    reset.invoke(null);
    byte[] result = (byte[])transcribe.invoke(null, args[1], samples);
    if (result == null) throw new AssertionError("Recognition failed");
    String text = new String(result, StandardCharsets.UTF_8);
    if (!text.toLowerCase(java.util.Locale.US).contains("country")) throw new AssertionError("Unexpected transcription: " + text);
    System.out.println("JNI transcription passed: " + text.trim());
    reset.invoke(null);
    cancel.invoke(null);
    if (transcribe.invoke(null,args[1],samples) != null) throw new AssertionError("Cancelled request produced a transcript");
    System.out.println("Native cancellation passed");
    reset.invoke(null);
    if (!Arrays.equals(result, (byte[]) transcribe.invoke(null, args[1], samples))) {
      throw new AssertionError("Reusing the model changed the transcript");
    }
    release.invoke(null);
    System.out.println("Model reuse and release passed");
  }
}
