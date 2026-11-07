package org.thunderdog.challegram.util;

import java.util.Arrays;

/** Streaming linear resampling of mono PCM to the 16 kHz expected by Whisper. */
public final class PcmResampler {
  private static final int MAX_SAMPLES = 16000 * 600;
  private float[] output = new float[16000 * 10];
  private int size;
  private long inputFrames;
  private double nextFrame;
  private float previous;

  public void add (float sample, int sampleRate) {
    if (sampleRate < 8000 || sampleRate > 192000) throw new IllegalArgumentException("Unsupported sample rate");
    if (inputFrames == 0) {
      append(sample);
      nextFrame = sampleRate / 16000.0;
    } else {
      while (nextFrame <= inputFrames) {
        float fraction = (float) (nextFrame - (inputFrames - 1));
        append(previous + (sample - previous) * fraction);
        nextFrame += sampleRate / 16000.0;
      }
    }
    previous = sample;
    inputFrames++;
  }

  private void append (float sample) {
    if (size >= MAX_SAMPLES) throw new IllegalArgumentException("Audio is longer than 10 minutes");
    if (size == output.length) output = Arrays.copyOf(output, Math.min(MAX_SAMPLES, output.length * 2));
    output[size++] = sample;
  }

  public float[] finish () { return Arrays.copyOf(output, size); }
}
