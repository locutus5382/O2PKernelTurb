package com.odin2portal.turbo

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.widget.Toast

class TurboTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        syncTile()
    }

    override fun onClick() {
        super.onClick()
        if (PServerExec.isTurboActive()) {
            PServerExec.revertToStock()
            Toast.makeText(this, "O2P Turbo: OFF (Stock)", Toast.LENGTH_SHORT).show()
        } else {
            PServerExec.applyTurbo()
            Toast.makeText(this, "O2P Turbo: ON (0.5ms Ramp)", Toast.LENGTH_SHORT).show()
        }
        syncTile()
    }

    private fun syncTile() {
        val tile = qsTile ?: return
        val active = PServerExec.isTurboActive()
        tile.state = if (active) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = if (active) "O2P Turbo: ON" else "O2P Turbo: OFF"
        tile.updateTile()
    }
}
