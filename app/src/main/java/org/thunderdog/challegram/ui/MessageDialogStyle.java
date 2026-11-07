package org.thunderdog.challegram.ui;

import android.app.AlertDialog;
import android.graphics.Path;
import android.view.View;
import android.view.Window;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.widget.ChatGlassDrawable;

final class MessageDialogStyle {
  static void apply (MessagesController controller, AlertDialog dialog) {
    Window window = dialog.getWindow();
    if (window == null) return;
    View host = window.getDecorView();
    ChatGlassDrawable glass = controller.createGlassSurface(host, ColorId.filling);
    window.setBackgroundDrawable(glass);
    window.setDimAmount(.32f);
    host.addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
      Path shape = new Path();
      shape.addRoundRect(new android.graphics.RectF(0, 0, right - left, bottom - top), Screen.dp(24), Screen.dp(24), Path.Direction.CW);
      glass.setShape(shape);
    });
    host.setAlpha(0f);
    host.setScaleX(.96f);
    host.setScaleY(.96f);
    host.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(180).start();
  }
}
