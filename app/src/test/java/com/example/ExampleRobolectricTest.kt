package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.camera.PhotoFilter
import com.example.geo.GeoLocationInfo
import com.example.geo.QrCodeGenerator
import com.example.geo.QrPayloadFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Geo Camera Pro", appName)
  }

  @Test
  fun `generates non-null QR bitmap for location payload`() {
    val info =
      GeoLocationInfo(
        latitude = 37.421998,
        longitude = -122.084000,
        addressLine = "1600 Amphitheatre Pkwy",
        area = "North Bayshore",
        locationSummary = "Mountain View, CA, US",
      )
    val payload = info.qrPayload(QrPayloadFormat.MAPS_URL)
    assertNotNull(payload)
    assertTrue(payload!!.contains("37.421998,-122.084000"))

    val bitmap = QrCodeGenerator.generateSync(payload, sizePx = 128)
    assertNotNull(bitmap)
    assertEquals(128, bitmap!!.width)
    assertEquals(128, bitmap.height)
  }

  @Test
  fun `photo filters produce distinct 4x5 color matrices`() {
    val normal = PhotoFilter.NONE.toColorMatrix().array.toList()
    PhotoFilter.entries.forEach { filter ->
      val matrix = filter.toColorMatrix().array
      assertEquals(20, matrix.size)
      if (filter != PhotoFilter.NONE) {
        assertFalse(matrix.toList() == normal)
      }
    }
  }
}
