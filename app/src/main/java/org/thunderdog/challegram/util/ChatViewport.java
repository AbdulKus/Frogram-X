package org.thunderdog.challegram.util;

/** Geometry shared by measured composer insets and reverse-layout message jumps. */
public final class ChatViewport {
  private ChatViewport () { }

  public static int height (int height, int top, int bottom) {
    return Math.max(0, height - Math.max(0, top) - Math.max(0, bottom));
  }

  public static int composerInset (int height, int margin, int inputTop, float replyVisibility,
                                   float replyOffset, float keyboardOffset, float attachments, int gap) {
    return Math.max(0, Math.round(height + margin - inputTop * (1f - replyVisibility) +
      replyOffset + keyboardOffset + attachments + gap));
  }

  public static int scrollBy (long current, long desired, long contentHeight, int viewportHeight) {
    long maximum = Math.max(0, contentHeight - viewportHeight);
    return (int) (current - Math.max(0, Math.min(maximum, desired)));
  }

  public static int messageOffset (int viewportHeight, int messageHeight, int topEdge, boolean unread) {
    return unread || messageHeight + topEdge >= viewportHeight ? viewportHeight - messageHeight :
      viewportHeight / 2 - messageHeight / 2 + topEdge;
  }
}
