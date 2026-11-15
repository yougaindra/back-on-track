package com.yougaindra.shakelight.tile

import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.annotation.RequiresApi
import com.yougaindra.shakelight.core.FlashlightController

@RequiresApi(Build.VERSION_CODES.N)
class FlashlightTileService : TileService() {

    private var flashlightController: FlashlightController? = null

    override fun onCreate() {
        super.onCreate()
        flashlightController = FlashlightController(applicationContext)
    }

    override fun onStartListening() {
        super.onStartListening()
        val controller = flashlightController ?: return
        updateTileState(controller.isTorchOn.value)
    }

    override fun onClick() {
        super.onClick()
        val controller = flashlightController ?: return
        val newState = !controller.isTorchOn.value
        controller.setTorch(newState)
        updateTileState(newState)
    }

    private fun updateTileState(isOn: Boolean) {
        val tile = qsTile ?: return
        tile.state = if (isOn) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = if (isOn) "Torch ON" else "Torch OFF"
        tile.updateTile()
    }

    override fun onDestroy() {
        flashlightController?.cleanUp()
        flashlightController = null
        super.onDestroy()
    }
}
