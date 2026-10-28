// Copyright (c) 2026 Multimedia Entertainment Hub LLC
// SPDX-License-Identifier: LicenseRef-Proprietary

package net.mehstudios.etimetable.server.db

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.mehstudios.etimetable.server.DatabaseConfig
import org.flywaydb.core.Flyway
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet
import javax.sql.DataSource

fun createDataSource(config: DatabaseConfig): DataSource = HikariDataSource(
    HikariConfig().apply {
        jdbcUrl = config.url
        username = config.user
        password = config.password
        maximumPoolSize = 10
    },
)

/** Applies the scripts in resources/db/migration that have not run yet. */
fun migrate(dataSource: DataSource) {
    Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate()
}

/** Runs [block] in one transaction on the IO dispatcher; JDBC blocks. */
suspend fun <T> DataSource.transaction(block: (Connection) -> T): T = withContext(Dispatchers.IO) {
    connection.use { connection ->
        connection.autoCommit = false
        try {
            block(connection).also { connection.commit() }
        } catch (e: Throwable) {
            connection.rollback()
            throw e
        }
    }
}

fun Connection.update(sql: String, vararg params: Any?): Int = prepare(sql, params).use { it.executeUpdate() }

fun <T> Connection.query(sql: String, vararg params: Any?, row: (ResultSet) -> T): List<T> =
    prepare(sql, params).use { statement ->
        statement.executeQuery().use { rs -> buildList { while (rs.next()) add(row(rs)) } }
    }

private fun Connection.prepare(sql: String, params: Array<out Any?>): PreparedStatement =
    prepareStatement(sql).apply { params.forEachIndexed { i, value -> setObject(i + 1, value) } }
