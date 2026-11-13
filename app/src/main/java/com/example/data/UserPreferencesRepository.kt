package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.camera.PhotoFilter
import com.example.geo.MiniMapTileLoader
import com.example.geo.QrPayloadFormat
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.geoCamDataStore: DataStore<Preferences> by
  preferencesDataStore(name = "geocam_preferences")

data class OverlayPreferences(
  val isDarkTheme: Boolean = true,
  val shutterSoundEnabled: Boolean = true,
  val geoTaggingEnabled: Boolean = true,
  val timestampOnly: Boolean = false,
  val stampOverlayOnCapture: Boolean = true,
  val useDmsCoordinates: Boolean = false,
  val showTimestamp: Boolean = true,
  val showGridLines: Boolean = false,
  val showCompassOverlay: Boolean = true,
  val mapZoomLevel: Int = MiniMapTileLoader.DEFAULT_ZOOM,
  val photoFilter: PhotoFilter = PhotoFilter.NONE,
  val qrPayloadFormat: QrPayloadFormat = QrPayloadFormat.MAPS_URL,
)

class UserPreferencesRepository(private val context: Context) {

  private object Keys {
    val IS_DARK_THEME = booleanPreferencesKey("is_dark_theme")
    val SHUTTER_SOUND_ENABLED = booleanPreferencesKey("shutter_sound_enabled")
    val GEO_TAGGING_ENABLED = booleanPreferencesKey("geo_tagging_enabled")
    val TIMESTAMP_ONLY = booleanPreferencesKey("timestamp_only")
    val STAMP_ON_CAPTURE = booleanPreferencesKey("stamp_on_capture")
    val USE_DMS = booleanPreferencesKey("use_dms")
    val SHOW_TIMESTAMP = booleanPreferencesKey("show_timestamp")
    val SHOW_GRID_LINES = booleanPreferencesKey("show_grid_lines")
    val SHOW_COMPASS_OVERLAY = booleanPreferencesKey("show_compass_overlay")
    val MAP_ZOOM_LEVEL = intPreferencesKey("map_zoom_level")
    val PHOTO_FILTER = stringPreferencesKey("photo_filter")
    val QR_PAYLOAD_FORMAT = stringPreferencesKey("qr_payload_format")
  }

  val preferences: Flow<OverlayPreferences> =
    context.geoCamDataStore.data.map { prefs ->
      val qrName = prefs[Keys.QR_PAYLOAD_FORMAT]
      val qrFormat =
        QrPayloadFormat.entries.firstOrNull { it.name == qrName } ?: QrPayloadFormat.MAPS_URL
      val filterName = prefs[Keys.PHOTO_FILTER]
      val filter =
        PhotoFilter.entries.firstOrNull { it.name == filterName } ?: PhotoFilter.NONE
      val zoom =
        (prefs[Keys.MAP_ZOOM_LEVEL] ?: MiniMapTileLoader.DEFAULT_ZOOM).coerceIn(
          MiniMapTileLoader.MIN_ZOOM,
          MiniMapTileLoader.MAX_ZOOM,
        )
      OverlayPreferences(
        isDarkTheme = prefs[Keys.IS_DARK_THEME] ?: true,
        shutterSoundEnabled = prefs[Keys.SHUTTER_SOUND_ENABLED] ?: true,
        geoTaggingEnabled = prefs[Keys.GEO_TAGGING_ENABLED] ?: true,
        timestampOnly = prefs[Keys.TIMESTAMP_ONLY] ?: false,
        stampOverlayOnCapture = prefs[Keys.STAMP_ON_CAPTURE] ?: true,
        useDmsCoordinates = prefs[Keys.USE_DMS] ?: false,
        showTimestamp = prefs[Keys.SHOW_TIMESTAMP] ?: true,
        showGridLines = prefs[Keys.SHOW_GRID_LINES] ?: false,
        showCompassOverlay = prefs[Keys.SHOW_COMPASS_OVERLAY] ?: true,
        mapZoomLevel = zoom,
        photoFilter = filter,
        qrPayloadFormat = qrFormat,
      )
    }

  suspend fun setDarkTheme(enabled: Boolean) {
    context.geoCamDataStore.edit { it[Keys.IS_DARK_THEME] = enabled }
  }

  suspend fun setShutterSoundEnabled(enabled: Boolean) {
    context.geoCamDataStore.edit { it[Keys.SHUTTER_SOUND_ENABLED] = enabled }
  }

  suspend fun setGeoTaggingEnabled(enabled: Boolean) {
    context.geoCamDataStore.edit { prefs ->
      prefs[Keys.GEO_TAGGING_ENABLED] = enabled
      if (enabled) {
        prefs[Keys.TIMESTAMP_ONLY] = false
      }
    }
  }

  suspend fun setTimestampOnly(enabled: Boolean) {
    context.geoCamDataStore.edit { prefs ->
      prefs[Keys.TIMESTAMP_ONLY] = enabled
      if (enabled) {
        prefs[Keys.GEO_TAGGING_ENABLED] = false
      }
    }
  }

  suspend fun setStampOnCapture(enabled: Boolean) {
    context.geoCamDataStore.edit { it[Keys.STAMP_ON_CAPTURE] = enabled }
  }

  suspend fun setUseDmsCoordinates(enabled: Boolean) {
    context.geoCamDataStore.edit { it[Keys.USE_DMS] = enabled }
  }

  suspend fun setShowTimestamp(enabled: Boolean) {
    context.geoCamDataStore.edit { it[Keys.SHOW_TIMESTAMP] = enabled }
  }

  suspend fun setShowGridLines(enabled: Boolean) {
    context.geoCamDataStore.edit { it[Keys.SHOW_GRID_LINES] = enabled }
  }

  suspend fun setShowCompassOverlay(enabled: Boolean) {
    context.geoCamDataStore.edit { it[Keys.SHOW_COMPASS_OVERLAY] = enabled }
  }

  suspend fun setMapZoomLevel(zoom: Int) {
    val clamped = zoom.coerceIn(MiniMapTileLoader.MIN_ZOOM, MiniMapTileLoader.MAX_ZOOM)
    context.geoCamDataStore.edit { it[Keys.MAP_ZOOM_LEVEL] = clamped }
  }

  suspend fun setPhotoFilter(filter: PhotoFilter) {
    context.geoCamDataStore.edit { it[Keys.PHOTO_FILTER] = filter.name }
  }

  suspend fun setQrPayloadFormat(format: QrPayloadFormat) {
    context.geoCamDataStore.edit { it[Keys.QR_PAYLOAD_FORMAT] = format.name }
  }
}
