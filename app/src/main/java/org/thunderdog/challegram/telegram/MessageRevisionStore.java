package org.thunderdog.challegram.telegram;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.tool.UI;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import tgx.td.ChatId;
import tgx.td.Td;

/** Local observations, never an assertion that Telegram supplied a complete edit history. */
public final class MessageRevisionStore {
  private static final ExecutorService IO = Executors.newSingleThreadExecutor();
  private static SQLiteOpenHelper helper;
  private static long lastPruned;
  private static long lastTranscriptPrune;
  private static final long TRANSCRIPT_TTL = 7L * 86400000L;

  private static SQLiteDatabase database () {
    if (helper == null) {
      helper = new SQLiteOpenHelper(UI.getAppContext(), "message-revisions.db", null, 2) {
        @Override public void onCreate (SQLiteDatabase db) {
          db.execSQL("CREATE TABLE revisions (sequence INTEGER PRIMARY KEY AUTOINCREMENT, account INTEGER NOT NULL, chat INTEGER NOT NULL, message INTEGER NOT NULL, observed INTEGER NOT NULL, text TEXT NOT NULL)");
          db.execSQL("CREATE INDEX revisions_message ON revisions(account, chat, message, sequence)");
          createTranscripts(db);
        }
        @Override public void onUpgrade (SQLiteDatabase db, int oldVersion, int newVersion) {
          if (oldVersion < 2) createTranscripts(db);
        }
      };
    }
    return helper.getWritableDatabase();
  }

  private static void createTranscripts (SQLiteDatabase db) {
    db.execSQL("CREATE TABLE transcripts (account INTEGER NOT NULL, user INTEGER NOT NULL, chat INTEGER NOT NULL, message INTEGER NOT NULL, observed INTEGER NOT NULL, text TEXT NOT NULL, PRIMARY KEY(account, user, chat, message))");
    db.execSQL("CREATE INDEX transcripts_age ON transcripts(observed)");
  }

  private static void pruneTranscripts (SQLiteDatabase db, long now) {
    if (now < lastTranscriptPrune || now - lastTranscriptPrune >= 3600000L) {
      db.delete("transcripts", "observed < ?", new String[] {Long.toString(now - TRANSCRIPT_TTL)});
      lastTranscriptPrune = now;
    }
  }

  public static void loadTranscript (int account, long user, long chat, long message, java.util.function.Consumer<String> callback) {
    IO.execute(() -> {
      String text = null;
      try {
        SQLiteDatabase db = database();
        long now = System.currentTimeMillis();
        pruneTranscripts(db, now);
        try (Cursor cursor = db.query("transcripts", new String[] {"text"},
            "account=? AND user=? AND chat=? AND message=? AND observed>=?",
            new String[] {Integer.toString(account), Long.toString(user), Long.toString(chat), Long.toString(message), Long.toString(now - TRANSCRIPT_TTL)},
            null, null, null)) {
          if (cursor.moveToFirst()) text = cursor.getString(0);
        }
      } catch (RuntimeException ignored) { }
      String result = text;
      UI.post(() -> callback.accept(result));
    });
  }

  public static void saveTranscript (int account, long user, long chat, long message, String text) {
    IO.execute(() -> {
      try {
        SQLiteDatabase db = database();
        long now = System.currentTimeMillis();
        pruneTranscripts(db, now);
        ContentValues values = new ContentValues();
        values.put("account", account);
        values.put("user", user);
        values.put("chat", chat);
        values.put("message", message);
        values.put("observed", now);
        values.put("text", text);
        db.replaceOrThrow("transcripts", null, values);
      } catch (RuntimeException ignored) { }
    });
  }

  public static void observe (int account, TdApi.Message message) {
    if (!canObserve(message)) return;
    observe(account, message.chatId, message.id, message.content, false);
  }

  private static boolean canObserve (TdApi.Message message) {
    return message != null && message.id > 0 && message.sendingState == null && message.canBeSaved &&
      message.selfDestructType == null && message.autoDeleteIn <= 0 && !ChatId.isSecret(message.chatId) &&
      Td.textOrCaption(message.content) != null;
  }

  public static void hasHistory (int account, TdApi.Message message, me.vkryl.core.lambda.RunnableBool callback) {
    if (!canObserve(message)) {
      callback.runWithBool(false);
      return;
    }
    String[] args = {Integer.toString(account), Long.toString(message.chatId), Long.toString(message.id),
      Long.toString(System.currentTimeMillis() - 30L * 86400000L)};
    IO.execute(() -> {
      boolean found = false;
      try (Cursor cursor = database().rawQuery(
          "SELECT 1 FROM revisions WHERE account=? AND chat=? AND message=? AND observed>=? LIMIT 1 OFFSET 1", args)) {
        found = cursor.moveToFirst();
      } catch (RuntimeException ignored) { }
      boolean result = found;
      UI.post(() -> callback.runWithBool(result));
    });
  }

  public static void observeChange (int account, long chat, long message, TdApi.MessageContent content) {
    observe(account, chat, message, content, true);
  }

  private static void observe (int account, long chat, long message, TdApi.MessageContent content, boolean requireBaseline) {
    if (message <= 0 || ChatId.isSecret(chat)) return;
    TdApi.FormattedText formatted = Td.textOrCaption(content);
    if (formatted == null) return;
    final String text = formatted.text;
    final long observed = System.currentTimeMillis();
    IO.execute(() -> {
      try {
        SQLiteDatabase db = database();
        try (Cursor cursor = db.rawQuery("SELECT text FROM revisions WHERE account=? AND chat=? AND message=? ORDER BY sequence DESC LIMIT 1", keys(account, chat, message))) {
          boolean exists = cursor.moveToFirst();
          // A rendered message can be a stale search/reply/cache snapshot. It may
          // establish a baseline, but only TDLib updates may append revisions.
          if ((!exists && requireBaseline) || (exists && !requireBaseline) || (exists && text.equals(cursor.getString(0)))) return;
        }
        ContentValues values = new ContentValues();
        values.put("account", account);
        values.put("chat", chat);
        values.put("message", message);
        values.put("observed", observed);
        values.put("text", text);
        db.insertOrThrow("revisions", null, values);
        db.execSQL("DELETE FROM revisions WHERE sequence <= COALESCE((SELECT sequence FROM revisions ORDER BY sequence DESC LIMIT 1 OFFSET 10000), 0)");
        // Bound disk use and retention, including messages no longer visited.
        if (observed - lastPruned > 60000) {
          db.delete("revisions", "observed < ?", new String[] {Long.toString(observed - 30L * 86400000L)});
          lastPruned = observed;
        }
      } catch (RuntimeException ignored) {
        // History must never interrupt delivery or rendering.
      }
    });
  }

  public interface Callback { void onLoaded (List<Revision> revisions); }

  public static void clearAccount (int account) {
    IO.execute(() -> {
      try {
        SQLiteDatabase db = database();
        String[] args = {Integer.toString(account)};
        db.delete("revisions", "account=?", args);
        db.delete("transcripts", "account=?", args);
      }
      catch (RuntimeException ignored) { }
    });
  }

  public static void clearChat (int account, long chat) {
    IO.execute(() -> {
      try {
        SQLiteDatabase db = database();
        String[] args = {Integer.toString(account), Long.toString(chat)};
        db.delete("revisions", "account=? AND chat=?", args);
        db.delete("transcripts", "account=? AND chat=?", args);
      }
      catch (RuntimeException ignored) { }
    });
  }

  public static void deleteMessages (int account, long chat, long[] messageIds) {
    final long[] ids = messageIds.clone();
    IO.execute(() -> {
      try {
        SQLiteDatabase db = database();
        for (long message : ids) {
          String[] args = keys(account, chat, message);
          db.delete("revisions", "account=? AND chat=? AND message=?", args);
          db.delete("transcripts", "account=? AND chat=? AND message=?", args);
        }
      } catch (RuntimeException ignored) { }
    });
  }

  public static final class Revision {
    public final long observed;
    public final String text;
    Revision (long observed, String text) { this.observed = observed; this.text = text; }
  }

  public static void load (int account, long chat, long message, Callback callback) {
    IO.execute(() -> {
      List<Revision> revisions = new ArrayList<>();
      try {
        database().delete("revisions", "observed < ?", new String[] {Long.toString(System.currentTimeMillis() - 30L * 86400000L)});
      } catch (RuntimeException ignored) { }
      try (Cursor cursor = database().rawQuery("SELECT observed, text FROM revisions WHERE account=? AND chat=? AND message=? ORDER BY sequence DESC LIMIT 100", keys(account, chat, message))) {
        while (cursor.moveToNext()) revisions.add(new Revision(cursor.getLong(0), cursor.getString(1)));
      } catch (RuntimeException ignored) { }
      UI.post(() -> callback.onLoaded(revisions));
    });
  }

  private static String[] keys (int account, long chat, long message) {
    return new String[] {Integer.toString(account), Long.toString(chat), Long.toString(message)};
  }
}
