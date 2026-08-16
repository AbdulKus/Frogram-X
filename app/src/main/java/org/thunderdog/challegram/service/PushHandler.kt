package org.thunderdog.challegram.service

import android.app.Service
import org.drinkless.tdlib.TdApi
import org.thunderdog.challegram.Log
import org.thunderdog.challegram.TDLib
import org.thunderdog.challegram.telegram.TdlibManager
import org.thunderdog.challegram.tool.UI
import org.thunderdog.challegram.unsorted.Settings
import tgx.bridge.PushDiagnostics
import tgx.bridge.PushManager
import tgx.td.stringify

class PushHandler : PushManager {
  override fun onNewToken(service: Service, token: TdApi.DeviceToken) {
    UI.initApp(service.applicationContext)
    log("onNewToken type=%s, sending to all accounts", token.javaClass.simpleName)
    TdlibManager.instance().runWithWakeLock { manager ->
      manager.setDeviceToken(token)
      PushDiagnostics.record("token_sent_to_tdlib_manager", "type=${token.javaClass.simpleName}")
    }
  }

  override fun onMessageReceived(service: Service, message: Map<String, Any>, sentTime: Long, ttl: Int) {
    UI.initApp(service.applicationContext)
    val pushId = Settings.instance().newPushId()

    PushDiagnostics.record(service, "push_processor_start", "pushId=$pushId, ttl=$ttl")
    val payload = stringify(message)

    val pushProcessor = PushProcessor(service)
    pushProcessor.processPush(pushId, payload, sentTime, ttl)
  }

  override fun log(format: String, vararg args: Any) =
    TDLib.Tag.notifications(format, args)

  override fun error(message: String, error: Throwable?) {
    TDLib.Tag.notifications("$message: ${Log.toString(error)}")
  }
}
