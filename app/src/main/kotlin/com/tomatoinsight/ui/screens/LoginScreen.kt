package com.tomatoinsight.ui.screens

import com.tomatoinsight.R
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.tomatoinsight.data.PendingSignup
import com.tomatoinsight.data.PendingSignupStore
import com.tomatoinsight.ui.theme.TomatoPrimary
import com.tomatoinsight.util.emailValidationError

@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit,
    onNavigateToSignup: () -> Unit,
    onNeedsVerification: () -> Unit
) {
    val context = LocalContext.current
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var rememberMe by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var showForgotPasswordDialog by remember { mutableStateOf(false) }
    var resetMessage by remember { mutableStateOf<String?>(null) }

    val auth = FirebaseAuth.getInstance()

    LaunchedEffect(Unit) {
        val (remember, saved) = readRememberedEmail(context)
        rememberMe = remember
        if (remember && saved.isNotBlank()) email = saved
    }

    AuthScreenScaffold(
        logoRes = R.drawable.auth_logo,
        headerMessage = "Log in to stay on top of your farm scans and plant health."
    ) {
        AuthTitleBlock(
            title = "Login",
            prompt = "Don't Have An Account?",
            linkLabel = "Sign Up",
            onLinkClick = {
                if (PendingSignupStore.hasPending(context)) onNeedsVerification()
                else onNavigateToSignup()
            }
        )

        AuthEmailField(
            value = email,
            onValueChange = {
                email = it
                if (error != null) error = null
            }
        )

        Spacer(modifier = Modifier.height(14.dp))

        AuthPasswordField(
            value = password,
            onValueChange = {
                password = it
                if (error != null) error = null
            },
            visible = passwordVisible,
            onToggleVisible = { passwordVisible = !passwordVisible }
        )

        AuthRememberForgotRow(
            rememberMe = rememberMe,
            onRememberChange = { rememberMe = it },
            onForgotPassword = { showForgotPasswordDialog = true }
        )

        AuthErrorText(error)
        AuthSuccessText(resetMessage)

        Spacer(modifier = Modifier.height(20.dp))

        AuthPrimaryButton(
            label = "Login",
            loading = isLoading,
            onClick = {
                val trimmedEmail = email.trim()
                val emailError = emailValidationError(trimmedEmail)
                if (emailError != null) {
                    error = emailError
                    return@AuthPrimaryButton
                }
                if (password.isBlank()) {
                    error = "Please enter your password"
                    return@AuthPrimaryButton
                }

                isLoading = true
                error = null

                auth.signInWithEmailAndPassword(trimmedEmail, password)
                    .addOnCompleteListener { task ->
                        if (task.isSuccessful) {
                            val user = task.result?.user
                            if (user == null) {
                                isLoading = false
                                error = "Could not open your account. Please sign in again."
                                return@addOnCompleteListener
                            }
                            persistRememberedAccount(context, rememberMe, user.email ?: trimmedEmail, user.uid)
                            if (!user.isEmailVerified) {
                                PendingSignupStore.save(
                                    context,
                                    PendingSignup(
                                        email = trimmedEmail,
                                        password = password,
                                        status = PendingSignup.STATUS_AWAITING_VERIFICATION
                                    )
                                )
                                user.sendEmailVerification()
                                    .addOnCompleteListener {
                                        auth.signOut()
                                        isLoading = false
                                        onNeedsVerification()
                                    }
                                return@addOnCompleteListener
                            }
                            isLoading = false
                            PendingSignupStore.clear(context)
                            onLoginSuccess()
                        } else {
                            isLoading = false
                            val exception = task.exception
                            error = when (exception) {
                                is FirebaseAuthInvalidUserException ->
                                    "This email is not registered. Please sign up first."
                                is FirebaseAuthInvalidCredentialsException ->
                                    "Incorrect email or password. Please try again."
                                else -> {
                                    val msg = exception?.message ?: ""
                                    when {
                                        msg.contains("network error", ignoreCase = true) ||
                                            msg.contains("timeout", ignoreCase = true) ||
                                            msg.contains("unreachable host", ignoreCase = true) ||
                                            msg.contains("interrupted connection", ignoreCase = true) ||
                                            msg.contains("Unable to resolve host", ignoreCase = true) ->
                                            "Can't log in. Check your internet connection"
                                        msg.contains("user-not-found", ignoreCase = true) ||
                                            msg.contains("no user record", ignoreCase = true) ->
                                            "This email is not registered. Please sign up first."
                                        msg.contains("wrong-password", ignoreCase = true) ||
                                            msg.contains("invalid-credential", ignoreCase = true) ->
                                            "Incorrect email or password. Please try again."
                                        else -> msg.ifBlank { "Login failed. Please try again." }
                                    }
                                }
                            }
                        }
                    }
            }
        )
    }

    if (showForgotPasswordDialog) {
        ForgotPasswordDialog(
            initialEmail = email.trim(),
            onDismiss = { showForgotPasswordDialog = false },
            onResetSent = { message ->
                showForgotPasswordDialog = false
                resetMessage = message
                error = null
            }
        )
    }
}

@Composable
private fun ForgotPasswordDialog(
    initialEmail: String,
    onDismiss: () -> Unit,
    onResetSent: (String) -> Unit
) {
    var resetEmail by remember { mutableStateOf(initialEmail) }
    var dialogError by remember { mutableStateOf<String?>(null) }
    var isSending by remember { mutableStateOf(false) }
    val auth = FirebaseAuth.getInstance()

    AlertDialog(
        onDismissRequest = { if (!isSending) onDismiss() },
        title = { Text("Reset Password") },
        text = {
            Column {
                Text(
                    text = "Enter your email address to request a password reset link.",
                    fontSize = 14.sp,
                    color = Color.Gray
                )
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = resetEmail,
                    onValueChange = {
                        resetEmail = it
                        dialogError = null
                    },
                    label = { Text("Email") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    singleLine = true
                )
                dialogError?.let {
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val trimmed = resetEmail.trim()
                    if (trimmed.isBlank()) {
                        dialogError = "Please enter your email"
                        return@TextButton
                    }
                    if (!trimmed.contains("@")) {
                        dialogError = "Please enter a valid email address"
                        return@TextButton
                    }

                    isSending = true
                    dialogError = null
                    auth.sendPasswordResetEmail(trimmed)
                        .addOnCompleteListener { task ->
                            isSending = false
                            if (task.isSuccessful) {
                                onResetSent("If this email has an account, check your inbox and spam folder for a password reset link.")
                            } else {
                                val exception = task.exception
                                dialogError = when (exception) {
                                    is FirebaseAuthInvalidUserException ->
                                        "This email is not registered."
                                    else -> exception?.localizedMessage
                                        ?: "Could not send reset email. Please try again."
                                }
                            }
                        }
                },
                enabled = !isSending
            ) {
                if (isSending) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Text("Send Reset Link", color = TomatoPrimary)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSending) {
                Text("Cancel")
            }
        }
    )
}
