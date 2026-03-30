package com.testpipreactnative

import android.content.Intent
import android.content.pm.PackageManager
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import com.facebook.react.bridge.ReadableMap
import com.facebook.react.modules.core.DeviceEventManagerModule
import java.lang.ref.WeakReference

class VideoPlayerModule(
  reactContext: ReactApplicationContext,
) : ReactContextBaseJavaModule(reactContext) {

  init {
    reactContextReference = WeakReference(reactContext)
  }

  override fun getName(): String = "VideoPlayerModule"

  @ReactMethod
  fun openPlayer(streamMap: ReadableMap, promise: Promise) {
    try {
      val stream = VideoPlayerContract.fromReadableMap(streamMap)
      val activity = reactApplicationContext.currentActivity
      val intent = Intent(reactApplicationContext, VideoPlayerActivity::class.java).apply {
        addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        if (activity == null) {
          addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        VideoPlayerContract.writeToIntent(this, stream)
      }

      if (activity != null) {
        activity.startActivity(intent)
      } else {
        reactApplicationContext.startActivity(intent)
      }

      promise.resolve(true)
    } catch (error: Exception) {
      promise.reject("E_OPEN_PLAYER", error.message, error)
    }
  }

  @ReactMethod
  fun isPictureInPictureSupported(promise: Promise) {
    val isSupported =
      reactApplicationContext.packageManager.hasSystemFeature(
        PackageManager.FEATURE_PICTURE_IN_PICTURE,
      )

    promise.resolve(isSupported)
  }

  @ReactMethod
  fun addListener(eventName: String) = Unit

  @ReactMethod
  fun removeListeners(count: Double) = Unit

  companion object {
    private var reactContextReference: WeakReference<ReactApplicationContext>? = null

    fun emitStreamChanged(stream: NativeStreamDescriptor) {
      emitEvent(
        Arguments.createMap().apply {
          putString("type", "stream_changed")
          putString("streamId", stream.id)
          putString("title", stream.title)
        },
      )
    }

    fun emitPlayerState(
      state: String,
      isPlaying: Boolean,
      stream: NativeStreamDescriptor?,
    ) {
      emitEvent(
        Arguments.createMap().apply {
          putString("type", "player_state")
          putString("state", state)
          putBoolean("isPlaying", isPlaying)
          stream?.let {
            putString("streamId", it.id)
            putString("title", it.title)
          }
        },
      )
    }

    fun emitPictureInPictureChange(isInPictureInPicture: Boolean) {
      emitEvent(
        Arguments.createMap().apply {
          putString("type", "pip_change")
          putBoolean("isInPictureInPicture", isInPictureInPicture)
        },
      )
    }

    fun emitError(message: String) {
      emitEvent(
        Arguments.createMap().apply {
          putString("type", "error")
          putString("message", message)
        },
      )
    }

    fun emitActivityClosed(stream: NativeStreamDescriptor?) {
      emitEvent(
        Arguments.createMap().apply {
          putString("type", "activity_closed")
          stream?.let {
            putString("streamId", it.id)
            putString("title", it.title)
          }
        },
      )
    }

    private fun emitEvent(payload: com.facebook.react.bridge.WritableMap) {
      val reactContext = reactContextReference?.get() ?: return
      if (!reactContext.hasActiveReactInstance()) {
        return
      }

      try {
        reactContext
          .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java)
          .emit(VideoPlayerContract.EVENT_NAME, payload)
      } catch (_: RuntimeException) {
      }
    }
  }
}