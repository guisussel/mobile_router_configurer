package com.roteador.core.designsystem

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/** Icons missing from material-icons-core (Material Symbols path data, Apache 2.0). */
object RoteadorIcons {
    val Wifi: ImageVector by lazy {
        icon(
            "Wifi",
            "M1,9l2,2c4.97,-4.97 13.03,-4.97 18,0l2,-2C16.93,2.93 7.08,2.93 1,9zM9,17l3,3 3,-3c-1.65,-1.66 -4.34,-1.66 -6,0zM5,13l2,2c2.76,-2.76 7.24,-2.76 10,0l2,-2C15.14,9.14 8.87,9.14 5,13z",
        )
    }

    val SignalBars: ImageVector by lazy {
        icon("SignalBars", "M17,4h3v16h-3zM5,14h3v6L5,20zM11,9h3v11h-3z")
    }

    val Devices: ImageVector by lazy {
        icon(
            "Devices",
            "M4,6h18L22,4L4,4c-1.1,0 -2,0.9 -2,2v11L0,17v3h14v-3L4,17L4,6zM23,8h-6c-0.55,0 -1,0.45 -1,1v10c0,0.55 0.45,1 1,1h6c0.55,0 1,-0.45 1,-1L24,9c0,-0.55 -0.45,-1 -1,-1zM22,17h-4v-7h4v7z",
        )
    }

    val Speed: ImageVector by lazy {
        icon(
            "Speed",
            "M20.38,8.57l-1.23,1.85a8,8 0,0 1,-0.22 7.58L5.07,18A8,8 0,0 1,15.58 6.85l1.85,-1.23A10,10 0,0 0,3.35 19a2,2 0,0 0,1.72 1h13.85a2,2 0,0 0,1.74 -1,10 10,0 0,0 -0.27,-10.44zM10.59,15.41a2,2 0,0 0,2.83 0l5.66,-8.49 -8.49,5.66a2,2 0,0 0,0 2.83z",
        )
    }

    private fun icon(name: String, path: String): ImageVector =
        ImageVector.Builder(name = name, defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
            .addPath(pathData = addPathNodes(path), fill = SolidColor(Color.Black))
            .build()
}
