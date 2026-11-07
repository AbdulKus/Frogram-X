package org.thunderdog.challegram.ui;

import android.app.AlertDialog;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.ScrollView;
import org.thunderdog.challegram.util.OfflineSpeech;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.UI;

/** A cancellable view of Telegram's asynchronous recognition result. */
final class MessageTranscriptionDialog {
  private final MessagesController controller;
  private AlertDialog dialog;
  private ProgressBar progress;
  private TextView text;
  private boolean closed;
  private long deadline;
  private TdApi.Message message;
  private OfflineSpeech.Job localJob;
  private boolean local;

  MessageTranscriptionDialog (MessagesController controller) { this.controller = controller; }

  void show (TdApi.Message message) {
    this.message = message;
    LinearLayout content = new LinearLayout(controller.context());
    content.setOrientation(LinearLayout.VERTICAL);
    int padding = Screen.dp(24);
    content.setPadding(padding, padding, padding, padding);
    progress = new ProgressBar(controller.context());
    content.addView(progress);
    text = new TextView(controller.context());
    text.setTextSize(16);
    text.setTextColor(Theme.textAccentColor());
    text.setTextIsSelectable(true);
    text.setPadding(0, Screen.dp(16), 0, 0);
    text.setText(Lang.getString(R.string.FrogramTranscribing));
    ScrollView scroll = new ScrollView(controller.context());
    scroll.addView(text);
    content.addView(scroll);
    dialog = controller.showAlert(new AlertDialog.Builder(controller.context(), Theme.dialogTheme())
      .setTitle(Lang.getString(R.string.FrogramTranscribe)).setView(content)
      .setPositiveButton(Lang.getOK(), (d, which) -> d.dismiss()));
    MessageDialogStyle.apply(controller, dialog);
    me.vkryl.core.lambda.Destroyable destroy = dialog::dismiss;
    controller.addDestroyListener(destroy);
    dialog.setOnDismissListener(d -> {
      closed = true;
      if (localJob != null) localJob.cancel();
      controller.removeDestroyListener(destroy);
    });
    deadline = android.os.SystemClock.elapsedRealtime() + 120000;
    TdApi.SpeechRecognitionResult cached = result(message);
    if (cached instanceof TdApi.SpeechRecognitionResultText) {
      finish(((TdApi.SpeechRecognitionResultText) cached).text);
      return;
    }
    controller.tdlib().client().send(new TdApi.RecognizeSpeech(message.chatId, message.id), response -> UI.post(() -> {
      if (!active()) return;
      if (response instanceof TdApi.Error) onError((TdApi.Error) response);
      else poll(message.chatId, message.id);
    }));
  }

  private boolean active () { return !closed && !controller.isDestroyed() && dialog.isShowing(); }

  private void poll (long chatId, long messageId) {
    if (!active()) return;
    if (android.os.SystemClock.elapsedRealtime() >= deadline) {
      finish(Lang.getString(R.string.FrogramTranscriptionTimeout));
      return;
    }
    controller.tdlib().client().send(new TdApi.GetMessage(chatId, messageId), response -> UI.post(() -> {
      if (!active()) return;
      if (!(response instanceof TdApi.Message)) {
        finish(Lang.getString(R.string.FrogramTranscriptionFailed));
        return;
      }
      TdApi.SpeechRecognitionResult recognized = result((TdApi.Message) response);
      if (recognized instanceof TdApi.SpeechRecognitionResultText) {
        finish(((TdApi.SpeechRecognitionResultText) recognized).text);
      } else if (recognized instanceof TdApi.SpeechRecognitionResultError) {
        onError(((TdApi.SpeechRecognitionResultError) recognized).error);
      } else {
        if (recognized instanceof TdApi.SpeechRecognitionResultPending) {
          String partial = ((TdApi.SpeechRecognitionResultPending) recognized).partialText;
          if (!partial.isEmpty()) text.setText(partial);
        }
        UI.post(() -> poll(chatId, messageId), 1200);
      }
    }));
  }

  private void onError (TdApi.Error error) {
    String reason = error.message == null ? "" : error.message;
    if (!controller.tdlib().hasPremium() && (controller.tdlib().isSpeechRecognitionTrialExhausted() || error.code == 429 || reason.contains("PREMIUM") || reason.contains("FLOOD") || reason.contains("QUOTA") || reason.contains("LIMIT"))) {
      offerLocal();
    } else finish(Lang.getString(R.string.FrogramTranscriptionFailed));
  }

  private void offerLocal () {
    if (!active() || local) return;
    if (android.os.Build.VERSION.SDK_INT < 21) {
      finish(Lang.getString(R.string.FrogramOfflineUnsupported));
      return;
    }
    local = true;
    if (!OfflineSpeech.hasModel()) {
      progress.setVisibility(View.GONE);
      text.setText(Lang.getString(R.string.FrogramOfflineDownload));
      dialog.getButton(AlertDialog.BUTTON_POSITIVE).setText(Lang.getString(R.string.FrogramOfflineDownloadButton));
      dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> startLocal());
    } else startLocal();
  }

  private void startLocal () {
    if (!active()) return;
    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setText(Lang.getOK());
    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> dialog.dismiss());
    progress.setVisibility(View.VISIBLE);
    text.setText(Lang.getString(R.string.FrogramOfflineWorking));
    TdApi.File file = message.content instanceof TdApi.MessageVoiceNote ? ((TdApi.MessageVoiceNote) message.content).voiceNote.voice : ((TdApi.MessageVideoNote) message.content).videoNote.video;
    if (file.local.isDownloadingCompleted) runLocal(file.local.path);
    else controller.tdlib().client().send(new TdApi.DownloadFile(file.id, 32, 0, 0, true), response -> UI.post(() -> {
      if (!active()) return;
      if (response instanceof TdApi.File && ((TdApi.File) response).local.isDownloadingCompleted) runLocal(((TdApi.File) response).local.path);
      else finish(Lang.getString(R.string.FrogramOfflineFailed));
    }));
  }

  private void runLocal (String path) {
    localJob = OfflineSpeech.transcribe(path, new OfflineSpeech.Listener() {
      @Override public void onProgress (int percent) {
        if (active()) text.setText(percent < 0 ? Lang.getString(R.string.FrogramOfflineWorking) : Lang.getString(R.string.FrogramOfflineDownloadProgress, percent));
      }
      @Override public void onResult (String value, boolean failed) {
        if (active()) finish(failed ? Lang.getString(R.string.FrogramOfflineFailed) : value);
      }
    });
  }

  private void finish (String value) {
    if (!active()) return;
    progress.setVisibility(View.GONE);
    text.setAlpha(0f);
    text.setText(value);
    text.animate().alpha(1f).setDuration(180).start();
  }

  private static TdApi.SpeechRecognitionResult result (TdApi.Message message) {
    if (message.content instanceof TdApi.MessageVoiceNote) return ((TdApi.MessageVoiceNote) message.content).voiceNote.speechRecognitionResult;
    if (message.content instanceof TdApi.MessageVideoNote) return ((TdApi.MessageVideoNote) message.content).videoNote.speechRecognitionResult;
    return null;
  }
}
