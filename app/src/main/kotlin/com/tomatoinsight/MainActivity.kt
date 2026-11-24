package com.tomatoinsight

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.navigation.NavType
import androidx.navigation.createGraph
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.auth.UserProfileChangeRequest
import com.tomatoinsight.api.ValidationResponse
import com.tomatoinsight.api.ValidationRetrofitClient
import com.tomatoinsight.data.AccountCredits
import com.tomatoinsight.data.CreditPack
import com.tomatoinsight.data.CreditRepository
import com.tomatoinsight.data.FIRST_FARM_WEEK
import com.tomatoinsight.data.LAST_FARM_WEEK
import com.tomatoinsight.data.MAX_SAVED_WEEKS
import com.tomatoinsight.data.FarmAccountIsolation
import com.tomatoinsight.data.FarmDeletion
import com.tomatoinsight.data.FarmerProfile
import com.tomatoinsight.data.FirestoreFarmRepository
import com.tomatoinsight.data.PaymentRecord
import com.tomatoinsight.data.PROFILE_BONUS_CREDITS
import com.tomatoinsight.data.SIGNUP_CREDITS
import com.tomatoinsight.data.ScanSaveOutcome
import com.tomatoinsight.data.isFarmSetupComplete
import com.tomatoinsight.data.remainingCreditsLine
import com.tomatoinsight.data.SavedRecommendation
import com.tomatoinsight.data.ScanCalendar
import com.tomatoinsight.data.asSavedRecommendation
import com.tomatoinsight.data.UserFarmRepository
import com.tomatoinsight.data.latestFor
import com.tomatoinsight.data.scansFor
import com.tomatoinsight.data.activeDeletedTiles
import com.tomatoinsight.data.asOfIso
import com.tomatoinsight.data.keepOfflineOnlyPlants
import com.tomatoinsight.data.mergeDeletions
import com.tomatoinsight.data.mergeFarmerProfiles
import com.tomatoinsight.data.mergePlantNames
import com.tomatoinsight.data.mergeRecommendations
import com.tomatoinsight.data.mergeScanHistories
import com.tomatoinsight.data.toTileScanResult
import com.tomatoinsight.data.todayIsoLocal
import com.tomatoinsight.data.withAuthFallback
import com.tomatoinsight.ui.components.CreditToast
import com.tomatoinsight.ui.components.CreditToastHost
import com.tomatoinsight.ui.components.OutOfCreditDialog
import com.tomatoinsight.ui.components.PageLoadOverlay
import com.tomatoinsight.ui.screens.AddBlueprintScreen
import com.tomatoinsight.ui.screens.CreditPlansScreen
import com.tomatoinsight.ui.screens.DashboardScreen
import com.tomatoinsight.ui.screens.HistoryScreen
import com.tomatoinsight.ui.screens.ProfileSaveResult
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import com.tomatoinsight.ui.screens.LoginScreen
import com.tomatoinsight.ui.screens.OnboardingScreen
import com.tomatoinsight.ui.screens.hasSeenOnboarding
import com.tomatoinsight.ui.screens.markOnboardingSeen
import com.tomatoinsight.ui.screens.MonitoringScreen
import com.tomatoinsight.ui.screens.TileScanDraft
import com.tomatoinsight.ui.screens.PlantInfoScreen
import com.tomatoinsight.ui.screens.ProfileScreen
import com.tomatoinsight.ui.screens.SignupScreen
import com.tomatoinsight.ui.screens.SplashScreen
import com.tomatoinsight.ui.screens.VerifyEmailScreen
import com.tomatoinsight.ui.screens.canResumeRememberedAccount
import com.tomatoinsight.ui.screens.appRouteAfterSplash
import com.tomatoinsight.ui.screens.TileMonitorDetailScreen
import com.tomatoinsight.data.PendingSignupStore
import com.tomatoinsight.ui.theme.TomatoInsightTheme
import com.tomatoinsight.util.hasValidatedInternet
import com.tomatoinsight.util.observeValidatedInternet

class MainActivity : ComponentActivity() {
    var onNotificationPermissionResult: ((Boolean) -> Unit)? = null

    private val notificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        onNotificationPermissionResult?.invoke(granted)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Restore navigation on rotation, but start a new task or process at its auth gate.
        val recreatingActivity = savedInstanceState?.getString(PROCESS_STATE_KEY) == processId
        super.onCreate(if (recreatingActivity) savedInstanceState else null)
        if (!recreatingActivity) {
            val auth = FirebaseAuth.getInstance()
            val user = auth.currentUser
            if (user != null && user.isEmailVerified && !canResumeRememberedAccount(this, user.uid, user.email)) {
                auth.signOut()
            }
        }
        ValidationRetrofitClient.init(this)
        ensureFarmAlertChannel(this)
        FirebaseMessaging.getInstance().token.addOnSuccessListener { token ->
            Log.d("TomatoInsight", "FCM token: $token")
        }
        setContent {
            TomatoInsightTheme {
                TomatoInsightApp()
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(PROCESS_STATE_KEY, processId)
    }

    fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            onNotificationPermissionResult?.invoke(true)
        }
    }

    companion object {
        private const val PROCESS_STATE_KEY = "tomatoinsight.activity.process"
        private val processId = java.util.UUID.randomUUID().toString()

        /** Cleared when the app process starts; splash runs once per cold start. */
        var splashFinishedThisProcess: Boolean = false
    }
}

@Composable
fun TomatoInsightApp() {
    val navController = rememberNavController()
    val auth = FirebaseAuth.getInstance()
    val context = LocalContext.current
    val farmRepository = remember { UserFarmRepository(context) }
    val firestoreFarmRepository = remember { FirestoreFarmRepository() }
    val creditRepository = remember { CreditRepository(context) }
    val scope = rememberCoroutineScope()
    val signedInAtStart = auth.currentUser?.takeIf { it.isEmailVerified }?.uid

    var farmRows by remember { mutableIntStateOf(0) }
    var farmColumns by remember { mutableIntStateOf(0) }
    var plantNames by remember { mutableStateOf<Map<Int, String>>(emptyMap()) }
    var plantRecommendations by remember { mutableStateOf<Map<Int, SavedRecommendation>>(emptyMap()) }
    var farmDeletions by remember { mutableStateOf<List<FarmDeletion>>(emptyList()) }
    var farmerProfile by remember { mutableStateOf(FarmerProfile()) }
    var selectedFarmWeek by remember { mutableIntStateOf(4) }
    var scanCalendar by remember { mutableStateOf(ScanCalendar()) }
    var cloudError by remember { mutableStateOf<String?>(null) }
    var accountCredits by remember {
        mutableStateOf(signedInAtStart?.let { creditRepository.visibleAccount(it) })
    }
    var creditsReady by remember { mutableStateOf(signedInAtStart == null || accountCredits != null) }
    var creditToasts by remember { mutableStateOf<List<CreditToast>>(emptyList()) }
    var showOutOfCredit by remember { mutableStateOf(false) }
    var checkoutBusy by remember { mutableStateOf(false) }
    var payments by remember { mutableStateOf<List<PaymentRecord>>(emptyList()) }
    var paymentsNote by remember { mutableStateOf<String?>(null) }

    var selectedTile by remember { mutableStateOf<Int?>(null) }
    var monitoringTile by remember { mutableStateOf<Int?>(null) }
    var monitoringWeek by remember { mutableStateOf<Int?>(null) }
    var scanRevisions by remember { mutableStateOf<Map<Int, String>>(emptyMap()) }
    var viewingSavedScan by remember { mutableStateOf(false) }
    val tileScanHistory = remember { mutableStateMapOf<Int, MutableList<com.tomatoinsight.data.TileScanResult>>() }

    var currentUserId by remember {
        mutableStateOf(auth.currentUser?.takeIf { it.isEmailVerified }?.uid)
    }

    val scanDrafts by rememberUpdatedState(remember(currentUserId) { mutableStateMapOf<Int, TileScanDraft>() })

    var online by remember { mutableStateOf(context.hasValidatedInternet()) }
    LaunchedEffect(context) {
        context.observeValidatedInternet().collect { online = it }
    }
    var syncAttempt by remember(currentUserId) { mutableIntStateOf(0) }
    var localLoadError by remember(currentUserId, syncAttempt) { mutableStateOf<String?>(null) }
    var localFarmReady by remember(currentUserId, syncAttempt) { mutableStateOf(false) }
    var farmSnapshotApplied by remember(currentUserId, online, syncAttempt) { mutableStateOf(false) }
    var syncComplete by remember(currentUserId, online, syncAttempt) { mutableStateOf(false) }
    var syncError by remember(currentUserId, online, syncAttempt) { mutableStateOf<String?>(null) }
    val farmLoading = currentUserId != null && localLoadError == null && syncError == null &&
        (!localFarmReady || (online && (!farmSnapshotApplied || !syncComplete)))

    DisposableEffect(auth) {
        val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            val user = firebaseAuth.currentUser
            // Unverified sessions (signup / verify polling) must not enter the farm graph.
            currentUserId = if (user != null && user.isEmailVerified) user.uid else null
        }
        auth.addAuthStateListener(listener)
        onDispose { auth.removeAuthStateListener(listener) }
    }

    fun showCreditToast(title: String, text: String, kind: String) {
        creditToasts = creditToasts + CreditToast(
            id = System.nanoTime(),
            title = title,
            text = text,
            kind = kind
        )
    }

    fun selectPack(pack: CreditPack) {
        val userId = currentUserId ?: return
        if (!context.hasValidatedInternet() || checkoutBusy) return
        scope.launch {
            checkoutBusy = true
            try {
                val result = creditRepository.addTopUpCredits(userId, pack)
                if (!result.ok) {
                    showCreditToast("Could not add credits", result.message, "info")
                    return@launch
                }
                result.account?.let { accountCredits = it }
                showOutOfCredit = false
                showCreditToast("+${pack.credits} credits added", "", "grant")
                val listed = creditRepository.listPayments(userId)
                payments = listed.rows
                paymentsNote = listed.error
            } finally {
                checkoutBusy = false
            }
        }
    }

    fun openCreditPlans() {
        if (!context.hasValidatedInternet()) return
        navController.navigate("plans")
    }

    fun tileCountNow(): Int = if (farmRows > 0 && farmColumns > 0) farmRows * farmColumns else 0

    fun scanCountNow(): Int = tileScanHistory.count { it.value.isNotEmpty() }

    fun farmWeekNow(): Int? = selectedFarmWeek.takeIf { it in FIRST_FARM_WEEK..LAST_FARM_WEEK }

    fun persistFarmData() {
        val userId = currentUserId ?: return
        scope.launch {
            farmRepository.saveForUser(
                userId = userId,
                rows = farmRows,
                columns = farmColumns,
                history = tileScanHistory.mapValues { it.value.toList() },
                plantNames = plantNames,
                deletions = farmDeletions
            )
        }
    }

    fun removePlantTile(tile: Int) {
        scanDrafts.remove(tile)
        val name = plantNames[tile]?.trim().orEmpty().ifBlank { "Tile $tile" }
        val removed = tileScanHistory.remove(tile).orEmpty()
        val nextNames = plantNames - tile
        plantNames = nextNames
        plantRecommendations = plantRecommendations - tile
        val now = System.currentTimeMillis()
        val entry = FarmDeletion(
            id = "$tile-$now",
            tileNumber = tile,
            plantName = name,
            deletedAtMillis = now,
            deletedAtLabel = java.text.SimpleDateFormat(
                "MMM d, yyyy · h:mm a",
                java.util.Locale.getDefault()
            ).format(java.util.Date(now))
        )
        val nextDeletions = listOf(entry) + farmDeletions.filter { it.tileNumber != tile }
        farmDeletions = nextDeletions
        if (selectedTile == tile) {
            selectedTile = null
            viewingSavedScan = false
        }
        if (monitoringTile == tile) {
            monitoringTile = null
            monitoringWeek = null
        }
        val userId = currentUserId ?: return
        val history = tileScanHistory.mapValues { it.value.toList() }
        val rows = farmRows
        val columns = farmColumns
        scope.launch {
            farmRepository.deleteScanImages(userId, removed.map { it.scanId })
            farmRepository.saveForUser(
                userId = userId,
                rows = rows,
                columns = columns,
                history = history,
                plantNames = nextNames,
                deletions = nextDeletions
            )
            runCatching {
                firestoreFarmRepository.deleteTileData(
                    uid = userId,
                    tileNumber = tile,
                    plantName = name,
                    deletionId = entry.id
                )
            }.onFailure { error ->
                cloudError = error.message ?: "Could not delete this plant from the website."
            }
        }
    }

    fun storeMobileRecommendation(tile: Int, response: ValidationResponse, scanId: Long? = null) {
        if (!response.hasAiRecommendations) return
        val guide = response.asSavedRecommendation() ?: return
        plantRecommendations = plantRecommendations + (tile to guide)
        val current = tileScanHistory[tile].orEmpty()
        val index = if (scanId != null) {
            current.indexOfFirst { it.scanId == scanId }
        } else {
            current.indices.maxByOrNull { current[it].scannedAtMillis } ?: -1
        }
        val savedScan = if (index >= 0) {
            val updated = current[index].copy(
                response = current[index].response.copy(
                    aiRecommendations = response.aiRecommendations,
                    aiRecommendationError = response.aiRecommendationError,
                    aiDisclaimer = response.aiDisclaimer,
                    recommendationSource = response.recommendationSource,
                    groqModel = response.groqModel,
                    severityLabel = response.severityLabel,
                    affectedAreaPct = response.affectedAreaPct,
                    severityAssessable = response.severityAssessable,
                    severityReason = response.severityReason
                )
            )
            val next = current.toMutableList()
            next[index] = updated
            tileScanHistory[tile] = next
            updated
        } else {
            null
        }
        persistFarmData()
        val userId = currentUserId ?: return
        val scan = savedScan ?: return
        val name = plantNames[tile].orEmpty()
        scope.launch {
            runCatching {
                firestoreFarmRepository.savePlantRecommendation(
                    uid = userId,
                    tileNumber = tile,
                    plantName = name,
                    scan = scan
                )
            }.onFailure { cloudError = it.message }
        }
    }

    LaunchedEffect(currentUserId, syncAttempt) {
        tileScanHistory.clear()
        scanRevisions = emptyMap()
        selectedTile = null
        monitoringTile = null
        monitoringWeek = null
        viewingSavedScan = false
        farmRows = 0
        farmColumns = 0
        plantNames = emptyMap()
        plantRecommendations = emptyMap()
        farmDeletions = emptyList()
        farmerProfile = FarmerProfile()
        selectedFarmWeek = 4
        scanCalendar = ScanCalendar()
        cloudError = null

        val userId = currentUserId
        if (userId == null) {
            FarmAccountIsolation.onSignedOut(context)
            return@LaunchedEffect
        }
        // Drop shared reminder cache if another account still owned this device session.
        FarmAccountIsolation.discardForeignSharedCache(context, userId)

        val loaded = try {
            farmRepository.loadForUser(userId)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            localLoadError = e.message ?: "Could not load your saved farm."
            return@LaunchedEffect
        }
        val localHistory = loaded.history
        val localRows = loaded.rows
        val localColumns = loaded.columns
        farmRows = localRows
        farmColumns = localColumns
        localHistory.forEach { (tile, scans) ->
            tileScanHistory[tile] = scans.toMutableList()
        }
        plantNames = loaded.plantNames
        farmDeletions = loaded.deletions
        // Tag this uid as owner after loading its Room rows (before any cloud upload).
        FarmAccountIsolation.claimOwner(context, userId)

        localFarmReady = true
    }

    LaunchedEffect(currentUserId, localFarmReady, online, syncAttempt) {
        val userId = currentUserId ?: return@LaunchedEffect
        if (!localFarmReady) return@LaunchedEffect
        try {
            firestoreFarmRepository.observe(userId, requireServer = online).collect { update ->
                if (update.error != null) {
                    syncError = update.error
                    return@collect
                }
                val farm = update.farm
                scanRevisions = farm.scanRevisions
                val disk = farmRepository.loadForUser(userId)
                val mergedDeletions = mergeDeletions(disk.deletions + farmDeletions, farm.deletions)
                val memoryHistory = tileScanHistory.mapValues { it.value.toList() }
                val localHistoryNow = mergeScanHistories(memoryHistory, disk.history)
                val localNames = mergePlantNames(
                    local = plantNames,
                    cloud = disk.plantNames,
                    localHistory = memoryHistory,
                    cloudHistory = disk.history
                )
                val newDeletions = mergedDeletions.filter { it !in farmDeletions }
                val deletedTiles = activeDeletedTiles(mergedDeletions, localHistoryNow, farm.history)
                val mergedHistory = mergeScanHistories(localHistoryNow, farm.history, deletedTiles)
                val mergedNames = mergePlantNames(
                    local = localNames,
                    cloud = farm.plantNames,
                    localHistory = localHistoryNow,
                    cloudHistory = farm.history,
                    deletedTiles = deletedTiles
                )
                val (keptHistory, keptNames) = keepOfflineOnlyPlants(
                    mergedHistory = mergedHistory,
                    mergedNames = mergedNames,
                    localHistory = localHistoryNow,
                    localNames = localNames,
                    cloudHistory = farm.history,
                    cloudNames = farm.plantNames,
                    deletedTiles = deletedTiles
                )
                val mergedRecommendations = mergeRecommendations(
                    local = plantRecommendations,
                    cloud = farm.recommendations,
                    localHistory = localHistoryNow,
                    cloudHistory = farm.history,
                    deletedTiles = deletedTiles
                )
                if (farm.rows > 0 && farm.columns > 0) {
                    farmRows = farm.rows
                    farmColumns = farm.columns
                }
                plantNames = keptNames
                plantRecommendations = mergedRecommendations
                farmDeletions = mergedDeletions
                val authUser = auth.currentUser
                val mergedProfile = mergeFarmerProfiles(farmerProfile, farm.profile)
                    .withAuthFallback(authUser?.displayName, authUser?.email)
                if (mergedProfile.displayName.isNotBlank() || mergedProfile.farmName.isNotBlank() ||
                    mergedProfile.photo != null || mergedProfile.email.isNotBlank()
                ) {
                    farmerProfile = mergedProfile
                }
                if (farm.selectedFarmWeek in FIRST_FARM_WEEK..LAST_FARM_WEEK) {
                    selectedFarmWeek = farm.selectedFarmWeek
                }
                if (farm.scanCalendar.enabled || farm.scanCalendar.anchor.isNotBlank() ||
                    farm.scanCalendar.selected.isNotBlank() || farm.scanCalendar.nextDue.isNotBlank()
                ) {
                    scanCalendar = farm.scanCalendar
                }

                tileScanHistory.clear()
                keptHistory.forEach { (tile, scans) ->
                    tileScanHistory[tile] = scans.toMutableList()
                }
                val newlyDeletedTiles = newDeletions.map { it.tileNumber }.intersect(deletedTiles)
                val validTiles = 1..(farmRows * farmColumns)
                scanDrafts.keys.toList().filter { it !in validTiles || it in newlyDeletedTiles }.forEach { scanDrafts.remove(it) }
                if (selectedTile?.let { it !in validTiles || it in newlyDeletedTiles } == true) {
                    selectedTile = null
                    viewingSavedScan = false
                }
                if (monitoringTile != null && monitoringTile !in keptHistory) {
                    monitoringTile = null
                    monitoringWeek = null
                }
                farmRepository.saveForUser(
                    userId = userId,
                    rows = farmRows,
                    columns = farmColumns,
                    history = tileScanHistory.mapValues { it.value.toList() },
                    plantNames = plantNames,
                    deletions = farmDeletions
                )
                FarmAccountIsolation.claimOwner(context, userId)
                refreshScanReminder(
                    context,
                    scanCalendar,
                    farmRows,
                    farmColumns,
                    tileScanHistory.mapValues { it.value.toList() },
                    plantNames
                )
                farmSnapshotApplied = true
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            syncError = e.message ?: "Could not load your farm."
        }
    }

    LaunchedEffect(currentUserId) {
        val uid = currentUserId
        if (uid == null) {
            accountCredits = null
            creditsReady = true
            payments = emptyList()
            paymentsNote = null
            creditToasts = emptyList()
            showOutOfCredit = false
            return@LaunchedEffect
        }
        val cached = creditRepository.visibleAccount(uid)
        if (cached != null) {
            accountCredits = cached
            creditsReady = true
        } else {
            creditsReady = false
        }
        launch {
            creditRepository.observe(uid).collect { next ->
                accountCredits = next
                creditsReady = true
            }
        }
        val synced = runCatching {
            withTimeout(15_000) { creditRepository.ensureAccountCredits(uid) }
        }.getOrNull()
        if (synced != null) {
            accountCredits = synced.account
            if (synced.signupCreditsJustGranted && creditRepository.claimToast(uid, "signup")) {
                showCreditToast(
                    "Welcome credits added",
                    "You received $SIGNUP_CREDITS free scan credits to start scanning your farm.",
                    "success"
                )
            }
        }
        creditsReady = true
        val listed = runCatching { creditRepository.listPayments(uid) }.getOrNull()
        if (listed != null) {
            payments = listed.rows
            paymentsNote = listed.error
        }
    }

    LaunchedEffect(currentUserId, farmLoading, farmSnapshotApplied, creditsReady, accountCredits?.profileBonusGranted) {
        val uid = currentUserId ?: return@LaunchedEffect
        val credits = accountCredits ?: return@LaunchedEffect
        if (!creditsReady || farmLoading || !farmSnapshotApplied || credits.profileBonusGranted) return@LaunchedEffect
        delay(600)
        if (isFarmSetupComplete(farmerProfile, tileCountNow(), scanCountNow(), farmWeekNow())) return@LaunchedEffect
        if (creditRepository.claimToast(uid, "profile")) {
            showCreditToast(
                "Earn 5 more credits",
                "Finish all six farm setup steps, then tap Update profile to add $PROFILE_BONUS_CREDITS extra scan credits.",
                "accent"
            )
        }
    }

    LaunchedEffect(currentUserId, online, farmSnapshotApplied, syncAttempt) {
        val uid = currentUserId ?: return@LaunchedEffect
        if (!online || !farmSnapshotApplied) return@LaunchedEffect
        try {
            check(FarmAccountIsolation.mayUploadLocalFarm(context, uid)) {
                "Could not confirm the owner of this farm."
            }
            firestoreFarmRepository.syncLocalFarm(
                uid = uid, rows = farmRows, columns = farmColumns,
                plantNames = plantNames, history = tileScanHistory.mapValues { it.value.toList() },
                profile = farmerProfile, calendar = scanCalendar, week = selectedFarmWeek,
                deletions = farmDeletions
            )
            accountCredits = creditRepository.applyPendingSpends(uid).account
            syncComplete = true
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            syncError = e.message ?: "Could not sync your farm."
        }
    }

    val navEntry by navController.currentBackStackEntryAsState()
    val route = navEntry?.destination?.route
    val pageLoading = currentUserId != null && (farmLoading || (online && !creditsReady))
    // Destinations in the remembered graph must read the current account's loading and retry state.
    val dashboardFarmLoading by rememberUpdatedState(currentUserId == null || farmLoading)
    val dashboardFarmError by rememberUpdatedState(localLoadError ?: syncError ?: if (
        !online && localFarmReady && (farmRows == 0 || farmColumns == 0)
    ) "Connect to load your farm layout." else null)
    val retryFarmSync by rememberUpdatedState<() -> Unit>({ syncAttempt++ })
    val loadMessage = if (pageLoading && route !in setOf("onboarding", "splash")) {
        if (online && farmLoading) "Syncing your farm…" else "Loading your farm…"
    } else null

    fun openSignedOutStart() {
        val next = if (hasSeenOnboarding(context)) "login" else "onboarding"
        navController.navigate(next) {
            popUpTo(0) { inclusive = true }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
    val navGraph = remember(navController) {
        val startDestination = if (MainActivity.splashFinishedThisProcess) {
            appRouteAfterSplash(auth, context)
        } else {
            "splash"
        }
        navController.createGraph(startDestination = startDestination) {
        composable("splash") {
            SplashScreen(
                onFinished = {
                    MainActivity.splashFinishedThisProcess = true
                    val next = appRouteAfterSplash(auth, context)
                    navController.navigate(next) {
                        popUpTo("splash") { inclusive = true }
                    }
                }
            )
        }
        composable("onboarding") {
            OnboardingScreen(
                onFinished = {
                    markOnboardingSeen(context)
                    navController.navigate("login") {
                        popUpTo("onboarding") { inclusive = true }
                    }
                }
            )
        }
        composable("login") {
            LoginScreen(
                onLoginSuccess = {
                    navController.navigate("dashboard") {
                        popUpTo("login") { inclusive = true }
                    }
                },
                onNavigateToSignup = {
                    if (PendingSignupStore.hasPending(context)) {
                        navController.navigate("verify")
                    } else {
                        navController.navigate("signup")
                    }
                },
                onNeedsVerification = {
                    navController.navigate("verify") {
                        launchSingleTop = true
                    }
                }
            )
        }
        composable(
            route = "signup",
            enterTransition = { forwardEnter() },
            exitTransition = { forwardExit() },
            popEnterTransition = { forwardPopEnter() },
            popExitTransition = { forwardPopExit() }
        ) {
            SignupScreen(
                onNeedsVerification = {
                    navController.navigate("verify") {
                        popUpTo("signup") { inclusive = true }
                        launchSingleTop = true
                    }
                },
                onNavigateToLogin = {
                    if (PendingSignupStore.hasPending(context)) {
                        navController.navigate("verify") {
                            launchSingleTop = true
                        }
                    } else {
                        navController.navigate("login")
                    }
                }
            )
        }
        composable(
            route = "verify",
            enterTransition = { forwardEnter() },
            exitTransition = { forwardExit() },
            popEnterTransition = { forwardPopEnter() },
            popExitTransition = { forwardPopExit() }
        ) {
            VerifyEmailScreen(
                onVerified = {
                    navController.navigate("dashboard") {
                        popUpTo(0) { inclusive = true }
                    }
                },
                onBackToLogin = {
                    navController.navigate("login") {
                        popUpTo("verify") { inclusive = true }
                    }
                }
            )
        }
        composable("dashboard") {
            DashboardScreen(
                draftTiles = scanDrafts.keys.toSet(),
                rows = farmRows,
                columns = farmColumns,
                farmerName = farmerProfile.displayName,
                farmName = farmerProfile.farmName,
                plantNames = plantNames,
                selectedFarmWeek = selectedFarmWeek,
                scanCalendar = scanCalendar,
                scanCalendarNote = scanCalendar.dashboardNote(),
                farmLoading = dashboardFarmLoading,
                farmUnavailable = dashboardFarmError,
                onRetrySync = retryFarmSync,
                creditBalance = accountCredits?.balance,
                creditsLoaded = creditsReady && accountCredits != null,
                profileBonusGranted = accountCredits?.profileBonusGranted ?: true,
                onOpenCreditPlans = { openCreditPlans() },
                cloudError = cloudError,
                onLogout = { openSignedOutStart() },
                onNavigateToAddBlueprint = {
                    navController.navigate("add_blueprint")
                },
                onNavigateToMonitoring = {
                    navController.navigate("monitoring")
                },
                onNavigateToHistory = {
                    navController.navigate("history")
                },
                onNavigateToProfile = {
                    navController.navigate("profile")
                },
                selectedTile = selectedTile,
                tileScanHistory = tileScanHistory,
                onTileSelected = { tile ->
                    selectedTile = if (selectedTile == tile) null else tile
                },
                onTileDoubleTap = { tile ->
                    if (tile in scanDrafts || tileScanHistory.latestFor(tile) != null) {
                        selectedTile = tile
                        viewingSavedScan = true
                        navController.navigate("plant_info")
                    }
                },
                onDeleteTile = { tile ->
                    removePlantTile(tile)
                },
                onCameraClick = {
                    if (selectedTile != null) {
                        viewingSavedScan = false
                        navController.navigate("plant_info")
                    }
                }
            )
        }
        composable("monitoring") {
            MonitoringScreen(
                onNavigateToHome = {
                    navController.navigate("dashboard") {
                        popUpTo("dashboard") { inclusive = true }
                    }
                },
                onNavigateToHistory = {
                    navController.navigate("history")
                },
                onNavigateToProfile = {
                    navController.navigate("profile")
                },
                onCameraClick = {
                    viewingSavedScan = false
                    navController.navigate("plant_info")
                },
                tileScanHistory = tileScanHistory,
                plantNames = plantNames,
                selectedTile = monitoringTile,
                selectedWeek = monitoringWeek,
                onSelectedTileChange = { monitoringTile = it },
                onSelectedWeekChange = { monitoringWeek = it }
            )
        }
        composable(
            route = "tile_monitor/{tileNumber}",
            arguments = listOf(navArgument("tileNumber") { type = NavType.IntType }),
            enterTransition = { forwardEnter() },
            exitTransition = { forwardExit() },
            popEnterTransition = { forwardPopEnter() },
            popExitTransition = { forwardPopExit() }
        ) { backStackEntry ->
            val tileNumber = backStackEntry.arguments?.getInt("tileNumber") ?: return@composable
            TileMonitorDetailScreen(
                tileNumber = tileNumber,
                scans = tileScanHistory.scansFor(tileNumber),
                onNavigateBack = { navController.popBackStack() },
                onDeleteTileData = {
                    removePlantTile(tileNumber)
                    navController.popBackStack()
                }
            )
        }
        composable("history") {
            HistoryScreen(
                onNavigateToHome = {
                    navController.navigate("dashboard") {
                        popUpTo("dashboard") { inclusive = true }
                    }
                },
                onNavigateToMonitoring = {
                    navController.navigate("monitoring")
                },
                onNavigateToProfile = {
                    navController.navigate("profile")
                },
                onCameraClick = {
                    viewingSavedScan = false
                    navController.navigate("plant_info")
                },
                tileScanHistory = tileScanHistory,
                plantNames = plantNames,
                recommendations = plantRecommendations,
                deletions = farmDeletions,
                selectedFarmWeek = selectedFarmWeek,
                scanCalendar = scanCalendar,
                cloudError = cloudError,
                onWeekSelected = { week ->
                    selectedFarmWeek = week
                    val userId = currentUserId
                    if (userId != null) {
                        scope.launch {
                            runCatching { firestoreFarmRepository.saveSelectedFarmWeek(userId, week) }
                                .onFailure { cloudError = it.message }
                        }
                    }
                },
                onRecommendationSaved = { scan, response ->
                    storeMobileRecommendation(scan.tileNumber, response, scan.scanId)
                }
            )
        }
        composable("profile") {
            ProfileScreen(
                onNavigateToHome = {
                    navController.navigate("dashboard") {
                        popUpTo("dashboard") { inclusive = true }
                    }
                },
                onNavigateToMonitoring = {
                    navController.navigate("monitoring")
                },
                onNavigateToHistory = {
                    navController.navigate("history")
                },
                onCameraClick = {
                    viewingSavedScan = false
                    navController.navigate("plant_info")
                },
                onLogout = { openSignedOutStart() },
                farmerProfile = farmerProfile,
                farmRows = farmRows,
                farmColumns = farmColumns,
                scanCount = tileScanHistory.count { it.value.isNotEmpty() },
                selectedFarmWeek = selectedFarmWeek,
                scanCalendar = scanCalendar,
                creditBalance = accountCredits?.balance,
                creditsLoaded = creditsReady && accountCredits != null,
                profileBonusGranted = accountCredits?.profileBonusGranted ?: false,
                onOpenCreditPlans = { openCreditPlans() },
                onProfileSaved = { profile, includePhoto ->
                    val keptPhoto = if (includePhoto) profile.photo else farmerProfile.photo
                    val next = mergeFarmerProfiles(
                        farmerProfile,
                        profile.copy(photo = keptPhoto)
                    ).copy(photo = keptPhoto)
                    farmerProfile = next
                    if (next.displayName.isNotBlank()) {
                        auth.currentUser?.updateProfile(
                            UserProfileChangeRequest.Builder()
                                .setDisplayName(next.displayName)
                                .build()
                        )
                    }
                    val userId = currentUserId
                        ?: return@ProfileScreen ProfileSaveResult(
                            true,
                            if (includePhoto) "Photo saved." else "Your details were saved."
                        )
                    try {
                        firestoreFarmRepository.saveProfile(userId, next, includePhoto)
                    } catch (error: Exception) {
                        cloudError = error.message
                        return@ProfileScreen ProfileSaveResult(
                            false,
                            error.message ?: "Could not save profile."
                        )
                    }
                    if (includePhoto) {
                        return@ProfileScreen ProfileSaveResult(true, "Photo saved.")
                    }
                    val grant = runCatching {
                        creditRepository.ensureAccountCredits(
                            uid = userId,
                            profile = next,
                            tileCount = tileCountNow(),
                            scanCount = scanCountNow(),
                            farmWeek = farmWeekNow(),
                            grantProfileBonus = true
                        )
                    }.getOrNull()
                    if (grant == null) {
                        return@ProfileScreen ProfileSaveResult(true, "Your details were saved.")
                    }
                    accountCredits = grant.account
                    if (grant.profileBonusJustGranted) {
                        showCreditToast(
                            "+$PROFILE_BONUS_CREDITS credits added",
                            remainingCreditsLine(grant.account.balance),
                            "grant"
                        )
                        ProfileSaveResult(
                            true,
                            "Your details were saved. 5 credits were added because farm setup is complete."
                        )
                    } else {
                        ProfileSaveResult(true, "Your details were saved.")
                    }
                },
                onScanCalendarChanged = { calendar ->
                    scanCalendar = calendar
                    val weekFromDates = calendar.resolvedFarmWeek()
                    if (weekFromDates != null) {
                        selectedFarmWeek = weekFromDates
                    }
                    refreshScanReminder(
                        context,
                        calendar,
                        farmRows,
                        farmColumns,
                        tileScanHistory.mapValues { it.value.toList() },
                        plantNames
                    )
                    val userId = currentUserId
                    if (userId != null) {
                        scope.launch {
                            runCatching { firestoreFarmRepository.saveScanCalendar(userId, calendar) }
                                .onFailure { cloudError = it.message }
                            if (weekFromDates != null) {
                                runCatching {
                                    firestoreFarmRepository.saveSelectedFarmWeek(userId, weekFromDates)
                                }.onFailure { cloudError = it.message }
                            }
                        }
                    }
                }
            )
        }
        composable(
            route = "add_blueprint",
            enterTransition = { forwardEnter() },
            exitTransition = { forwardExit() },
            popEnterTransition = { forwardPopEnter() },
            popExitTransition = { forwardPopExit() }
        ) {
            val occupiedTiles = remember(plantNames, tileScanHistory.values.sumOf { it.size }) {
                buildMap {
                    plantNames.forEach { (tile, name) ->
                        if (name.isNotBlank()) put(tile, name.trim())
                    }
                    tileScanHistory.forEach { (tile, scans) ->
                        if (scans.isNotEmpty() && !containsKey(tile)) {
                            put(tile, "Tile $tile")
                        }
                    }
                }
            }
            AddBlueprintScreen(
                initialRows = farmRows,
                initialColumns = farmColumns,
                occupiedTiles = occupiedTiles,
                onNavigateBack = {
                    navController.popBackStack()
                },
                onSaveSuccess = { rows, cols ->
                    farmRows = rows
                    farmColumns = cols
                    scanDrafts.keys.toList().filter { it !in 1..(rows * cols) }.forEach { scanDrafts.remove(it) }
                    persistFarmData()
                    val userId = currentUserId
                    if (userId != null) {
                        scope.launch {
                            runCatching { firestoreFarmRepository.saveLayout(userId, rows, cols) }
                                .onFailure { cloudError = it.message }
                        }
                    }
                    navController.popBackStack()
                }
            )
        }
        composable("plans") {
            CreditPlansScreen(
                balance = if (creditsReady) accountCredits?.balance else null,
                currentPackId = accountCredits?.currentPackId,
                payments = payments,
                paymentsNote = paymentsNote,
                onBack = { navController.popBackStack() },
                onSelectPack = { selectPack(it) },
                onNavigateToHome = {
                    navController.navigate("dashboard") {
                        popUpTo("dashboard") { inclusive = true }
                    }
                },
                onNavigateToMonitoring = { navController.navigate("monitoring") },
                onNavigateToHistory = { navController.navigate("history") },
                onNavigateToProfile = { navController.navigate("profile") },
                onCameraClick = {
                    viewingSavedScan = false
                    navController.navigate("plant_info")
                }
            )
        }
        composable(
            route = "plant_info",
            enterTransition = { forwardEnter() },
            exitTransition = { forwardExit() },
            popEnterTransition = { forwardPopEnter() },
            popExitTransition = { forwardPopExit() }
        ) {
            val saved = if (viewingSavedScan) {
                selectedTile?.let { tileScanHistory.latestFor(it) }
            } else {
                null
            }
            val tileForDraft = selectedTile
            val accountDrafts = scanDrafts
            var openedRevision by remember(tileForDraft) {
                mutableStateOf(accountDrafts[tileForDraft]?.openedRevision ?: scanRevisions[tileForDraft].orEmpty())
            }
            PlantInfoScreen(
                tileNumber = tileForDraft,
                initialDraft = tileForDraft?.let { accountDrafts[it] },
                onDraftChanged = { draft ->
                    if (tileForDraft != null) {
                        if (draft == null) accountDrafts.remove(tileForDraft) else {
                            draft.openedRevision = openedRevision
                            accountDrafts[tileForDraft] = draft
                            scope.launch { draft.compactPhoto() }
                        }
                    }
                },
                savedImage = saved?.image,
                savedResult = saved?.response,
                savedScores = saved?.scores.orEmpty(),
                plantName = selectedTile?.let { plantNames[it] }.orEmpty(),
                savedRecommendation = selectedTile?.let { plantRecommendations[it] },
                onNavigateBack = {
                    navController.popBackStack()
                },
                onRecommendationSaved = { response ->
                    val tile = selectedTile ?: return@PlantInfoScreen
                    storeMobileRecommendation(tile, response)
                },
                onResultSaved = { response, bitmap, enteredName ->
                    val tile = selectedTile
                    val trimmedName = enteredName.trim()
                    if (tile == null || trimmedName.isEmpty()) {
                        return@PlantInfoScreen ScanSaveOutcome.Blocked(
                            "invalid",
                            "Enter a plant name to save this scan."
                        )
                    }
                    val drafted = response.toTileScanResult(tile, bitmap)
                        ?: return@PlantInfoScreen ScanSaveOutcome.Blocked(
                            "invalid",
                            "This scan does not have a result to save yet."
                        )
                    val userId = currentUserId
                        ?: return@PlantInfoScreen ScanSaveOutcome.Blocked(
                            "signed-out",
                            "Sign in to use scan credits."
                        )
                    val week = drafted.weekNumber
                        ?: selectedFarmWeek.takeIf { it in FIRST_FARM_WEEK..LAST_FARM_WEEK }
                        ?: scanCalendar.resolvedFarmWeek()
                        ?: FIRST_FARM_WEEK
                    // Wall-clock time for sync/deletes; calendar day only for reminders.
                    val now = System.currentTimeMillis()
                    val dayIso = if (scanCalendar.enabled) scanCalendar.asOfIso() else todayIsoLocal()
                    val stamped = drafted.copy(
                        weekNumber = week,
                        scannedAtMillis = now,
                        scanId = now,
                        scanDayIso = dayIso
                    )
                    val onlineSave = context.hasValidatedInternet()
                    val expectedRevision = openedRevision
                    val revision = java.util.UUID.randomUUID().toString()
                    var conflict: ScanSaveOutcome.TileChanged? = null
                    val charge = creditRepository.spendForSuccessfulScan(
                        uid = userId,
                        offline = !onlineSave
                    ) { tx, account ->
                        firestoreFarmRepository.writeTileScan(tx, account, tile, listOf(stamped),
                            trimmedName, expectedRevision, revision) { conflict = it }
                    }
                    if (!charge.ok) {
                        if (charge.code == "tile-changed") conflict?.let {
                            openedRevision = it.revision
                            viewingSavedScan = true
                            return@PlantInfoScreen it
                        }
                        if (charge.code == "insufficient-credits") showOutOfCredit = true
                        charge.account?.let { accountCredits = it }
                        return@PlantInfoScreen ScanSaveOutcome.Blocked(
                            charge.code,
                            charge.message.ifBlank { "Could not use a scan credit." }
                        )
                    }
                    if (onlineSave) {
                        openedRevision = revision
                        scanRevisions = scanRevisions + (tile to revision)
                    }
                    try {
                        farmDeletions = farmDeletions.filter { it.tileNumber != tile }
                        plantNames = plantNames + (tile to trimmedName)
                        if (response.hasAiRecommendations) {
                            response.asSavedRecommendation()?.let { guide ->
                                plantRecommendations = plantRecommendations + (tile to guide)
                            }
                        }
                        val existing = tileScanHistory[tile]
                            .orEmpty()
                            .filterNot { it.weekNumber == week && it.scannedAtMillis == now }
                            .sortedWith(compareBy({ it.scannedAtMillis }, { it.scanId }))
                        val removedIds = if (existing.size >= MAX_SAVED_WEEKS) {
                            listOf(existing.last().scanId)
                        } else {
                            emptyList()
                        }
                        val updated = if (existing.size >= MAX_SAVED_WEEKS) {
                            existing.dropLast(1) + stamped
                        } else {
                            existing + stamped
                        }
                        tileScanHistory[tile] = updated.toMutableList()
                        if (removedIds.isNotEmpty()) {
                            farmRepository.deleteScanImages(userId, removedIds)
                        }
                        farmRepository.saveForUser(
                            userId = userId,
                            rows = farmRows,
                            columns = farmColumns,
                            history = tileScanHistory.mapValues { it.value.toList() },
                            plantNames = plantNames,
                            deletions = farmDeletions
                        )
                        refreshScanReminder(
                            context,
                            scanCalendar,
                            farmRows,
                            farmColumns,
                            tileScanHistory.mapValues { it.value.toList() },
                            plantNames
                        )
                        charge.account?.let { accountCredits = it }
                        showCreditToast(
                            "1 credit deducted",
                            remainingCreditsLine(charge.balance),
                            "deduct"
                        )
                        ScanSaveOutcome.Saved
                    } catch (_: Exception) {
                        // An online transaction already committed the scan and its charge together.
                        if (onlineSave) return@PlantInfoScreen ScanSaveOutcome.Saved
                        creditRepository.refundScanCredit(userId, charge.queuedOffline)
                            ?.let { accountCredits = it }
                        ScanSaveOutcome.Blocked(
                            "save-failed",
                            "Your credit was returned because the save did not complete."
                        )
                    }
                }
            )
        }
        }
    }
    NavHost(
        navController = navController,
        graph = navGraph,
        modifier = Modifier.fillMaxSize(),
        enterTransition = { fadeThroughEnter() },
        exitTransition = { fadeThroughExit() },
        popEnterTransition = { fadeThroughEnter() },
        popExitTransition = { fadeThroughExit() }
    )
    var askedNotification by remember { mutableStateOf(false) }
    var reminderTick by remember { mutableIntStateOf(0) }
    val activity = context as? MainActivity
    val latestCalendar by rememberUpdatedState(scanCalendar)
    val latestRows by rememberUpdatedState(farmRows)
    val latestColumns by rememberUpdatedState(farmColumns)
    val latestHistory by rememberUpdatedState(tileScanHistory)
    val latestPlantNames by rememberUpdatedState(plantNames)

    fun pushScanReminder() {
        refreshScanReminder(
            context,
            latestCalendar,
            latestRows,
            latestColumns,
            latestHistory.mapValues { it.value.toList() },
            latestPlantNames
        )
    }

    DisposableEffect(activity) {
        activity?.onNotificationPermissionResult = { granted ->
            if (granted) {
                reminderTick += 1
                pushScanReminder()
            }
        }
        onDispose { activity?.onNotificationPermissionResult = null }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                reminderTick += 1
                pushScanReminder()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(route) {
        if (askedNotification || route == null || route == "onboarding" || route == "splash") {
            return@LaunchedEffect
        }
        askedNotification = true
        activity?.requestNotificationPermissionIfNeeded()
    }

    val historyFingerprint = tileScanHistory.entries.sumOf { (tile, scans) ->
        tile + scans.size * 31 + scans.sumOf { it.weekNumber ?: 0 }
    }
    LaunchedEffect(
        currentUserId,
        scanCalendar.enabled,
        scanCalendar.anchor,
        scanCalendar.selected,
        scanCalendar.nextDue,
        farmRows,
        farmColumns,
        historyFingerprint,
        reminderTick
    ) {
        if (currentUserId == null) return@LaunchedEffect
        pushScanReminder()
    }
    loadMessage?.let { PageLoadOverlay(it) }
    Box(modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth()) {
        CreditToastHost(
            toasts = creditToasts,
            onDismiss = { id -> creditToasts = creditToasts.filter { it.id != id } }
        )
    }
    if (showOutOfCredit) {
        OutOfCreditDialog(
            onDismiss = { showOutOfCredit = false },
            onSelectPack = { selectPack(it) },
            onViewPlans = {
                if (!context.hasValidatedInternet()) return@OutOfCreditDialog
                showOutOfCredit = false
                openCreditPlans()
            }
        )
    }
    }
}



private const val PAGE_MOTION_MS = 340
private val pageEasing = FastOutSlowInEasing

private fun fadeThroughEnter() =
    fadeIn(tween(PAGE_MOTION_MS, easing = pageEasing)) +
        scaleIn(initialScale = 0.98f, animationSpec = tween(PAGE_MOTION_MS, easing = pageEasing))

private fun fadeThroughExit() =
    fadeOut(tween(240, easing = pageEasing)) +
        scaleOut(targetScale = 0.98f, animationSpec = tween(240, easing = pageEasing))

private fun forwardEnter() =
    slideInHorizontally(tween(PAGE_MOTION_MS, easing = pageEasing)) { it / 6 } +
        fadeIn(tween(260, easing = pageEasing))

private fun forwardExit() =
    slideOutHorizontally(tween(280, easing = pageEasing)) { -it / 14 } +
        fadeOut(tween(200, easing = pageEasing))

private fun forwardPopEnter() =
    slideInHorizontally(tween(PAGE_MOTION_MS, easing = pageEasing)) { -it / 14 } +
        fadeIn(tween(260, easing = pageEasing))

private fun forwardPopExit() =
    slideOutHorizontally(tween(PAGE_MOTION_MS, easing = pageEasing)) { it / 6 } +
        fadeOut(tween(220, easing = pageEasing))
