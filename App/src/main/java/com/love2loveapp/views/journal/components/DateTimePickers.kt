package com.love2loveapp.views.journal.components

import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import java.text.SimpleDateFormat
import java.util.*

/**
 * 📅 Date/Time Pickers Material3 - Sélecteurs Natifs Android
 * Équivalent iOS DatePicker et TimePicker
 * 
 * Utilise les nouveaux pickers Material3 pour une expérience native Android optimale.
 */

/**
 * 📅 Sélecteur de Date
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JournalDatePicker(
    selectedDate: Date,
    onDateSelected: (Date) -> Unit,
    onDismiss: () -> Unit
) {
    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = selectedDate.time
    )
    
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        onDateSelected(Date(millis))
                    }
                    onDismiss()
                }
            ) {
                Text(
                    text = "Confirmer",
                    color = Color(0xFFFF6B9D)
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = "Annuler",
                    color = Color.Gray
                )
            }
        },
        colors = DatePickerDefaults.colors(
            selectedDayContainerColor = Color(0xFFFF6B9D),
            todayContentColor = Color(0xFFFF6B9D),
            todayDateBorderColor = Color(0xFFFF6B9D)
        )
    ) {
        DatePicker(
            state = datePickerState,
            colors = DatePickerDefaults.colors(
                selectedDayContainerColor = Color(0xFFFF6B9D),
                todayContentColor = Color(0xFFFF6B9D),
                todayDateBorderColor = Color(0xFFFF6B9D)
            )
        )
    }
}

/**
 * ⏰ Sélecteur d'Heure
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JournalTimePicker(
    selectedTime: Date,
    onTimeSelected: (Date) -> Unit,
    onDismiss: () -> Unit
) {
    val calendar = Calendar.getInstance().apply {
        time = selectedTime
    }
    
    val timePickerState = rememberTimePickerState(
        initialHour = calendar.get(Calendar.HOUR_OF_DAY),
        initialMinute = calendar.get(Calendar.MINUTE)
    )
    
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    // Créer nouvelle date avec heure mise à jour
                    val updatedCalendar = Calendar.getInstance().apply {
                        time = selectedTime
                        set(Calendar.HOUR_OF_DAY, timePickerState.hour)
                        set(Calendar.MINUTE, timePickerState.minute)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }
                    
                    onTimeSelected(updatedCalendar.time)
                    onDismiss()
                }
            ) {
                Text(
                    text = "Confirmer",
                    color = Color(0xFFFF6B9D)
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = "Annuler",
                    color = Color.Gray
                )
            }
        },
        title = { Text("Sélectionner l'heure") },
        text = {
            TimePicker(
                state = timePickerState,
                colors = TimePickerDefaults.colors(
                    clockDialSelectedContentColor = Color.White,
                    clockDialUnselectedContentColor = Color.Gray,
                    selectorColor = Color(0xFFFF6B9D),
                    periodSelectorSelectedContainerColor = Color(0xFFFF6B9D),
                    periodSelectorUnselectedContainerColor = Color.Transparent,
                    periodSelectorSelectedContentColor = Color.White,
                    periodSelectorUnselectedContentColor = Color.Gray,
                    timeSelectorSelectedContainerColor = Color(0xFFFF6B9D),
                    timeSelectorUnselectedContainerColor = Color.Transparent,
                    timeSelectorSelectedContentColor = Color.White,
                    timeSelectorUnselectedContentColor = Color.Gray
                )
            )
        }
    )
}

/**
 * 📅⏰ Sélecteur Date et Heure Combiné
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable  
fun JournalDateTimePicker(
    selectedDateTime: Date,
    onDateTimeSelected: (Date) -> Unit,
    onDismiss: () -> Unit
) {
    var showingDatePicker by remember { mutableStateOf(true) }
    var tempDate by remember { mutableStateOf(selectedDateTime) }
    
    if (showingDatePicker) {
        JournalDatePicker(
            selectedDate = tempDate,
            onDateSelected = { newDate ->
                tempDate = newDate
                showingDatePicker = false
            },
            onDismiss = onDismiss
        )
    } else {
        JournalTimePicker(
            selectedTime = tempDate,
            onTimeSelected = { newDateTime ->
                onDateTimeSelected(newDateTime)
            },
            onDismiss = {
                showingDatePicker = true
                onDismiss()
            }
        )
    }
}

/**
 * 🕐 Utilitaires de formatage
 */
object JournalDateTimeFormatters {
    
    val dateFormatter = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
    val timeFormatter = SimpleDateFormat("HH:mm", Locale.getDefault())
    val fullFormatter = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
    
    fun formatDate(date: Date): String = dateFormatter.format(date)
    fun formatTime(date: Date): String = timeFormatter.format(date)
    fun formatDateTime(date: Date): String = fullFormatter.format(date)
}
