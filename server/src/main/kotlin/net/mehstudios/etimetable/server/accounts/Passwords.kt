// Copyright (c) 2026 Multimedia Entertainment Hub LLC
// SPDX-License-Identifier: LicenseRef-Proprietary

package net.mehstudios.etimetable.server.accounts

import org.bouncycastle.crypto.generators.Argon2BytesGenerator
import org.bouncycastle.crypto.params.Argon2Parameters
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/**
 * Argon2id with the OWASP minimum (19 MiB, 2 passes, 1 lane), stored in the
 * PHC string format so the parameters can be raised later without breaking
 * existing hashes.
 */
object Passwords {
    private const val MEMORY_KIB = 19_456
    private const val ITERATIONS = 2
    private const val PARALLELISM = 1
    private const val SALT_BYTES = 16
    private const val HASH_BYTES = 32

    private val random = SecureRandom()
    private val encoder = Base64.getEncoder().withoutPadding()
    private val decoder = Base64.getDecoder()

    fun hash(password: String): String {
        val salt = ByteArray(SALT_BYTES).also(random::nextBytes)
        val hash = derive(password, salt, MEMORY_KIB, ITERATIONS, PARALLELISM, HASH_BYTES)
        return "\$argon2id\$v=19\$m=$MEMORY_KIB,t=$ITERATIONS,p=$PARALLELISM\$${encoder.encodeToString(
            salt,
        )}\$${encoder.encodeToString(hash)}"
    }

    fun verify(password: String, encoded: String): Boolean {
        // ["", "argon2id", "v=19", "m=..,t=..,p=..", salt, hash]
        val parts = encoded.split('$')
        if (parts.size != 6 || parts[1] != "argon2id") return false
        val params = parts[3].split(',').associate { it.substringBefore('=') to it.substringAfter('=').toInt() }
        val salt = decoder.decode(parts[4])
        val expected = decoder.decode(parts[5])
        val actual =
            derive(password, salt, params.getValue("m"), params.getValue("t"), params.getValue("p"), expected.size)
        return MessageDigest.isEqual(expected, actual)
    }

    private fun derive(
        password: String,
        salt: ByteArray,
        memoryKib: Int,
        iterations: Int,
        parallelism: Int,
        length: Int,
    ): ByteArray {
        val generator = Argon2BytesGenerator()
        generator.init(
            Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
                .withVersion(Argon2Parameters.ARGON2_VERSION_13)
                .withMemoryAsKB(memoryKib)
                .withIterations(iterations)
                .withParallelism(parallelism)
                .withSalt(salt)
                .build(),
        )
        return ByteArray(length).also { generator.generateBytes(password.toCharArray(), it) }
    }
}
