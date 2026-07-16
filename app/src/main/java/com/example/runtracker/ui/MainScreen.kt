package com.example.runtracker.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.runtracker.core.Split
import com.example.runtracker.core.TrackSnapshot
import com.example.runtracker.util.Formatters
import com.example.runtracker.viewmodel.TrackingViewModel

@Composable
fun MainScreen(
    viewModel: TrackingViewModel,
    onOpenHistory: () -> Unit
) {
    val state by viewModel.liveState.collectAsState()
    val isTracking by viewModel.isTracking.collectAsState()
    val goalReached by viewModel.goalReachedEvent.collectAsState()

    var goalInput by remember { mutableStateOf("") }
    var goalUnitIsKm by remember { mutableStateOf(true) }

    if (goalReached) {
        AlertDialog(
            onDismissRequest = { /* must choose an action */ },
            title = { Text("Goal reached! \uD83C\uDFC1") },
            text = { Text("You've covered ${Formatters.distance(state.totalDistanceMeters)}. Keep going or wrap up?") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.snoozeGoal(500.0)
                    viewModel.consumeGoalReachedEvent()
                }) { Text("Snooze +500m") }
            },
            dismissButton = {
                TextButton(onClick = {
                    viewModel.consumeGoalReachedEvent()
                    viewModel.stopSession()
                }) { Text("Stop") }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("RunTracker", style = MaterialTheme.typography.headlineMedium)
            IconButton(onClick = onOpenHistory) {
                Icon(Icons.Filled.History, contentDescription = "History")
            }
        }

        Spacer(Modifier.height(24.dp))

        if (!isTracking) {
            GoalInput(
                goalInput = goalInput,
                onGoalInputChange = { goalInput = it },
                goalUnitIsKm = goalUnitIsKm,
                onUnitChange = { goalUnitIsKm = it }
            )
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = {
                    val meters = parseGoalMeters(goalInput, goalUnitIsKm)
                    viewModel.startSession(meters)
                },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                colors = ButtonDefaults.buttonColors()
            ) {
                Text("Start run", style = MaterialTheme.typography.titleMedium)
            }
        } else {
            LiveStats(state = state)
            Spacer(Modifier.height(16.dp))

            state.goalMeters?.let { goal ->
                val progress = (state.totalDistanceMeters / goal).toFloat().coerceIn(0f, 1f)
                Column {
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth().height(8.dp)
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Goal: ${Formatters.distance(goal)}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                Spacer(Modifier.height(16.dp))
            }

            if (!state.gpsLocked) {
                Text(
                    "Waiting for GPS signal\u2026 go outdoors for best results.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.tertiary
                )
                Spacer(Modifier.height(12.dp))
            }

            Text("Splits", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            LazyColumn(modifier = Modifier.weight(1f, fill = true)) {
                items(state.splits.reversed()) { split ->
                    SplitRow(split)
                }
            }

            Spacer(Modifier.height(16.dp))
            OutlinedButton(
                onClick = { viewModel.stopSession() },
                modifier = Modifier.fillMaxWidth().height(56.dp)
            ) {
                Text("Stop run", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@Composable
private fun GoalInput(
    goalInput: String,
    onGoalInputChange: (String) -> Unit,
    goalUnitIsKm: Boolean,
    onUnitChange: (Boolean) -> Unit
) {
    Text("Set a distance goal (optional)", style = MaterialTheme.typography.titleMedium)
    Spacer(Modifier.height(8.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = goalInput,
            onValueChange = onGoalInputChange,
            label = { Text("Distance") },
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.weight(1f)
        )
        Spacer(Modifier.width(12.dp))
        SegmentedUnitToggle(goalUnitIsKm, onUnitChange)
    }
}

@Composable
private fun SegmentedUnitToggle(isKm: Boolean, onChange: (Boolean) -> Unit) {
    Row {
        TextButton(onClick = { onChange(true) }) {
            Text(if (isKm) "km \u2713" else "km")
        }
        TextButton(onClick = { onChange(false) }) {
            Text(if (!isKm) "m \u2713" else "m")
        }
    }
}

@Composable
private fun LiveStats(state: TrackSnapshot) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                Formatters.distance(state.totalDistanceMeters),
                style = MaterialTheme.typography.displayLarge,
                color = MaterialTheme.colorScheme.onPrimary
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                StatColumn("Time", Formatters.duration(state.elapsedMillis))
                StatColumn("Pace", Formatters.pace(state.currentPaceSecPerKm))
                StatColumn("Avg pace", Formatters.pace(state.avgPaceSecPerKm))
            }
        }
    }
}

@Composable
private fun StatColumn(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimary)
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimary)
    }
}

@Composable
private fun SplitRow(split: Split) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text("Split ${split.index}", fontWeight = FontWeight.Medium)
        Text(Formatters.distance(split.distanceMeters))
        Text(Formatters.duration(split.durationMillis))
        Text(Formatters.pace(split.paceSecPerKm))
    }
}

private fun parseGoalMeters(input: String, isKm: Boolean): Double? {
    val value = input.trim().toDoubleOrNull() ?: return null
    if (value <= 0) return null
    return if (isKm) value * 1000.0 else value
}
