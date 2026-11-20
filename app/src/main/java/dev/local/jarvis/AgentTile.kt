package dev.local.jarvis

import android.content.Intent
import android.service.quicksettings.TileService

class AgentTile : TileService() {
    override fun onClick() {
        super.onClick()
        val open = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra("from_tile", true)
        unlockAndRun { startActivityAndCollapse(open) }
    }
}
