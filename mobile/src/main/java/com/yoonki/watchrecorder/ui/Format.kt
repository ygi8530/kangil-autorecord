package com.yoonki.watchrecorder.ui

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object Format {
    private val time = SimpleDateFormat("a h:mm", Locale.KOREA)
    private val dayInYear = SimpleDateFormat("M월 d일 EEEE", Locale.KOREA)
    private val dayOtherYear = SimpleDateFormat("yyyy년 M월 d일 EEEE", Locale.KOREA)
    private val month = SimpleDateFormat("yyyy년 M월", Locale.KOREA)
    private val full = SimpleDateFormat("yyyy년 M월 d일 a h:mm", Locale.KOREA)

    fun time(ms: Long): String = time.format(Date(ms))
    fun full(ms: Long): String = full.format(Date(ms))
    fun month(ms: Long): String = month.format(Date(ms))

    fun day(ms: Long): String {
        val today = startOfDay(System.currentTimeMillis())
        val day = startOfDay(ms)
        return when (day) {
            today -> "오늘"
            today - DAY -> "어제"
            else -> if (year(ms) == year(System.currentTimeMillis())) dayInYear.format(Date(ms)) else dayOtherYear.format(Date(ms))
        }
    }

    fun dayKey(ms: Long) = startOfDay(ms)

    fun monthKey(ms: Long): Int = Calendar.getInstance().apply { timeInMillis = ms }.let { it.get(Calendar.YEAR) * 100 + it.get(Calendar.MONTH) }

    fun duration(ms: Long): String {
        val s = (ms + 500) / 1000
        return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, s / 60 % 60, s % 60) else "%d:%02d".format(s / 60, s % 60)
    }

    /** "1시간 3분", "12분", "40초" */
    fun totalDuration(ms: Long): String {
        val s = ms / 1000
        return when {
            s >= 3600 -> "${s / 3600}시간 ${s / 60 % 60}분"
            s >= 60 -> "${s / 60}분"
            else -> "${s}초"
        }
    }

    fun size(bytes: Long): String = when {
        bytes >= 1024 * 1024 -> "%.1fMB".format(bytes / 1024.0 / 1024.0)
        else -> "${(bytes + 1023) / 1024}KB"
    }

    private const val DAY = 24 * 60 * 60 * 1000L
    private fun startOfDay(ms: Long) = Calendar.getInstance().apply {
        timeInMillis = ms
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private fun year(ms: Long) = Calendar.getInstance().apply { timeInMillis = ms }.get(Calendar.YEAR)
}
