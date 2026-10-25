package com.steplite.pedometer.ui

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.steplite.pedometer.sensor.StepSensorService

class MainActivity : ComponentActivity() {

    private val viewModel: StepViewModel by viewModels()

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        startStepService()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestNecessaryPermissionsAndStart()

        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                StepLiteAppScreen(viewModel = viewModel)
            }
        }
    }

    private fun requestNecessaryPermissionsAndStart() {
        val perms = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            perms.add(Manifest.permission.ACTIVITY_RECOGNITION)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            perms.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (perms.isNotEmpty()) {
            permissionLauncher.launch(perms.toTypedArray())
        } else {
            startStepService()
        }
    }

    private fun startStepService() {
        val intent = Intent(this, StepSensorService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }
}

@Composable
fun StepLiteAppScreen(viewModel: StepViewModel) {
    val today by viewModel.todayState.collectAsState()
    val weekDays by viewModel.weekHistory.collectAsState()
    val monthDays by viewModel.monthHistory.collectAsState()
    val yearMonths by viewModel.yearMonthlyTotals.collectAsState()
    val multiYears by viewModel.multiYearTotals.collectAsState()

    var activeRange by remember { mutableStateOf(HistoryRange.WEEK_7D) }

    val steps = today?.steps ?: 0
    val goal = today?.goal ?: 6000
    val distanceKm = (today?.distanceMeters ?: 0f) / 1000f
    val calories = today?.caloriesKcal ?: 0

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF090D16))
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "StepLite Pedometer",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )

        // Today Hero Card
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF111827)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("TODAY'S STEPS", color = Color(0xFF34D399), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Text("$steps", color = Color.White, fontSize = 44.sp, fontWeight = FontWeight.ExtraBold)
                Text("Goal: $goal steps", color = Color(0xFF94A3B8), fontSize = 13.sp)
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Text(String.format("%.2f km", distanceKm), color = Color.White, fontWeight = FontWeight.Bold)
                    Text("$calories kcal", color = Color(0xFFFBBF24), fontWeight = FontWeight.Bold)
                }
            }
        }

        // Multi-Year History Range Selector (7D / 30D / 12M / Multi-Year)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf(
                HistoryRange.WEEK_7D to "7D",
                HistoryRange.MONTH_30D to "30D",
                HistoryRange.YEAR_12M to "12M (Year)",
                HistoryRange.MULTI_YEAR_ALL to "Years"
            ).forEach { (range, label) ->
                val selected = activeRange == range
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .background(
                            if (selected) Color(0xFF10B981) else Color(0xFF1E293B),
                            RoundedCornerShape(12.dp)
                        )
                        .clickable { activeRange = range }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (selected) Color(0xFF022C22) else Color.White
                    )
                }
            }
        }

        // History Summary Card
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF111827)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                when (activeRange) {
                    HistoryRange.WEEK_7D -> {
                        val total = weekDays.sumOf { it.steps }
                        Text("Last 7 Days Total: $total steps", color = Color.White, fontWeight = FontWeight.Bold)
                        weekDays.forEach { d ->
                            Text("${d.dateIso}: ${d.steps} steps", color = Color(0xFF94A3B8), fontSize = 13.sp)
                        }
                    }
                    HistoryRange.MONTH_30D -> {
                        val total = monthDays.sumOf { it.steps }
                        Text("Last 30 Days Total: $total steps", color = Color.White, fontWeight = FontWeight.Bold)
                        Text("Recorded days: ${monthDays.size}", color = Color(0xFF94A3B8), fontSize = 13.sp)
                    }
                    HistoryRange.YEAR_12M -> {
                        val yrTotal = yearMonths.sumOf { it.totalSteps }
                        Text("Year Monthly Breakdown: $yrTotal steps", color = Color.White, fontWeight = FontWeight.Bold)
                        yearMonths.forEach { m ->
                            Text("${m.label}: ${m.totalSteps} steps (avg ${m.avgSteps}/day)", color = Color(0xFF94A3B8), fontSize = 13.sp)
                        }
                    }
                    HistoryRange.MULTI_YEAR_ALL -> {
                        val allTime = multiYears.sumOf { it.totalSteps }
                        Text("All-Time Multi-Year Total: $allTime steps", color = Color(0xFF34D399), fontWeight = FontWeight.Bold)
                        multiYears.forEach { y ->
                            Text("Year ${y.label}: ${y.totalSteps} steps (${y.daysGoalMet} goal days)", color = Color.White, fontSize = 13.sp)
                        }
                    }
                }
            }
        }
    }
}
