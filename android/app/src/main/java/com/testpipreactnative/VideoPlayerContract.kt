package com.testpipreactnative

import android.content.Intent
import android.os.Bundle
import com.facebook.react.bridge.ReadableMap
import com.facebook.react.bridge.ReadableType

data class NativeStreamDescriptor(
  val id: String,
  val title: String,
  val url: String,
  val mimeType: String,
  val drmScheme: String? = null,
  val drmLicenseUrl: String? = null,
  val headers: Map<String, String> = emptyMap(),
)

object VideoPlayerContract {
  const val EVENT_NAME = "VideoPlayerEvent"

  private const val EXTRA_STREAM_ID = "com.testpipreactnative.extra.STREAM_ID"
  private const val EXTRA_STREAM_TITLE = "com.testpipreactnative.extra.STREAM_TITLE"
  private const val EXTRA_STREAM_URL = "com.testpipreactnative.extra.STREAM_URL"
  private const val EXTRA_STREAM_MIME_TYPE = "com.testpipreactnative.extra.STREAM_MIME_TYPE"
  private const val EXTRA_DRM_SCHEME = "com.testpipreactnative.extra.DRM_SCHEME"
  private const val EXTRA_DRM_LICENSE_URL = "com.testpipreactnative.extra.DRM_LICENSE_URL"
  private const val EXTRA_HEADERS = "com.testpipreactnative.extra.HEADERS"

  fun fromReadableMap(map: ReadableMap): NativeStreamDescriptor {
    val id = requireString(map, "id")
    val title = requireString(map, "title")
    val url = requireString(map, "url")
    val mimeType = optionalString(map, "mimeType") ?: "application/x-mpegURL"
    val drmScheme = optionalString(map, "drmScheme")
    val drmLicenseUrl = optionalString(map, "drmLicenseUrl")

    val headers = if (
      map.hasKey("headers") &&
      !map.isNull("headers") &&
      map.getType("headers") == ReadableType.Map
    ) {
      readHeaders(map.getMap("headers"))
    } else {
      emptyMap()
    }

    return NativeStreamDescriptor(
      id = id,
      title = title,
      url = url,
      mimeType = mimeType,
      drmScheme = drmScheme,
      drmLicenseUrl = drmLicenseUrl,
      headers = headers,
    )
  }

  fun writeToIntent(intent: Intent, stream: NativeStreamDescriptor) {
    intent.putExtra(EXTRA_STREAM_ID, stream.id)
    intent.putExtra(EXTRA_STREAM_TITLE, stream.title)
    intent.putExtra(EXTRA_STREAM_URL, stream.url)
    intent.putExtra(EXTRA_STREAM_MIME_TYPE, stream.mimeType)
    intent.putExtra(EXTRA_DRM_SCHEME, stream.drmScheme)
    intent.putExtra(EXTRA_DRM_LICENSE_URL, stream.drmLicenseUrl)

    if (stream.headers.isNotEmpty()) {
      val headersBundle = Bundle()
      stream.headers.forEach { (key, value) ->
        headersBundle.putString(key, value)
      }
      intent.putExtra(EXTRA_HEADERS, headersBundle)
    }
  }

  fun fromIntent(intent: Intent?): NativeStreamDescriptor? {
    if (intent == null) {
      return null
    }

    val id = intent.getStringExtra(EXTRA_STREAM_ID) ?: return null
    val title = intent.getStringExtra(EXTRA_STREAM_TITLE) ?: return null
    val url = intent.getStringExtra(EXTRA_STREAM_URL) ?: return null
    val mimeType = intent.getStringExtra(EXTRA_STREAM_MIME_TYPE) ?: "application/x-mpegURL"
    val headersBundle = intent.getBundleExtra(EXTRA_HEADERS)

    return NativeStreamDescriptor(
      id = id,
      title = title,
      url = url,
      mimeType = mimeType,
      drmScheme = intent.getStringExtra(EXTRA_DRM_SCHEME),
      drmLicenseUrl = intent.getStringExtra(EXTRA_DRM_LICENSE_URL),
      headers = bundleToHeaders(headersBundle),
    )
  }

  private fun requireString(map: ReadableMap, key: String): String {
    val value = optionalString(map, key)
    require(!value.isNullOrBlank()) { "Missing required stream field: $key" }
    return value
  }

  private fun optionalString(map: ReadableMap, key: String): String? {
    if (!map.hasKey(key) || map.isNull(key)) {
      return null
    }

    return map.getString(key)?.trim()?.takeIf { it.isNotEmpty() }
  }

  private fun readHeaders(headersMap: ReadableMap?): Map<String, String> {
    if (headersMap == null) {
      return emptyMap()
    }

    val headers = mutableMapOf<String, String>()
    val iterator = headersMap.keySetIterator()

    while (iterator.hasNextKey()) {
      val key = iterator.nextKey()
      val value = headersMap.getString(key)?.trim()
      if (!value.isNullOrEmpty()) {
        headers[key] = value
      }
    }

    return headers
  }

  private fun bundleToHeaders(headersBundle: Bundle?): Map<String, String> {
    if (headersBundle == null || headersBundle.isEmpty) {
      return emptyMap()
    }

    return headersBundle.keySet().associateWith { key ->
      headersBundle.getString(key).orEmpty()
    }.filterValues { it.isNotEmpty() }
  }
}