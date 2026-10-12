import org.thunderdog.challegram.util.ChatViewport;

public final class ChatViewportTest {
  private static void equal (long expected, long actual, String scenario) {
    if (expected != actual) throw new AssertionError(scenario + ": expected " + expected + ", got " + actual);
  }

  public static void main (String[] args) {
    // At each density, resize through every IME frame, then reverse direction.
    // The input has 6px margins, 49px content and a 24px system bottom inset.
    for (int density = 1; density <= 4; density++) {
      int screen = 800 * density, top = 90 * density;
      for (int keyboard = 0; keyboard <= 320; keyboard++) {
        int systemBottom = (24 + keyboard) * density;
        int bottom = ChatViewport.composerInset(61 * density + systemBottom, 0, 6 * density,
          0f, 0f, 0f, 0f, 6 * density);
        equal(61 * density + systemBottom, bottom, "IME counted once");
        int viewport = ChatViewport.height(screen, top, bottom);
        int raisedBottom = ChatViewport.composerInset(63 * density + systemBottom, 0, 6 * density,
          0f, 0f, 0f, 0f, 6 * density);
        equal(bottom + 2 * density, raisedBottom, "rounded-screen clearance counted once with IME");
        equal(viewport - 2 * density, ChatViewport.height(screen, top, raisedBottom), "raised composer preserves jump viewport");
        int message = 80 * density;
        int offset = (viewport - message) / 2;
        int previousMessages = 900 * density;
        int current = 1200 * density;
        int desired = previousMessages - offset;
        int dy = ChatViewport.scrollBy(current, desired, 4000 * density, viewport);
        equal(desired, current - dy, "smooth and immediate jumps agree above IME");
        int messageBottom = screen - bottom - offset;
        if (messageBottom > screen - bottom || messageBottom - message < top)
          throw new AssertionError("jump obscured by header or keyboard");
        equal(current, ChatViewport.scrollBy(current, 0, 4000 * density, viewport), "jump to newest message");
      }
    }
    equal(135, ChatViewport.composerInset(85, 0, 6, 1f, 44f, 0f, 0f, 6), "reply row reserved once");
    equal(137, ChatViewport.composerInset(87, 0, 6, 1f, 44f, 0f, 0f, 6), "raised reply shares composer clearance");
    equal(303, ChatViewport.composerInset(85, 0, 6, 0f, 0f, 218f, 0f, 6), "emoji translation reserved once");
    equal(305, ChatViewport.composerInset(87, 0, 6, 0f, 0f, 218f, 0f, 6), "raised composer with emoji keyboard");
    equal(255, ChatViewport.composerInset(85, 0, 6, 0f, 0f, 0f, 170f, 6), "attachment tray");
    equal(0, ChatViewport.height(300, 140, 320), "landscape with tall IME");
    equal(0, ChatViewport.scrollBy(0, 90, 120, 400), "short chat remains at bottom");
    equal(-600, ChatViewport.scrollBy(0, 1000, 1000, 400), "history start clamped to visible viewport");
    equal(0, ChatViewport.scrollBy(0, -80, 1000, 400), "newest message cannot leave a bottom gap");
    equal(260, ChatViewport.messageOffset(600, 80, 0, false), "initial jump without keyboard");
    equal(100, ChatViewport.messageOffset(280, 80, 0, false), "same pending jump after IME measurement");
    equal(-220, ChatViewport.messageOffset(280, 500, 0, false), "tall message shows its top above IME");
    equal(200, ChatViewport.messageOffset(280, 80, 0, true), "unread boundary below glass header");
    System.out.println("Chat viewport regression tests passed");
  }
}
