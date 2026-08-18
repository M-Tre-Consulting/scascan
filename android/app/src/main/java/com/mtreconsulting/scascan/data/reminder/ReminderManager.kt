package com.mtreconsulting.scascan.data.reminder

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.mtreconsulting.scascan.data.local.UserProfileStore
import com.mtreconsulting.scascan.data.worker.HydrationReminderWorker
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Schedules SLOT_COUNT one-shot hydration reminders spread evenly across the day's window.
 * Mirrors iOS's hydration scheduling (see ios/ARCHITECTURE.md §11): logging water pushes the
 * remaining (future) reminders back proportionally to the amount, so a reminder never fires
 * right after the user already drank.
 */
@Singleton
class ReminderManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val profileStore: UserProfileStore
) {
    private val workManager = WorkManager.getInstance(context)
    private val zone = ZoneId.systemDefault()

    /** (Re)computes today's schedule if needed, and enqueues one-shot work for every slot still ahead. */
    fun topUpTodaySchedule() {
        if (!profileStore.waterRemindersEnabled) return
        val today = LocalDate.now(zone)
        val stamp = today.toEpochDay()

        val slots = if (profileStore.reminderSlotsDayStamp == stamp && profileStore.reminderSlotsCsv.isNotBlank()) {
            parseSlots(profileStore.reminderSlotsCsv)
        } else {
            val computed = evenSlotsFor(today)
            profileStore.reminderSlotsDayStamp = stamp
            profileStore.reminderSlotsCsv = joinSlots(computed)
            computed
        }
        slots.forEachIndexed { index, epochMs -> scheduleSlot(index, epochMs) }
    }

    /** Pushes every future slot later, proportionally to [amountMl] relative to the user's typical quick-add. */
    fun onWaterLogged(amountMl: Int) {
        if (!profileStore.waterRemindersEnabled) return
        val today = LocalDate.now(zone)
        if (profileStore.reminderSlotsDayStamp != today.toEpochDay()) return
        val slots = parseSlots(profileStore.reminderSlotsCsv).toMutableList()
        if (slots.isEmpty()) return

        val now = System.currentTimeMillis()
        val typical = profileStore.typicalQuickAddMl.coerceAtLeast(1)
        val pushMs = ((amountMl.toDouble() / typical) * PUSH_UNIT_MS).toLong()
        val windowEndMs = windowEnd(today)

        for (i in slots.indices) {
            if (slots[i] > now) {
                slots[i] = (slots[i] + pushMs).coerceAtMost(windowEndMs)
            }
        }

        profileStore.reminderSlotsCsv = joinSlots(slots)
        slots.forEachIndexed { index, epochMs -> scheduleSlot(index, epochMs) }
    }

    fun cancelHydrationReminder() {
        for (i in 0 until SLOT_COUNT) workManager.cancelUniqueWork(workName(i))
        profileStore.reminderSlotsCsv = ""
    }

    private fun scheduleSlot(index: Int, epochMs: Long) {
        val delay = epochMs - System.currentTimeMillis()
        if (delay <= 0) {
            workManager.cancelUniqueWork(workName(index))
            return
        }
        val request = OneTimeWorkRequestBuilder<HydrationReminderWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .build()
        workManager.enqueueUniqueWork(workName(index), ExistingWorkPolicy.REPLACE, request)
    }

    private fun evenSlotsFor(day: LocalDate): List<Long> {
        val startMs = day.atTime(WINDOW_START_HOUR, 0).atZone(zone).toInstant().toEpochMilli()
        val endMs = windowEnd(day)
        val step = (endMs - startMs) / (SLOT_COUNT + 1)
        return (1..SLOT_COUNT).map { startMs + step * it }
    }

    private fun windowEnd(day: LocalDate): Long =
        day.atTime(WINDOW_END_HOUR, 0).atZone(zone).toInstant().toEpochMilli()

    private fun parseSlots(csv: String): List<Long> = csv.split(",").mapNotNull { it.toLongOrNull() }

    private fun joinSlots(slots: List<Long>): String = slots.joinToString(",")

    private fun workName(index: Int) = "hydration_slot_$index"

    companion object {
        private const val WINDOW_START_HOUR = 10
        private const val WINDOW_END_HOUR = 20
        private const val SLOT_COUNT = 3

        /** How much a "typical" quick-add push the remaining reminders back by. Tunable. */
        private const val PUSH_UNIT_MS = 60 * 60 * 1000L
    }
}
