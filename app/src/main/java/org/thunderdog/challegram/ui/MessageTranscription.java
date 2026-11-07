package org.thunderdog.challegram.ui;

import android.os.Build;
import android.util.LruCache;
import android.util.Pair;
import android.widget.TextView;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.util.OfflineSpeech;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import me.vkryl.core.lambda.RunnableBool;

import tgx.td.ChatId;

final class MessageTranscription {
  interface Listener {
    void onWorking (boolean working);
    void onResult (String text);
  }

  private final MessagesController owner;
  private final Map<Pair<Long, Long>, Request> requests = new HashMap<>();
  private record TranscriptKey(long userId, long chatId, long messageId) {}
  private static final LruCache<TranscriptKey, String> recentTranscripts = new LruCache<>(128);

  private final LruCache<Pair<Long, Long>, Boolean> collapsed = new LruCache<>(64);

  MessageTranscription (MessagesController owner) { this.owner = owner; }

  static boolean supportsTranscription (TdApi.Message message) {
    return message != null && message.chatId != 0 &&
      !ChatId.isSecret(message.chatId) && message.selfDestructType == null &&
      (message.content instanceof TdApi.MessageVoiceNote || message.content instanceof TdApi.MessageVideoNote);
  }

  static boolean canTranscribe (TdApi.Message message) {
    return supportsTranscription(message) && message.id > 0 && message.sendingState == null;
  }

  private TranscriptKey transcriptKey (TdApi.Message message) {
    return new TranscriptKey(owner.tdlib().myUserId(true), message.chatId, message.id);
  }

  private String cachedText (TdApi.Message message) {
    TranscriptKey key = transcriptKey(message);
    return key.userId() != 0 ? recentTranscripts.get(key) : null;
  }

  private void cacheText (TdApi.Message message, String text) {
    TranscriptKey key = transcriptKey(message);
    if (key.userId() != 0) recentTranscripts.put(key, text);
  }

  boolean isWorking (TdApi.Message message) {
    if (requests.isEmpty()) return false;
    Request request = requests.get(Pair.create(message.chatId, message.id));
    return request != null && request.working;
  }

  String openedVoiceTranscript (TdApi.Message message) {
    Pair<Long, Long> key = Pair.create(message.chatId, message.id);
    return collapsed.get(key) == null ? cachedText(message) : null;
  }

  void setVoiceTranscriptExpanded (TdApi.Message message, boolean expanded) {
    Pair<Long, Long> key = Pair.create(message.chatId, message.id);
    if (expanded) collapsed.remove(key);
    else collapsed.put(key, true);
  }

  void start (TdApi.Message message, Listener listener) {
    if (!canTranscribe(message) || owner.isDestroyed()) return;
    Pair<Long, Long> key = Pair.create(message.chatId, message.id);
    String text = cachedText(message);
    TdApi.SpeechRecognitionResult cached = recognitionResult(message);
    if (cached instanceof TdApi.SpeechRecognitionResultText) {
      text = ((TdApi.SpeechRecognitionResultText) cached).text;
    }
    if (text != null) {
      cacheText(message, text);
      listener.onWorking(false);
      if (!text.trim().isEmpty()) listener.onResult(text);
      else UI.showToast(R.string.FrogramTranscriptionEmpty, android.widget.Toast.LENGTH_SHORT);
      return;
    }
    Request existing = requests.get(key);
    if (existing != null) {
      existing.listeners.add(listener);
      listener.onWorking(existing.working);
      return;
    }
    Request request = new Request(key, message);
    request.listeners.add(listener);
    requests.put(key, request);
    request.start(cached instanceof TdApi.SpeechRecognitionResultPending);
  }

  void showVideo (TdApi.Message message, RunnableBool onWorking) {
    start(message, new Listener() {
      @Override public void onWorking (boolean working) {
        onWorking.runWithBool(working);
      }

      @Override public void onResult (String text) {
        if (!owner.isFocused() || owner.getChatId() != message.chatId) return;
        MessageDialogStyle panel = new MessageDialogStyle(owner, Lang.getString(R.string.FrogramTranscribe), false);
        TextView label = panel.label(text, 16, Theme.textAccentColor(), false);
        label.setTextIsSelectable(true);
        panel.addScrollable(label, 12, 340);
        panel.add(panel.button(Lang.getOK(), Theme.textAccentColor(), false, panel::dismiss), 12);
        panel.show();
      }
    });
  }

  void cancel () {
    List<Request> pending = new ArrayList<>(requests.values());
    for (Request request : pending) request.finish(null, 0);
  }

  private final class Request {
    final Pair<Long, Long> key;
    final TdApi.Message message;
    final List<Listener> listeners = new ArrayList<>();
    boolean working;
    boolean server = true;
    OfflineSpeech.Job job;
    final Runnable timeout = () -> { if (active() && server) fallback(); };

    Request (Pair<Long, Long> key, TdApi.Message message) {
      this.key = key;
      this.message = message;
    }

    boolean active () { return requests.get(key) == this && !owner.isDestroyed(); }

    void working (boolean value) {
      if (working == value) return;
      working = value;
      for (Listener listener : listeners) listener.onWorking(value);
    }

    void start (boolean alreadyPending) {
      working(true);
      UI.post(timeout, 120000);
      if (alreadyPending) {
        poll();
      } else if (!owner.tdlib().canTrySpeechRecognition(duration(message))) {
        fallback();
      } else {
        owner.tdlib().client().send(new TdApi.RecognizeSpeech(message.chatId, message.id), response -> UI.post(() -> {
          if (!active() || !server) return;
          if (response instanceof TdApi.Error) fallback();
          else poll();
        }));
      }
    }

    void poll () {
      if (!active() || !server) return;
      owner.tdlib().client().send(new TdApi.GetMessage(message.chatId, message.id), response -> UI.post(() -> {
        if (!active() || !server) return;
        if (!(response instanceof TdApi.Message)) {
          fallback();
          return;
        }
        TdApi.SpeechRecognitionResult result = recognitionResult((TdApi.Message) response);
        if (result instanceof TdApi.SpeechRecognitionResultText) {
          finish(((TdApi.SpeechRecognitionResultText) result).text, 0);
        } else if (result instanceof TdApi.SpeechRecognitionResultError) {
          fallback();
        } else {
          UI.post(this::poll, 1000);
        }
      }));
    }

    void fallback () {
      server = false;
      UI.removePendingRunnable(timeout);
      if (Build.VERSION.SDK_INT < 21) {
        finish(null, R.string.FrogramOfflineUnsupported);
      } else if (OfflineSpeech.hasModel()) {
        startLocal(false);
      } else if (!owner.isFocused()) {
        finish(null, 0);
      } else {
        working(false);
        MessageDialogStyle panel = new MessageDialogStyle(owner, Lang.getString(R.string.FrogramTranscribe), true);
        panel.add(panel.label(Lang.getString(R.string.FrogramOfflineDownload), 14, Theme.textDecentColor(), false), 8);
        panel.add(panel.button(Lang.getString(R.string.FrogramOfflineDownloadButton), Theme.getColor(ColorId.textLink), true, () -> {
          panel.setOnDismiss(null);
          panel.dismiss();
          if (active()) startLocal(true);
        }), 12);
        panel.setOnDismiss(() -> finish(null, 0));
        panel.show();
      }
    }

    void startLocal (boolean downloadModel) {
      working(true);
      TdApi.File file = message.content instanceof TdApi.MessageVoiceNote ?
        ((TdApi.MessageVoiceNote) message.content).voiceNote.voice : ((TdApi.MessageVideoNote) message.content).videoNote.video;
      if (file.local.isDownloadingCompleted) {
        recognize(file.local.path, downloadModel);
      } else {
        owner.tdlib().client().send(new TdApi.DownloadFile(file.id, 32, 0, 0, true), response -> UI.post(() -> {
          if (!active()) return;
          if (response instanceof TdApi.File && ((TdApi.File) response).local.isDownloadingCompleted) {
            recognize(((TdApi.File) response).local.path, downloadModel);
          } else {
            finish(null, R.string.FrogramOfflineFailed);
          }
        }));
      }
    }

    void recognize (String path, boolean downloadModel) {
      job = OfflineSpeech.transcribe(path, downloadModel, (value, failed) -> {
        if (active()) finish(failed ? null : value, failed ? R.string.FrogramOfflineFailed : 0);
      });
    }

    void finish (String text, int error) {
      if (requests.get(key) != this) return;
      requests.remove(key);
      UI.removePendingRunnable(timeout);
      if (job != null) job.cancel();
      working(false);
      if (owner.isDestroyed()) return;
      if (text != null && !text.trim().isEmpty()) {
        cacheText(message, text);
        for (Listener listener : listeners) listener.onResult(text);
      } else if (owner.isFocused() && (error != 0 || text != null)) {
        UI.showToast(error != 0 ? error : R.string.FrogramTranscriptionEmpty, android.widget.Toast.LENGTH_SHORT);
      }
    }
  }

  private static TdApi.SpeechRecognitionResult recognitionResult (TdApi.Message message) {
    if (message.content instanceof TdApi.MessageVoiceNote) return ((TdApi.MessageVoiceNote) message.content).voiceNote.speechRecognitionResult;
    if (message.content instanceof TdApi.MessageVideoNote) return ((TdApi.MessageVideoNote) message.content).videoNote.speechRecognitionResult;
    return null;
  }

  private static int duration (TdApi.Message message) {
    return message.content instanceof TdApi.MessageVoiceNote ? ((TdApi.MessageVoiceNote) message.content).voiceNote.duration :
      ((TdApi.MessageVideoNote) message.content).videoNote.duration;
  }
}
