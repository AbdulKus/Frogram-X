package org.thunderdog.challegram.telegram;

import android.content.ContentValues;
import android.content.ContentResolver;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.net.Uri;
import android.util.JsonReader;
import android.util.JsonWriter;
import android.widget.Toast;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.tool.UI;

import java.io.FilterReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

/** Account-local additions to Telegram's saved animations. Stable IDs, not TDLib session file IDs, go on disk. */
public final class SavedGifLibrary {
  private static final ExecutorService IO = Executors.newSingleThreadExecutor();
  private static SQLiteOpenHelper helper;
  private static final int ARCHIVE_VERSION = 1;
  private static final int MAX_ARCHIVE_CHARS = 32 * 1024 * 1024;
  private static final int MAX_FIELD_LENGTH = 4096;
  private static final java.util.regex.Pattern FILE_ID = java.util.regex.Pattern.compile("[A-Za-z0-9_=-]+");
  private final Tdlib tdlib;
  private final ArrayDeque<Runnable> changes = new ArrayDeque<>();
  private boolean archiveQueued, archiveDirty;

  private enum WriteMode { SAVE, IMPORT, ARCHIVE, ENABLE_ARCHIVE }

  SavedGifLibrary (Tdlib tdlib) { this.tdlib = tdlib; }

  private record Entry(String unique, String remote, String mime, String name, int width, int height, int duration) {
    static Entry from (TdApi.Animation animation) {
      if (animation == null || animation.animation == null || animation.animation.remote == null) return null;
      TdApi.RemoteFile remote = animation.animation.remote;
      if (!validId(remote.uniqueId) || !validId(remote.id)) return null;
      return new Entry(remote.uniqueId, remote.id, animation.mimeType, animation.fileName,
        animation.width, animation.height, animation.duration);
    }

    TdApi.Animation toAnimation (TdApi.File file) {
      return new TdApi.Animation(duration, width, height, name, mime, false, null, null, file);
    }
  }

  private static boolean validId (String value) {
    return value != null && !value.isEmpty() && value.length() <= MAX_FIELD_LENGTH && FILE_ID.matcher(value).matches();
  }

  private static SQLiteDatabase database () {
    if (helper == null) {
      helper = new SQLiteOpenHelper(UI.getAppContext(), "saved-gifs.db", null, 1) {
        @Override public void onCreate (SQLiteDatabase db) {
          db.execSQL("CREATE TABLE gifs (account INTEGER NOT NULL, uid TEXT NOT NULL, remote TEXT NOT NULL, mime TEXT NOT NULL, name TEXT NOT NULL, width INTEGER NOT NULL, height INTEGER NOT NULL, duration INTEGER NOT NULL, PRIMARY KEY(account,uid))");
          db.execSQL("CREATE TABLE gif_archive_accounts (account INTEGER PRIMARY KEY)");
        }
        @Override public void onUpgrade (SQLiteDatabase db, int oldVersion, int newVersion) { }
      };
    }
    return helper.getWritableDatabase();
  }

  private long user () { return tdlib.myUserId(true); }
  private boolean isCurrent (long account) { return account != 0 && tdlib.isAuthorized() && user() == account; }

  private static List<Entry> read (long account) {
    List<Entry> entries = new ArrayList<>();
    try (Cursor cursor = database().rawQuery("SELECT uid, remote, mime, name, width, height, duration FROM gifs WHERE account=? ORDER BY rowid DESC",
        new String[] {Long.toString(account)})) {
      while (cursor.moveToNext()) {
        entries.add(new Entry(cursor.getString(0), cursor.getString(1), cursor.getString(2), cursor.getString(3),
          cursor.getInt(4), cursor.getInt(5), cursor.getInt(6)));
      }
    }
    return entries;
  }

  private static int put (long account, List<Entry> entries, WriteMode mode) {
    SQLiteDatabase db = database();
    int added = 0;
    db.beginTransaction();
    try {
      for (int index = entries.size() - 1; index >= 0; index--) {
        Entry entry = entries.get(index);
        ContentValues values = new ContentValues();
        values.put("account", account);
        values.put("uid", entry.unique());
        values.put("remote", entry.remote());
        values.put("mime", entry.mime());
        values.put("name", entry.name());
        values.put("width", entry.width());
        values.put("height", entry.height());
        values.put("duration", entry.duration());
        if (db.insertWithOnConflict("gifs", null, values, mode == WriteMode.SAVE ? SQLiteDatabase.CONFLICT_REPLACE : SQLiteDatabase.CONFLICT_IGNORE) != -1) {
          added++;
        } else if (mode == WriteMode.ARCHIVE || mode == WriteMode.ENABLE_ARCHIVE) {
          db.update("gifs", values, "account=? AND uid=? AND remote<>?",
            new String[] {Long.toString(account), entry.unique(), entry.remote()});
        }
      }
      if (mode == WriteMode.ENABLE_ARCHIVE) {
        db.execSQL("INSERT OR IGNORE INTO gif_archive_accounts(account) VALUES (?)", new Object[] {account});
      }
      db.setTransactionSuccessful();
    } finally {
      db.endTransaction();
    }
    return added;
  }

  private void cloud (Consumer<TdApi.Animation[]> callback) {
    tdlib.client().send(new TdApi.GetSavedAnimations(), result -> UI.post(() ->
      callback.accept(result instanceof TdApi.Animations ? ((TdApi.Animations) result).animations : null)));
  }

  private static String unique (TdApi.Animation animation) {
    return animation.animation.remote != null ? animation.animation.remote.uniqueId : "";
  }

  private void resolve (List<Entry> entries, Consumer<TdApi.Animation[]> callback) {
    TdApi.Animation[] resolved = new TdApi.Animation[entries.size()];
    if (entries.isEmpty()) {
      callback.accept(resolved);
      return;
    }
    // Limit the number of concurrent TDLib lookups when restoring large collections.
    AtomicInteger next = new AtomicInteger();
    AtomicInteger remaining = new AtomicInteger(entries.size());
    Runnable lookup = new Runnable() {
      @Override public void run () {
        int index = next.getAndIncrement();
        if (index >= entries.size()) return;
        Entry entry = entries.get(index);
        tdlib.client().send(new TdApi.GetRemoteFile(entry.remote(), new TdApi.FileTypeAnimation()), result -> {
          if (result instanceof TdApi.File) {
            TdApi.File file = (TdApi.File) result;
            if (file.remote != null && entry.unique().equals(file.remote.uniqueId)) resolved[index] = entry.toAnimation(file);
          }
          if (remaining.decrementAndGet() == 0) UI.post(() -> callback.accept(resolved));
          else run();
        });
      }
    };
    for (int n = 0; n < Math.min(entries.size(), 16); n++) lookup.run();
  }

  public void load (Consumer<List<TdApi.Animation>> callback) {
    long account = user();
    cloud(cloud -> IO.execute(() -> {
      List<Entry> local;
      try { local = read(account); }
      catch (RuntimeException error) { local = List.of(); }
      List<Entry> entries = local;
      UI.post(() -> {
        if (!isCurrent(account)) { callback.accept(List.of()); return; }
        LinkedHashMap<String, TdApi.Animation> remote = new LinkedHashMap<>();
        if (cloud != null) {
          for (TdApi.Animation animation : cloud) remote.putIfAbsent(animationKey(animation), animation);
        }
        List<Entry> localOnly = new ArrayList<>();
        for (Entry entry : entries) {
          if (!remote.containsKey(entry.unique())) localOnly.add(entry);
        }
        resolve(localOnly, resolved -> {
          if (!isCurrent(account)) { callback.accept(List.of()); return; }
          LinkedHashMap<String, TdApi.Animation> resolvedLocal = new LinkedHashMap<>();
          for (TdApi.Animation animation : resolved) {
            if (animation != null) resolvedLocal.putIfAbsent(animationKey(animation), animation);
          }
          List<TdApi.Animation> merged = new ArrayList<>(entries.size() + remote.size());
          for (Entry entry : entries) {
            TdApi.Animation animation = remote.remove(entry.unique());
            if (animation == null) animation = resolvedLocal.get(entry.unique());
            if (animation != null) merged.add(animation);
          }
          merged.addAll(remote.values());
          callback.accept(merged);
        });
      });
    }));
  }

  private static String animationKey (TdApi.Animation animation) {
    String id = unique(animation);
    return id != null && !id.isEmpty() ? id : "#" + animation.animation.id;
  }

  private void changed () {
    tdlib.listeners().updateSavedAnimations(new TdApi.UpdateSavedAnimations(new int[0]));
  }

  private void enqueue (Runnable change) {
    changes.add(change);
    if (changes.size() == 1) change.run();
  }

  private void advance () {
    changes.removeFirst();
    Runnable next = changes.peekFirst();
    if (next != null) next.run();
  }

  void refreshCloudArchive () {
    long account = user();
    if (!isCurrent(account)) return;
    if (archiveQueued) {
      archiveDirty = true;
      return;
    }
    archiveQueued = true;
    enqueue(() -> IO.execute(() -> {
      boolean enabled;
      try (Cursor cursor = database().rawQuery("SELECT 1 FROM gif_archive_accounts WHERE account=?",
          new String[] {Long.toString(account)})) {
        enabled = cursor.moveToFirst();
      } catch (RuntimeException error) { enabled = false; }
      boolean shouldArchive = enabled;
      UI.post(() -> {
        if (shouldArchive && isCurrent(account)) {
          snapshotCloud(account, WriteMode.ARCHIVE, added -> finishArchive(added > 0));
        } else {
          finishArchive(false);
        }
      });
    }));
  }

  public void archiveCurrent () {
    long account = user();
    if (!isCurrent(account)) return;
    enqueue(() -> snapshotCloud(account, WriteMode.ENABLE_ARCHIVE, added -> {
      if (isCurrent(account)) {
        if (added >= 0) {
          if (added > 0) changed();
          UI.showToast(Lang.getString(R.string.FrogramGifArchived, added), Toast.LENGTH_LONG);
        } else {
          UI.showToast(R.string.FrogramGifBackupFailed, Toast.LENGTH_SHORT);
        }
      }
      advance();
    }));
  }

  private void snapshotCloud (long account, WriteMode mode, IntConsumer done) {
    cloud(animations -> {
      if (animations == null || !isCurrent(account)) {
        done.accept(-1);
        return;
      }
      List<Entry> entries = new ArrayList<>(animations.length);
      for (TdApi.Animation animation : animations) {
        Entry entry = Entry.from(animation);
        if (entry != null) entries.add(entry);
      }
      IO.execute(() -> {
        int count;
        try { count = put(account, entries, mode); }
        catch (RuntimeException error) { count = -1; }
        int added = count;
        UI.post(() -> done.accept(added));
      });
    });
  }

  private void finishArchive (boolean notify) {
    boolean repeat = archiveDirty;
    archiveDirty = archiveQueued = false;
    if (notify) changed();
    advance();
    if (repeat) refreshCloudArchive();
  }

  public void save (List<TdApi.Animation> animations) {
    long account = user();
    List<Entry> entries = new ArrayList<>();
    LinkedHashMap<String, TdApi.Animation> candidates = new LinkedHashMap<>();
    for (TdApi.Animation animation : animations) {
      Entry entry = Entry.from(animation);
      if (entry != null && candidates.putIfAbsent(entry.unique(), animation) == null) entries.add(entry);
    }
    if (entries.isEmpty() || !isCurrent(account)) {
      UI.showToast(R.string.FrogramGifSaveFailed, Toast.LENGTH_SHORT);
      return;
    }
    enqueue(() -> IO.execute(() -> {
      boolean saved;
      try { put(account, entries, WriteMode.SAVE); saved = true; }
      catch (RuntimeException error) { saved = false; }
      boolean success = saved;
      UI.post(() -> {
        if (!success || !isCurrent(account)) {
          if (!success) UI.showToast(R.string.FrogramGifSaveFailed, Toast.LENGTH_SHORT);
          advance();
          return;
        }
        changed();
        UI.showToast(entries.size() == 1 ? Lang.getString(R.string.GifSaved) :
          Lang.pluralBold(R.string.XGifSaved, entries.size()), Toast.LENGTH_SHORT);
        cloud(cloud -> tdlib.client().send(new TdApi.GetOption("saved_animations_limit"), option -> UI.post(() -> {
          if (!isCurrent(account) || cloud == null || !(option instanceof TdApi.OptionValueInteger)) {
            advance();
            return;
          }
          Set<String> ids = new HashSet<>();
          for (TdApi.Animation animation : cloud) ids.add(animationKey(animation));
          long freeSlots = Math.max(0, ((TdApi.OptionValueInteger) option).value - cloud.length);
          List<TdApi.Animation> toSync = new ArrayList<>();
          for (TdApi.Animation candidate : candidates.values()) {
            if ("video/mp4".equals(candidate.mimeType) && !ids.contains(animationKey(candidate)) && freeSlots > 0) {
              toSync.add(candidate);
              freeSlots--;
            }
          }
          sync(account, toSync, 0);
        })));
      });
    }));
  }

  private void sync (long account, List<TdApi.Animation> animations, int index) {
    if (index >= animations.size() || !isCurrent(account)) { advance(); return; }
    TdApi.Animation animation = animations.get(index);
    tdlib.client().send(new TdApi.AddSavedAnimation(new TdApi.InputFileId(animation.animation.id)), ignored ->
      UI.post(() -> sync(account, animations, index + 1)));
  }

  public void remove (TdApi.Animation animation) {
    long account = user();
    enqueue(() -> cloud(cloud -> {
      if (!isCurrent(account)) { advance(); return; }
      if (cloud == null) {
        UI.showToast(R.string.FrogramGifRemoveFailed, Toast.LENGTH_SHORT);
        advance();
        return;
      }
      for (TdApi.Animation remote : cloud) {
        if (animationKey(animation).equals(animationKey(remote))) {
          tdlib.client().send(new TdApi.RemoveSavedAnimation(new TdApi.InputFileId(remote.animation.id)), result -> UI.post(() -> {
            if (result instanceof TdApi.Ok) removeLocal(account, unique(animation));
            else { UI.showError(result); advance(); }
          }));
          return;
        }
      }
      removeLocal(account, unique(animation));
    }));
  }

  private void removeLocal (long account, String id) {
    IO.execute(() -> {
      boolean deleted;
      try {
        database().delete("gifs", "account=? AND uid=?", new String[] {Long.toString(account), id != null ? id : ""});
        deleted = true;
      } catch (RuntimeException error) { deleted = false; }
      boolean success = deleted;
      UI.post(() -> {
        if (isCurrent(account)) {
          if (success) changed();
          else UI.showToast(R.string.FrogramGifRemoveFailed, Toast.LENGTH_SHORT);
        }
        advance();
      });
    });
  }

  public void exportTo (Uri uri) {
    long account = user();
    if (!isCurrent(account)) return;
    cloud(cloud -> IO.execute(() -> {
      try {
        LinkedHashMap<String, Entry> entries = new LinkedHashMap<>();
        for (Entry entry : read(account)) entries.put(entry.unique(), entry);
        if (cloud == null) throw new IOException("Saved animations unavailable");
        for (TdApi.Animation animation : cloud) {
          Entry entry = Entry.from(animation);
          if (entry != null) entries.put(entry.unique(), entry);
        }
        try (OutputStream out = resolver().openOutputStream(uri, "wt")) {
          if (out == null) throw new IOException("Backup is not writable");
          try (JsonWriter writer = new JsonWriter(new OutputStreamWriter(out, StandardCharsets.UTF_8))) {
            writer.beginArray().value(ARCHIVE_VERSION).value(account);
            for (Entry entry : entries.values()) {
              writer.beginArray().value(entry.unique()).value(entry.remote()).value(entry.mime()).value(entry.name())
                .value(entry.width()).value(entry.height()).value(entry.duration()).endArray();
            }
            writer.endArray();
          }
        }
        UI.post(() -> UI.showToast(R.string.FrogramGifExported, Toast.LENGTH_SHORT));
      } catch (IOException | RuntimeException error) {
        UI.post(() -> UI.showToast(R.string.FrogramGifBackupFailed, Toast.LENGTH_SHORT));
      }
    }));
  }

  public void importFrom (Uri uri) {
    long account = user();
    if (!isCurrent(account)) return;
    IO.execute(() -> {
      try {
        List<Entry> entries = readBackup(uri, account);
        UI.post(() -> validateImport(account, entries));
      } catch (IOException | RuntimeException error) {
        UI.post(() -> UI.showToast(R.string.FrogramGifBackupFailed, Toast.LENGTH_SHORT));
      }
    });
  }

  private void validateImport (long account, List<Entry> entries) {
    if (!isCurrent(account)) return;
    resolve(entries, resolved -> {
      if (!isCurrent(account)) return;
      List<Entry> valid = new ArrayList<>();
      for (int index = 0; index < resolved.length; index++) {
        if (resolved[index] != null) valid.add(entries.get(index));
      }
      enqueue(() -> IO.execute(() -> {
        try {
          int imported = put(account, valid, WriteMode.IMPORT);
          UI.post(() -> {
            if (isCurrent(account)) {
              changed();
              UI.showToast(Lang.getString(R.string.FrogramGifImported, imported, entries.size() - valid.size()), Toast.LENGTH_LONG);
            }
            advance();
          });
        } catch (RuntimeException error) {
          UI.post(() -> { UI.showToast(R.string.FrogramGifBackupFailed, Toast.LENGTH_SHORT); advance(); });
        }
      }));
    });
  }

  private static List<Entry> readBackup (Uri uri, long account) throws IOException {
    LinkedHashMap<String, Entry> entries = new LinkedHashMap<>();
    try (InputStream in = resolver().openInputStream(uri)) {
      if (in == null) throw new IOException("Backup is not readable");
      try (JsonReader reader = new JsonReader(new FilterReader(new InputStreamReader(in, StandardCharsets.UTF_8)) {
        private int read;
        @Override public int read (char[] buffer, int offset, int length) throws IOException {
          int count = super.read(buffer, offset, length);
          if (count > 0 && (read += count) > MAX_ARCHIVE_CHARS) throw new IOException("GIF backup is too large");
          return count;
        }
      })) {
        reader.beginArray();
        if (reader.nextInt() != ARCHIVE_VERSION || reader.nextLong() != account) throw new IOException("Incompatible GIF backup");
        while (reader.hasNext()) {
          reader.beginArray();
          String unique = reader.nextString();
          String remote = reader.nextString();
          String mime = reader.nextString();
          String name = reader.nextString();
          int width = reader.nextInt();
          int height = reader.nextInt();
          int duration = reader.nextInt();
          reader.endArray();
          if (!validId(unique) || !validId(remote) || !("video/mp4".equals(mime) || "image/gif".equals(mime)) ||
              name.length() > MAX_FIELD_LENGTH || width < 0 || width > 16384 || height < 0 || height > 16384 || duration < 0) {
            throw new IOException("Invalid GIF metadata");
          }
          entries.putIfAbsent(unique, new Entry(unique, remote, mime, name, width, height, duration));
        }
        reader.endArray();
        if (reader.peek() != android.util.JsonToken.END_DOCUMENT) throw new IOException("Unexpected data after backup");
      }
    }
    return new ArrayList<>(entries.values());
  }

  private static ContentResolver resolver () { return UI.getAppContext().getContentResolver(); }
}
