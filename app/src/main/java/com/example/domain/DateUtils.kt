package com.example.domain

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object DateUtils {
    private val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.ENGLISH)
    private val shortDateFormat = SimpleDateFormat("dd MMM", Locale.ENGLISH)
    private val dateTimeFormat = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.ENGLISH)
    private val billDatePrefixFormat = SimpleDateFormat("yyyyMMdd", Locale.ENGLISH)

    fun formatDate(millis: Long): String = dateFormat.format(Date(millis))

    fun formatShortDate(millis: Long): String = shortDateFormat.format(Date(millis))

    fun formatDateTime(millis: Long): String = dateTimeFormat.format(Date(millis))

    fun startOfDayMillis(millis: Long = System.currentTimeMillis()): Long {
        val cal = Calendar.getInstance().apply {
            timeInMillis = millis
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return cal.timeInMillis
    }

    fun endOfDayMillis(millis: Long = System.currentTimeMillis()): Long {
        val cal = Calendar.getInstance().apply {
            timeInMillis = millis
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
            set(Calendar.MILLISECOND, 999)
        }
        return cal.timeInMillis
    }

    fun startOfDaysAgoMillis(days: Int): Long {
        val cal = Calendar.getInstance().apply {
            timeInMillis = startOfDayMillis()
            add(Calendar.DAY_OF_YEAR, -days)
        }
        return cal.timeInMillis
    }

    fun startOfMonthMillis(): Long {
        val cal = Calendar.getInstance().apply {
            timeInMillis = startOfDayMillis()
            set(Calendar.DAY_OF_MONTH, 1)
        }
        return cal.timeInMillis
    }

    fun formatBillNumberPrefix(millis: Long = System.currentTimeMillis()): String {
        return "JD-${billDatePrefixFormat.format(Date(millis))}"
    }
}
