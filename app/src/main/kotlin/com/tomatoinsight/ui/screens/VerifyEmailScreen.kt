package com.tomatoinsight.ui.screens

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import com.tomatoinsight.data.PendingSignup
import com.tomatoinsight.data.PendingSignupStore
import com.tomatoinsight.ui.theme.TomatoPrimary
import com.tomatoinsight.util.await
import com.tomatoinsight.util.awaitComplete
import com.tomatoinsight.util.emailValidationError
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private const val VERIFY_POLL_MS = 4_000L
private const val RESEND_COOLDOWN_SEC = 60

@Composable
fun VerifyEmailScreen(
    onVerified: () -> Unit,
    onBackToLogin: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val auth = remember { FirebaseAuth.getInstance() }

    var pending by remember { mutableStateOf(PendingSignupStore.read(context)) }
    var passwordVisible by remember { mutableStateOf(false) }
    var passwordDraft by remember { mutableStateOf(pending?.password.orEmpty()) }
    var showPasswordField by remember {
        mutableStateOf(pending?.password.isNullOrBlank())
    }
    var message by remember {
        mutableStateOf(
            if (pending?.isCreating == true) {
                "Finishing account setup… Keep the app open until verification is ready."
            } else {
                "We sent a verification link to your Gmail. Open it, then this screen will continue automatically."
            }
        )
    }
    var error by remember { mutableStateOf<String?>(null) }
    var success by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(pending?.isCreating == true) }
    var resendUntilMs by remember { mutableLongStateOf(pending?.resendUntilMs ?: 0L) }
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var openingDashboard by remember { mutableStateOf(false) }
    var cancelled by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        while (isActive) {
            nowMs = System.currentTimeMillis()
            delay(250)
        }
    }

    fun cancelVerification() {
        if (openingDashboard) return
        cancelled = true
        busy = false
        PendingSignupStore.clear(context)
        pending = null
        runCatching { auth.signOut() }
        onBackToLogin()
    }

    fun persistPassword(password: String) {
        val current = PendingSignupStore.read(context) ?: return
        PendingSignupStore.save(context, current.copy(password = password))
        pending = PendingSignupStore.read(context)
        passwordDraft = password
        showPasswordField = password.isBlank()
    }

    suspend fun finishVerified() {
        if (openingDashboard || cancelled) return
        openingDashboard = true
        busy = true
        success = "Email verified. Opening your farm dashboard…"
        PendingSignupStore.clear(context)
        onVerified()
    }

    suspend fun checkVerified(email: String, password: String): Boolean {
        val result = auth.signInWithEmailAndPassword(email, password).await()
        result.user?.reload()?.awaitComplete()
        val verified = auth.currentUser?.isEmailVerified == true
        if (!verified) {
            auth.signOut()
        }
        return verified
    }

    suspend fun sendVerification(email: String, password: String): Pair<Boolean, String?> {
        val credential = auth.signInWithEmailAndPassword(email, password).await()
        val user = credential.user
            ?: return false to "Could not open your account to send verification."
        user.reload().awaitComplete()
        if (user.isEmailVerified) {
            return true to null
        }
        user.sendEmailVerification().awaitComplete()
        auth.signOut()
        return false to null
    }

    LaunchedEffect(pending?.email, pending?.password, pending?.status, cancelled) {
        if (cancelled) return@LaunchedEffect
        val current = PendingSignupStore.read(context)
        pending = current
        if (current == null) {
            error = "No pending signup found. Sign in or create an account again."
            return@LaunchedEffect
        }

        if (current.isCreating) {
            busy = true
            message = "Finishing account setup… Keep the app open until verification is ready."
            try {
                if (checkVerified(current.email, current.password)) {
                    finishVerified()
                    return@LaunchedEffect
                }
                val (alreadyVerified, sendError) = sendVerification(current.email, current.password)
                if (alreadyVerified) {
                    finishVerified()
                    return@LaunchedEffect
                }
                if (sendError != null) {
                    error = sendError
                } else {
                    PendingSignupStore.updateStatus(
                        context,
                        PendingSignup.STATUS_AWAITING_VERIFICATION
                    )
                    PendingSignupStore.markResendCooldown(context, RESEND_COOLDOWN_SEC)
                    pending = PendingSignupStore.read(context)
                    resendUntilMs = pending?.resendUntilMs ?: 0L
                    message =
                        "Account is ready. Check your Gmail and open the verification link — we will open your dashboard when it is confirmed."
                    success = "Verification email sent. Check inbox and spam."
                }
            } catch (err: Exception) {
                // Account may not exist yet if create never finished.
                PendingSignupStore.clear(context)
                pending = null
                error = err.localizedMessage
                    ?: "Account setup was interrupted. Create your account again."
            } finally {
                busy = false
            }
        }

        while (isActive && !cancelled && PendingSignupStore.hasPending(context) && !openingDashboard) {
            val latest = PendingSignupStore.read(context) ?: break
            if (latest.password.isBlank()) {
                showPasswordField = true
                delay(VERIFY_POLL_MS)
                continue
            }
            try {
                if (checkVerified(latest.email, latest.password)) {
                    finishVerified()
                    break
                }
            } catch (_: Exception) {
                // Keep waiting; user may still need to open the Gmail link.
            }
            delay(VERIFY_POLL_MS)
        }
    }

    DisposableEffect(Unit) {
        onDispose { }
    }

    val remainingSec = ((resendUntilMs - nowMs) / 1000L).coerceAtLeast(0L).toInt()

    AuthScreenScaffold(
        headerMessage = "Confirm your Gmail before you can use TomatoInsight.",
        scrollContent = true
    ) {
        Text(
            text = "Verify email",
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF1C1B1F)
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = "We sent a link to ${pending?.email ?: "your Gmail"}",
            color = Color(0xFF8A8A8E),
            fontSize = 14.sp
        )
        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = message,
            color = Color(0xFF5F6368),
            fontSize = 14.sp,
            lineHeight = 20.sp,
            textAlign = TextAlign.Start,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        if (showPasswordField) {
            Spacer(modifier = Modifier.height(8.dp))
            AuthPasswordField(
                value = passwordDraft,
                onValueChange = {
                    passwordDraft = it
                    error = null
                },
                placeholder = "Password (to resend / continue)",
                visible = passwordVisible,
                onToggleVisible = { passwordVisible = !passwordVisible }
            )
        }

        AuthErrorText(error)
        AuthSuccessText(success)

        Spacer(modifier = Modifier.height(20.dp))

        AuthPrimaryButton(
            label = when {
                openingDashboard -> "Opening dashboard…"
                busy -> "Working…"
                remainingSec > 0 -> "Resend in ${remainingSec}s"
                else -> "Resend verification email"
            },
            loading = busy || openingDashboard,
            enabled = !openingDashboard && remainingSec <= 0,
            onClick = {
                val email = pending?.email.orEmpty()
                val password = when {
                    !pending?.password.isNullOrBlank() -> pending!!.password
                    else -> passwordDraft
                }
                val emailError = emailValidationError(email)
                if (emailError != null) {
                    error = emailError
                    return@AuthPrimaryButton
                }
                if (password.isBlank()) {
                    showPasswordField = true
                    error = "Enter your password to resend the verification email."
                    return@AuthPrimaryButton
                }

                scope.launch {
                    busy = true
                    error = null
                    success = null
                    try {
                        persistPassword(password)
                        val (alreadyVerified, sendError) = sendVerification(email, password)
                        if (alreadyVerified) {
                            finishVerified()
                            return@launch
                        }
                        if (sendError != null) {
                            error = sendError
                        } else {
                            PendingSignupStore.updateStatus(
                                context,
                                PendingSignup.STATUS_AWAITING_VERIFICATION
                            )
                            PendingSignupStore.markResendCooldown(context, RESEND_COOLDOWN_SEC)
                            pending = PendingSignupStore.read(context)
                            resendUntilMs = pending?.resendUntilMs ?: 0L
                            success = "Verification email sent again. Check your inbox and spam folder."
                            message =
                                "Waiting for email verification. Open the Gmail link we sent, then this screen will continue automatically."
                        }
                    } catch (err: Exception) {
                        error = err.localizedMessage ?: "Could not resend verification email."
                    } finally {
                        busy = false
                    }
                }
            }
        )

        Spacer(modifier = Modifier.height(8.dp))

        TextButton(
            onClick = { cancelVerification() },
            enabled = !openingDashboard && !cancelled
        ) {
            Text(
                text = "Cancel verification",
                color = Color(0xFF3C3C43),
                fontWeight = FontWeight.SemiBold
            )
        }

        TextButton(
            onClick = { cancelVerification() },
            enabled = !openingDashboard && !cancelled
        ) {
            Text(
                text = "Back to login",
                color = TomatoPrimary,
                fontWeight = FontWeight.SemiBold
            )
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}
