package com.projectbharat.counterapp

import java.time.LocalDate
import java.time.temporal.ChronoUnit

internal enum class DatingMethod {
    LMP, CONCEPTION, IVF_DAY_3, IVF_DAY_5, DUE_DATE;

    fun dueDate(date: LocalDate, cycleLength: Int): LocalDate = when (this) {
        LMP -> date.plusDays(280L + (cycleLength - 28))
        CONCEPTION -> date.plusDays(266)
        IVF_DAY_3 -> date.plusDays(263)
        IVF_DAY_5 -> date.plusDays(261)
        DUE_DATE -> date
    }
}

internal data class PregnancyDates(val dueDate: LocalDate, val today: LocalDate) {
    val datingStart: LocalDate = dueDate.minusDays(280)
    val daysElapsed = ChronoUnit.DAYS.between(datingStart, today)
    val daysToDueDate = 280 - daysElapsed
    val weeks = daysElapsed / 7
    val extraDays = daysElapsed % 7
    val completedMonths = ChronoUnit.MONTHS.between(datingStart.withDayOfMonth(1), today.withDayOfMonth(1))
        .let { difference -> if (datingStart.plusMonths(difference).isAfter(today)) difference - 1 else difference }
    val runningMonth = completedMonths + 1
    val trimester = when {
        weeks < 14 -> 1
        weeks < 28 -> 2
        else -> 3
    }
    val daysToWeek37 = (259 - daysElapsed).coerceAtLeast(0)
}
