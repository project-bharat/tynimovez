package com.projectbharat.counterapp

import android.app.DatePickerDialog
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

private enum class PregnancyLanguage(val label: String, val tag: String) {
    ENGLISH("English", "en-IN"), HINDI("हिन्दी", "hi-IN"), MARATHI("मराठी", "mr-IN");

    fun text(en: String, hi: String, mr: String): String = when (this) {
        ENGLISH -> en
        HINDI -> hi
        MARATHI -> mr
    }
}

private fun DatingMethod.label(language: PregnancyLanguage): String = with(language) {
    when (this@label) {
        DatingMethod.LMP -> text("Last menstrual period", "अंतिम मासिक धर्म", "शेवटची मासिक पाळी")
        DatingMethod.CONCEPTION -> text("Conception date", "गर्भधारण की तारीख", "गर्भधारणेची तारीख")
        DatingMethod.IVF_DAY_3 -> text("IVF transfer · Day 3", "IVF ट्रांसफर · दिन 3", "IVF ट्रान्सफर · दिवस 3")
        DatingMethod.IVF_DAY_5 -> text("IVF transfer · Day 5", "IVF ट्रांसफर · दिन 5", "IVF ट्रान्सफर · दिवस 5")
        DatingMethod.DUE_DATE -> text("Confirmed due date", "पुष्ट प्रसव की तारीख", "निश्चित प्रसूती तारीख")
    }
}

@Composable
internal fun PregnancyCalculatorScreen(english: Boolean) {
    val context = LocalContext.current
    val prefs = remember(context) { context.getSharedPreferences("pregnancy_dates", Context.MODE_PRIVATE) }
    val language = if (english) PregnancyLanguage.ENGLISH else PregnancyLanguage.MARATHI
    var method by remember {
        mutableStateOf(DatingMethod.entries.firstOrNull { it.name == prefs.getString("method", null) } ?: DatingMethod.LMP)
    }
    var inputDate by remember(method) {
        mutableStateOf(prefs.getString("date_${method.name}", null)?.let(LocalDate::parse))
    }
    var cycle by remember { mutableStateOf(prefs.getString("cycle", "28").orEmpty()) }
    var today by remember { mutableStateOf(LocalDate.now()) }
    var showMethods by remember { mutableStateOf(false) }
    var editing by rememberSaveable { mutableStateOf(false) }
    LifecycleResumeEffect(Unit) {
        today = LocalDate.now()
        onPauseOrDispose { }
    }

    val format = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(Locale.forLanguageTag(language.tag))
    val cycleLength = if (method == DatingMethod.LMP) cycle.toIntOrNull()?.takeIf { it in 15..60 } else 28
    val timeline = inputDate?.let { date -> cycleLength?.let { PregnancyDates(method.dueDate(date, it), today) } }

    val valid = timeline?.takeIf { it.daysElapsed >= 0 }
    with(language) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (valid != null) {
                PregnancySummary(valid, language, format) { milestone ->
                    val share = Intent(Intent.ACTION_SEND).setType("text/plain")
                        .putExtra(Intent.EXTRA_TEXT, milestone)
                    context.startActivity(Intent.createChooser(share, null))
                }
            }

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (valid != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(text("Pregnancy dates", "गर्भावस्था की तारीखें", "गर्भावस्थेच्या तारखा"),
                                    style = MaterialTheme.typography.titleMedium)
                                Text(method.label(language),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            TextButton(onClick = { editing = !editing }) {
                                Text(if (editing) text("Done", "हो गया", "पूर्ण") else text("Edit", "बदलें", "बदला"))
                            }
                        }
                    }
                    if (editing || valid == null) {
                        if (valid == null) Text(
                            text("Pregnancy dates", "गर्भावस्था की तारीखें", "गर्भावस्थेच्या तारखा"),
                            style = MaterialTheme.typography.titleMedium)
                        Box {
                            OutlinedButton(onClick = { showMethods = true }, modifier = Modifier.fillMaxWidth()) { Text(method.label(language)) }
                            DropdownMenu(expanded = showMethods, onDismissRequest = { showMethods = false }) {
                                DatingMethod.entries.forEach { option ->
                                    DropdownMenuItem(text = { Text(option.label(language)) }, onClick = {
                                        method = option
                                        prefs.edit().putString("method", option.name).apply()
                                        showMethods = false
                                    })
                                }
                            }
                        }
                        Button(onClick = {
                            val initial = inputDate ?: today
                            DatePickerDialog(context, { _, year, month, day ->
                                val date = LocalDate.of(year, month + 1, day)
                                inputDate = date
                                prefs.edit().putString("date_${method.name}", date.toString()).apply()
                            }, initial.year, initial.monthValue - 1, initial.dayOfMonth).apply {
                                if (method != DatingMethod.DUE_DATE) datePicker.maxDate = System.currentTimeMillis()
                            }.show()
                        }, modifier = Modifier.fillMaxWidth()) { Text(inputDate?.format(format) ?: text("Choose date", "तारीख चुनें", "तारीख निवडा")) }
                        if (method == DatingMethod.LMP) {
                            OutlinedTextField(value = cycle, onValueChange = { value ->
                                if (value.length <= 2 && value.all { it in '0'..'9' }) {
                                    cycle = value
                                    prefs.edit().putString("cycle", value).apply()
                                }
                            }, label = { Text(text("Cycle length (days)", "मासिक चक्र (दिन)", "पाळीचे चक्र (दिवस)")) },
                                isError = cycleLength == null,
                                supportingText = {
                                    if (cycleLength == null) Text(text("Enter 15–60 days.", "15–60 दिन दर्ज करें।", "१५–६० दिवस प्रविष्ट करा."))
                                },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true, modifier = Modifier.fillMaxWidth())
                        }
                    }
                    if (timeline != null && valid == null) Text(text(
                        "Check the date. The estimated pregnancy start is in the future.",
                        "तारीख जाँचें। गर्भावस्था की अनुमानित शुरुआत भविष्य में है।",
                        "तारीख तपासा. गर्भावस्थेची अंदाजित सुरुवात भविष्यात आहे."),
                        color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
private fun PregnancySummary(
    timeline: PregnancyDates,
    language: PregnancyLanguage,
    format: DateTimeFormatter,
    onShare: (String) -> Unit
) = with(language) {
    val weeks = timeline.weeks
    val days = timeline.extraDays
    val gestation = text("$weeks weeks, $days days", "$weeks सप्ताह, $days दिन", "$weeks आठवडे, $days दिवस")
    val completed = timeline.completedMonths
    val running = timeline.runningMonth
    val month = text("Month $running running · $completed completed",
        "महीना $running चल रहा है · $completed पूरे", "महिना $running चालू · $completed पूर्ण")
    val stage = text("Trimester ${timeline.trimester}", "तिमाही ${timeline.trimester}", "तिमाही ${timeline.trimester}")
    val cream = Color(0xFFF7F2E7)
    val accent = Color(0xFFEBC586)
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), color = Color(0xFF304238),
        contentColor = cream) {
        Column(Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(Color(0xFF203830), Color(0xFF476052)))).padding(20.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Text(stage, color = accent, style = MaterialTheme.typography.labelLarge)
            Text(gestation, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
            Text(month, style = MaterialTheme.typography.bodyMedium)
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                val segments = listOf(0L to 98L, 98L to 98L, 196L to 84L)
                segments.forEach { (start, length) ->
                    val fill = ((timeline.daysElapsed - start) / length.toFloat()).coerceIn(0f, 1f)
                    Box(Modifier.weight(length.toFloat()).height(6.dp).background(cream.copy(alpha = .25f), RoundedCornerShape(4.dp))) {
                        if (fill > 0f) Box(Modifier.fillMaxWidth(fill).height(6.dp).background(accent, RoundedCornerShape(4.dp)))
                    }
                }
            }
        }
    }
    val remaining = timeline.daysToDueDate
    val until37 = timeline.daysToWeek37
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(MaterialTheme.colorScheme.surface, MaterialTheme.colorScheme.secondaryContainer))).padding(18.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(text("Estimated due date", "अनुमानित प्रसव तारीख", "अंदाजित प्रसूती तारीख"),
                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(timeline.dueDate.format(format), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text(when {
                remaining > 0 -> text("$remaining days until due date", "प्रसव तारीख तक $remaining दिन", "प्रसूती तारखेपर्यंत $remaining दिवस")
                remaining == 0L -> text("Due date today", "प्रसव की अनुमानित तारीख आज है", "अंदाजित प्रसूती तारीख आज आहे")
                else -> text("${-remaining} days past due date", "प्रसव तारीख से ${-remaining} दिन आगे", "प्रसूती तारखेनंतर ${-remaining} दिवस")
            }, style = MaterialTheme.typography.bodyMedium)
            if (until37 > 0) Text(text("$until37 days until 37 weeks", "37 सप्ताह तक $until37 दिन",
                "३७ आठवड्यांपर्यंत $until37 दिवस"), style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = { onShare("$gestation\n$month\n$stage") }) {
                Text(text("Share milestone", "पड़ाव साझा करें", "टप्पा शेअर करा"))
            }
        }
    }
    Text(text("Calendar months are approximate. Use the due date confirmed by your maternity care team.",
        "कैलेंडर महीने अनुमानित हैं। अपनी मातृत्व देखभाल टीम की पुष्टि की हुई तारीख मानें।",
        "कॅलेंडरमधील महिने अंदाजे आहेत. प्रसूती-सेवा पथकाने निश्चित केलेली तारीख वापरा."),
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
