package org.thunderdog.challegram.ui;

import android.app.AlertDialog;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
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
  private AlertDialog dialog;
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
    String[] choices = new String[styles.length + 1];
    choices[0] = Lang.getString(R.string.FrogramAiProofread);
    for (int i = 0; i < styles.length; i++) choices[i + 1] = styles[i].title;
    controller.showAlert(new AlertDialog.Builder(controller.context(), Theme.dialogTheme())
      .setTitle(Lang.getString(R.string.FrogramAi)).setItems(choices, (d, index) -> {
        if (controller.isDestroyed()) return;
        preview(index == 0 ? null : styles[index - 1].name);
      }).setNegativeButton(Lang.getString(R.string.Cancel), null));
  }

  private void preview (String style) {
    LinearLayout content = new LinearLayout(controller.context());
    content.setOrientation(LinearLayout.VERTICAL);
    int padding = Screen.dp(20);
    content.setPadding(padding, padding, padding, padding);
    TextView original = label(Lang.getString(R.string.FrogramAiOriginal) + "\n" + source.text);
    content.addView(original);
    progress = new ProgressBar(controller.context());
    content.addView(progress);
    preview = label(Lang.getString(R.string.FrogramAiWorking));
    content.addView(preview);
    ScrollView scroll = new ScrollView(controller.context());
    scroll.addView(content);
    dialog = controller.showAlert(new AlertDialog.Builder(controller.context(), Theme.dialogTheme())
      .setTitle(Lang.getString(R.string.FrogramAiPreview)).setView(scroll)
      .setNegativeButton(Lang.getString(R.string.Cancel), null)
      .setPositiveButton(Lang.getString(R.string.FrogramAiApply), (d, which) -> {
        if (result != null && !controller.isDestroyed()) apply.accept(result);
      }));
    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
    MessageDialogStyle.apply(controller, dialog);
    final int request = ++generation;
    me.vkryl.core.lambda.Destroyable destroy = dialog::dismiss;
    controller.addDestroyListener(destroy);
    dialog.setOnDismissListener(d -> {
      generation++;
      controller.removeDestroyListener(destroy);
    });
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

  private TextView label (CharSequence value) {
    TextView view = new TextView(controller.context());
    view.setTextColor(Theme.textAccentColor());
    view.setTextSize(16);
    view.setTextIsSelectable(true);
    view.setPadding(0, 0, 0, Screen.dp(16));
    view.setText(value);
    return view;
  }

  private void complete (int request, TdApi.FormattedText value, TdApi.Error error) {
    if (request != generation || controller.isDestroyed() || !dialog.isShowing()) return;
    progress.setVisibility(View.GONE);
    result = value;
    preview.setAlpha(0f);
    preview.setText(value != null ? TD.toCharSequence(value) : Lang.getString(error != null && "AICOMPOSE_FLOOD_PREMIUM".equals(error.message) ? R.string.FrogramAiQuota : R.string.FrogramAiFailed));
    preview.animate().alpha(1f).setDuration(180).start();
    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(value != null);
  }
}
