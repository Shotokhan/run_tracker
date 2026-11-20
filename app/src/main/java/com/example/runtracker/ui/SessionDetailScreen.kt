package com.example.runtracker.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.ui.platform.LocalContext
import android.content.Intent
import com.example.runtracker.util.ShareText
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.runtracker.data.SessionEntity
import com.example.runtracker.data.SplitEntity
import com.example.runtracker.util.Formatters
import com.example.runtracker.viewmodel.TrackingViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SessionDetailScreen(
    viewModel: TrackingViewModel,
    sessionId: Long,
    onBack: () -> Unit
) {
    var session by remember { mutableStateOf<SessionEntity?>(null) }
    var splits by remember { mutableStateOf<List<SplitEntity>>(emptyList()) }
    var confirmDelete by remember { mutableStateOf(false) }
    val context = LocalContext.current

    LaunchedEffect(sessionId) {
        session = viewModel.getSession(sessionId)
        splits = viewModel.getSplitsForSession(sessionId)
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this run?") },
            text = { Text("This permanently removes the run and its splits.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    viewModel.deleteSession(sessionId)
                    onBack()
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } }
        )
    }

    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
            }
            Text(
                "Run details",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(start = 8.dp).weight(1f)
            )
            IconButton(
                enabled = session != null,
                onClick = {
                    session?.let { s ->
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, ShareText.build(s, splits))
                        }
                        context.startActivity(Intent.createChooser(send, "Share run"))
                    }
                }
            ) { Icon(Icons.Filled.Share, contentDescription = "Share") }
            IconButton(onClick = { confirmDelete = true }) {
                Icon(Icons.Filled.Delete, contentDescription = "Delete")
            }
        }
        Spacer(Modifier.height(16.dp))

        session?.let { s ->
            val dateFormat = remember { SimpleDateFormat("EEEE, MMM d \u00b7 HH:mm", Locale.getDefault()) }
            Text(dateFormat.format(Date(s.startEpochMillis)), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(32.dp)) {
                LabeledStat("Distance", Formatters.distance(s.distanceMeters))
                LabeledStat("Duration", Formatters.duration(s.durationMillis))
                LabeledStat("Avg pace", Formatters.pace(s.avgPaceSecPerKm))
            }
            Spacer(Modifier.height(24.dp))
            Text("Splits", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Divider()
            LazyColumn {
                items(splits) { split ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Split ${split.splitIndex}", fontWeight = FontWeight.Medium)
                        Text(Formatters.distance(split.distanceMeters))
                        Text(Formatters.duration(split.durationMillis))
                        Text(Formatters.pace(split.paceSecPerKm))
                    }
                    Divider()
                }
            }
        }
    }
}

@Composable
private fun LabeledStat(label: String, value: String) {
    Column {
        Text(value, style = MaterialTheme.typography.titleMedium)
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}
