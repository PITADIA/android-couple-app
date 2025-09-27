package com.love2loveapp.views.onboarding

import android.widget.NumberPicker
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import com.love2loveapp.R
import java.time.LocalDate
import java.time.YearMonth

@Composable
fun RelationshipDateStepScreen(
    selectedDate: LocalDate?,
    onDateChange: (LocalDate) -> Unit,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    
    // Default = année actuelle avec mois/jour de l'année dernière pour une date valide dans le passé
    var currentDate by rememberSaveable(stateSaver = LocalDateSaver) {
        mutableStateOf(selectedDate ?: run {
            val now = LocalDate.now()
            val oneYearAgo = now.minusYears(1)
            // Année actuelle, mais mois/jour de l'année dernière
            LocalDate.of(now.year, oneYearAgo.month, oneYearAgo.dayOfMonth)
        })
    }

    // Update parent when date changes
    LaunchedEffect(currentDate) {
        onDateChange(currentDate)
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = OnboardingColors.Background
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            Spacer(Modifier.height(OnboardingDimensions.TitleContentSpacing))

            // Titre selon les spécifications du rapport
            Text(
                text = stringResource(R.string.relationship_duration_question),
                style = OnboardingTypography.TitleLarge,
                modifier = Modifier.padding(horizontal = OnboardingDimensions.HorizontalPadding)
            )

            Spacer(modifier = Modifier.weight(1f))

            // DatePickerCarousel selon le rapport
            Column(
                modifier = Modifier
                    .padding(horizontal = OnboardingDimensions.HorizontalPadding)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                DatePickerCarousel(
                    selectedDate = currentDate,
                    onDateChange = { currentDate = it }
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            // Zone bouton selon les spécifications du rapport
            OnboardingButtonZone(
                onContinueClick = onContinue,
                isContinueEnabled = true
            )
        }
    }
}

@Composable
private fun DatePickerCarousel(
    selectedDate: LocalDate,
    onDateChange: (LocalDate) -> Unit
) {
    val context = LocalContext.current
    
    // Mois localisés depuis strings.xml
    val localizedMonths = listOf(
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
    )
    
    // Plage d'années raisonnable : de l'année actuelle jusqu'à 50 ans en arrière
    val currentYear = LocalDate.now().year
    val yearsDesc = (currentYear downTo (currentYear - 50)).toList()
    
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

    // HStack avec 3 Pickers selon le rapport
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(OnboardingDimensions.DatePickerHeight),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // MONTH picker avec mois localisés
        AndroidView(
            factory = { ctx ->
                NumberPicker(ctx).apply {
                    wrapSelectorWheel = true
                    minValue = 1
                    maxValue = 12
                    displayedValues = localizedMonths.toTypedArray()
                    value = month
                    setOnValueChangedListener { _, _, newVal -> month = newVal }
                    textSize = 18 * ctx.resources.displayMetrics.density
                }
            },
            update = { picker ->
                picker.displayedValues = null
                picker.minValue = 1
                picker.maxValue = 12
                picker.displayedValues = localizedMonths.toTypedArray()
                if (picker.value != month) picker.value = month
            },
            modifier = Modifier.weight(1f)
        )

        // DAY picker (1...daysInSelectedMonth selon le rapport)
        AndroidView(
            factory = { ctx ->
                NumberPicker(ctx).apply {
                    wrapSelectorWheel = true
                    minValue = 1
                    maxValue = daysInMonth
                    value = day
                    setOnValueChangedListener { _, _, newVal -> day = newVal }
                    textSize = 18 * ctx.resources.displayMetrics.density
                }
            },
            update = { picker ->
                val current = day.coerceIn(1, daysInMonth)
                if (picker.maxValue != daysInMonth) {
                    picker.minValue = 1
                    picker.maxValue = daysInMonth
                }
                if (picker.value != current) picker.value = current
            },
            modifier = Modifier.weight(1f)
        )

        // YEAR picker - années de l'année actuelle vers le passé
        val yearIndex = remember(year, yearsDesc) { yearsDesc.indexOf(year).coerceAtLeast(0) }
        AndroidView(
            factory = { ctx ->
                NumberPicker(ctx).apply {
                    wrapSelectorWheel = false // Désactiver le défilement circulaire pour éviter de dépasser les limites
                    minValue = 0
                    maxValue = yearsDesc.lastIndex
                    displayedValues = yearsDesc.map { it.toString() }.toTypedArray()
                    value = yearIndex
                    setOnValueChangedListener { _, _, newIndex ->
                        val newYear = yearsDesc.getOrNull(newIndex) ?: year
                        // S'assurer qu'on ne dépasse pas l'année actuelle
                        if (newYear <= currentYear) {
                            year = newYear
                        }
                    }
                    textSize = 18 * ctx.resources.displayMetrics.density
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

/** Saver so LocalDate survives process death & configuration changes in rememberSaveable */
val LocalDateSaver = run {
    androidx.compose.runtime.saveable.Saver<LocalDate, Long>(
        save = { it.toEpochDay() },
        restore = { LocalDate.ofEpochDay(it) }
    )
}