// Copyright (c) 2026 Multimedia Entertainment Hub LLC
// SPDX-License-Identifier: LicenseRef-Proprietary

package net.mehstudios.etimetable.android.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import net.mehstudios.etimetable.android.R
import net.mehstudios.etimetable.android.data.ActionResult
import net.mehstudios.etimetable.api.ErrorCodes
import net.mehstudios.etimetable.api.MIN_PASSWORD_LENGTH

/** The sentence to show for a failed action, or null when it worked. */
@Composable
fun ActionResult.message(): String? = when (this) {
    ActionResult.Done, ActionResult.SavedOffline -> null
    ActionResult.NoConnection -> stringResource(R.string.error_no_connection)
    is ActionResult.Refused -> when (code) {
        ErrorCodes.WRONG_CREDENTIALS -> stringResource(R.string.error_wrong_credentials)
        ErrorCodes.EMAIL_TAKEN -> stringResource(R.string.error_email_taken)
        ErrorCodes.INVALID_EMAIL -> stringResource(R.string.error_invalid_email)
        ErrorCodes.WEAK_PASSWORD -> pluralStringResource(
            R.plurals.error_weak_password,
            MIN_PASSWORD_LENGTH,
            MIN_PASSWORD_LENGTH,
        )
        else -> stringResource(R.string.error_generic)
    }
}
