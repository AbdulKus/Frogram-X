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
import org.thunderdog.challegram.telegram.AuthorizationListener;
import org.thunderdog.challegram.telegram.MessageRevisionStore;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.util.OfflineSpeech;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import me.vkryl.core.lambda.RunnableBool;

import tgx.td.ChatId;

final class MessageTranscription {
  interface Listener {
    void onWorking (boolean working);
    void onResult (String text);
  }

  private final MessagesController owner;
  private record TranscriptKey(int account, long userId, long chatId, long messageId) {}
  private record CachedTranscript(String text, long expires) {}
  // Requests and their UI subscriptions are confined to the main thread.
  private static final Map<TranscriptKey, Request> requests = new HashMap<>();
  private static final LruCache<TranscriptKey, CachedTranscript> recentTranscripts = new LruCache<>(128);

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
    return new TranscriptKey(owner.tdlib().id(), owner.tdlib().myUserId(true), message.chatId, message.id);
  }

  private static String cachedText (TranscriptKey key) {
    if (key.userId() == 0) return null;
    CachedTranscript cached = recentTranscripts.get(key);
    return cached != null && cached.expires() > System.currentTimeMillis() ? cached.text() : null;
  }

  private static boolean canPersist (TranscriptKey key, TdApi.Message message) {
    return key.userId() != 0 && message.canBeSaved && message.autoDeleteIn <= 0;
  }

  boolean isWorking (TdApi.Message message) {
    if (requests.isEmpty()) return false;
    Request request = requests.get(transcriptKey(message));
    return request != null && request.working;
  }

  String openedVoiceTranscript (TdApi.Message message) {
    Pair<Long, Long> key = Pair.create(message.chatId, message.id);
    return collapsed.get(key) == null ? cachedText(transcriptKey(message)) : null;
  }

  void setVoiceTranscriptExpanded (TdApi.Message message, boolean expanded) {
    Pair<Long, Long> key = Pair.create(message.chatId, message.id);
    if (expanded) collapsed.remove(key);
    else collapsed.put(key, true);
  }

  void observeVoice (TdApi.Message message, Listener listener) {
    if (!canTranscribe(message) || owner.isDestroyed()) return;
    TranscriptKey key = transcriptKey(message);
    Request existing = requests.get(key);
    if (existing != null) {
      existing.subscribe(this, listener);
      return;
    }
    String text = openedVoiceTranscript(message);
    if (text != null) {
      listener.onResult(text);
    } else if (canPersist(key, message)) {
      MessageRevisionStore.loadTranscript(key.account(), key.userId(), key.chatId(), key.messageId(), stored -> {
        if (!owner.isDestroyed() && owner.getChatId() == key.chatId() && transcriptKey(message).equals(key) &&
            collapsed.get(Pair.create(key.chatId(), key.messageId())) == null && !requests.containsKey(key) && stored != null) {
          listener.onResult(stored);
        }
      });
    }
  }

  void start (TdApi.Message message, Listener listener) {
    if (!canTranscribe(message) || owner.isDestroyed()) return;
    TranscriptKey key = transcriptKey(message);
    Request existing = requests.get(key);
    if (existing != null) {
      existing.subscribe(this, listener);
      return;
    }
    Request request = new Request(key, owner.tdlib(), message);
    requests.put(key, request);
    request.tdlib.incrementTranscriptionReferenceCount();
    request.tdlib.listeners().addAuthorizationChangeListener(request);
    request.subscribe(this, listener);
    request.loadOrStart();
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

  void detach () {
    for (Request request : requests.values()) request.listeners.remove(this);
  }

  private static final class Request implements AuthorizationListener {
    final TranscriptKey key;
    final Tdlib tdlib;
    final TdApi.Message message;
    final Map<MessageTranscription, Listener> listeners = new HashMap<>();
    boolean working;
    boolean server = true;
    OfflineSpeech.Job job;
    final Runnable timeout = () -> { if (active() && server) fallback(); };

    Request (TranscriptKey key, Tdlib tdlib, TdApi.Message message) {
      this.key = key;
      this.tdlib = tdlib;
      this.message = message;
    }

    boolean active () {
      if (requests.get(key) != this) return false;
      if (tdlib.isAuthorized() && tdlib.myUserId(true) == key.userId()) return true;
      finish(null, 0);
      return false;
    }

    @Override public void onAuthorizationStateChanged (TdApi.AuthorizationState state) {
      if (!(state instanceof TdApi.AuthorizationStateReady)) UI.post(() -> finish(null, 0));
    }

    void subscribe (MessageTranscription owner, Listener listener) {
      listeners.put(owner, listener);
      listener.onWorking(working);
    }

    void working (boolean value) {
      if (working == value) return;
      working = value;
      for (Listener listener : new ArrayList<>(listeners.values())) listener.onWorking(value);
    }

    void loadOrStart () {
      TdApi.SpeechRecognitionResult cached = recognitionResult(message);
      String text = cached instanceof TdApi.SpeechRecognitionResultText ?
        ((TdApi.SpeechRecognitionResultText) cached).text : cachedText(key);
      if (text != null && !text.trim().isEmpty()) {
        finish(text, 0, false);
        return;
      }
      working(true);
      if (canPersist(key, message)) {
        MessageRevisionStore.loadTranscript(key.account(), key.userId(), key.chatId(), key.messageId(), stored -> {
          if (!active()) return;
          if (stored != null) finish(stored, 0, false);
          else start(cached instanceof TdApi.SpeechRecognitionResultPending);
        });
      } else {
        start(cached instanceof TdApi.SpeechRecognitionResultPending);
      }
    }

    private MessagesController focusedOwner () {
      for (MessageTranscription observer : listeners.keySet()) {
        if (!observer.owner.isDestroyed() && observer.owner.isFocused() && observer.owner.getChatId() == key.chatId()) {
          return observer.owner;
        }
      }
      return null;
    }

    void start (boolean alreadyPending) {
      working(true);
      UI.post(timeout, 120000);
      if (alreadyPending) {
        poll();
      } else if (!tdlib.canTrySpeechRecognition(duration(message))) {
        fallback();
      } else {
        tdlib.client().send(new TdApi.RecognizeSpeech(message.chatId, message.id), response -> UI.post(() -> {
          if (!active() || !server) return;
          if (response instanceof TdApi.Error) fallback();
          else poll();
        }));
      }
    }

    void poll () {
      if (!active() || !server) return;
      tdlib.client().send(new TdApi.GetMessage(message.chatId, message.id), response -> UI.post(() -> {
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
      } else {
        MessagesController owner = focusedOwner();
        if (owner == null) {
          finish(null, 0);
          return;
        }
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
        tdlib.client().send(new TdApi.DownloadFile(file.id, 32, 0, 0, true), response -> UI.post(() -> {
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
      finish(text, error, true);
    }

    void finish (String text, int error, boolean save) {
      if (requests.get(key) != this) return;
      requests.remove(key);
      tdlib.listeners().removeAuthorizationChangeListener(this);
      tdlib.decrementTranscriptionReferenceCount();
      UI.removePendingRunnable(timeout);
      if (job != null) job.cancel();
      working(false);
      if (text != null && !text.trim().isEmpty()) {
        if (key.userId() != 0 && save) {
          recentTranscripts.put(key, new CachedTranscript(text, System.currentTimeMillis() + 7L * 86400000L));
          if (canPersist(key, message)) {
            MessageRevisionStore.saveTranscript(key.account(), key.userId(), key.chatId(), key.messageId(), text);
          }
        }
        for (Listener listener : new ArrayList<>(listeners.values())) listener.onResult(text);
      } else if (focusedOwner() != null && (error != 0 || text != null)) {
        UI.showToast(error != 0 ? error : R.string.FrogramTranscriptionEmpty, android.widget.Toast.LENGTH_SHORT);
      }
      listeners.clear();
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
