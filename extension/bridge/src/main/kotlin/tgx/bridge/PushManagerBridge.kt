package tgx.bridge

import android.app.Service
import android.content.Context
import kotlinx.coroutines.CoroutineScope
import org.drinkless.tdlib.TdApi.DeviceToken
import java.util.Locale

interface DeviceTokenRetrieverFactory {
  fun onCreateNewTokenRetriever(context: Context): DeviceTokenRetriever
}

interface PushManager {
  fun onNewToken (service: Service, token: DeviceToken)
  fun onMessageReceived (service: Service, message: Map<String, Any>, sentTime: Long, ttl: Int)

  fun log(format: String, vararg args: Any)
  fun error(message: String, error: Throwable?)
}

object PushManagerBridge {
  lateinit var applicationScope: CoroutineScope
  lateinit var manager: PushManager
  lateinit var deviceTokenRetrieverFactory: DeviceTokenRetrieverFactory

  @JvmStatic fun initialize (applicationScope: CoroutineScope, receiver: PushManager, deviceTokenRetrieverFactory: DeviceTokenRetrieverFactory) {
    this.applicationScope = applicationScope
    this.manager = receiver
    this.deviceTokenRetrieverFactory = deviceTokenRetrieverFactory
  }

  @JvmStatic fun onCreateNewTokenRetriever(context: Context): DeviceTokenRetriever {
    PushDiagnostics.initialize(context)
    val retriever = deviceTokenRetrieverFactory.onCreateNewTokenRetriever(context)
    val available = try {
      retriever.isAvailable(context)
    } catch (_: Throwable) {
      false
    }
    PushDiagnostics.record("token_retriever_created", "name=${retriever.name}, available=$available")
    return retriever
  }

  @JvmStatic fun onNewToken (service: Service, token: DeviceToken) {
    PushDiagnostics.record(service, "token_callback", "type=${token.javaClass.simpleName}")
    manager.onNewToken(service, token)
  }

  @JvmStatic fun onMessageReceived (service: Service, payload: Map<String, Any>, sentTime: Long, ttl: Int) {
    val delayMs = if (sentTime > 0L) (System.currentTimeMillis() - sentTime).coerceAtLeast(0L) else -1L
    PushDiagnostics.record(service, "push_received", "ttl=$ttl, delayMs=$delayMs, keys=${payload.size}")
    manager.onMessageReceived(service, payload, sentTime, ttl)
  }

  @JvmStatic fun log(format: String, vararg args: Any) {
    PushDiagnostics.record("push_log", safeFormat(format, args))
    manager.log(format, *args)
  }

  @JvmStatic fun error(format: String, t: Throwable?) {
    val detail = buildString {
      append(format)
      if (t != null) {
        append(" [")
        append(t.javaClass.simpleName)
        if (!t.message.isNullOrEmpty()) {
          append(": ")
          append(t.message)
        }
        append(']')
      }
    }
    PushDiagnostics.record("push_error", detail)
    manager.error(format, t)
  }

  private fun safeFormat(format: String, args: Array<out Any>): String {
    if (args.isEmpty()) return format
    return try {
      String.format(Locale.US, format, *args)
    } catch (_: Throwable) {
      "$format [${args.size} args]"
    }
  }
}
