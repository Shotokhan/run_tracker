package com.example.runtracker.ui

import androidx.compose.foundation.clickable
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.runtracker.data.SessionEntity
import com.example.runtracker.util.Formatters
import com.example.runtracker.viewmodel.TrackingViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HistoryScreen(
    viewModel: TrackingViewModel,
    onBack: () -> Unit,
    onOpenSession: (Long) -> Unit
) {
    val sessions by viewModel.history.collectAsState()
    var pendingDelete by remember { mutableStateOf<SessionEntity?>(null) }

    pendingDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete this run?") },
            text = {
                Text("${Formatters.distance(target.distanceMeters)} \u00b7 ${Formatters.duration(target.durationMillis)} will be permanently removed.")
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteSession(target.id)
                    pendingDelete = null
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Cancel") } }
        )
    }

    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        Row {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
            }
            Spacer(Modifier.height(0.dp))
            Text(
                "History",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(start = 8.dp)
            )
        }
        Spacer(Modifier.height(16.dp))

        if (sessions.isEmpty()) {
            Text("No runs yet \u2014 your completed sessions will show up here.", style = MaterialTheme.typography.bodyLarge)
        } else {
            LazyColumn {
                items(sessions) { session ->
                    SessionRow(session, onClick = { onOpenSession(session.id) }, onDelete = { pendingDelete = session })
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
private fun SessionRow(session: SessionEntity, onClick: () -> Unit, onDelete: () -> Unit) {
    val dateFormat = remember { SimpleDateFormat("EEE, MMM d \u00b7 HH:mm", Locale.getDefault()) }
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(dateFormat.format(Date(session.startEpochMillis)), style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Text(Formatters.distance(session.distanceMeters))
                Text(Formatters.duration(session.durationMillis))
                Text(Formatters.pace(session.avgPaceSecPerKm))
            }
            if (session.goalMeters != null) {
                Spacer(Modifier.height(4.dp))
                Text(
                    if (session.goalWasReached) "Goal reached \u2713" else "Goal: ${Formatters.distance(session.goalMeters)}",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Filled.Delete, contentDescription = "Delete run")
        }
        }
    }
}
