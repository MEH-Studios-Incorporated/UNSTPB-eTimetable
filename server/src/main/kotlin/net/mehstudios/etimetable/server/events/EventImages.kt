// Copyright (c) 2026 Multimedia Entertainment Hub LLC
// SPDX-License-Identifier: LicenseRef-Proprietary

package net.mehstudios.etimetable.server.events

import java.awt.RenderingHints
import java.awt.geom.AffineTransform
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageWriteParam
import kotlin.math.max
import kotlin.math.roundToInt

/** A picture the dashboard refuses, with the reason in Romanian for the form. */
class ImageRejected(message: String) : Exception(message)

/** A picture as the server keeps it: JPEG, at most [EventImages.MAX_SIDE] on its long side. */
class StoredImage(val hash: String, val data: ByteArray, val width: Int, val height: Int)

/**
 * Turns an uploaded JPEG or PNG into the JPEG the apps download. Re-encoding drops the
 * metadata a phone camera writes (location included) and turns the picture the way its
 * EXIF orientation says, since browsers do and ImageIO does not.
 */
object EventImages {
    const val MAX_UPLOAD_BYTES = 8 * 1024 * 1024
    const val MAX_SIDE = 1600

    // a 40 megapixel photo decodes to 160 MB; anything larger is refused before decoding
    private const val MAX_PIXELS = 40_000_000L
    private const val QUALITY = 0.85f

    fun process(upload: ByteArray): StoredImage {
        if (upload.size > MAX_UPLOAD_BYTES) throw ImageRejected("Imaginea are peste 8 MB.")
        if (!isJpeg(upload) && !isPng(upload)) throw ImageRejected("Imaginea trebuie să fie JPEG sau PNG.")
        val decoded = decode(upload)
        val turned = if (isJpeg(upload)) orient(decoded, exifOrientation(upload)) else decoded
        val scale = minOf(1.0, MAX_SIDE.toDouble() / max(turned.width, turned.height))
        val width = (turned.width * scale).roundToInt().coerceAtLeast(1)
        val height = (turned.height * scale).roundToInt().coerceAtLeast(1)
        val data = jpeg(resize(turned, width, height))
        return StoredImage(sha256(data), data, width, height)
    }

    private fun isJpeg(b: ByteArray) =
        b.size > 3 && b[0] == 0xFF.toByte() && b[1] == 0xD8.toByte() && b[2] == 0xFF.toByte()

    private fun isPng(b: ByteArray) = b.size > 8 &&
        b[0] == 0x89.toByte() &&
        b[1] == 'P'.code.toByte() &&
        b[2] == 'N'.code.toByte() &&
        b[3] == 'G'.code.toByte()

    private fun decode(bytes: ByteArray): BufferedImage {
        val input = ImageIO.createImageInputStream(ByteArrayInputStream(bytes))
            ?: throw ImageRejected("Imaginea nu poate fi citită.")
        input.use {
            val reader = ImageIO.getImageReaders(it).asSequence().firstOrNull()
                ?: throw ImageRejected("Imaginea nu poate fi citită.")
            try {
                reader.input = it
                if (reader.getWidth(0).toLong() * reader.getHeight(0) > MAX_PIXELS) {
                    throw ImageRejected("Imaginea are prea mulți pixeli; micșoreaz-o sub 40 de megapixeli.")
                }
                return reader.read(0)
            } catch (e: javax.imageio.IIOException) {
                throw ImageRejected("Imaginea este deteriorată sau nu poate fi citită.")
            } finally {
                reader.dispose()
            }
        }
    }

    /** Draws onto white (a PNG may be transparent; JPEG is not) at the final size. */
    private fun resize(source: BufferedImage, width: Int, height: Int): BufferedImage {
        val target = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        val g = target.createGraphics()
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g.color = java.awt.Color.WHITE
            g.fillRect(0, 0, width, height)
            // halving first keeps bicubic from skipping pixels on a big shrink
            var step = source
            while (step.width / 2 >= width && step.height / 2 >= height) step = half(step)
            g.drawImage(step, 0, 0, width, height, null)
        } finally {
            g.dispose()
        }
        return target
    }

    private fun half(source: BufferedImage): BufferedImage {
        val smaller = BufferedImage(source.width / 2, source.height / 2, BufferedImage.TYPE_INT_ARGB)
        val g = smaller.createGraphics()
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
            g.drawImage(source, 0, 0, smaller.width, smaller.height, null)
        } finally {
            g.dispose()
        }
        return smaller
    }

    private fun jpeg(image: BufferedImage): ByteArray {
        val writer = ImageIO.getImageWritersByFormatName("jpeg").next()
        val out = ByteArrayOutputStream()
        try {
            ImageIO.createImageOutputStream(out).use { stream ->
                writer.output = stream
                val param = writer.defaultWriteParam.apply {
                    compressionMode = ImageWriteParam.MODE_EXPLICIT
                    compressionQuality = QUALITY
                }
                writer.write(null, IIOImage(image, null, null), param)
            }
        } finally {
            writer.dispose()
        }
        return out.toByteArray()
    }

    private fun sha256(data: ByteArray) =
        MessageDigest.getInstance("SHA-256").digest(data).joinToString("") { "%02x".format(it) }

    /** Applies EXIF orientation 2 to 8 (1, or none, is upright). */
    private fun orient(image: BufferedImage, orientation: Int): BufferedImage {
        if (orientation !in 2..8) return image
        val w = image.width.toDouble()
        val h = image.height.toDouble()
        val swap = orientation >= 5
        val t = AffineTransform()
        when (orientation) {
            2 -> t.apply {
                translate(w, 0.0)
                scale(-1.0, 1.0)
            }
            3 -> t.apply {
                translate(w, h)
                rotate(Math.PI)
            }
            4 -> t.apply {
                translate(0.0, h)
                scale(1.0, -1.0)
            }
            5 -> t.apply {
                rotate(Math.PI / 2)
                scale(1.0, -1.0)
            }
            6 -> t.apply {
                translate(h, 0.0)
                rotate(Math.PI / 2)
            }
            7 -> t.apply {
                scale(-1.0, 1.0)
                translate(-h, 0.0)
                translate(0.0, w)
                rotate(3 * Math.PI / 2)
            }
            8 -> t.apply {
                translate(0.0, w)
                rotate(3 * Math.PI / 2)
            }
        }
        val target = BufferedImage(
            if (swap) image.height else image.width,
            if (swap) image.width else image.height,
            BufferedImage.TYPE_INT_RGB,
        )
        val g = target.createGraphics()
        try {
            g.drawImage(image, t, null)
        } finally {
            g.dispose()
        }
        return target
    }

    /** The Orientation tag (0x0112) of a JPEG's EXIF block, 1 when there is none. */
    internal fun exifOrientation(jpeg: ByteArray): Int {
        var at = 2
        while (at + 4 <= jpeg.size && jpeg[at] == 0xFF.toByte()) {
            val marker = jpeg[at + 1].toInt() and 0xFF
            val length = ((jpeg[at + 2].toInt() and 0xFF) shl 8) or (jpeg[at + 3].toInt() and 0xFF)
            if (marker == 0xDA) return 1 // image data starts; no EXIF before it
            val segment = at + 4
            if (marker == 0xE1 && segment + 6 <= jpeg.size && String(jpeg, segment, 4, Charsets.US_ASCII) == "Exif") {
                return orientationIn(jpeg, segment + 6, minOf(jpeg.size, at + 2 + length)) ?: 1
            }
            at += 2 + length
        }
        return 1
    }

    private fun orientationIn(b: ByteArray, tiff: Int, end: Int): Int? {
        if (tiff + 8 > end) return null
        val little = b[tiff] == 'I'.code.toByte()
        fun u16(i: Int) = if (little) {
            (b[i].toInt() and 0xFF) or ((b[i + 1].toInt() and 0xFF) shl 8)
        } else {
            ((b[i].toInt() and 0xFF) shl 8) or (b[i + 1].toInt() and 0xFF)
        }
        fun u32(i: Int) = if (little) u16(i) or (u16(i + 2) shl 16) else (u16(i) shl 16) or u16(i + 2)
        val ifd = tiff + u32(tiff + 4)
        if (ifd + 2 > end) return null
        val entries = u16(ifd)
        for (n in 0 until entries) {
            val entry = ifd + 2 + n * 12
            if (entry + 12 > end) return null
            if (u16(entry) == 0x0112) return u16(entry + 8)
        }
        return null
    }
}
