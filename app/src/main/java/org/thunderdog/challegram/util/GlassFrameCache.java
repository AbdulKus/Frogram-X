package org.thunderdog.challegram.util;

/** Owns the completed blur independently of the scratch capture and its view lifetime. */
public final class GlassFrameCache {
  private final GlassBlur blur = new GlassBlur();
  private int[] output = new int[0];
  private int width, height;
  private long fingerprint;
  private boolean ready;

  public void clear () { ready = false; }
  public int[] pixels () { return output; }

  public boolean update (int[] source, int width, int height, boolean force) {
    if (width < 1 || height < 1 || source.length < width * height) throw new IllegalArgumentException();
    long next = 0xcbf29ce484222325L;
    for (int i = 0; i < width * height; i++) next = (next ^ source[i]) * 0x100000001b3L;
    if (ready && !force && this.width == width && this.height == height && fingerprint == next) return false;
    int size = width * height;
    if (output.length < size) output = new int[size];
    System.arraycopy(source, 0, output, 0, size);
    blur.blur(output, width, height);
    this.width = width; this.height = height; fingerprint = next; ready = true;
    return true;
  }
}
