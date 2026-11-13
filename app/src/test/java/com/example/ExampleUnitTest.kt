package com.example

import com.example.camera.PhotoFilter
import com.example.camera.ProCameraCapabilities
import com.example.camera.ProCameraController
import com.example.camera.ProCameraSettings
import com.example.camera.ProWhiteBalance
import com.example.geo.CompassRepository
import com.example.geo.GeoLocationInfo
import com.example.geo.MiniMapTileLoader
import com.example.geo.QrPayloadFormat
import com.example.media.CapturedMediaRepository
import com.example.media.GalleryFilter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {
  @Test
  fun formatsDecimalAndDmsCoordinates() {
    val info =
      GeoLocationInfo(
        latitude = 37.421998,
        longitude = -122.084000,
        addressLine = "1600 Amphitheatre Pkwy",
        area = "North Bayshore",
        locationSummary = "Mountain View, CA",
      )
    assertEquals("37.421998° N, 122.084000° W", info.formattedCoordinates(useDms = false))
    val dms = info.formattedCoordinates(useDms = true)
    assertTrue(dms.contains("37°25'"))
    assertTrue(dms.contains("122°05'"))
  }

  @Test
  fun buildsFullSnippetPayload() {
    val info =
      GeoLocationInfo(
        latitude = 37.421998,
        longitude = -122.084000,
        addressLine = "1600 Amphitheatre Pkwy",
        area = "North Bayshore",
        locationSummary = "Mountain View, CA",
      )
    val snippet = info.qrPayload(QrPayloadFormat.FULL_SNIPPET).orEmpty()
    assertTrue(snippet.contains("Address: 1600 Amphitheatre Pkwy"))
    assertTrue(snippet.contains("Area: North Bayshore"))
    assertTrue(snippet.contains("Location: Mountain View, CA"))
  }

  @Test
  fun photoFiltersAndZoomBoundsAreConfigured() {
    assertTrue(MiniMapTileLoader.DEFAULT_ZOOM in MiniMapTileLoader.MIN_ZOOM..MiniMapTileLoader.MAX_ZOOM)
    assertEquals(7, PhotoFilter.entries.size)
    assertEquals(PhotoFilter.NONE, PhotoFilter.entries.first())
  }

  @Test
  fun formatsVideoDurationsCorrectly() {
    assertEquals("00:00", CapturedMediaRepository.formatDuration(0L))
    assertEquals("00:09", CapturedMediaRepository.formatDuration(9_400L))
    assertEquals("01:05", CapturedMediaRepository.formatDuration(65_000L))
    assertEquals("1:01:05", CapturedMediaRepository.formatDuration(3_665_000L))
    assertEquals(3, GalleryFilter.entries.size)
  }

  @Test
  fun formatsProCameraControlsAndTracksOverrides() {
    assertEquals("1/1000", ProCameraController.formatShutterSpeed(1_000_000L))
    assertEquals("1/60", ProCameraController.formatShutterSpeed(16_666_667L))
    assertEquals("0.5s", ProCameraController.formatShutterSpeed(500_000_000L))

    assertEquals("0.0", ProCameraController.formatEv(0, 0.5f))
    assertEquals("+1.5", ProCameraController.formatEv(3, 0.5f))
    assertEquals("-1.0", ProCameraController.formatEv(-2, 0.5f))

    assertEquals("AF", ProCameraController.formatFocusLabel(null, 10f))
    assertEquals("INF", ProCameraController.formatFocusLabel(0.2f, 10f))
    assertEquals("MACRO", ProCameraController.formatFocusLabel(9.5f, 10f))

    val defaultPro = ProCameraSettings()
    assertFalse(defaultPro.hasAnyOverride)
    assertFalse(defaultPro.isManualExposure)

    val manualPro = defaultPro.copy(iso = 400, whiteBalance = ProWhiteBalance.DAYLIGHT)
    assertTrue(manualPro.hasAnyOverride)
    assertTrue(manualPro.isManualExposure)

    val caps = ProCameraCapabilities()
    assertTrue(caps.availableIsoStops().isNotEmpty())
    assertTrue(caps.availableShutterStopsNs().isNotEmpty())
  }

  @Test
  fun computesCompassDirectionsAndShortestArcSmoothing() {
    assertEquals("N", CompassRepository.cardinalDirection(0f))
    assertEquals("N", CompassRepository.cardinalDirection(355f))
    assertEquals("NE", CompassRepository.cardinalDirection(45f))
    assertEquals("E", CompassRepository.cardinalDirection(90f))
    assertEquals("SW", CompassRepository.cardinalDirection(225f))
    assertEquals("NW", CompassRepository.cardinalDirection(315f))

    // Smoothing across 350 -> 10 wraps forward through 0 instead of backwards through 180
    val smoothed = CompassRepository.smoothAngle(current = 350f, target = 10f, alpha = 0.5f)
    assertEquals(0f, smoothed, 0.01f)

    // Back camera (-Z) facing North when held upright (+X=East, +Y=Up, +Z=South)
    val northFacingMatrix =
      floatArrayOf(
        1f, 0f, 0f,
        0f, 0f, -1f,
        0f, 1f, 0f,
      )
    assertEquals(
      0f,
      CompassRepository.computeCameraAzimuthDegrees(northFacingMatrix, isFrontCamera = false),
      0.1f,
    )
    assertEquals(
      180f,
      CompassRepository.computeCameraAzimuthDegrees(northFacingMatrix, isFrontCamera = true),
      0.1f,
    )

    // Back camera (-Z) facing East when held upright (+X=South, +Y=Up, +Z=West)
    val eastFacingMatrix =
      floatArrayOf(
        0f, 0f, -1f,
        -1f, 0f, 0f,
        0f, 1f, 0f,
      )
    assertEquals(
      90f,
      CompassRepository.computeCameraAzimuthDegrees(eastFacingMatrix, isFrontCamera = false),
      0.1f,
    )
  }
}
