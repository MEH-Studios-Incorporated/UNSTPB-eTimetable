// Copyright (c) 2026 Multimedia Entertainment Hub LLC
// SPDX-License-Identifier: LicenseRef-Proprietary

package net.mehstudios.etimetable.server.admin

import io.ktor.http.encodeURLParameter
import io.nayuki.qrcodegen.QrCode
import java.nio.ByteBuffer
import java.security.SecureRandom
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Six-digit codes from an authenticator app (RFC 6238: HMAC-SHA1, 30 second steps), the
 * kind Google Authenticator, Microsoft Authenticator, Aegis and 1Password all read.
 */
object Totp {
    private const val STEP_SECONDS = 30L
    private const val DIGITS = 6

    /** One step either side, so a phone clock 30 seconds off still works. */
    private const val WINDOW = 1

    private val random = SecureRandom()

    fun newSecret(): ByteArray = ByteArray(20).also(random::nextBytes)

    fun step(epochSeconds: Long): Long = epochSeconds / STEP_SECONDS

    fun code(secret: ByteArray, step: Long): String {
        val mac = Mac.getInstance("HmacSHA1").apply { init(SecretKeySpec(secret, "HmacSHA1")) }
        val hash = mac.doFinal(ByteBuffer.allocate(8).putLong(step).array())
        val offset = hash.last().toInt() and 0x0f
        val binary = ((hash[offset].toInt() and 0x7f) shl 24) or
            ((hash[offset + 1].toInt() and 0xff) shl 16) or
            ((hash[offset + 2].toInt() and 0xff) shl 8) or
            (hash[offset + 3].toInt() and 0xff)
        return (binary % 1_000_000).toString().padStart(DIGITS, '0')
    }

    /**
     * The step [code] belongs to, if it is valid now and later than [usedStep], so the same
     * code cannot sign in twice.
     */
    fun verify(secret: ByteArray, code: String, epochSeconds: Long, usedStep: Long?): Long? {
        val digits = code.filter { !it.isWhitespace() }
        if (digits.length != DIGITS || !digits.all(Char::isDigit)) return null
        val now = step(epochSeconds)
        return (now - WINDOW..now + WINDOW).firstOrNull { s ->
            (usedStep == null || s > usedStep) && constantTimeEquals(code(secret, s), digits)
        }
    }

    /** The otpauth link an authenticator app reads from the QR code. */
    fun uri(secret: ByteArray, email: String, issuer: String = "eTimetable FAIMA"): String =
        "otpauth://totp/${issuer.encodeURLParameter()}:${email.encodeURLParameter()}" +
            "?secret=${base32(secret)}&issuer=${issuer.encodeURLParameter()}&digits=$DIGITS&period=$STEP_SECONDS"

    /** RFC 4648 base32 without padding, in groups of four for typing by hand when [grouped]. */
    fun base32(bytes: ByteArray, grouped: Boolean = false): String {
        val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
        val out = StringBuilder()
        var buffer = 0
        var bits = 0
        for (b in bytes) {
            buffer = (buffer shl 8) or (b.toInt() and 0xff)
            bits += 8
            while (bits >= 5) {
                out.append(alphabet[(buffer shr (bits - 5)) and 31])
                bits -= 5
            }
        }
        if (bits > 0) out.append(alphabet[(buffer shl (5 - bits)) and 31])
        return if (grouped) out.chunked(4).joinToString(" ") else out.toString()
    }

    /** The QR code of [text] as an inline SVG, black on white so every phone camera reads it. */
    fun qrSvg(text: String): String {
        val qr = QrCode.encodeText(text, QrCode.Ecc.MEDIUM)
        val border = 4
        val size = qr.size + border * 2
        val path = StringBuilder()
        for (y in 0 until qr.size) {
            for (x in 0 until qr.size) {
                if (qr.getModule(x, y)) path.append("M${x + border},${y + border}h1v1h-1z")
            }
        }
        return """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 $size $size" width="200" height="200" """ +
            """role="img" aria-label="Codul QR pentru aplicația de autentificare" shape-rendering="crispEdges">""" +
            """<rect width="100%" height="100%" fill="#FFFFFF"/><path d="$path" fill="#000000"/></svg>"""
    }

    private fun constantTimeEquals(a: String, b: String): Boolean =
        java.security.MessageDigest.isEqual(a.toByteArray(), b.toByteArray())
}
