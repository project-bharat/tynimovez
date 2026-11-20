package com.projectbharat.counterapp

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.projectbharat.counterapp.data.CounterLog
import com.projectbharat.counterapp.data.DayTotal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun CounterHistoryScreen(
    english: Boolean,
    ink: Color,
    date: LocalDate,
    logs: List<CounterLog>,
    days: List<DayTotal>,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onUpdate: (CounterLog) -> Unit,
    onDelete: (CounterLog) -> Unit
) {
    var editing by remember { mutableStateOf<CounterLog?>(null) }
    var deleting by remember { mutableStateOf<CounterLog?>(null) }
    val total = logs.sumOf { it.count }
    Column(Modifier.fillMaxSize()) {
        Text(if (english) "HISTORY & ANALYTICS" else "इतिहास व विश्लेषण", color = ink, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Card(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 6.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.fillMaxWidth().padding(12.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text(if (english) "7-day total" else "७ दिवसांची एकूण मोजणी", color = ink.copy(alpha = .6f), style = MaterialTheme.typography.labelSmall)
                        Text(days.sumOf { it.total }.toString(), color = ink, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(if (english) "Daily average" else "दररोज सरासरी", color = ink.copy(alpha = .6f), style = MaterialTheme.typography.labelSmall)
                        Text("%.1f".format(days.sumOf { it.total }.toDouble() / 7.0), color = ink, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    }
                }
                WeeklyChart(days, Modifier.fillMaxWidth().height(92.dp).padding(top = 8.dp))
            }
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = onPrevious) { Text("‹ " + if (english) "Previous" else "मागील") }
            Text(date.format(DateTimeFormatter.ofPattern("dd MMM yyyy")), color = ink, fontWeight = FontWeight.SemiBold)
            TextButton(onClick = onNext, enabled = date.isBefore(LocalDate.now())) { Text((if (english) "Next" else "पुढील") + " ›") }
        }
        Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface) {
            Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text(if (english) "Recorded entries" else "नोंदी", color = ink.copy(alpha = .6f), style = MaterialTheme.typography.labelSmall)
                    Text(logs.size.toString(), color = ink, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(if (english) "Total count" else "एकूण मोजणी", color = ink.copy(alpha = .6f), style = MaterialTheme.typography.labelSmall)
                    Text(total.toString(), color = ink, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        if (logs.isEmpty()) {
            Text(if (english) "No entries recorded on this date." else "या दिवशी कोणतीही नोंद नाही.", color = ink.copy(alpha = .6f), modifier = Modifier.padding(12.dp))
        } else {
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp), contentPadding = PaddingValues(bottom = 12.dp)) {
                items(logs, key = { it.id }) { log ->
                    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                        Row(Modifier.fillMaxWidth().padding(start = 13.dp, end = 5.dp, top = 9.dp, bottom = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f).clickable { editing = log }.padding(vertical = 3.dp)) {
                                val time = Instant.ofEpochMilli(log.timestamp).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("hh:mm:ss a"))
                                Text(time + "  ·  +" + log.count, color = ink, fontWeight = FontWeight.SemiBold)
                                val details = listOfNotNull(log.strength?.takeIf { it.isNotBlank() }, log.note?.takeIf { it.isNotBlank() }).joinToString(" · ")
                                if (details.isNotBlank()) Text(details, color = ink.copy(alpha = .62f), style = MaterialTheme.typography.bodySmall)
                                if (log.sessionId != null) Text(if (english) "Baby movement session" else "बाळाच्या हालचालींचे सत्र", color = ink.copy(alpha = .48f), style = MaterialTheme.typography.labelSmall)
                            }
                            IconButton(onClick = { editing = log }) { Icon(Icons.Default.Edit, contentDescription = if (english) "Edit" else "बदला", tint = ink.copy(alpha = .7f)) }
                            IconButton(onClick = { deleting = log }) { Icon(Icons.Default.Delete, contentDescription = if (english) "Delete" else "हटवा", tint = Color(0xFFB34E55)) }
                        }
                    }
                }
            }
        }
    }
    editing?.let { original ->
        var strength by remember(original.id) { mutableStateOf(original.strength.orEmpty()) }
        var note by remember(original.id) { mutableStateOf(original.note.orEmpty()) }
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text(if (english) "Edit entry" else "नोंद बदला") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(strength, { strength = it }, label = { Text(if (english) "Strength / tag" else "ताकद / खूण") }, singleLine = true)
                    OutlinedTextField(note, { note = it }, label = { Text(if (english) "Note" else "टीप") }, minLines = 2)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    onUpdate(original.copy(strength = strength.trim().ifBlank { null }, note = note.trim().ifBlank { null }))
                    editing = null
                }) { Text(if (english) "Save" else "जतन करा") }
            },
            dismissButton = { TextButton(onClick = { editing = null }) { Text(if (english) "Cancel" else "रद्द") } }
        )
    }
    deleting?.let { item ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(if (english) "Delete this entry?" else "ही नोंद हटवायची?") },
            text = { Text(if (english) "This cannot be undone." else "ही कृती मागे घेता येणार नाही.") },
            confirmButton = { TextButton(onClick = { onDelete(item); deleting = null }) { Text(if (english) "Delete" else "हटवा") } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text(if (english) "Cancel" else "रद्द") } }
        )
    }
}
