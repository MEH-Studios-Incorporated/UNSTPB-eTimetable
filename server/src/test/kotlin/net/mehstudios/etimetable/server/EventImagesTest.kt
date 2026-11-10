// Copyright (c) 2026 Multimedia Entertainment Hub LLC
// SPDX-License-Identifier: LicenseRef-Proprietary

package net.mehstudios.etimetable.server

import net.mehstudios.etimetable.server.events.EventImages
import net.mehstudios.etimetable.server.events.ImageRejected
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Pictures a test can upload: a plain image with a red left half, so turning it is visible. */
internal fun picture(width: Int, height: Int, format: String = "jpeg"): ByteArray {
    val image = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
    val g = image.createGraphics()
    g.color = Color.WHITE
    g.fillRect(0, 0, width, height)
    g.color = Color.RED
    g.fillRect(0, 0, width / 2, height)
    g.dispose()
    return ByteArrayOutputStream().also { ImageIO.write(image, format, it) }.toByteArray()
}

/** [jpeg] with an EXIF block that says how the camera was held. */
internal fun withOrientation(jpeg: ByteArray, orientation: Int): ByteArray {
    val tiff = byteArrayOf(
        'M'.code.toByte(), 'M'.code.toByte(), 0, 42, 0, 0, 0, 8, // big-endian header, IFD at 8
        0, 1, // one entry
        0x01, 0x12, 0, 3, 0, 0, 0, 1, 0, orientation.toByte(), 0, 0, // Orientation, SHORT, 1 value
        0, 0, 0, 0, // no next IFD
    )
    val body = "Exif".toByteArray() + byteArrayOf(0, 0) + tiff
    val length = body.size + 2
    val app1 = byteArrayOf(0xFF.toByte(), 0xE1.toByte(), (length shr 8).toByte(), length.toByte()) + body
    return jpeg.copyOfRange(0, 2) + app1 + jpeg.copyOfRange(2, jpeg.size)
}

class EventImagesTest {
    @Test
    fun aWidePngBecomesAJpegAtMost1600Wide() {
        val stored = EventImages.process(picture(3200, 1800, "png"))
        assertEquals(1600 to 900, stored.width to stored.height)
        val decoded = ImageIO.read(ByteArrayInputStream(stored.data))
        assertEquals(1600, decoded.width)
        assertEquals(0xFF.toByte(), stored.data[0])
        assertEquals(64, stored.hash.length)
    }

    @Test
    fun aSmallPictureKeepsItsSize() {
        val stored = EventImages.process(picture(800, 450))
        assertEquals(800 to 450, stored.width to stored.height)
    }

    @Test
    fun aPhoneHeldUprightIsTurnedAndItsExifDropped() {
        val sideways = withOrientation(picture(400, 200), 6)
        assertEquals(6, EventImages.exifOrientation(sideways))
        val stored = EventImages.process(sideways)
        assertEquals(200 to 400, stored.width to stored.height)
        // the red half was on the left; turned a quarter clockwise it is on top
        val decoded = ImageIO.read(ByteArrayInputStream(stored.data))
        val top = Color(decoded.getRGB(100, 50))
        val bottom = Color(decoded.getRGB(100, 350))
        assertTrue(top.red > 200 && top.green < 80, "top is $top")
        assertTrue(bottom.red > 200 && bottom.green > 200, "bottom is $bottom")
        assertFalse(String(stored.data, Charsets.ISO_8859_1).contains("Exif"))
    }

    @Test
    fun otherFilesAreRefused() {
        assertFailsWith<ImageRejected> { EventImages.process("nu e o imagine".toByteArray()) }
        assertFailsWith<ImageRejected> { EventImages.process(picture(100, 100, "gif")) }
        assertFailsWith<ImageRejected> { EventImages.process(ByteArray(EventImages.MAX_UPLOAD_BYTES + 1)) }
        // a JPEG header followed by nothing usable
        assertFailsWith<ImageRejected> { EventImages.process(byteArrayOf(-1, -40, -1, 0, 1, 2, 3)) }
    }
}
