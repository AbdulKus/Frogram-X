package org.thunderdog.challegram.ui;

import android.app.Dialog;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.Window;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Fonts;
import org.thunderdog.challegram.tool.Screen;

import me.vkryl.core.ColorUtils;
import me.vkryl.core.lambda.Destroyable;

final class MessageDialogStyle {
  private final ViewController<?> owner;
  private final Dialog dialog;
  final LinearLayout content;
  private final boolean compact;
  private Runnable onDismiss;

  MessageDialogStyle (ViewController<?> owner, String title, boolean compact) {
    this.owner = owner;
    this.compact = compact;
    dialog = new Dialog(owner.context(), Theme.dialogTheme());
    dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
    content = new LinearLayout(owner.context());
    content.setOrientation(LinearLayout.VERTICAL);
    int padding = Screen.dp(compact ? 16 : 20);
    content.setPadding(padding, padding, padding, padding);
    if (owner instanceof MessagesController) {
      content.setBackground(((MessagesController) owner).createGlassSurface(content, ColorId.filling));
    } else {
      content.setBackground(rounded(Theme.getColor(ColorId.filling), 26));
    }
    if (Build.VERSION.SDK_INT >= 21) {
      content.setOutlineProvider(new ViewOutlineProvider() {
        @Override public void getOutline (View view, Outline outline) {
          outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), Screen.dp(26));
        }
      });
      content.setClipToOutline(true);
    }
    content.addView(label(title, compact ? 16 : 20, Theme.textAccentColor(), true));
    dialog.setContentView(content);
  }

  TextView label (CharSequence text, int size, int color, boolean bold) {
    TextView view = new TextView(owner.context());
    view.setGravity(Gravity.START);
    view.setIncludeFontPadding(false);
    view.setTextSize(size);
    view.setTextColor(color);
    view.setTypeface(bold ? Fonts.getRobotoMedium() : Fonts.getRobotoRegular());
    view.setText(text);
    return view;
  }

  void add (View view, int marginTop) {
    LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
    params.topMargin = Screen.dp(marginTop);
    content.addView(view, params);
  }

  void addScrollable (View view, int marginTop, int maxHeight) {
    ScrollView scroll = new ScrollView(owner.context()) {
      @Override protected void onMeasure (int widthSpec, int heightSpec) {
        super.onMeasure(widthSpec, MeasureSpec.makeMeasureSpec(
          MeasureSpec.getMode(heightSpec) == MeasureSpec.UNSPECIFIED ? Screen.dp(maxHeight) :
            Math.min(MeasureSpec.getSize(heightSpec), Screen.dp(maxHeight)), MeasureSpec.AT_MOST));
      }
    };
    scroll.setVerticalScrollBarEnabled(false);
    scroll.addView(view, new ScrollView.LayoutParams(-1, -2));
    add(scroll, marginTop);
  }

  TextView button (CharSequence text, int color, boolean filled, Runnable action) {
    TextView view = label(text, 14, filled ? Color.WHITE : color, true);
    view.setGravity(Gravity.CENTER);
    view.setMinHeight(Screen.dp(44));
    view.setPadding(Screen.dp(10), Screen.dp(8), Screen.dp(10), Screen.dp(8));
    view.setBackground(rounded(filled ? color : ColorUtils.alphaColor(.10f, color), 14));
    view.setOnClickListener(v -> action.run());
    return view;
  }

  static GradientDrawable rounded (int color, int radius) {
    GradientDrawable background = new GradientDrawable();
    background.setColor(color);
    background.setCornerRadius(Screen.dp(radius));
    return background;
  }

  void setOnDismiss (Runnable onDismiss) { this.onDismiss = onDismiss; }
  void dismiss () { dialog.dismiss(); }

  void show () {
    if (owner.isDestroyed()) return;
    Destroyable destroy = dialog::dismiss;
    owner.addDestroyListener(destroy);
    dialog.setOnDismissListener(ignored -> {
      owner.removeDestroyListener(destroy);
      if (onDismiss != null) onDismiss.run();
    });
    Window window = dialog.getWindow();
    window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
    window.setDimAmount(compact ? .20f : .30f);
    dialog.show();
    int width = owner.context().getResources().getDisplayMetrics().widthPixels - Screen.dp(48);
    window.setLayout(Math.min(width, Screen.dp(compact ? 320 : 400)), ViewGroup.LayoutParams.WRAP_CONTENT);
  }
}
