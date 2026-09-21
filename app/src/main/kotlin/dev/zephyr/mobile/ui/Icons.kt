package dev.zephyr.mobile.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/**
 * Drawn here rather than pulled from an icon library: it keeps the app free of
 * a dependency whose contents nobody reviews, keeps the download small, and
 * matches the 2px round stroke the desktop build uses.
 */
private fun stroke(name: String, vararg pathData: String): ImageVector =
    ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        pathData.forEach { data ->
            addPath(
                pathData = PathParser().parsePathString(data).toNodes(),
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
        }
    }.build()

object ZIcon {
    val Activity = stroke("activity", "M3 12h4l3-8l4 16l3-8h4")
    val Globe = stroke(
        "globe",
        "M12 3a9 9 0 1 0 0 18a9 9 0 1 0 0-18",
        "M3 12h18",
        "M12 3a13 9 0 0 1 0 18a13 9 0 0 1 0-18",
    )
    val Download = stroke("download", "M12 3v12", "M7 11l5 5l5-5", "M4 20h16")
    val Terminal = stroke("terminal", "M5 7l4 4l-4 4", "M12 16h7")
    val Sliders = stroke(
        "sliders",
        "M4 7h16", "M4 12h16", "M4 17h16",
        "M9 5v4", "M15 10v4", "M7 15v4",
    )
    val Refresh = stroke("refresh", "M20 12a8 8 0 1 1-2.3-5.6", "M20 4v5h-5")
    val Plus = stroke("plus", "M12 5v14", "M5 12h14")
    val Close = stroke("close", "M6 6l12 12", "M18 6l-12 12")
    val Check = stroke("check", "M5 13l4 4l10-10")
    val ChevronRight = stroke("chevron-right", "M9 6l6 6l-6 6")
    val ChevronDown = stroke("chevron-down", "M6 9l6 6l6-6")
    val Zap = stroke("zap", "M13 2L4 14h7l-1 8l9-12h-7z")
    val Search = stroke("search", "M11 4a7 7 0 1 0 0 14a7 7 0 1 0 0-14", "M16 16l4 4")
    val Trash = stroke(
        "trash",
        "M4 7h16", "M10 11v6", "M14 11v6",
        "M6 7l1 13h10l1-13", "M9 7V4h6v3",
    )
    val ArrowDown = stroke("arrow-down", "M12 5v14", "M6 13l6 6l6-6")
    val ArrowUp = stroke("arrow-up", "M12 19V5", "M6 11l6-6l6 6")
    val Route = stroke(
        "route",
        "M6 4a2 2 0 1 0 0 4a2 2 0 1 0 0-4",
        "M18 16a2 2 0 1 0 0 4a2 2 0 1 0 0-4",
        "M6 8v4a4 4 0 0 0 4 4h4",
    )
    val Shield = stroke("shield", "M12 3l8 3v6c0 5-4 8-8 9c-4-1-8-4-8-9V6z")
    val ListChecks = stroke(
        "list-checks",
        "M4 6l2 2l3-3", "M4 15l2 2l3-3", "M13 7h7", "M13 16h7",
    )
    val Power = stroke("power", "M12 4v8", "M7.5 6.5a7 7 0 1 0 9 0")
    val Link = stroke(
        "link",
        "M9 15l6-6",
        "M10 7l1-1a4 4 0 0 1 6 6l-1 1",
        "M14 17l-1 1a4 4 0 0 1-6-6l1-1",
    )
    val Cpu = stroke(
        "cpu",
        "M7 7h10v10H7z", "M9 3v2", "M15 3v2", "M9 19v2", "M15 19v2",
        "M3 9h2", "M3 15h2", "M19 9h2", "M19 15h2",
    )
    val Alert = stroke("alert", "M12 4v9", "M12 17v.5", "M12 3a9 9 0 1 0 0 18a9 9 0 1 0 0-18")
}
