package com.yourapp.onboarding

import android.widget.NumberPicker
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.yourapp.R
import java.time.LocalDate
import java.time.YearMonth

/**
 * Kotlin/Compose port of RelationshipDateStepView + DatePickerCarousel
 * Notes:
 * - Uses standard Android string resources via stringResource(R.string.*)
 * - Replaces SwiftUI wheel pickers with Android NumberPicker via AndroidView
 * - Default selected date = today minus 1 year (as in Swift)
 */

@Composable
fun RelationshipDateStepScreen(
    viewModel: OnboardingViewModel,
) {
    val context = LocalContext.current

    // Background color close to SwiftUI Color(0.97, 0.97, 0.98)
    val screenBg = Color(0xFFF7F7FA)
    val ctaColor = Color(0xFFFD267A)

    // Default = one year ago (matching Swift onAppear behavior)
    var selectedDate by rememberSaveable(stateSaver = LocalDateSaver) {
        mutableStateOf(LocalDate.now().minusYears(1))
    }

    Scaffold(
        containerColor = screenBg,
        bottomBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White)
            ) {
                Button(
                    onClick = {
                        viewModel.relationshipStartDate = selectedDate
                        viewModel.nextStep()
                    },
                    modifier = Modifier
                        .padding(horizontal = 30.dp, vertical = 30.dp)
                        .fillMaxWidth()
                        .height(56.dp)
                        .clip(MaterialTheme.shapes.extraLarge),
                    colors = ButtonDefaults.buttonColors(containerColor = ctaColor)
                ) {
                    Text(
                        text = stringResource(R.string.continue_label),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                }
            }
        }
    ) { inner ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
        ) {
            Spacer(modifier = Modifier.height(40.dp))

            // Title row (left aligned)
            Row(
                modifier = Modifier
                    .padding(horizontal = 30.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.relationship_duration_question),
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black,
                    lineHeight = 40.sp,
                    modifier = Modifier.weight(1f)
                )
            }

            // First spacer to center content
            Spacer(modifier = Modifier.weight(1f))

            // Main content (date wheels)
            Column(
                modifier = Modifier
                    .padding(horizontal = 30.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                DatePickerCarousel(
                    selectedDate = selectedDate,
                    onDateChange = { selectedDate = it },
                    months = listOf(
                        stringResource(R.string.month_january),
                        stringResource(R.string.month_february),
                        stringResource(R.string.month_march),
                        stringResource(R.string.month_april),
                        stringResource(R.string.month_may),
                        stringResource(R.string.month_june),
                        stringResource(R.string.month_july),
                        stringResource(R.string.month_august),
                        stringResource(R.string.month_september),
                        stringResource(R.string.month_october),
                        stringResource(R.string.month_november),
                        stringResource(R.string.month_december)
                    ),
                    yearsDesc = (LocalDate.now().year downTo 1990).toList()
                )
            }

            // Second spacer to push bottom bar down
            Spacer(modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun DatePickerCarousel(
    selectedDate: LocalDate,
    onDateChange: (LocalDate) -> Unit,
    months: List<String>,
    yearsDesc: List<Int>, // descending, e.g., 2025..1990
) {
    var day by rememberSaveable { mutableStateOf(selectedDate.dayOfMonth) }
    var month by rememberSaveable { mutableStateOf(selectedDate.monthValue) } // 1..12
    var year by rememberSaveable { mutableStateOf(selectedDate.year) }

    val daysInMonth = remember(month, year) { YearMonth.of(year, month).lengthOfMonth() }

    // Ensure day is valid when month/year changes
    LaunchedEffect(month, year) {
        if (day > daysInMonth) day = daysInMonth
    }

    // Propagate composed date upward
    LaunchedEffect(day, month, year) {
        onDateChange(LocalDate.of(year, month, day))
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // MONTH picker (1..12) with localized labels
        AndroidView(
            factory = { ctx ->
                NumberPicker(ctx).apply {
                    wrapSelectorWheel = true
                    minValue = 1
                    maxValue = 12
                    displayedValues = months.toTypedArray()
                    value = month
                    setOnValueChangedListener { _, _, newVal -> month = newVal }
                }
            },
            update = { picker ->
                // When months array changes, reset displayed values to avoid NPE
                picker.displayedValues = null
                picker.minValue = 1
                picker.maxValue = 12
                picker.displayedValues = months.toTypedArray()
                if (picker.value != month) picker.value = month
            },
            modifier = Modifier.weight(1f)
        )

        // DAY picker (1..daysInMonth)
        AndroidView(
            factory = { ctx ->
                NumberPicker(ctx).apply {
                    wrapSelectorWheel = true
                    minValue = 1
                    maxValue = daysInMonth
                    value = day
                    setOnValueChangedListener { _, _, newVal -> day = newVal }
                }
            },
            update = { picker ->
                // Update bounds safely when month/year changes
                val current = day.coerceIn(1, daysInMonth)
                if (picker.maxValue != daysInMonth) {
                    picker.minValue = 1
                    picker.maxValue = daysInMonth
                }
                if (picker.value != current) picker.value = current
            },
            modifier = Modifier.weight(1f)
        )

        // YEAR picker (descending order with displayedValues)
        val yearIndex = remember(year, yearsDesc) { yearsDesc.indexOf(year).coerceAtLeast(0) }
        AndroidView(
            factory = { ctx ->
                NumberPicker(ctx).apply {
                    wrapSelectorWheel = true
                    minValue = 0
                    maxValue = yearsDesc.lastIndex
                    displayedValues = yearsDesc.map { it.toString() }.toTypedArray()
                    value = yearIndex
                    setOnValueChangedListener { _, _, newIndex ->
                        val newYear = yearsDesc.getOrNull(newIndex) ?: year
                        year = newYear
                    }
                }
            },
            update = { picker ->
                picker.displayedValues = null
                picker.minValue = 0
                picker.maxValue = yearsDesc.lastIndex
                picker.displayedValues = yearsDesc.map { it.toString() }.toTypedArray()
                val idx = yearsDesc.indexOf(year).coerceAtLeast(0)
                if (picker.value != idx) picker.value = idx
            },
            modifier = Modifier.weight(1f)
        )
    }
}

// -- Helpers -----------------------------------------------------------------

/** Saver so LocalDate survives process death & configuration changes in rememberSaveable */
val LocalDateSaver = run {
    androidx.compose.runtime.saveable.Saver<LocalDate, Long>(
        save = { it.toEpochDay() },
        restore = { LocalDate.ofEpochDay(it) }
    )
}

/** Minimal contract mirroring your Swift view model usage. Wire it to your actual VM. */
interface OnboardingViewModel {
    var relationshipStartDate: LocalDate?
    fun nextStep()
}

/*
======================= strings.xml (excerpt) =======================
<resources>
    <!-- Title and CTA -->
    <string name="relationship_duration_question">Depuis quand êtes‑vous ensemble ?</string>
    <string name="continue_label">Continuer</string>

    <!-- Months -->
    <string name="month_january">Janvier</string>
    <string name="month_february">Février</string>
    <string name="month_march">Mars</string>
    <string name="month_april">Avril</string>
    <string name="month_may">Mai</string>
    <string name="month_june">Juin</string>
    <string name="month_july">Juillet</string>
    <string name="month_august">Août</string>
    <string name="month_september">Septembre</string>
    <string name="month_october">Octobre</string>
    <string name="month_november">Novembre</string>
    <string name="month_december">Décembre</string>
</resources>
====================================================================
*/
