package com.tomatoinsight.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MailOutline
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.tomatoinsight.data.FIRST_FARM_WEEK
import com.tomatoinsight.data.FarmerProfile
import com.tomatoinsight.data.LAST_FARM_WEEK
import com.tomatoinsight.data.ScanCalendar
import com.tomatoinsight.data.parseProfileName
import com.tomatoinsight.ui.components.ConnectionStatusIndicator
import com.tomatoinsight.ui.components.CustomBottomBar
import com.tomatoinsight.ui.components.InternetConnectionNotice
import com.tomatoinsight.ui.theme.TomatoRed
import com.tomatoinsight.util.PASSWORD_REQUIREMENTS_HINT
import com.tomatoinsight.util.hasValidatedInternet
import com.tomatoinsight.util.observeValidatedInternet
import com.tomatoinsight.util.passwordValidationError
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

private val PageBackground = Color(0xFFFFFCF9)
private val Ink = Color(0xFF202421)
private val Muted = Color(0xFF656B67)
private val Orange = Color(0xFF1F7A35)
private val SoftGreen = Color(0xFFE8F2E5)
private val Divider = Color(0xFF202421).copy(alpha = 0.10f)
private val FieldBorder = Color(0xFFDDE2DC)

data class ProfileSaveResult(val ok: Boolean, val message: String)

@Composable
fun ProfileScreen(
    onNavigateToHome: () -> Unit,
    onNavigateToMonitoring: () -> Unit,
    onNavigateToHistory: () -> Unit,
    onCameraClick: () -> Unit,
    onLogout: () -> Unit,
    farmerProfile: FarmerProfile = FarmerProfile(),
    farmRows: Int = 0,
    farmColumns: Int = 0,
    scanCount: Int = 0,
    selectedFarmWeek: Int = 0,
    scanCalendar: ScanCalendar = ScanCalendar(),
    creditBalance: Int? = null,
    creditsLoaded: Boolean = false,
    profileBonusGranted: Boolean = true,
    onOpenCreditPlans: () -> Unit = {},
    onProfileSaved: suspend (FarmerProfile, Boolean) -> ProfileSaveResult = { _, _ ->
        ProfileSaveResult(true, "Saved.")
    },
    onScanCalendarChanged: (ScanCalendar) -> Unit = {}
) {
    val auth = FirebaseAuth.getInstance()
    val scope = rememberCoroutineScope()
    val showCreditReminder = creditsLoaded && !profileBonusGranted
    val user = auth.currentUser
    val context = LocalContext.current
    val internetAvailable by produceState(initialValue = context.hasValidatedInternet(), context) {
        context.observeValidatedInternet().collect { value = it }
    }
    val email = farmerProfile.email.ifBlank { user?.email.orEmpty() }
    val tileCount = if (farmRows > 0 && farmColumns > 0) farmRows * farmColumns else 0
    val weekLabel = if (selectedFarmWeek in FIRST_FARM_WEEK..LAST_FARM_WEEK) selectedFarmWeek else null

    val authNameParts = remember(user?.displayName) {
        parseProfileName(user?.displayName)
    }
    var firstName by remember {
        mutableStateOf(farmerProfile.firstName.ifBlank { authNameParts.firstName })
    }
    var middleInitial by remember {
        mutableStateOf(
            farmerProfile.middleInitial.ifBlank {
                authNameParts.middleName.trim().uppercase().take(1)
            }
        )
    }
    var lastName by remember {
        mutableStateOf(farmerProfile.lastName.ifBlank { authNameParts.lastName })
    }
    var farmName by remember { mutableStateOf(farmerProfile.farmName) }
    var formError by remember { mutableStateOf<String?>(null) }
    var formSuccess by remember { mutableStateOf<String?>(null) }
    var photoStatus by remember { mutableStateOf<String?>(null) }
    var photoStatusOk by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }
    var isEditingProfile by remember { mutableStateOf(false) }
    var showLogoutDialog by remember { mutableStateOf(false) }
    var showChangePasswordDialog by remember { mutableStateOf(false) }
    var passwordChangeMessage by remember { mutableStateOf<String?>(null) }
    var calendarNote by remember(scanCalendar.enabled, scanCalendar.anchor, scanCalendar.nextDue) {
        mutableStateOf(scanCalendar.describe())
    }
    var year by remember { mutableIntStateOf(Calendar.getInstance().get(Calendar.YEAR)) }
    var month by remember { mutableIntStateOf(Calendar.getInstance().get(Calendar.MONTH)) }

    // Accept richer profile updates; never wipe filled Basic information with blanks.
    LaunchedEffect(
        farmerProfile.firstName,
        farmerProfile.middleInitial,
        farmerProfile.lastName,
        farmerProfile.farmName,
        authNameParts
    ) {
        if (farmerProfile.firstName.isNotBlank()) {
            firstName = farmerProfile.firstName
        } else if (firstName.isBlank() && authNameParts.firstName.isNotBlank()) {
            firstName = authNameParts.firstName
        }
        if (farmerProfile.middleInitial.isNotBlank()) {
            middleInitial = farmerProfile.middleInitial
        } else if (middleInitial.isBlank() && authNameParts.middleName.isNotBlank()) {
            middleInitial = authNameParts.middleName.trim().uppercase().take(1)
        }
        if (farmerProfile.lastName.isNotBlank()) {
            lastName = farmerProfile.lastName
        } else if (lastName.isBlank() && authNameParts.lastName.isNotBlank()) {
            lastName = authNameParts.lastName
        }
        if (farmerProfile.farmName.isNotBlank()) {
            farmName = farmerProfile.farmName
        }
    }

    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val bitmap = runCatching { decodeProfilePhoto(context, uri) }.getOrNull()
        if (bitmap == null) {
            photoStatusOk = false
            photoStatus = "Could not use that image."
            return@rememberLauncherForActivityResult
        }
        photoStatusOk = true
        photoStatus = "Saving photo…"
        scope.launch {
            val result = onProfileSaved(
                farmerProfile.copy(
                    firstName = firstName.trim(),
                    middleInitial = middleInitial.trim().uppercase().take(1),
                    lastName = lastName.trim(),
                    farmName = farmName.trim(),
                    email = email,
                    photo = bitmap
                ),
                true
            )
            photoStatusOk = result.ok
            photoStatus = result.message
        }
    }

    val displayName = listOf(firstName, middleInitial, lastName)
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .joinToString(" ")
        .ifBlank { user?.displayName?.trim().orEmpty() }
        .ifBlank { "Tomato Insight user" }

    val tasks = farmTasks(
        firstName,
        lastName,
        farmName,
        farmerProfile.photo != null,
        tileCount,
        scanCount,
        weekLabel,
        onNavigateToHome,
        onNavigateToMonitoring,
        onUploadPhoto = { photoPicker.launch("image/*") }
    )
    val doneCount = tasks.count { it.done }

    Scaffold(
        containerColor = PageBackground,
        bottomBar = {
            CustomBottomBar(
                selectedTab = "profile",
                cameraEnabled = true,
                onHomeClick = onNavigateToHome,
                onMonitoringClick = onNavigateToMonitoring,
                onCameraClick = onCameraClick,
                onHistoryClick = onNavigateToHistory,
                onProfileClick = { }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp)
        ) {
            InternetConnectionNotice(
                modifier = Modifier.padding(top = 8.dp)
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 20.dp, bottom = 22.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Profile",
                        color = Ink,
                        fontSize = 32.sp,
                        lineHeight = 36.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.6).sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Manage your farm and account", color = Muted, fontSize = 15.sp)
                }
                ConnectionStatusIndicator(labelColor = Ink, dotSize = 9.dp)
            }

            Row(
                modifier = Modifier.fillMaxWidth()
                    .background(Color.White, RoundedCornerShape(18.dp))
                    .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(18.dp))
                    .padding(horizontal = 14.dp, vertical = 18.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val setupProgress = doneCount.toFloat() / tasks.size.coerceAtLeast(1)
                Box(
                    modifier = Modifier.size(94.dp)
                        .drawBehind {
                            drawCircle(Color(0xFFE2E8F0))
                            drawArc(Color(0xFF1F8A4C), -90f, 360f * setupProgress, useCenter = true)
                        }
                        .clickable(role = Role.Button, onClickLabel = "Change profile photo") {
                            photoPicker.launch("image/*")
                        }
                        .semantics { stateDescription = "Farm setup: $doneCount of ${tasks.size} complete" },
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize().padding(6.dp)
                            .background(Color.White, CircleShape).padding(4.dp)
                            .clip(CircleShape)
                            .background(Brush.linearGradient(listOf(Color(0xFF2FA35C), Color(0xFF17703C)))),
                        contentAlignment = Alignment.Center
                    ) {
                        val photo = farmerProfile.photo
                        if (photo != null) {
                            Image(
                                bitmap = photo.asImageBitmap(),
                                contentDescription = "Profile photo",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Text(
                                text = profileInitials(farmerProfile, email),
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 26.sp
                            )
                        }
                    }
                    Box(
                        modifier = Modifier.align(Alignment.BottomEnd).size(28.dp)
                            .background(Color(0xFFD9480F), CircleShape)
                            .border(2.dp, Color.White, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        androidx.compose.material3.Icon(
                            imageVector = Icons.Outlined.PhotoCamera,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = displayName,
                            modifier = Modifier.weight(1f),
                            color = Ink,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Active",
                            color = Orange,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(SoftGreen)
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(3.dp))
                    Text("Grower · ${farmName.trim().ifBlank { "Your farm" }}", color = Muted, fontSize = 13.sp)
                    Text(
                        text = email.ifBlank { "No email available" },
                        color = Muted,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            onClick = { isEditingProfile = true },
                            shape = RoundedCornerShape(9.dp),
                            color = PageBackground,
                            border = androidx.compose.foundation.BorderStroke(1.dp, Orange)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                androidx.compose.material3.Icon(
                                    Icons.Default.Edit,
                                    contentDescription = null,
                                    tint = Orange,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Edit profile", color = Orange, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        if (farmerProfile.photo != null) {
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Remove",
                                color = Muted,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.sp,
                                modifier = Modifier.clickable {
                                    photoStatus = "Removing photo…"
                                    scope.launch {
                                        val result = onProfileSaved(
                                            farmerProfile.copy(
                                                firstName = firstName.trim(),
                                                middleInitial = middleInitial.trim().uppercase().take(1),
                                                lastName = lastName.trim(),
                                                farmName = farmName.trim(),
                                                email = email,
                                                photo = null
                                            ),
                                            true
                                        )
                                        photoStatusOk = result.ok
                                        photoStatus = if (result.ok) "Photo removed." else result.message
                                    }
                                }
                            )
                        }
                    }
                }
            }

            photoStatus?.let { message ->
                Text(
                    text = message,
                    color = if (photoStatusOk) Orange else Color(0xFFB71C1C),
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))
            ProfileSummaryBand(
                credits = if (creditsLoaded && creditBalance != null) creditBalance.toString() else "—",
                tiles = tileCount.toString(),
                scans = scanCount.toString(),
                week = weekLabel?.let { "Week $it" } ?: "Week —"
            )
            Spacer(modifier = Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = onOpenCreditPlans,
                    enabled = internetAvailable,
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Orange,
                        contentColor = Color.White,
                        disabledContainerColor = Color(0xFFE0E0E0),
                        disabledContentColor = Color(0xFF8B8B8B)
                    )
                ) {
                    Text(if (internetAvailable) "Top up credits" else "Top up offline", fontWeight = FontWeight.Bold)
                }
                OutlinedButton(
                    onClick = onNavigateToHome,
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Orange),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Orange)
                ) {
                    Text("Open farm", fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(28.dp))
            ProfileSectionHeader("Farm activity", "View all", onNavigateToHistory)
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onNavigateToHistory)
                    .padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier.size(46.dp).clip(RoundedCornerShape(12.dp)).background(SoftGreen),
                    contentAlignment = Alignment.Center
                ) {
                    androidx.compose.material3.Icon(
                        Icons.Default.CalendarMonth,
                        contentDescription = null,
                        tint = Orange,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(weekLabel?.let { "Week $it" } ?: "No week selected", color = Ink, fontWeight = FontWeight.Bold)
                    Text(
                        text = when {
                            tileCount == 0 -> "Set up your farm layout to begin"
                            scanCount == 1 -> "1 saved scan · View the latest result"
                            else -> "$scanCount saved scans · View your results"
                        },
                        color = Muted,
                        fontSize = 12.sp
                    )
                }
                androidx.compose.material3.Icon(
                    Icons.Default.ChevronRight,
                    contentDescription = "View farm activity",
                    tint = Muted
                )
            }
            ProfileDivider()

            Spacer(modifier = Modifier.height(26.dp))
            ProfileSectionHeader("Farm schedule")
            Spacer(modifier = Modifier.height(10.dp))
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = Color.White.copy(alpha = 0.56f),
                border = androidx.compose.foundation.BorderStroke(1.dp, Divider)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = {
                            val cal = Calendar.getInstance()
                            cal.set(year, month, 1)
                            cal.add(Calendar.MONTH, -1)
                            year = cal.get(Calendar.YEAR)
                            month = cal.get(Calendar.MONTH)
                        }) { Text("‹", color = Ink, fontSize = 22.sp) }
                        Text(
                            text = monthTitle(year, month),
                            modifier = Modifier.weight(1f),
                            textAlign = TextAlign.Center,
                            fontWeight = FontWeight.Bold,
                            color = Ink
                        )
                        TextButton(onClick = {
                            val cal = Calendar.getInstance()
                            cal.set(year, month, 1)
                            cal.add(Calendar.MONTH, 1)
                            year = cal.get(Calendar.YEAR)
                            month = cal.get(Calendar.MONTH)
                        }) { Text("›", color = Ink, fontSize = 22.sp) }
                    }
                    ProfileMonth(
                        year = year,
                        month = month,
                        calendar = scanCalendar,
                        onDate = { iso ->
                            if (!scanCalendar.enabled) return@ProfileMonth
                            val result = scanCalendar.chooseDate(iso)
                            calendarNote = result.second
                            onScanCalendarChanged(result.first)
                        }
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = if (scanCalendar.enabled) "✓  Custom dates on" else "Custom dates",
                            color = if (scanCalendar.enabled) Orange else Ink,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(if (scanCalendar.enabled) SoftGreen else Color(0xFFF2F3EF))
                                .clickable {
                                    val next = scanCalendar.copy(enabled = !scanCalendar.enabled)
                                    calendarNote = next.describe()
                                    onScanCalendarChanged(next)
                                }
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        )
                        if (scanCalendar.enabled) {
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Reset dates",
                                color = Orange,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                modifier = Modifier.clickable {
                                    val next = ScanCalendar(enabled = true)
                                    calendarNote = "Dates cleared. Tap a new first scan day to start Week 4 again."
                                    onScanCalendarChanged(next)
                                }
                            )
                        }
                    }
                    Text(calendarNote, color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
                }
            }

            Spacer(modifier = Modifier.height(28.dp))
            ProfileSectionHeader(
                title = "Personal & farm details",
                actionLabel = if (isEditingProfile) "Done" else "Edit",
                onAction = { isEditingProfile = !isEditingProfile }
            )
            if (showCreditReminder) {
                Text(
                    text = "Complete all six setup steps, then update your profile to earn 5 bonus scan credits.",
                    color = Color(0xFF7A5A15),
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            if (isEditingProfile) {
                ProfileField("First name", firstName) { firstName = it.filterNot(Char::isDigit); formError = null; formSuccess = null }
                Spacer(modifier = Modifier.height(8.dp))
                ProfileField("Last name", lastName) { lastName = it.filterNot(Char::isDigit); formError = null; formSuccess = null }
                Spacer(modifier = Modifier.height(8.dp))
                ProfileField("Middle initial", middleInitial) {
                    middleInitial = it.filterNot(Char::isDigit).take(1)
                    formError = null
                    formSuccess = null
                }
                Spacer(modifier = Modifier.height(8.dp))
                ProfileField("Farm name", farmName, "e.g. Green Valley Tomatoes") {
                    farmName = it
                    formError = null
                    formSuccess = null
                }
            } else {
                ProfileDetailRow("First name", firstName)
                ProfileDetailRow("Last name", lastName)
                ProfileDetailRow("Middle initial", middleInitial)
                ProfileDetailRow("Farm name", farmName)
            }

            formError?.let { ProfileMessage(it, error = true) }
            formSuccess?.let { ProfileMessage(it, error = false) }
            passwordChangeMessage?.let { ProfileMessage(it, error = false) }

            Spacer(modifier = Modifier.height(10.dp))
            Button(
                onClick = {
                    val next = FarmerProfile(
                        firstName = firstName.trim(),
                        middleInitial = middleInitial.trim().uppercase().take(1),
                        lastName = lastName.trim(),
                        farmName = farmName.trim(),
                        email = email,
                        photo = farmerProfile.photo
                    )
                    when {
                        next.firstName.isBlank() || next.lastName.isBlank() -> {
                            formError = "First name and last name are required."
                            isEditingProfile = true
                        }
                        listOf(next.firstName, next.middleInitial, next.lastName)
                            .any { value -> value.any(Char::isDigit) } -> {
                            formError = "Names cannot contain numbers."
                            isEditingProfile = true
                        }
                        next.middleInitial.isNotEmpty() && !next.middleInitial.matches(Regex("[A-Z]")) -> {
                            formError = "Middle initial must be a single letter."
                            isEditingProfile = true
                        }
                        else -> {
                            formError = null
                            isSaving = true
                            scope.launch {
                                val result = onProfileSaved(next, false)
                                isSaving = false
                                if (result.ok) {
                                    formSuccess = result.message
                                    isEditingProfile = false
                                } else {
                                    formError = result.message
                                }
                            }
                        }
                    }
                },
                enabled = !isSaving,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Orange, contentColor = Color.White)
            ) {
                Text(if (isSaving) "Saving…" else "Update profile", fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(28.dp))
            ProfileSectionHeader("Account")
            Spacer(modifier = Modifier.height(8.dp))
            ProfileAccountRow(
                icon = Icons.Default.MailOutline,
                label = "Email address",
                value = email.ifBlank { "Not available" }
            )
            ProfileDivider()
            ProfileAccountRow(
                icon = Icons.Default.Lock,
                label = "Change password",
                value = null,
                onClick = { showChangePasswordDialog = true }
            )
            ProfileDivider()
            Text("Email cannot be edited.", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))

            Spacer(modifier = Modifier.height(28.dp))
            ProfileSectionHeader("Farm setup", "$doneCount of ${tasks.size} complete")
            Spacer(modifier = Modifier.height(10.dp))
            LinearProgressIndicator(
                progress = { doneCount.toFloat() / tasks.size.coerceAtLeast(1) },
                modifier = Modifier.fillMaxWidth().height(7.dp).clip(CircleShape),
                color = Orange,
                trackColor = SoftGreen
            )
            if (showCreditReminder) {
                Text(
                    text = "Complete your setup to earn 5 bonus scan credits.",
                    color = Muted,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                )
            } else {
                Spacer(modifier = Modifier.height(6.dp))
            }
            tasks.forEach { task ->
                ProfileSetupTaskRow(task)
                ProfileDivider()
            }

            Spacer(modifier = Modifier.height(22.dp))
            OutlinedButton(
                onClick = { showLogoutDialog = true },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, TomatoRed),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = TomatoRed)
            ) {
                androidx.compose.material3.Icon(
                    Icons.AutoMirrored.Filled.Logout,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Sign out", fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text("Sign out") },
            text = { Text("Are you sure you want to sign out?") },
            confirmButton = {
                TextButton(onClick = {
                    auth.signOut()
                    onLogout()
                }) { Text("Sign out", color = TomatoRed) }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showChangePasswordDialog) {
        ChangePasswordDialog(
            userEmail = email,
            onDismiss = { showChangePasswordDialog = false },
            onPasswordChanged = {
                showChangePasswordDialog = false
                passwordChangeMessage = "Password updated successfully."
            }
        )
    }
}

@Composable
private fun ProfileCard(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) { content() }
    }
}

@Composable
private fun ProfileSummaryBand(credits: String, tiles: String, scans: String, week: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = SoftGreen
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().height(82.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ProfileSummaryStat(credits, "Credits", Modifier.weight(1f))
            ProfileSummaryDivider()
            ProfileSummaryStat(tiles, "Tiles", Modifier.weight(1f))
            ProfileSummaryDivider()
            ProfileSummaryStat(scans, "Scan", Modifier.weight(1f))
            ProfileSummaryDivider()
            ProfileSummaryStat(week, "", Modifier.weight(1.15f))
        }
    }
}

@Composable
private fun ProfileSummaryStat(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = Ink, fontSize = if (value.startsWith("Week")) 15.sp else 22.sp, fontWeight = FontWeight.Bold)
        if (label.isNotBlank()) Text(label, color = Muted, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun ProfileSummaryDivider() {
    Box(modifier = Modifier.width(1.dp).height(38.dp).background(Divider))
}

@Composable
private fun ProfileSectionHeader(
    title: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            color = Ink,
            fontSize = 20.sp,
            lineHeight = 24.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.2).sp
        )
        if (actionLabel != null) {
            Text(
                text = actionLabel,
                color = if (onAction != null) Orange else Muted,
                fontSize = 12.sp,
                fontWeight = if (onAction != null) FontWeight.Bold else FontWeight.Medium,
                modifier = if (onAction != null) Modifier.clickable(onClick = onAction) else Modifier
            )
        }
    }
}

@Composable
private fun ProfileDivider() {
    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Divider))
}

@Composable
private fun ProfileDetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = Ink, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Text(
            text = value.ifBlank { "Not added" },
            color = Muted,
            fontSize = 14.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
    ProfileDivider()
}

@Composable
private fun ProfileAccountRow(
    icon: ImageVector,
    label: String,
    value: String?,
    onClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        androidx.compose.material3.Icon(icon, contentDescription = null, tint = Orange, modifier = Modifier.size(21.dp))
        Spacer(modifier = Modifier.width(12.dp))
        Text(label, color = Ink, fontSize = 14.sp, modifier = Modifier.weight(1f))
        if (value != null) {
            Text(
                text = value,
                color = Muted,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (onClick != null) {
            androidx.compose.material3.Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = Muted,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

@Composable
private fun ProfileSetupTaskRow(task: FarmTask) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (task.action != null) Modifier.clickable(onClick = task.action) else Modifier)
            .padding(vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(CircleShape)
                .background(if (task.done) Orange else Color.Transparent)
                .border(1.5.dp, if (task.done) Orange else Muted, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (task.done) Text("✓", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(task.title, color = Ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            if (task.note.isNotBlank()) Text(task.note, color = Muted, fontSize = 12.sp)
        }
        if (task.link != null && task.action != null) {
            Text(task.link, color = Orange, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun ProfileFact(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp)) {
        Text(label, color = Muted, fontSize = 12.sp, modifier = Modifier.width(52.dp))
        Text(
            text = value,
            color = Ink,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun SectionHead(title: String, tag: String?) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(title, modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold, color = Ink, fontSize = 16.sp)
        if (tag != null) Text(tag, color = Muted, fontSize = 12.sp)
    }
}

@Composable
private fun ProfileField(
    label: String,
    value: String,
    placeholder: String = "",
    onValueChange: (String) -> Unit
) {
    Text(label, color = Muted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    Spacer(modifier = Modifier.height(4.dp))
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        placeholder = if (placeholder.isBlank()) null else {
            { Text(placeholder, color = Muted) }
        },
        shape = RoundedCornerShape(12.dp),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
        colors = profileFieldColors()
    )
}

@Composable
private fun profileFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = Color.Black,
    unfocusedTextColor = Color.Black,
    disabledTextColor = Color.Black,
    focusedBorderColor = Orange,
    unfocusedBorderColor = FieldBorder,
    cursorColor = Orange
)

@Composable
private fun ProfileMessage(message: String, error: Boolean) {
    Text(
        text = message,
        color = if (error) Color(0xFFB71C1C) else Color(0xFF1B5E20),
        fontSize = 13.sp,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (error) Color(0xFFFFE8E6) else Color(0xFFE8F8EF))
            .padding(12.dp)
    )
}

@Composable
private fun SnapshotChip(modifier: Modifier, label: String) {
    Text(
        text = label,
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFFF4F7FB))
            .padding(horizontal = 8.dp, vertical = 10.dp),
        color = Ink,
        fontWeight = FontWeight.Bold,
        fontSize = 12.sp,
        textAlign = TextAlign.Center,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}

@Composable
private fun ProfileMonth(
    year: Int,
    month: Int,
    calendar: ScanCalendar,
    onDate: (String) -> Unit
) {
    val today = profileIso(Calendar.getInstance())
    val lead = Calendar.getInstance().apply { set(year, month, 1) }.get(Calendar.DAY_OF_WEEK) - 1
    val days = Calendar.getInstance().apply { set(year, month, 1) }.getActualMaximum(Calendar.DAY_OF_MONTH)
    val labels = listOf("S", "M", "T", "W", "T", "F", "S")
    Column {
        Row(modifier = Modifier.fillMaxWidth()) {
            labels.forEach { label ->
                Text(
                    text = label,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    color = Muted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        val cells = List(lead) { null } + (1..days).toList()
        cells.chunked(7).forEach { week ->
            Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                week.forEach { day ->
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        if (day == null) {
                            Spacer(modifier = Modifier.size(32.dp))
                        } else {
                            val iso = "%04d-%02d-%02d".format(year, month + 1, day)
                            // Black "today" only when custom dates are off; custom mode uses anchor/due/selected only.
                            val todayMark = iso == today && !calendar.enabled
                            val anchor = calendar.enabled && iso == calendar.anchor
                            val due = calendar.enabled && iso == calendar.nextDue
                            val selected = calendar.enabled && iso == calendar.selected && !due
                            val bg = when {
                                due -> Orange
                                todayMark -> Ink
                                selected -> Color(0xFFE7EEFE)
                                else -> Color.Transparent
                            }
                            val fg = if (due || todayMark) Color.White else Ink
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(bg)
                                    .then(
                                        if (anchor && !due) Modifier.border(2.dp, Color(0xFF2E7D32), CircleShape)
                                        else Modifier
                                    )
                                    .clickable(enabled = calendar.enabled) { onDate(iso) },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(day.toString(), color = fg, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
                repeat(7 - week.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

private data class FarmTask(
    val done: Boolean,
    val title: String,
    val note: String,
    val link: String? = null,
    val action: (() -> Unit)? = null
)

@Composable
private fun ChangePasswordDialog(
    userEmail: String,
    onDismiss: () -> Unit,
    onPasswordChanged: () -> Unit
) {
    var currentPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var dialogError by remember { mutableStateOf<String?>(null) }
    var isUpdating by remember { mutableStateOf(false) }

    val auth = FirebaseAuth.getInstance()
    val user = auth.currentUser

    AlertDialog(
        onDismissRequest = { if (!isUpdating) onDismiss() },
        title = { Text("Change Password") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(text = "Account: $userEmail", fontSize = 13.sp, color = Color.Gray)
                OutlinedTextField(
                    value = currentPassword,
                    onValueChange = { currentPassword = it; dialogError = null },
                    label = { Text("Current Password") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    singleLine = true,
                    colors = profileFieldColors()
                )
                OutlinedTextField(
                    value = newPassword,
                    onValueChange = { newPassword = it; dialogError = null },
                    label = { Text("New Password") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    singleLine = true,
                    colors = profileFieldColors()
                )
                Text(text = PASSWORD_REQUIREMENTS_HINT, fontSize = 12.sp, color = Color.Gray)
                OutlinedTextField(
                    value = confirmPassword,
                    onValueChange = { confirmPassword = it; dialogError = null },
                    label = { Text("Confirm New Password") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    singleLine = true,
                    colors = profileFieldColors()
                )
                dialogError?.let {
                    Text(text = it, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (currentPassword.isBlank()) {
                        dialogError = "Enter your current password"
                        return@TextButton
                    }
                    passwordValidationError(newPassword)?.let {
                        dialogError = it
                        return@TextButton
                    }
                    if (newPassword != confirmPassword) {
                        dialogError = "New passwords do not match"
                        return@TextButton
                    }
                    if (newPassword == currentPassword) {
                        dialogError = "New password must be different from current password"
                        return@TextButton
                    }
                    if (user == null || userEmail.isBlank()) {
                        dialogError = "You must be logged in to change your password"
                        return@TextButton
                    }
                    isUpdating = true
                    dialogError = null
                    val credential = EmailAuthProvider.getCredential(userEmail, currentPassword)
                    user.reauthenticate(credential).addOnCompleteListener { authTask ->
                        if (!authTask.isSuccessful) {
                            isUpdating = false
                            dialogError = "Current password is incorrect"
                            return@addOnCompleteListener
                        }
                        user.updatePassword(newPassword).addOnCompleteListener { updateTask ->
                            isUpdating = false
                            if (updateTask.isSuccessful) onPasswordChanged()
                            else dialogError = updateTask.exception?.localizedMessage ?: "Could not update password"
                        }
                    }
                },
                enabled = !isUpdating
            ) {
                if (isUpdating) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Text("Update Password")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isUpdating) { Text("Cancel") }
        }
    )
}

private fun farmTasks(
    firstName: String,
    lastName: String,
    farmName: String,
    hasPhoto: Boolean,
    tileCount: Int,
    scanCount: Int,
    weekLabel: Int?,
    onHome: () -> Unit,
    onMonitoring: () -> Unit,
    onUploadPhoto: () -> Unit
): List<FarmTask> = listOf(
    FarmTask(
        done = firstName.isNotBlank() && lastName.isNotBlank(),
        title = "Add your name",
        note = "First and last name on this profile"
    ),
    FarmTask(
        done = farmName.isNotBlank(),
        title = "Name your farm",
        note = farmName.ifBlank { "Shown on your profile" }
    ),
    FarmTask(
        done = hasPhoto,
        title = "Add a profile photo",
        note = if (hasPhoto) "Your photo is on this profile" else "Use the avatar above to upload a photo.",
        link = "Upload photo",
        action = onUploadPhoto
    ),
    FarmTask(
        done = tileCount > 0,
        title = "Set the farm layout",
        note = if (tileCount > 0) "$tileCount tiles" else "Choose rows and columns",
        link = "Home",
        action = onHome
    ),
    FarmTask(
        done = scanCount > 0,
        title = "Save a leaf scan",
        note = if (scanCount > 0) "$scanCount saved" else "Scan a tile and tap Save Info",
        link = "Scan",
        action = onHome
    ),
    FarmTask(
        done = weekLabel != null,
        title = "Choose a farm week",
        note = weekLabel?.let { "Week $it" } ?: "Pick a week in History or Monitoring",
        link = "Monitoring",
        action = onMonitoring
    )
)

private fun profileInitials(profile: FarmerProfile, email: String): String {
    val first = profile.firstName.trim()
    val last = profile.lastName.trim()
    if (first.isNotEmpty() && last.isNotEmpty()) {
        return "${first.first()}${last.first()}".uppercase()
    }
    val parts = profile.displayName.split(Regex("\\s+")).filter { it.isNotEmpty() }
    if (parts.size >= 2) return "${parts.first().first()}${parts.last().first()}".uppercase()
    if (parts.isNotEmpty()) return parts.first().take(2).uppercase()
    return email.take(2).uppercase().ifBlank { "?" }
}

private fun monthTitle(year: Int, month: Int): String {
    val cal = Calendar.getInstance()
    cal.set(year, month, 1)
    return SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(cal.time).uppercase()
}

private fun profileIso(calendar: Calendar): String =
    "%04d-%02d-%02d".format(
        calendar.get(Calendar.YEAR),
        calendar.get(Calendar.MONTH) + 1,
        calendar.get(Calendar.DAY_OF_MONTH)
    )

private fun ScanCalendar.describe(): String = when {
    !enabled -> "Turn this on to pick the first scan day and the next week."
    nextDue.isNotBlank() ->
        "Week ${weekNumberForIso(nextDue)} is scheduled for ${formatFarmDate(nextDue)}. Saved plants show that reminder on Home. Save Info updates that week."
    anchor.isNotBlank() ->
        "Week 4 starts ${formatFarmDate(anchor)}. Scan once, then pick a date 7 days later."
    else -> "Tap the first scan date. Scanning that day saves Week 4 without a second Save Info."
}

private fun ScanCalendar.chooseDate(iso: String): Pair<ScanCalendar, String> {
    if (!enabled) return this to ""
    val fromAnchor = daysBetween(anchor, iso)
    if (anchor.isBlank() || fromAnchor == null || fromAnchor < 0) {
        val next = copy(anchor = iso, selected = iso, nextDue = "")
        return next to "Week 4 starts ${formatFarmDate(iso)}. Scan each plant once, then choose a date 7 days later for Week 5."
    }
    if (fromAnchor >= 7) {
        val next = copy(selected = iso, nextDue = iso)
        return next to "Week ${next.weekNumberForIso(iso)} needs a scan on ${formatFarmDate(iso)}. Saving a plant updates that week instead of starting another one."
    }
    val next = copy(selected = iso)
    return next to "Choose a date at least 7 days after ${formatFarmDate(anchor)} to schedule Week 5."
}

private fun ScanCalendar.weekNumberForIso(iso: String): Int {
    if (anchor.isBlank() || iso.isBlank()) return FIRST_FARM_WEEK
    val days = daysBetween(anchor, iso) ?: return FIRST_FARM_WEEK
    if (days < 0) return FIRST_FARM_WEEK
    return (FIRST_FARM_WEEK + days / 7).coerceAtMost(LAST_FARM_WEEK)
}

private fun daysBetween(startIso: String, endIso: String): Int? {
    val start = parseFarmDay(startIso) ?: return null
    val end = parseFarmDay(endIso) ?: return null
    return ((end - start) / 86_400_000L).toInt()
}

private fun parseFarmDay(iso: String): Long? {
    if (!Regex("\\d{4}-\\d{2}-\\d{2}").matches(iso)) return null
    return runCatching {
        SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { isLenient = false }.parse(iso)?.time
    }.getOrNull()
}

private fun formatFarmDate(iso: String): String {
    val millis = parseFarmDay(iso) ?: return iso
    return SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(millis)
}

private fun decodeProfilePhoto(context: android.content.Context, uri: Uri): Bitmap? {
    val resolver = context.contentResolver
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    var sample = 1
    while (bounds.outWidth / sample > 1280 || bounds.outHeight / sample > 1280) sample *= 2
    val decoded = resolver.openInputStream(uri)?.use {
        BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
    } ?: return null
    return decoded
}
