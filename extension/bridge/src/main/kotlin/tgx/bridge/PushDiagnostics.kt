package tgx.bridge

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object PushDiagnostics {
  private const val PREFS_NAME = "frogram_push_diagnostics"
  private const val KEY_EVENTS = "events"
  private const val MAX_EVENTS = 64

  @Volatile
  private var appContext: Context? = null

  @JvmStatic
  fun initialize(context: Context) {
    appContext = context.applicationContext
  }

  @JvmStatic
  fun record(context: Context, event: String, details: String? = null) {
    initialize(context)
    record(event, details)
  }

  @JvmStatic
  @Synchronized
  fun record(event: String, details: String? = null) {
    val context = appContext ?: return
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    val previous = prefs.getString(KEY_EVENTS, "").orEmpty()
      .lineSequence()
      .filter { it.isNotBlank() }
      .toMutableList()

    val line = buildString {
      append(System.currentTimeMillis())
      append('\t')
      append(clean(event))
      append('\t')
      append(clean(details))
    }
    previous.add(line)
    val retained = previous.takeLast(MAX_EVENTS).joinToString("\n")
    prefs.edit().putString(KEY_EVENTS, retained).commit()
  }

  @JvmStatic
  @Synchronized
  fun clear() {
    val context = appContext ?: return
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
      .edit()
      .remove(KEY_EVENTS)
      .commit()
  }

  @JvmStatic
  @Synchronized
  fun summary(): String {
    val lines = eventLines()
    if (lines.isEmpty()) {
      return if (appContext == null) "Not initialized" else "No events yet"
    }
    val parts = lines.last().split('\t', limit = 3)
    val timestamp = parts.getOrNull(0)?.toLongOrNull() ?: 0L
    val event = parts.getOrNull(1).orEmpty()
    return "${lines.size} events • ${formatTime(timestamp)} • $event"
  }

  @JvmStatic
  @Synchronized
  fun report(): String {
    val lines = eventLines()
    return buildString {
      append("Stored push events: ")
      append(lines.size)
      append('\n')
      if (lines.isEmpty()) {
        append("No diagnostic events recorded yet.")
        return@buildString
      }
      append("Oldest/newest history is capped at ")
      append(MAX_EVENTS)
      append(" events.\n\n")
      lines.forEach { line ->
        val parts = line.split('\t', limit = 3)
        val timestamp = parts.getOrNull(0)?.toLongOrNull() ?: 0L
        append(formatTime(timestamp))
        append(" | ")
        append(parts.getOrNull(1).orEmpty())
        val details = parts.getOrNull(2).orEmpty()
        if (details.isNotEmpty()) {
          append(" | ")
          append(details)
        }
        append('\n')
      }
    }.trimEnd()
  }

  private fun eventLines(): List<String> {
    val context = appContext ?: return emptyList()
    return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
      .getString(KEY_EVENTS, "")
      .orEmpty()
      .lineSequence()
      .filter { it.isNotBlank() }
      .toList()
  }

  private fun formatTime(timestamp: Long): String {
    if (timestamp <= 0L) return "unknown time"
    return SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date(timestamp))
  }

  private fun clean(value: String?): String {
    if (value.isNullOrBlank()) return ""
    var result = value
      .replace('\n', ' ')
      .replace('\r', ' ')
      .replace(Regex("\\s+"), " ")
      .trim()
    result = result.replace(Regex("AIza[0-9A-Za-z_-]{20,}"), "AIza<redacted>")
    result = result.replace(Regex("\"[A-Za-z0-9_-]{20,}\""), "\"<redacted>\"")
    if (result.length > 320) {
      result = result.take(317) + "..."
    }
    return result
  }
}
