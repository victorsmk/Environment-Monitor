package com.environmentmonitor.app.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp
import com.environmentmonitor.app.data.ProfileIcon
import com.environmentmonitor.app.data.SensorType

/**
 * The app's own icons, drawn here rather than taken from a library or from emoji.
 *
 * They share one style so they look like a set: a 24 by 24 grid, outlines only, a stroke of
 * [STROKE] with round ends and corners, and no fill. Each is a single path in black; the screen
 * draws it in whatever colour it needs (a sensor's colour, the theme's text colour, ...).
 */
private const val STROKE = 1.8f

private fun drawn(name: String, shapes: PathBuilder.() -> Unit): ImageVector =
    ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = STROKE,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
        pathBuilder = shapes,
    ).build()

// --- small drawing helpers ---

private fun PathBuilder.line(x1: Float, y1: Float, x2: Float, y2: Float) {
    moveTo(x1, y1)
    lineTo(x2, y2)
}

private fun PathBuilder.circle(cx: Float, cy: Float, r: Float) {
    moveTo(cx - r, cy)
    arcToRelative(r, r, 0f, true, true, 2 * r, 0f)
    arcToRelative(r, r, 0f, true, true, -2 * r, 0f)
    close()
}

private fun PathBuilder.roundRect(x: Float, y: Float, w: Float, h: Float, r: Float) {
    moveTo(x + r, y)
    lineTo(x + w - r, y)
    arcTo(r, r, 0f, false, true, x + w, y + r)
    lineTo(x + w, y + h - r)
    arcTo(r, r, 0f, false, true, x + w - r, y + h)
    lineTo(x + r, y + h)
    arcTo(r, r, 0f, false, true, x, y + h - r)
    lineTo(x, y + r)
    arcTo(r, r, 0f, false, true, x + r, y)
    close()
}

object AppIcons {

    // --- sensors ---

    /** A thermometer: a tube ending in a bulb, with the mercury line inside. */
    val Thermometer: ImageVector by lazy {
        drawn("Thermometer") {
            moveTo(10f, 13.4f)
            lineTo(10f, 5f)
            arcTo(2f, 2f, 0f, false, true, 14f, 5f)
            lineTo(14f, 13.4f)
            arcTo(4f, 4f, 0f, true, true, 10f, 13.4f)
            close()
            line(12f, 9f, 12f, 16.2f)
            circle(12f, 17f, 0.6f)
        }
    }

    /** A water drop. */
    val Droplet: ImageVector by lazy {
        drawn("Droplet") {
            moveTo(12f, 3f)
            curveTo(12f, 3f, 5.5f, 10f, 5.5f, 14.5f)
            arcTo(6.5f, 6.5f, 0f, false, false, 18.5f, 14.5f)
            curveTo(18.5f, 10f, 12f, 3f, 12f, 3f)
            close()
            // A small highlight on the lower left.
            moveTo(8.6f, 14.6f)
            arcTo(3.4f, 3.4f, 0f, false, false, 11.6f, 17.8f)
        }
    }

    /** A light bulb. */
    val Bulb: ImageVector by lazy {
        drawn("Bulb") {
            moveTo(9f, 16f)
            lineTo(9f, 14.3f)
            arcTo(6f, 6f, 0f, true, true, 15f, 14.3f)
            lineTo(15f, 16f)
            close()
            line(9.6f, 19f, 14.4f, 19f)
            line(10.6f, 21.4f, 13.4f, 21.4f)
        }
    }

    /** A loudspeaker with two sound waves. */
    val Speaker: ImageVector by lazy {
        drawn("Speaker") {
            moveTo(4f, 9.5f)
            lineTo(7.5f, 9.5f)
            lineTo(12f, 5.5f)
            lineTo(12f, 18.5f)
            lineTo(7.5f, 14.5f)
            lineTo(4f, 14.5f)
            close()
            moveTo(15.2f, 9.2f)
            arcTo(4f, 4f, 0f, false, true, 15.2f, 14.8f)
            moveTo(17.8f, 6.6f)
            arcTo(8f, 8f, 0f, false, true, 17.8f, 17.4f)
        }
    }

    // --- profiles ---

    /** A crescent moon (sleep). */
    val Moon: ImageVector by lazy {
        drawn("Moon") {
            // The outer edge is most of a circle of radius 9; the inner edge is a smaller circle
            // of radius 7 that bites the crescent out of it.
            moveTo(20.4f, 13.3f)
            arcTo(8.6f, 8.6f, 0f, true, true, 10.7f, 3.6f)
            arcTo(6.7f, 6.7f, 0f, false, false, 20.4f, 13.3f)
            close()
        }
    }

    /** A laptop (work). */
    val Laptop: ImageVector by lazy {
        drawn("Laptop") {
            roundRect(5f, 5f, 14f, 10f, 1.6f)
            moveTo(2.8f, 18.6f)
            lineTo(21.2f, 18.6f)
        }
    }

    /** A dumbbell (sport). */
    val Dumbbell: ImageVector by lazy {
        drawn("Dumbbell") {
            line(7f, 12f, 17f, 12f)
            roundRect(4f, 8f, 3f, 8f, 1f)
            roundRect(17f, 8f, 3f, 8f, 1f)
            line(2f, 10.5f, 2f, 13.5f)
            line(22f, 10.5f, 22f, 13.5f)
        }
    }

    /** An open book (study). */
    val Book: ImageVector by lazy {
        drawn("Book") {
            moveTo(12f, 6.2f)
            curveTo(10f, 4.9f, 7f, 4.6f, 4f, 5.2f)
            lineTo(4f, 18f)
            curveTo(7f, 17.4f, 10f, 17.8f, 12f, 19.2f)
            curveTo(14f, 17.8f, 17f, 17.4f, 20f, 18f)
            lineTo(20f, 5.2f)
            curveTo(17f, 4.6f, 14f, 4.9f, 12f, 6.2f)
            close()
            line(12f, 6.2f, 12f, 19.2f)
        }
    }

    /** A game controller (games). */
    val Gamepad: ImageVector by lazy {
        drawn("Gamepad") {
            roundRect(3f, 7.5f, 18f, 10f, 5f)
            line(7.4f, 12.5f, 10.6f, 12.5f)
            line(9f, 10.9f, 9f, 14.1f)
            circle(15f, 11.4f, 0.7f)
            circle(17.4f, 13.6f, 0.7f)
        }
    }

    /** A lotus flower (relax). */
    val Lotus: ImageVector by lazy {
        drawn("Lotus") {
            // Centre petal.
            moveTo(12f, 5f)
            curveTo(9.2f, 8.2f, 9.2f, 13.2f, 12f, 17f)
            curveTo(14.8f, 13.2f, 14.8f, 8.2f, 12f, 5f)
            close()
            // Left and right petals.
            moveTo(12f, 17f)
            curveTo(8.4f, 17.4f, 5.4f, 15.4f, 4f, 11.6f)
            curveTo(7.2f, 11.2f, 10.6f, 12.8f, 12f, 17f)
            close()
            moveTo(12f, 17f)
            curveTo(15.6f, 17.4f, 18.6f, 15.4f, 20f, 11.6f)
            curveTo(16.8f, 11.2f, 13.4f, 12.8f, 12f, 17f)
            close()
            line(7f, 20.2f, 17f, 20.2f)
        }
    }

    /** A cooking pot (cooking). */
    val Pot: ImageVector by lazy {
        drawn("Pot") {
            moveTo(5f, 10f)
            lineTo(19f, 10f)
            lineTo(19f, 16.5f)
            arcTo(3.5f, 3.5f, 0f, false, true, 15.5f, 20f)
            lineTo(8.5f, 20f)
            arcTo(3.5f, 3.5f, 0f, false, true, 5f, 16.5f)
            close()
            line(2.6f, 12.2f, 5f, 12.2f)
            line(19f, 12.2f, 21.4f, 12.2f)
            line(9.5f, 7.2f, 14.5f, 7.2f)
            line(12f, 7.2f, 12f, 10f)
        }
    }

    /** A leaf (nature). */
    val Leaf: ImageVector by lazy {
        drawn("Leaf") {
            moveTo(5f, 19f)
            curveTo(5f, 10f, 10f, 5f, 20f, 4.6f)
            curveTo(20f, 14f, 15f, 19f, 5f, 19f)
            close()
            line(5f, 19f, 13f, 11f)
        }
    }

    /** An artist's palette (art). */
    val Palette: ImageVector by lazy {
        drawn("Palette") {
            moveTo(12f, 3f)
            arcTo(9f, 9f, 0f, true, false, 12f, 21f)
            curveTo(13.4f, 21f, 14f, 20f, 13.6f, 18.8f)
            curveTo(13.2f, 17.6f, 14f, 16.5f, 15.3f, 16.5f)
            lineTo(17f, 16.5f)
            arcTo(4f, 4f, 0f, false, false, 21f, 12.5f)
            curveTo(21f, 7f, 17f, 3f, 12f, 3f)
            close()
            circle(7.6f, 11.4f, 0.9f)
            circle(10.2f, 7.8f, 0.9f)
            circle(14.6f, 7.8f, 0.9f)
        }
    }

    /** Headphones (music). */
    val Headphones: ImageVector by lazy {
        drawn("Headphones") {
            moveTo(4f, 15f)
            lineTo(4f, 12f)
            arcToRelative(8f, 8f, 0f, false, true, 16f, 0f)
            lineTo(20f, 15f)
            roundRect(3f, 14f, 4f, 6f, 1.6f)
            roundRect(17f, 14f, 4f, 6f, 1.6f)
        }
    }
}

/** The drawing for a sensor. */
val SensorType.iconVector: ImageVector
    get() = when (this) {
        SensorType.TEMPERATURE -> AppIcons.Thermometer
        SensorType.HUMIDITY -> AppIcons.Droplet
        SensorType.LIGHT -> AppIcons.Bulb
        SensorType.SOUND -> AppIcons.Speaker
    }

/** The drawing for a profile icon. */
val ProfileIcon.vector: ImageVector
    get() = when (this) {
        ProfileIcon.MOON -> AppIcons.Moon
        ProfileIcon.LAPTOP -> AppIcons.Laptop
        ProfileIcon.DUMBBELL -> AppIcons.Dumbbell
        ProfileIcon.BOOK -> AppIcons.Book
        ProfileIcon.GAMEPAD -> AppIcons.Gamepad
        ProfileIcon.LOTUS -> AppIcons.Lotus
        ProfileIcon.POT -> AppIcons.Pot
        ProfileIcon.LEAF -> AppIcons.Leaf
        ProfileIcon.PALETTE -> AppIcons.Palette
        ProfileIcon.HEADPHONES -> AppIcons.Headphones
    }
