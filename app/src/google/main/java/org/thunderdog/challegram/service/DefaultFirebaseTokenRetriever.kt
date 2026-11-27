package org.thunderdog.challegram.service

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import org.thunderdog.challegram.U
import tgx.bridge.DeviceTokenRetriever
import tgx.bridge.PushManagerBridge
import java.util.regex.Pattern

abstract class DefaultFirebaseTokenRetriever : DeviceTokenRetriever("firebase") {
  override val configuration: String
    get() = try {
      val options = FirebaseApp.getInstance().options
      // Deliberately exclude API keys, registration tokens and installation IDs.
      "project=${options.projectId}, sender=${options.gcmSenderId}, " +
        "app=${options.applicationId}, autoInit=${FirebaseMessaging.getInstance().isAutoInitEnabled}"
    } catch (_: IllegalStateException) {
      "Firebase is not initialized"
    }

  override fun isAvailable(context: Context): Boolean =
    U.isGooglePlayServicesAvailable(context)

  override fun performInitialization(context: Context): Boolean {
    try {
      PushManagerBridge.log("FirebaseApp is initializing...")
      if (FirebaseApp.initializeApp(context) != null) {
        // BaseApplication disables this persisted setting for alternative push providers.
        // Restore it when Firebase is selected again, including after Play Services returns.
        FirebaseMessaging.getInstance().isAutoInitEnabled = true
        PushManagerBridge.log("FirebaseApp initialization finished successfully")
        PushManagerBridge.log("Firebase client configuration: %s", configuration)
        return true
      } else {
        PushManagerBridge.log("FirebaseApp initialization failed")
      }
    } catch (e: Throwable) {
      PushManagerBridge.error("FirebaseApp initialization failed with error", e)
    }
    return false
  }

  companion object {
    fun extractFirebaseErrorName(e: Throwable): String {
      val message = e.message
      if (!message.isNullOrEmpty()) {
        val matcher = Pattern.compile("(?<=: )[A-Z_]+$").matcher(message)
        if (matcher.find()) {
          return matcher.group()
        }
        return message
      }
      return e.javaClass.getSimpleName()
    }
  }
}
