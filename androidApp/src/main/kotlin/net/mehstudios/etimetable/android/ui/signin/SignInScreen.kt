// Copyright (c) 2026 Multimedia Entertainment Hub LLC
// SPDX-License-Identifier: LicenseRef-Proprietary

package net.mehstudios.etimetable.android.ui.signin

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import net.mehstudios.etimetable.AppInfo
import net.mehstudios.etimetable.android.R
import net.mehstudios.etimetable.android.data.ActionResult
import net.mehstudios.etimetable.android.ui.components.message
import net.mehstudios.etimetable.android.ui.theme.ButtonShape
import net.mehstudios.etimetable.api.ErrorCodes
import net.mehstudios.etimetable.api.MIN_PASSWORD_LENGTH
import net.mehstudios.etimetable.api.SignupRequest
import net.mehstudios.etimetable.api.isValidPhone

/** Inverts black logos to white in the dark theme. */
val invertInDark = ColorFilter.colorMatrix(
    ColorMatrix(floatArrayOf(-1f, 0f, 0f, 0f, 255f, 0f, -1f, 0f, 0f, 255f, 0f, 0f, -1f, 0f, 255f, 0f, 0f, 0f, 1f, 0f)),
)

private const val PASSWORDS_DIFFER = "passwords_differ"

@Composable
fun SignInScreen(
    signIn: suspend (email: String, password: String, remember: Boolean) -> ActionResult,
    signUp: suspend (form: SignupRequest, remember: Boolean) -> ActionResult,
) {
    var creating by rememberSaveable { mutableStateOf(false) }
    var firstName by rememberSaveable { mutableStateOf("") }
    var lastName by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var phone by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var repeat by rememberSaveable { mutableStateOf("") }
    var rememberMe by rememberSaveable { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    var lastResult by remember { mutableStateOf<ActionResult?>(null) }
    val scope = rememberCoroutineScope()
    val dark = isSystemInDarkTheme()

    val error = when ((lastResult as? ActionResult.Refused)?.code) {
        PASSWORDS_DIFFER -> stringResource(R.string.error_passwords_differ)
        ErrorCodes.MISSING_NAME -> stringResource(R.string.error_missing_name)
        ErrorCodes.INVALID_PHONE -> stringResource(R.string.error_invalid_phone)
        else -> lastResult?.message()
    }
    val canSubmit = !busy &&
        email.isNotBlank() &&
        password.isNotBlank() &&
        (!creating || (firstName.isNotBlank() && lastName.isNotBlank() && phone.isNotBlank() && repeat.isNotBlank()))

    fun submit() {
        if (!canSubmit) return
        // the server checks these too; checking here saves a round trip
        if (creating) {
            lastResult = when {
                password.length < MIN_PASSWORD_LENGTH -> ActionResult.Refused(ErrorCodes.WEAK_PASSWORD)
                password != repeat -> ActionResult.Refused(PASSWORDS_DIFFER)
                !isValidPhone(phone) -> ActionResult.Refused(ErrorCodes.INVALID_PHONE)
                else -> null
            }
            if (lastResult != null) return
        }
        busy = true
        scope.launch {
            lastResult = if (creating) {
                signUp(
                    SignupRequest(email.trim(), password, firstName.trim(), lastName.trim(), phone.trim()),
                    rememberMe,
                )
            } else {
                signIn(email, password, rememberMe)
            }
            busy = false
        }
    }

    Scaffold { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(if (creating) 16.dp else 40.dp))
            Image(
                painter = painterResource(R.drawable.faima_seal),
                contentDescription = stringResource(R.string.faima_seal),
                colorFilter = if (dark) invertInDark else null,
                modifier = Modifier.width(if (creating) 88.dp else 132.dp),
            )
            Spacer(Modifier.height(14.dp))
            Text(AppInfo.NAME, style = MaterialTheme.typography.headlineMedium)
            Text(
                stringResource(R.string.tagline),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(28.dp))
            Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                AnimatedVisibility(
                    visible = creating,
                    enter = expandVertically() + fadeIn(),
                    exit =
                    shrinkVertically() + fadeOut(),
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Field(firstName, {
                            firstName = it
                        }, R.string.first_name, Modifier.weight(1f), KeyboardType.Text, KeyboardCapitalization.Words)
                        Field(lastName, {
                            lastName = it
                        }, R.string.last_name, Modifier.weight(1f), KeyboardType.Text, KeyboardCapitalization.Words)
                    }
                }
                Field(email, { email = it }, R.string.email, Modifier.fillMaxWidth(), KeyboardType.Email)
                AnimatedVisibility(
                    visible = creating,
                    enter = expandVertically() + fadeIn(),
                    exit =
                    shrinkVertically() + fadeOut(),
                ) {
                    Field(phone, { phone = it }, R.string.phone, Modifier.fillMaxWidth(), KeyboardType.Phone)
                }
                Field(password, {
                    password = it
                }, R.string.password, Modifier.fillMaxWidth(), KeyboardType.Password, secret = true)
                AnimatedVisibility(
                    visible = creating,
                    enter = expandVertically() + fadeIn(),
                    exit =
                    shrinkVertically() + fadeOut(),
                ) {
                    Field(repeat, {
                        repeat = it
                    }, R.string.password_repeat, Modifier.fillMaxWidth(), KeyboardType.Password, secret = true)
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Checkbox(checked = rememberMe, onCheckedChange = { rememberMe = it })
                    Text(stringResource(R.string.remember_me), style = MaterialTheme.typography.bodyMedium)
                }
                if (error != null) {
                    Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelLarge)
                }
                Button(
                    shape = ButtonShape,
                    onClick = ::submit,
                    enabled = canSubmit,
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                ) {
                    Text(stringResource(if (creating) R.string.create_account else R.string.sign_in))
                }
                OutlinedButton(
                    shape = ButtonShape,
                    onClick = {
                        creating = !creating
                        lastResult = null
                    },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                ) {
                    Text(stringResource(if (creating) R.string.have_account else R.string.create_account))
                }
            }
            Spacer(Modifier.weight(1f).height(32.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.padding(vertical = 24.dp),
            ) {
                Image(
                    painter = painterResource(R.drawable.meh_mark),
                    contentDescription = null,
                    colorFilter = if (dark) null else invertInDark,
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    stringResource(R.string.footer_credit),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun Field(
    value: String,
    onChange: (String) -> Unit,
    label: Int,
    modifier: Modifier,
    type: KeyboardType,
    capitalization: KeyboardCapitalization = KeyboardCapitalization.None,
    secret: Boolean = false,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(stringResource(label)) },
        singleLine = true,
        visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(
            keyboardType = type,
            capitalization = capitalization,
            imeAction = ImeAction.Next,
        ),
        modifier = modifier,
    )
}
