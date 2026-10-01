package com.tunegrab.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.util.Size
import android.widget.RemoteViews
import com.tunegrab.app.MainActivity
import com.tunegrab.app.R
import com.tunegrab.app.player.PlayerManager
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Home-screen widget host. Declared in the manifest with player_widget_info. */
class PlayerWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        // Never let a widget refresh crash the update: a throw here is what
        // the launcher renders as "Can't load widget".
        runCatching { PlayerWidget.refresh(context) }
    }
}

/** Receives widget button taps and forwards them to the player. */
class PlayerWidgetReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        // Make sure the controller is connected (no-op when it already is).
        PlayerManager.connect(app)
        // Queue the tap until the connection completes: the first tap used to
        // arrive before connect finished and get silently dropped.
        when (intent.action) {
            PlayerWidget.ACTION_TOGGLE -> PlayerManager.runWhenConnected { PlayerManager.togglePlayPause() }
            PlayerWidget.ACTION_NEXT -> PlayerManager.runWhenConnected { PlayerManager.next() }
            PlayerWidget.ACTION_PREV -> PlayerManager.runWhenConnected { PlayerManager.previous() }
        }
        // PlayerManager's state callbacks push a refresh once the command lands.
    }
}

/**
 * Builds and pushes the widget's RemoteViews. [refresh] is called from
 * [PlayerWidgetProvider.onUpdate] and from [PlayerManager] whenever playback
 * state or the current track changes; it no-ops when no widget is pinned.
 */
object PlayerWidget {
    const val ACTION_TOGGLE = "com.tunegrab.app.widget.TOGGLE"
    const val ACTION_NEXT = "com.tunegrab.app.widget.NEXT"
    const val ACTION_PREV = "com.tunegrab.app.widget.PREV"

    private val scope = CoroutineScope(
        SupervisorJob() + Dispatchers.Main.immediate +
            CoroutineExceptionHandler { _, _ -> /* widget refresh must never crash */ }
    )

    fun refresh(context: Context) {
        val app = context.applicationContext
        val mgr = AppWidgetManager.getInstance(app)
        val ids = mgr.getAppWidgetIds(ComponentName(app, PlayerWidgetProvider::class.java))
        if (ids.isEmpty()) return
        val playing = runCatching { PlayerManager.isPlaying.value }.getOrDefault(false)
        val info = runCatching { PlayerManager.nowPlayingInfo() }.getOrNull()
        scope.launch(Dispatchers.IO) {
            val art = info?.artworkUri?.let { loadArt(app, it) }
            withContext(Dispatchers.Main) {
                ids.forEach { id ->
                    runCatching { updateOne(app, mgr, id, playing, info, art) }
                }
            }
        }
    }

    private fun updateOne(
        app: Context,
        mgr: AppWidgetManager,
        id: Int,
        playing: Boolean,
        info: PlayerManager.NowPlayingInfo?,
        art: Bitmap?,
    ) {
        val views = RemoteViews(app.packageName, R.layout.widget_player)
        views.setTextViewText(R.id.widget_title, info?.title ?: "TuneGrab")
        views.setTextViewText(
            R.id.widget_artist,
            info?.artist?.takeIf { it.isNotBlank() } ?: "Nothing playing",
        )
        views.setImageViewResource(
            R.id.widget_play,
            if (playing) android.R.drawable.ic_media_pause
            else android.R.drawable.ic_media_play,
        )
        if (art != null) views.setImageViewBitmap(R.id.widget_art, art)
        else views.setImageViewResource(
            R.id.widget_art,
            android.R.drawable.ic_media_play,
        )
        views.setOnClickPendingIntent(
            R.id.widget_play, actionIntent(app, ACTION_TOGGLE, id)
        )
        views.setOnClickPendingIntent(
            R.id.widget_next, actionIntent(app, ACTION_NEXT, id)
        )
        views.setOnClickPendingIntent(
            R.id.widget_prev, actionIntent(app, ACTION_PREV, id)
        )
        views.setOnClickPendingIntent(R.id.widget_root, openAppIntent(app))
        mgr.updateAppWidget(id, views)
    }

    private fun actionIntent(context: Context, action: String, widgetId: Int): PendingIntent {
        val intent = Intent(context, PlayerWidgetReceiver::class.java).setAction(action)
        // Distinct request codes so the three buttons don't overwrite each other.
        val code = widgetId * 31 + action.hashCode()
        return PendingIntent.getBroadcast(
            context,
            code,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    private fun openAppIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
        return PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    private fun loadArt(context: Context, uri: Uri): Bitmap? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return null
        return runCatching {
            context.contentResolver.loadThumbnail(uri, Size(192, 192), null)
        }.getOrNull()
    }
}
