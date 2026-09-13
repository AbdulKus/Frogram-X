package org.thunderdog.challegram.util;

/** Small frame-rate-independent spring. Gesture thresholds use the finger, not this visual value. */
public final class DampedSpring {
  public float value, target;
  private float velocity;
  public void reset (float value) { this.value = this.target = value; velocity = 0f; }
  public boolean isMoving () { return Math.abs(target - value) > .05f || Math.abs(velocity) > .05f; }
  public boolean step (float seconds) {
    seconds = Math.max(0f, Math.min(.064f, seconds));
    int steps = Math.max(1, (int) Math.ceil(seconds * 120f));
    float dt = seconds / steps;
    for (int i = 0; i < steps; i++) {
      velocity += (320f * (target - value) - 27f * velocity) * dt;
      value += velocity * dt;
    }
    if (!isMoving()) { value = target; velocity = 0f; return false; }
    return true;
  }
}
