package com.steplite.pedometer.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.steplite.pedometer.data.DailyStepEntity
import com.steplite.pedometer.data.PeriodSummary
import com.steplite.pedometer.data.StepDatabase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

enum class HistoryRange { WEEK_7D, MONTH_30D, YEAR_12M, MULTI_YEAR_ALL }

class StepViewModel(application: Application) : AndroidViewModel(application) {
    private val dao = StepDatabase.getInstance(application).stepDao()
    private val today = LocalDate.now()

    val selectedYear = MutableStateFlow(today.year)

    val todayState: StateFlow<DailyStepEntity?> = dao
        .observeDay(today.toString())
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /** 7-Day Week History */
    val weekHistory: StateFlow<List<DailyStepEntity>> = dao
        .observeRecentDays(today.minusDays(6).toEpochDay())
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** 30-Day Month History */
    val monthHistory: StateFlow<List<DailyStepEntity>> = dao
        .observeRecentDays(today.minusDays(29).toEpochDay())
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** 12-Month Breakdown for Selected Year */
    val yearMonthlyTotals: StateFlow<List<PeriodSummary>> = selectedYear
        .flatMapLatest { yr -> dao.observeMonthlyTotalsForYear(yr) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Multi-Year All-Time Totals (tracks 5–10+ years seamlessly) */
    val multiYearTotals: StateFlow<List<PeriodSummary>> = dao
        .observeYearlyTotals()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun exportFullMultiYearCsv(onResult: (String) -> Unit) {
        viewModelScope.launch {
            val allDays = dao.getAllHistoryForCsv()
            val sb = StringBuilder("Date,Year,YearMonth,Steps,Goal,Distance_m,Calories_kcal,ActiveSeconds\n")
            for (row in allDays) {
                sb.append("${row.dateIso},${row.year},${row.yearMonth},${row.steps},${row.goal},${row.distanceMeters},${row.caloriesKcal},${row.activeSeconds}\n")
            }
            onResult(sb.toString())
        }
    }
}
