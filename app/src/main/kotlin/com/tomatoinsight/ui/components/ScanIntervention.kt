package com.tomatoinsight.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tomatoinsight.api.ValidationResponse
import com.tomatoinsight.data.ensureGuidesForAllDetections

@Composable
internal fun ScanIntervention(result: ValidationResponse) {
    var expanded by rememberSaveable { mutableStateOf(true) }
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                .semantics { stateDescription = if (expanded) "Expanded" else "Collapsed" }
                .toggleable(value = expanded, role = Role.Button) { expanded = it },
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Intervention", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            Icon(if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, contentDescription = null)
        }
        if (expanded) {
            val guide = remember(result) { result.ensureGuidesForAllDetections() }
            when {
                result.awaitingOpenCvSeverity() -> Text("Intervention is available after severity is assessed.")
                !guide.hasAiRecommendations -> Text("No intervention is available for this scan.")
                else -> {
                    if (!result.hasAiRecommendations) Text("Care guidance based on this week’s saved scan.", fontSize = 13.sp)
                    InterventionGuideCard(guide)
                }
            }
        }
    }
}
