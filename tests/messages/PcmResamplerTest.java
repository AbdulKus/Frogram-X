import org.thunderdog.challegram.util.PcmResampler;

public final class PcmResamplerTest {
  private static void check(boolean condition, String message) {
    if (!condition) throw new AssertionError(message);
  }
  public static void main(String[] args) {
    for (int rate : new int[] {8000, 16000, 22050, 44100, 48000, 96000, 192000}) {
      PcmResampler pcm = new PcmResampler();
      for (int i = 0; i < rate * 2; i++) pcm.add((float) Math.sin(2 * Math.PI * 440 * i / rate), rate);
      float[] output = pcm.finish();
      check(Math.abs(output.length - 32000) <= 2, "Resampling changed audio duration at " + rate);
      double error = 0;
      for (int i = 1; i < output.length - 1; i++) {
        check(Float.isFinite(output[i]) && Math.abs(output[i]) <= 1.001f, "Invalid normalized PCM");
        double diff = output[i] - Math.sin(2 * Math.PI * 440 * i / 16000);
        error += diff * diff;
      }
      check(Math.sqrt(error / output.length) < .015, "Resampling distorted speech frequencies at " + rate);
    }
    PcmResampler exact = new PcmResampler();
    for (int i = 0; i < 1000; i++) exact.add(i / 1000f, 16000);
    float[] output = exact.finish();
    check(output.length == 1000, "16 kHz sample count changed");
    for (int i = 0; i < 1000; i++) check(Math.abs(output[i] - i / 1000f) < .00001, "16 kHz PCM was modified");
    check(new PcmResampler().finish().length == 0, "Empty audio produced samples");
    try { new PcmResampler().add(0, 0); throw new AssertionError("Invalid rate accepted"); }
    catch (IllegalArgumentException expected) { }
    PcmResampler bounded = new PcmResampler();
    for (int i = 0; i < 16000 * 600; i++) bounded.add(0, 16000);
    try { bounded.add(0, 16000); throw new AssertionError("Unbounded audio accepted"); }
    catch (IllegalArgumentException expected) { }
    System.out.println("PCM duration, waveform, identity, invalid rate and duration cap: passed");
  }
}
