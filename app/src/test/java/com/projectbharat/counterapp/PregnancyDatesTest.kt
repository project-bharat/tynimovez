package com.projectbharat.counterapp

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class PregnancyDatesTest {
    @Test
    fun datingMethodsProduceConsistentDueDate() {
        val start = LocalDate.of(2026, 1, 1)
        val due = start.plusDays(280)
        assertEquals(due, DatingMethod.LMP.dueDate(start, 28))
        assertEquals(due, DatingMethod.LMP.dueDate(start.minusDays(4), 32))
        assertEquals(due, DatingMethod.CONCEPTION.dueDate(start.plusDays(14), 28))
        assertEquals(due, DatingMethod.IVF_DAY_3.dueDate(start.plusDays(17), 28))
        assertEquals(due, DatingMethod.IVF_DAY_5.dueDate(start.plusDays(19), 28))
        assertEquals(due, DatingMethod.DUE_DATE.dueDate(due, 28))
    }

    @Test
    fun calendarMonthRollsOverOnClampedEndOfMonth() {
        val start = LocalDate.of(2026, 1, 31)
        val due = start.plusDays(280)
        assertEquals(0L, PregnancyDates(due, LocalDate.of(2026, 2, 27)).completedMonths)
        val atMonth = PregnancyDates(due, LocalDate.of(2026, 2, 28))
        assertEquals(1L, atMonth.completedMonths)
        assertEquals(2L, atMonth.runningMonth)
        assertEquals(4L, atMonth.weeks)
        assertEquals(0L, atMonth.extraDays)
    }

    @Test
    fun trimesterAndCountdownUseGestationalDays() {
        val start = LocalDate.of(2026, 1, 1)
        val due = start.plusDays(280)
        assertEquals(1, PregnancyDates(due, start.plusDays(97)).trimester)
        assertEquals(2, PregnancyDates(due, start.plusDays(98)).trimester)
        assertEquals(3, PregnancyDates(due, start.plusDays(196)).trimester)
        assertEquals(1L, PregnancyDates(due, start.plusDays(258)).daysToWeek37)
        assertEquals(0L, PregnancyDates(due, start.plusDays(259)).daysToWeek37)
        assertEquals(-1L, PregnancyDates(due, due.plusDays(1)).daysToDueDate)
    }
}
