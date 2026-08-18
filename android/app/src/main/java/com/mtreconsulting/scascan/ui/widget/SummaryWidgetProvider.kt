package com.mtreconsulting.scascan.ui.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.mtreconsulting.scascan.MainActivity
import com.mtreconsulting.scascan.R
import com.mtreconsulting.scascan.data.repository.LogRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject
import kotlin.math.roundToInt

@AndroidEntryPoint
class SummaryWidgetProvider : AppWidgetProvider() {

    @Inject
    lateinit var logRepository: LogRepository

    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.Main + job)

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val pendingResult = goAsync()
        scope.launch {
            try {
                for (appWidgetId in appWidgetIds) {
                    updateAppWidget(context, appWidgetManager, appWidgetId)
                }
            } finally {
                pendingResult?.finish()
            }
        }
    }

    private suspend fun updateAppWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val start = cal.timeInMillis
        val end = start + 24 * 60 * 60 * 1000L
        
        val entries = logRepository.getEntriesForRangeSync(start, end)
        val consumed = entries.sumOf { it.calories }.roundToInt()
        val target = logRepository.getLiveTarget()
        
        val p = entries.sumOf { it.protein }.roundToInt()
        val c = entries.sumOf { it.carbohydrates }.roundToInt()
        val f = entries.sumOf { it.fat }.roundToInt()
        
        val water = logRepository.getWaterTotalForRangeSync(start, end)

        val views = RemoteViews(context.packageName, R.layout.widget_summary)
        
        views.setTextViewText(R.id.tv_widget_consumed, consumed.toString())
        views.setTextViewText(R.id.tv_widget_target, "/ $target kcal")
        views.setProgressBar(R.id.progress_widget_calories, target, consumed, false)
        
        // Macro + Water breakdown text
        views.setTextViewText(R.id.tv_widget_macros, "P: ${p}g · C: ${c}g · F: ${f}g · ${water}ml")

        // Setup Intents
        views.setOnClickPendingIntent(R.id.btn_widget_scan, getPendingIntent(context, MainActivity.ACTION_QUICK_SCAN))
        views.setOnClickPendingIntent(R.id.btn_widget_barcode, getPendingIntent(context, MainActivity.ACTION_QUICK_BARCODE))
        views.setOnClickPendingIntent(R.id.btn_widget_search, getPendingIntent(context, MainActivity.ACTION_QUICK_SEARCH))
        views.setOnClickPendingIntent(R.id.widget_root, getPendingIntent(context, MainActivity.ACTION_OPEN_LOG))
        
        // Water Shortcut: Broadcast to self
        val waterIntent = Intent(context, SummaryWidgetProvider::class.java).apply {
            action = ACTION_ADD_WATER
        }
        val waterPending = PendingIntent.getBroadcast(
            context, 200, waterIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.btn_widget_water, waterPending)
        
        // Undo WaterShortcut: Broadcast to self
        val undoIntent = Intent(context, SummaryWidgetProvider::class.java).apply {
            action = ACTION_UNDO_WATER
        }
        val undoPending = PendingIntent.getBroadcast(
            context, 201, undoIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.btn_widget_undo_water, undoPending)

        appWidgetManager.updateAppWidget(appWidgetId, views)
    }

    private fun getPendingIntent(context: Context, action: String?): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            action?.let { this.action = it }
        }
        val requestCode = action?.hashCode() ?: 0
        return PendingIntent.getActivity(
            context, 
            requestCode,
            intent, 
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            ACTION_DATA_CHANGED -> {
                updateAllWidgets(context)
            }
            ACTION_ADD_WATER -> {
                scope.launch {
                    logRepository.addWater(250)
                }
            }
            ACTION_UNDO_WATER -> {
                scope.launch {
                    logRepository.removeLastWater()
                }
            }
        }
    }

    private fun updateAllWidgets(context: Context) {
        val appWidgetManager = AppWidgetManager.getInstance(context)
        val componentName = ComponentName(context, SummaryWidgetProvider::class.java)
        val ids = appWidgetManager.getAppWidgetIds(componentName)
        onUpdate(context, appWidgetManager, ids)
    }

    companion object {
        const val ACTION_DATA_CHANGED = "com.mtreconsulting.scascan.ACTION_WIDGET_DATA_CHANGED"
        const val ACTION_ADD_WATER = "com.mtreconsulting.scascan.ACTION_WIDGET_ADD_WATER"
        const val ACTION_UNDO_WATER = "com.mtreconsulting.scascan.ACTION_WIDGET_UNDO_WATER"

        fun triggerUpdate(context: Context) {
            val intent = Intent(context, SummaryWidgetProvider::class.java).apply {
                action = ACTION_DATA_CHANGED
            }
            context.sendBroadcast(intent)
        }
    }
}
