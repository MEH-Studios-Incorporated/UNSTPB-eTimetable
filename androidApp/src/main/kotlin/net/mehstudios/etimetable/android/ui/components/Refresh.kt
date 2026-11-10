// Copyright (c) 2026 Multimedia Entertainment Hub LLC
// SPDX-License-Identifier: LicenseRef-Proprietary

package net.mehstudios.etimetable.android.ui.components

import android.animation.ValueAnimator
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import kotlinx.coroutines.launch

/**
 * Pull down to sync. The indicator shows only for a sync the student pulled for, so the
 * syncs the app starts by itself never move anything on the screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RefreshBox(refresh: suspend () -> Unit, modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    var refreshing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = {
            scope.launch {
                refreshing = true
                try {
                    refresh()
                } finally {
                    refreshing = false
                }
            }
        },
        modifier = modifier,
        content = content,
    )
}

/**
 * True when the phone's animator duration scale is 0 (Remove animations in the
 * accessibility settings, or the developer option). Compose's own animations follow that
 * setting; this is for the ones it does not reach, such as Coil's crossfade.
 */
@Composable
fun reducedMotion(): Boolean = remember { !ValueAnimator.areAnimatorsEnabled() }
