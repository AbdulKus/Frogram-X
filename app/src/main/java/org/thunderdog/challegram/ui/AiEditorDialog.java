package org.thunderdog.challegram.ui;

import android.view.View;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.ProgressBar;
import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.UI;
import java.util.function.Consumer;

/** Telegram AI transformations are previewed; only Apply can modify the draft. */
final class AiEditorDialog {
  private final MessagesController controller;
  private final TdApi.FormattedText source;
  private final Consumer<TdApi.FormattedText> apply;
  private TextView applyButton;
  private TextView preview;
  private ProgressBar progress;
  private TdApi.FormattedText result;
  private int generation;

  AiEditorDialog (MessagesController controller, TdApi.FormattedText source, Consumer<TdApi.FormattedText> apply) {
    this.controller = controller;
    this.source = source;
    this.apply = apply;
  }

  void show () {
    TdApi.TextCompositionStyle[] styles = controller.tdlib().textCompositionStyles();
    MessageDialogStyle panel = new MessageDialogStyle(controller, Lang.getString(R.string.FrogramAi), true);
    panel.add(panel.button(Lang.getString(R.string.FrogramAiProofread), Theme.textLinkColor(), false, () -> {
      panel.dismiss();
      preview(null);
    }), 12);
    if (styles.length > 0) {
      LinearLayout choices = new LinearLayout(controller.context());
      choices.setOrientation(LinearLayout.VERTICAL);
      for (TdApi.TextCompositionStyle style : styles) {
        TextView choice = panel.button(style.title, Theme.textAccentColor(), false, () -> {
          panel.dismiss();
          preview(style.name);
        });
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        if (choices.getChildCount() > 0) params.topMargin = Screen.dp(6);
        choices.addView(choice, params);
      }
      panel.addScrollable(choices, 6, 280);
    }
    panel.show();
  }

  private void preview (String style) {
    if (controller.isDestroyed()) return;
    MessageDialogStyle panel = new MessageDialogStyle(controller, Lang.getString(R.string.FrogramAiPreview), false);
    LinearLayout comparison = new LinearLayout(controller.context());
    comparison.setOrientation(LinearLayout.VERTICAL);
    comparison.addView(panel.label(Lang.getString(R.string.FrogramAiOriginal), 13, Theme.textDecentColor(), true));
    TextView original = panel.label(TD.toCharSequence(source), 16, Theme.textAccentColor(), false);
    original.setPadding(0, Screen.dp(6), 0, Screen.dp(16));
    comparison.addView(original);
    comparison.addView(panel.label(Lang.getString(R.string.FrogramAiResult), 13, Theme.textDecentColor(), true));
    progress = new ProgressBar(controller.context());
    LinearLayout.LayoutParams progressParams = new LinearLayout.LayoutParams(Screen.dp(24), Screen.dp(24));
    progressParams.gravity = Gravity.CENTER_HORIZONTAL;
    comparison.addView(progress, progressParams);
    preview = panel.label(Lang.getString(R.string.FrogramAiWorking), 16, Theme.textAccentColor(), false);
    preview.setPadding(0, Screen.dp(6), 0, 0);
    preview.setTextIsSelectable(true);
    comparison.addView(preview);
    panel.addScrollable(comparison, 14, 340);
    applyButton = panel.button(Lang.getString(R.string.FrogramAiApply), Theme.textLinkColor(), true, () -> {
      if (result != null && !controller.isDestroyed()) {
        panel.dismiss();
        apply.accept(result);
      }
    });
    applyButton.setEnabled(false);
    applyButton.setAlpha(.45f);
    panel.add(applyButton, 12);
    final int request = ++generation;
    panel.setOnDismiss(() -> generation++);
    panel.show();
    if (style == null) {
      controller.tdlib().client().send(new TdApi.FixTextWithAi(source), response -> UI.post(() -> {
        if (response instanceof TdApi.FixedText) complete(request, ((TdApi.FixedText) response).text, null);
        else complete(request, null, response instanceof TdApi.Error ? (TdApi.Error) response : null);
      }));
    } else {
      controller.tdlib().client().send(new TdApi.ComposeTextWithAi(source, "", style, false), response -> UI.post(() -> {
        if (response instanceof TdApi.FormattedText) complete(request, (TdApi.FormattedText) response, null);
        else complete(request, null, response instanceof TdApi.Error ? (TdApi.Error) response : null);
      }));
    }
  }

  private void complete (int request, TdApi.FormattedText value, TdApi.Error error) {
    if (request != generation || controller.isDestroyed()) return;
    progress.setVisibility(View.GONE);
    result = value;
    preview.setAlpha(0f);
    preview.setText(value != null ? TD.toCharSequence(value) : Lang.getString(error != null && "AICOMPOSE_FLOOD_PREMIUM".equals(error.message) ? R.string.FrogramAiQuota : R.string.FrogramAiFailed));
    preview.animate().alpha(1f).setDuration(180).start();
    applyButton.setEnabled(value != null);
    applyButton.setAlpha(value != null ? 1f : .45f);
  }
}
