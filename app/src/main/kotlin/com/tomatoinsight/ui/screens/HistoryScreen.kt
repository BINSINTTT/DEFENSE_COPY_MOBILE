package com.tomatoinsight.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import com.tomatoinsight.api.ValidationResponse
import com.tomatoinsight.api.WebsiteScanClient
import com.tomatoinsight.util.hasValidatedInternet
import com.tomatoinsight.util.observeValidatedInternet
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.tomatoinsight.api.diseaseDisplayLabel
import com.tomatoinsight.api.isHealthyDisease
import com.tomatoinsight.data.FIRST_FARM_WEEK
import com.tomatoinsight.data.FarmDeletion
import com.tomatoinsight.data.LAST_FARM_WEEK
import com.tomatoinsight.data.SavedRecommendation
import com.tomatoinsight.data.ScanCalendar
import com.tomatoinsight.data.TileScanHistory
import com.tomatoinsight.data.TileScanResult
import com.tomatoinsight.data.asSavedValidationResponse
import com.tomatoinsight.data.ensureGuidesForAllDetections
import com.tomatoinsight.data.recommendationCacheKey
import com.tomatoinsight.ui.components.ConnectionStatusIndicator
import com.tomatoinsight.ui.components.CustomBottomBar
import com.tomatoinsight.ui.components.InternetConnectionNotice
import com.tomatoinsight.ui.components.DiseaseTreatmentCard
import com.tomatoinsight.ui.components.HealthStatusCard
import java.text.SimpleDateFormat
import java.io.ByteArrayOutputStream
import java.util.Calendar
import java.util.Date
import java.util.Locale

private val PageBackground = Color(0xFFFFFCF9)
private val Ink = Color(0xFF202421)
private val Muted = Color(0xFF656B67)
private val SummaryBackground = Color(0xFFF1F3EC)
private val Divider = Color(0xFF202421).copy(alpha = 0.10f)
private val ForestGreen = Color(0xFF1F7A35)
private val SoftGreen = Color(0xFFE8F2E5)
private val StatusOkBg = Color(0xFFE8F5E9)
private val StatusOkFg = Color(0xFF1B5E20)
private val StatusIssueBg = Color(0xFFFFE8E3)
private val StatusIssueFg = Color(0xFFD94335)
private val StatusSavedBg = Color(0xFFECEFF1)
private val StatusSavedFg = Color(0xFF546E7A)
private val StatusDeletedBg = Color(0xFFFBE9E7)
private val StatusDeletedFg = Color(0xFF6D4C41)

private enum class HistoryFilter(val label: String) {
    All("All records"),
    Healthy("Healthy only"),
    Issues("Issues"),
    Deleted("Deleted plants")
}

@Composable
fun HistoryScreen(
    onNavigateToHome: () -> Unit,
    onNavigateToMonitoring: () -> Unit,
    onNavigateToProfile: () -> Unit,
    onCameraClick: () -> Unit,
    tileScanHistory: TileScanHistory = emptyMap(),
    plantNames: Map<Int, String> = emptyMap(),
    recommendations: Map<Int, SavedRecommendation> = emptyMap(),
    deletions: List<FarmDeletion> = emptyList(),
    selectedFarmWeek: Int = FIRST_FARM_WEEK,
    scanCalendar: ScanCalendar = ScanCalendar(),
    cloudError: String? = null,
    onWeekSelected: (Int) -> Unit = {},
    onRecommendationSaved: (TileScanResult, ValidationResponse) -> Unit = { _, _ -> }
) {
    var weekMenu by remember { mutableStateOf(false) }
    var filterMenu by remember { mutableStateOf(false) }
    var filter by remember { mutableStateOf(HistoryFilter.All) }
    var openScan by remember { mutableStateOf<TileScanResult?>(null) }
    var showScanTip by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(180)
        showScanTip = true
    }

    val week = selectedFarmWeek.coerceIn(FIRST_FARM_WEEK, LAST_FARM_WEEK)
    val savedTiles = tileScanHistory.filterValues { it.isNotEmpty() }
    val weekRows = savedTiles.mapNotNull { (tile, scans) ->
        scans.filter { it.weekNumber == week }
            .maxByOrNull { it.scannedAtMillis }
            ?.let { tile to it }
    }.sortedByDescending { (_, scan) -> scan.scannedAtMillis }

    val visibleScans = when (filter) {
        HistoryFilter.Deleted -> emptyList()
        HistoryFilter.Healthy -> weekRows.filter { (_, scan) -> scan.isHealthy }
        HistoryFilter.Issues -> weekRows.filter { (_, scan) -> !scan.isHealthy && scan.percent > 0 }
        HistoryFilter.All -> weekRows
    }
    val showDeleted = filter == HistoryFilter.All || filter == HistoryFilter.Deleted
    val notificationCount = savedTiles.keys.sumOf { tile ->
        tileNotificationMessages(tile, tileScanHistory, scanCalendar).size
    }

    androidx.compose.material3.Scaffold(
        containerColor = PageBackground,
        bottomBar = {
            CustomBottomBar(
                selectedTab = "history",
                cameraEnabled = false,
                onHomeClick = onNavigateToHome,
                onMonitoringClick = onNavigateToMonitoring,
                onCameraClick = onCameraClick,
                onHistoryClick = { },
                onProfileClick = onNavigateToProfile
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 18.dp)
        ) {
            InternetConnectionNotice(
                modifier = Modifier.padding(top = 8.dp)
            )
            if (!cloudError.isNullOrBlank()) {
                Text(
                    text = cloudError,
                    fontSize = 12.sp,
                    color = StatusIssueFg,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 20.dp, bottom = 22.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "History",
                        fontSize = 32.sp,
                        lineHeight = 36.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.6).sp,
                        color = Ink
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Your recent plant scans",
                        fontSize = 15.sp,
                        color = Muted
                    )
                }
                ConnectionStatusIndicator(
                    labelColor = Ink,
                    dotSize = 9.dp
                )
            }

            HistorySummaryBand(
                alerts = notificationCount,
                scans = savedTiles.size,
                deleted = deletions.size
            )

            Spacer(modifier = Modifier.height(14.dp))
            AnimatedVisibility(
                visible = showScanTip,
                enter = fadeIn(tween(320)) +
                    slideInHorizontally(tween(460, easing = FastOutSlowInEasing)) { it / 3 }
            ) {
                SingleLeafScanReminder()
            }

            Spacer(modifier = Modifier.height(22.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Recent activity",
                    modifier = Modifier.weight(1f),
                    color = Ink,
                    fontSize = 20.sp,
                    lineHeight = 24.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.2).sp
                )
                Box {
                    HistoryFilterChip(
                        label = "This week",
                        selected = true,
                        onClick = { weekMenu = true }
                    )
                    DropdownMenu(expanded = weekMenu, onDismissRequest = { weekMenu = false }) {
                        (FIRST_FARM_WEEK..LAST_FARM_WEEK).forEach { option ->
                            DropdownMenuItem(
                                text = { Text("Week $option") },
                                onClick = {
                                    weekMenu = false
                                    if (option != week) onWeekSelected(option)
                                }
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
                Box {
                    HistoryFilterChip(
                        label = filter.label,
                        selected = false,
                        onClick = { filterMenu = true }
                    )
                    DropdownMenu(expanded = filterMenu, onDismissRequest = { filterMenu = false }) {
                        HistoryFilter.entries.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option.label) },
                                onClick = {
                                    filter = option
                                    filterMenu = false
                                }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            val hasVisibleRows = visibleScans.isNotEmpty() || (showDeleted && deletions.isNotEmpty())
            if (hasVisibleRows) {
                LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f)) {
                    items(
                        visibleScans,
                        key = { (_, scan) -> "scan-${scan.tileNumber}-${scan.weekNumber}-${scan.scanId}" }
                    ) { (tile, scan) ->
                        HistoryScanRow(
                            scan = scan,
                            plantName = plantNames[tile]?.ifBlank { null } ?: "Tile $tile",
                            onClick = { openScan = scan }
                        )
                    }
                    if (showDeleted) {
                        items(deletions, key = { "deleted-${it.id}" }) { entry ->
                            DeletedHistoryRow(entry)
                        }
                    }
                    item {
                        FutureResultsHint(modifier = Modifier.padding(top = 68.dp, bottom = 24.dp))
                    }
                }
            } else {
                EmptyHistoryState(
                    message = when {
                        filter == HistoryFilter.Deleted -> "No plants have been deleted yet"
                        weekRows.isEmpty() -> "New scan results will appear here"
                        else -> "No scans match this filter"
                    },
                    modifier = Modifier.fillMaxWidth().weight(1f)
                )
            }
        }
    }

    openScan?.let { scan ->
        val latestId = tileScanHistory[scan.tileNumber]
            ?.maxByOrNull { it.scannedAtMillis }
            ?.scanId
        HistoryDetailDialog(
            scan = scan,
            plantName = plantNames[scan.tileNumber]?.ifBlank { null } ?: "Tile ${scan.tileNumber}",
            savedRecommendation = recommendations[scan.tileNumber]?.takeIf { latestId == scan.scanId },
            onDismiss = { openScan = null },
            onRecommendationSaved = { response ->
                onRecommendationSaved(scan, response)
            }
        )
    }
}

private enum class HistoryStatus(val label: String, val background: Color, val foreground: Color) {
    Healthy("Healthy", StatusOkBg, StatusOkFg),
    Issue("Issue detected", StatusIssueBg, StatusIssueFg),
    Saved("Saved", StatusSavedBg, StatusSavedFg),
    Deleted("Deleted", StatusDeletedBg, StatusDeletedFg)
}

private fun TileScanResult.historyStatus(): HistoryStatus = when {
    isHealthy && percent > 0 -> HistoryStatus.Healthy
    !isHealthy && percent > 0 -> HistoryStatus.Issue
    else -> HistoryStatus.Saved
}

@Composable
private fun HistorySummaryBand(alerts: Int, scans: Int, deleted: Int) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = SummaryBackground
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().height(80.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            HistorySummaryStat("Alerts", alerts, Modifier.weight(1f))
            SummaryDivider()
            HistorySummaryStat("Scans", scans, Modifier.weight(1f))
            SummaryDivider()
            HistorySummaryStat("Deleted", deleted, Modifier.weight(1f))
        }
    }
}

@Composable
private fun HistorySummaryStat(label: String, value: Int, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value.toString(), color = Ink, fontSize = 25.sp, fontWeight = FontWeight.Bold)
        Text(label, color = Muted, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun SingleLeafScanReminder() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = SoftGreen,
        border = BorderStroke(1.dp, ForestGreen.copy(alpha = 0.12f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.78f)),
                contentAlignment = Alignment.Center
            ) {
                SingleLeafIcon(modifier = Modifier.size(28.dp))
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Scan one leaf at a time",
                    color = Ink,
                    fontSize = 14.sp,
                    lineHeight = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = "Keep only one tomato leaf in frame. Avoid hands, soil, other leaves, and background clutter for a more reliable result.",
                    color = Muted,
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )
            }
        }
    }
}

@Composable
private fun SingleLeafIcon(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val leaf = Path().apply {
            moveTo(size.width * 0.18f, size.height * 0.80f)
            cubicTo(
                size.width * 0.20f,
                size.height * 0.32f,
                size.width * 0.55f,
                size.height * 0.10f,
                size.width * 0.88f,
                size.height * 0.14f
            )
            cubicTo(
                size.width * 0.88f,
                size.height * 0.54f,
                size.width * 0.64f,
                size.height * 0.82f,
                size.width * 0.18f,
                size.height * 0.80f
            )
        }
        drawPath(
            path = leaf,
            color = ForestGreen,
            style = Stroke(width = 2.2.dp.toPx())
        )
        drawLine(
            color = ForestGreen,
            start = androidx.compose.ui.geometry.Offset(size.width * 0.12f, size.height * 0.90f),
            end = androidx.compose.ui.geometry.Offset(size.width * 0.65f, size.height * 0.38f),
            strokeWidth = 2.2.dp.toPx()
        )
    }
}

@Composable
private fun SummaryDivider() {
    Box(modifier = Modifier.width(1.dp).height(40.dp).background(Divider))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HistoryFilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(9.dp),
        color = if (selected) ForestGreen else PageBackground,
        border = if (selected) null else BorderStroke(1.dp, Divider),
        shadowElevation = if (selected) 2.dp else 0.dp
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            color = if (selected) Color.White else Muted,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1
        )
    }
}

@Composable
private fun HistoryScanRow(scan: TileScanResult, plantName: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        HistoryThumbnail(scan = scan)
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = diseaseDisplayLabel(scan.diseaseName),
                color = Ink,
                fontSize = 16.sp,
                lineHeight = 20.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = "${formatHistoryTimestamp(scan.scannedAtMillis)} · $plantName",
                color = Muted,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(7.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                HistoryStatusPill(scan.historyStatus())
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "${scan.percent}% confidence",
                    color = Muted,
                    fontSize = 12.sp,
                    maxLines = 1
                )
            }
        }
        Spacer(modifier = Modifier.width(4.dp))
        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = "View scan details",
            tint = Muted,
            modifier = Modifier.size(24.dp)
        )
    }
    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Divider))
}

@Composable
private fun HistoryThumbnail(scan: TileScanResult) {
    val modifier = Modifier.size(72.dp).clip(RoundedCornerShape(12.dp))
    if (scan.image != null) {
        Image(
            bitmap = scan.image.asImageBitmap(),
            contentDescription = "Saved scan of tile ${scan.tileNumber}",
            modifier = modifier,
            contentScale = ContentScale.Crop
        )
    } else {
        Box(modifier = modifier.background(SoftGreen), contentAlignment = Alignment.Center) {
            Icon(
                imageVector = Icons.Default.Eco,
                contentDescription = null,
                tint = ForestGreen,
                modifier = Modifier.size(30.dp)
            )
        }
    }
}

@Composable
private fun DeletedHistoryRow(entry: FarmDeletion) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(72.dp).clip(RoundedCornerShape(12.dp)).background(StatusDeletedBg),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.DeleteOutline, contentDescription = null, tint = StatusDeletedFg)
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entry.plantName,
                color = Ink,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = "${entry.deletedAtLabel} · Tile ${entry.tileNumber}",
                color = Muted,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(7.dp))
            HistoryStatusPill(HistoryStatus.Deleted)
        }
    }
    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Divider))
}

@Composable
private fun EmptyHistoryState(message: String, modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        FutureResultsHint(modifier = Modifier.padding(bottom = 42.dp), message = message)
    }
}

@Composable
private fun FutureResultsHint(
    modifier: Modifier = Modifier,
    message: String = "New scan results will appear here"
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier.size(46.dp).clip(CircleShape).background(SoftGreen),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Eco,
                contentDescription = null,
                tint = ForestGreen,
                modifier = Modifier.size(27.dp)
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text(text = message, color = Muted, fontSize = 14.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}

private fun formatHistoryTimestamp(timestamp: Long): String {
    val target = Calendar.getInstance().apply { timeInMillis = timestamp }
    val today = Calendar.getInstance()
    val dayLabel = when {
        sameCalendarDay(target, today) -> "Today"
        sameCalendarDay(target, (today.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -1) }) -> "Yesterday"
        else -> SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(timestamp))
    }
    val time = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(timestamp))
    return "$dayLabel · $time"
}

private fun sameCalendarDay(first: Calendar, second: Calendar): Boolean =
    first.get(Calendar.ERA) == second.get(Calendar.ERA) &&
        first.get(Calendar.YEAR) == second.get(Calendar.YEAR) &&
        first.get(Calendar.DAY_OF_YEAR) == second.get(Calendar.DAY_OF_YEAR)

@Composable
private fun HistoryStatusPill(status: HistoryStatus) {
    Text(
        text = status.label,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(status.background)
            .padding(horizontal = 8.dp, vertical = 3.dp),
        color = status.foreground,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1
    )
}

@Composable
private fun HistoryDetailDialog(
    scan: TileScanResult,
    plantName: String,
    savedRecommendation: SavedRecommendation? = null,
    onDismiss: () -> Unit,
    onRecommendationSaved: (ValidationResponse) -> Unit = {}
) {
    var showCare by remember(scan.scanId) { mutableStateOf(false) }
    var showOfflineAlert by remember(scan.scanId) { mutableStateOf(false) }
    var awaitingRecommendation by remember(scan.scanId) { mutableStateOf(false) }
    var isLoadingRecommendation by remember(scan.scanId) { mutableStateOf(false) }
    var loadedRecommendation by remember(scan.scanId) { mutableStateOf<com.tomatoinsight.api.ValidationResponse?>(null) }
    val baseResult = scan.response.takeIf { it.hasDiseaseResult }
        ?: scan.scores.asSavedValidationResponse()
    val result = (loadedRecommendation ?: baseResult).ensureGuidesForAllDetections()
    val compatibleSavedRecommendation = savedRecommendation?.takeIf { saved ->
                    saved.cacheKey.isNotBlank() &&
                        saved.cacheKey == result.recommendationCacheKey()
    }
    val needsOnlineRecommendation = scan.response.stage == "offline" &&
        loadedRecommendation?.recommendationSource != "groq" &&
        compatibleSavedRecommendation?.guides.isNullOrEmpty()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val internetAvailable by produceState(initialValue = context.hasValidatedInternet(), context) {
        context.observeValidatedInternet().collect { value = it }
    }

    fun loadCareRecommendation(openCare: Boolean = true) {
        val image = scan.image ?: return
        scope.launch {
            if (openCare) showCare = true
            isLoadingRecommendation = true
            awaitingRecommendation = false
            showOfflineAlert = false
            try {
                val server = withTimeout(300_000L) {
                    val bytes = ByteArrayOutputStream().use { stream ->
                        image.compress(android.graphics.Bitmap.CompressFormat.JPEG, 95, stream)
                        stream.toByteArray()
                    }
                    WebsiteScanClient.withSegmentationSeverity(bytes, baseResult)
                }
                loadedRecommendation = server.ensureGuidesForAllDetections()
                loadedRecommendation?.let(onRecommendationSaved)
                if (openCare) showCare = true
            } catch (_: Exception) {
                if (openCare) showCare = false
            } finally {
                isLoadingRecommendation = false
            }
        }
    }

    LaunchedEffect(awaitingRecommendation, internetAvailable) {
        if (awaitingRecommendation && internetAvailable && scan.image != null) {
            loadCareRecommendation()
        }
    }

    LaunchedEffect(internetAvailable, baseResult.severityReason) {
        if (
            internetAvailable &&
            baseResult.awaitingOpenCvSeverity() &&
            loadedRecommendation == null &&
            scan.image != null
        ) {
            loadCareRecommendation(openCare = false)
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth().fillMaxHeight(0.88f),
            shape = RoundedCornerShape(20.dp),
            color = Color.White
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = plantName, fontWeight = FontWeight.Bold, fontSize = 20.sp, color = Ink)
                        Text(
                            text = "Tile ${scan.tileNumber} · Week ${scan.weekNumber ?: "—"} · ${scan.formattedDate}",
                            fontSize = 12.sp,
                            color = Muted
                        )
                    }
                    TextButton(onClick = onDismiss) { Text("Close") }
                }
                scan.image?.let { bitmap ->
                    Spacer(modifier = Modifier.height(12.dp))
                    androidx.compose.foundation.Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "Saved plant scan",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                            .clip(RoundedCornerShape(16.dp)),
                        contentScale = ContentScale.Crop
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text(text = "Classification", fontWeight = FontWeight.Bold, color = Ink)
                Spacer(modifier = Modifier.height(8.dp))
                HealthStatusCard(result = result)
                if (isLoadingRecommendation && result.awaitingOpenCvSeverity()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(text = "Syncing severity…", color = Muted, fontSize = 13.sp)
                    }
                }
                if (result.allDiseaseScores.any { it.percent > 0 } && !result.awaitingOpenCvSeverity()) {
                    val showsRescanGuidance = result.severityAssessable == false &&
                        result.allDiseaseScores.maxByOrNull { it.percent }
                            ?.let { !isHealthyDisease(it.name) } == true
                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(
                        onClick = {
                            if (!internetAvailable) {
                                showCare = false
                                awaitingRecommendation = true
                                showOfflineAlert = true
                            } else if (needsOnlineRecommendation) {
                                loadCareRecommendation()
                            } else {
                                showCare = !showCare
                            }
                        },
                        enabled = !isLoadingRecommendation
                    ) {
                        Text(
                            when {
                                isLoadingRecommendation -> "Preparing care plan..."
                                showsRescanGuidance && showCare && internetAvailable -> "Hide rescan guidance"
                                showsRescanGuidance -> "View rescan guidance"
                                showCare && internetAvailable -> "Hide Intervention"
                                else -> "View Intervention"
                            }
                        )
                    }
                    AnimatedVisibility(
                        visible = showCare && internetAvailable,
                        enter = fadeIn(tween(280, easing = FastOutSlowInEasing)) +
                            expandVertically(tween(320, easing = FastOutSlowInEasing)),
                        exit = fadeOut(tween(200, easing = FastOutSlowInEasing)) +
                            shrinkVertically(tween(280, easing = FastOutSlowInEasing))
                    ) {
                        if (isLoadingRecommendation) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(32.dp),
                                strokeWidth = 3.dp
                            )
                        } else {
                            DiseaseTreatmentCard(
                                result = result,
                                savedRecommendation = if (loadedRecommendation?.hasAiRecommendations == true) {
                                    null
                                } else {
                                    compatibleSavedRecommendation
                                }
                            )
                        }
                    }
                }
            }
        }
    }
    if (showOfflineAlert) {
        AlertDialog(
            onDismissRequest = { showOfflineAlert = false },
            icon = {
                Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFD92D20))
            },
            title = { Text("Internet required", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "Care recommendations need an internet connection. " +
                        "This recommendation will open automatically when the connection is back."
                )
            },
            confirmButton = {
                TextButton(onClick = { showOfflineAlert = false }) { Text("OK") }
            }
        )
    }
}
