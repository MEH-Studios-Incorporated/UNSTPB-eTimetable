// Copyright (c) 2026 Multimedia Entertainment Hub LLC
// SPDX-License-Identifier: LicenseRef-Proprietary

package net.mehstudios.etimetable.android

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.ktor.client.engine.okhttp.OkHttp
import net.mehstudios.etimetable.AppInfo
import net.mehstudios.etimetable.android.ui.theme.EtTheme
import net.mehstudios.etimetable.api.ApiClient

private sealed interface ServerState {
    data object Checking : ServerState
    data class Ok(val version: String) : ServerState
    data object Unreachable : ServerState
}

/** First screen of the skeleton: shows whether the development server answers. */
@Composable
fun HomeScreen(serverUrl: String) {
    var attempt by remember { mutableIntStateOf(0) }
    var state by remember { mutableStateOf<ServerState>(ServerState.Checking) }

    LaunchedEffect(serverUrl, attempt) {
        state = ServerState.Checking
        val client = ApiClient(OkHttp.create(), serverUrl)
        state = try {
            ServerState.Ok(client.health().version)
        } catch (e: Exception) {
            ServerState.Unreachable
        } finally {
            client.close()
        }
    }

    HomeContent(serverUrl, state, onRetry = { attempt++ })
}

@Composable
private fun HomeContent(serverUrl: String, state: ServerState, onRetry: () -> Unit) {
    Scaffold { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(AppInfo.NAME, style = MaterialTheme.typography.headlineSmall)
            Text(
                stringResource(R.string.home_subtitle),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                stringResource(R.string.server_label),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 24.dp),
            )
            Text(
                when (state) {
                    ServerState.Checking -> stringResource(R.string.server_checking, serverUrl)
                    is ServerState.Ok -> stringResource(R.string.server_ok, state.version)
                    ServerState.Unreachable -> stringResource(R.string.server_error, serverUrl)
                },
                style = MaterialTheme.typography.bodyLarge,
                color = when (state) {
                    ServerState.Unreachable -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.onSurface
                },
            )
            if (state is ServerState.Unreachable) {
                OutlinedButton(onClick = onRetry) { Text(stringResource(R.string.retry)) }
            }
        }
    }
}

@Preview
@Composable
private fun HomePreview() {
    EtTheme { HomeContent("http://10.0.2.2:8080", ServerState.Ok("0.1.0"), onRetry = {}) }
}
