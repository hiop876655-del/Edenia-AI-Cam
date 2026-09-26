package com.aicamera.recorder

import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.annotation.RequiresApi

/**
 * System Quick Settings Tile for instant bottom sheet access from notification shade.
 */
@RequiresApi(Build.VERSION_CODES.N)
class TileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        val tile = qsTile ?: return
        tile.state = Tile.STATE_INACTIVE
        tile.label = "AI Recorder"
        tile.updateTile()
    }

    override fun onClick() {
        super.onClick()
        // Open the app straight to the transparent Recorder BottomSheet
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("OPEN_RECORDER_SHEET", true)
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startActivityAndCollapse(PendingIntentCompat.getActivity(this, 0, intent, 0, false))
        } else {
            startActivityAndCollapse(intent)
        }
    }
}
