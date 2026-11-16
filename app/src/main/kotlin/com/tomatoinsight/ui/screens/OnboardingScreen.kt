@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.tomatoinsight.ui.screens

import android.app.Activity
import android.content.Context
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.tomatoinsight.R
import kotlinx.coroutines.launch

private const val ONBOARDING_PREFS = "tomatoinsight_onboarding"
private const val ONBOARDING_COMPLETED = "completed"

private val LeafTitleStyle = TextStyle(
    fontFamily = FontFamily(Font(R.font.times_new_roman_bold, FontWeight.Bold)),
    fontWeight = FontWeight.Bold,
    fontSize = 36.sp,
    lineHeight = 39.sp,
    letterSpacing = (-0.5).sp
)

private val LeafBodyStyle = TextStyle(fontSize = 17.sp, lineHeight = 25.sp)

fun hasSeenOnboarding(context: Context): Boolean =
    context.getSharedPreferences(ONBOARDING_PREFS, Context.MODE_PRIVATE)
        .getBoolean(ONBOARDING_COMPLETED, false)

fun markOnboardingSeen(context: Context) {
    context.getSharedPreferences(ONBOARDING_PREFS, Context.MODE_PRIVATE)
        .edit()
        .putBoolean(ONBOARDING_COMPLETED, true)
        .commit()
}

private data class ClassificationSlide(
    val title: String,
    val detail: String,
    val accent: Color,
    val darkThemeAccent: Color,
    @DrawableRes val imageRes: Int
)

private val classificationSlides = listOf(
    ClassificationSlide(
        title = "Healthy",
        detail = "An even green leaf with no spots, rings, or curling. TomatoInsight reads this as a healthy plant. Keep watering steady and scan the rest of the farm so new problems show up early.",
        accent = Color(0xFF216546),
        darkThemeAccent = Color(0xFF94DDB0),
        imageRes = R.drawable.onboarding_healthy
    ),
    ClassificationSlide(
        title = "Yellow leaf curl virus",
        detail = "Leaves curl upward, turn yellow, and fruit set drops. Whiteflies spread this virus between plants. Remove severely infected plants and check new growth for whiteflies.",
        accent = Color(0xFF78510B),
        darkThemeAccent = Color(0xFFF2C778),
        imageRes = R.drawable.onboarding_yellow_leaf_curl
    ),
    ClassificationSlide(
        title = "Early blight",
        detail = "Brown spots with target-like rings, usually on older lower leaves. The fungus can strip foliage if it spreads. Remove infected leaves, water at the base, and open up airflow.",
        accent = Color(0xFF913E32),
        darkThemeAccent = Color(0xFFFFB5A8),
        imageRes = R.drawable.onboarding_early_blight
    ),
    ClassificationSlide(
        title = "Spotted wilt",
        detail = "Bronze or ring spots, wilted tips, and stunted growth from tomato spotted wilt virus. Thrips carry it from plant to plant. Remove infected plants and control thrips before it moves across the farm.",
        accent = Color(0xFF903656),
        darkThemeAccent = Color(0xFFF8AEC9),
        imageRes = R.drawable.onboarding_spotted_wilt
    )
)

@Composable
fun OnboardingScreen(onFinished: () -> Unit) {
    val pagerState = rememberPagerState(pageCount = { classificationSlides.size })
    val scope = rememberCoroutineScope()
    val index = pagerState.currentPage
    val slide = classificationSlides[index]
    val darkTheme = isSystemInDarkTheme()
    val surface = if (darkTheme) Color(0xFF181D18) else Color(0xFFF8F8F0)
    val ink = if (darkTheme) Color(0xFFF4F6ED) else Color(0xFF18251C)
    val secondaryInk = if (darkTheme) Color(0xFFC1CCBF) else Color(0xFF46544A)
    val track = if (darkTheme) Color(0xFF525F53) else Color(0xFFD4DDD1)
    val accent = if (darkTheme) slide.darkThemeAccent else slide.accent
    val onAccent = if (darkTheme) Color(0xFF18251C) else Color.White
    val lastPage = index == classificationSlides.lastIndex

    val view = LocalView.current
    if (!view.isInEditMode) {
        DisposableEffect(view, surface, darkTheme) {
            val window = (view.context as Activity).window
            val insets = WindowCompat.getInsetsController(window, view)
            val oldStatusBar = window.statusBarColor
            val oldNavigationBar = window.navigationBarColor
            val oldStatusAppearance = insets.isAppearanceLightStatusBars
            val oldNavigationAppearance = insets.isAppearanceLightNavigationBars
            window.statusBarColor = surface.toArgb()
            window.navigationBarColor = surface.toArgb()
            insets.isAppearanceLightStatusBars = !darkTheme
            insets.isAppearanceLightNavigationBars = !darkTheme
            onDispose {
                window.statusBarColor = oldStatusBar
                window.navigationBarColor = oldNavigationBar
                insets.isAppearanceLightStatusBars = oldStatusAppearance
                insets.isAppearanceLightNavigationBars = oldNavigationAppearance
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(surface)
            .statusBarsPadding()
            .navigationBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier
                .widthIn(max = 560.dp)
                .fillMaxWidth()
                .padding(start = 24.dp, end = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Leaf guide", style = MaterialTheme.typography.titleMedium, color = ink)
            TextButton(onClick = onFinished, modifier = Modifier.heightIn(min = 48.dp)) {
                Text("Skip", style = MaterialTheme.typography.labelLarge, color = secondaryInk)
            }
        }

        BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxWidth()) {
            val photoHeight = (maxHeight * 0.60f).coerceIn(128.dp, 460.dp)
            HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { pageIndex ->
                val page = classificationSlides[pageIndex]
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Column(
                        modifier = Modifier
                            .widthIn(max = 560.dp)
                            .fillMaxWidth()
                            .padding(start = 24.dp, top = 8.dp, end = 24.dp, bottom = 12.dp)
                    ) {
                        Image(
                            painter = painterResource(page.imageRes),
                            contentDescription = null,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(photoHeight)
                                .clip(RoundedCornerShape(16.dp)),
                            contentScale = ContentScale.Crop
                        )
                        Spacer(Modifier.height(20.dp))
                        Text(
                            text = page.title,
                            color = ink,
                            style = LeafTitleStyle,
                            modifier = Modifier.semantics { heading() }
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(text = page.detail, color = secondaryInk, style = LeafBodyStyle)
                    }
                }
            }
        }

        Column(
            modifier = Modifier
                .widthIn(max = 560.dp)
                .fillMaxWidth()
                .padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    repeat(classificationSlides.size) { step ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(4.dp)
                                .clip(CircleShape)
                                .background(if (step <= index) accent else track)
                        )
                    }
                }
                Text(
                    text = "${index + 1} of ${classificationSlides.size}",
                    color = secondaryInk,
                    style = MaterialTheme.typography.labelMedium
                )
            }
            Button(
                onClick = {
                    if (!pagerState.isScrollInProgress) {
                        if (lastPage) onFinished()
                        else scope.launch { pagerState.animateScrollToPage(index + 1) }
                    }
                },
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = onAccent)
            ) {
                Text(
                    text = if (lastPage) "Get started" else "Continue",
                    style = MaterialTheme.typography.labelLarge
                )
                Spacer(Modifier.width(8.dp))
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    modifier = Modifier.size(19.dp)
                )
            }
        }
    }
}
