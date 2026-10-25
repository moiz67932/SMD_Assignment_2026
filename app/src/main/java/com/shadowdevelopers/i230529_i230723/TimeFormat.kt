package com.shadowdevelopers.i230529_i230723

import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** One place for every timestamp shown in the app: "now", "5m", "2h", "Yesterday", "12 Oct". */
object TimeFormat {
    fun ago(time: Long): String {
        if (time <= 0L) return "now"
        val diff = (FirebaseData.now() - time).coerceAtLeast(0)
        val minutes = diff / 60_000
        return when {
            minutes < 1 -> "now"
            minutes < 60 -> "${minutes}m"
            minutes < 24 * 60 && isSameDay(time, FirebaseData.now()) -> "${minutes / 60}h"
            isSameDay(time, FirebaseData.now() - 86_400_000) -> "Yesterday"
            minutes < 24 * 60 -> "${minutes / 60}h"
            isSameYear(time) -> SimpleDateFormat("d MMM", Locale.getDefault()).format(Date(time))
            else -> SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(Date(time))
        }
    }

    /** Clock time such as "4:05 PM" (follows the device's 12/24 h setting). */
    fun clock(time: Long): String = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(time))

    /** Call duration such as "2:31". */
    fun duration(seconds: Int) = String.format(Locale.US, "%d:%02d", seconds / 60, seconds % 60)

    private fun isSameDay(a: Long, b: Long): Boolean {
        val x = Calendar.getInstance().apply { timeInMillis = a }
        val y = Calendar.getInstance().apply { timeInMillis = b }
        return x.get(Calendar.YEAR) == y.get(Calendar.YEAR) && x.get(Calendar.DAY_OF_YEAR) == y.get(Calendar.DAY_OF_YEAR)
    }

    private fun isSameYear(time: Long) =
        Calendar.getInstance().apply { timeInMillis = time }.get(Calendar.YEAR) == Calendar.getInstance().get(Calendar.YEAR)
}
