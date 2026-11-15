package com.environmentmonitor.app.ui.icons

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.environmentmonitor.app.data.ProfileIcon
import com.environmentmonitor.app.data.SensorType
import com.environmentmonitor.app.ui.theme.EnvironmentMonitorTheme
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Draws every icon for real and checks none comes out blank or clipped. It also saves a contact
 * sheet of all of them as a picture, so the drawings can be looked at (the file path is printed).
 */
@RunWith(AndroidJUnit4::class)
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
class AppIconsRenderTest {

    @get:Rule
    val rule = createComposeRule()

    @Composable
    private fun IconCell(name: String, tag: String, content: @Composable () -> Unit) {
        Column(
            modifier = Modifier.padding(4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(Modifier.size(72.dp).background(Color.White).testTag(tag), contentAlignment = Alignment.Center) {
                content()
            }
            Text(name, color = Color.Black)
        }
    }

    private fun showAll() {
        rule.setContent {
            EnvironmentMonitorTheme(darkTheme = false) {
                Column(Modifier.background(Color(0xFFF4F6F1)).testTag("sheet").padding(8.dp)) {
                    FlowRow(horizontalArrangement = Arrangement.Start) {
                        SensorType.entries.forEach { sensor ->
                            IconCell(sensor.label, "sensor-${sensor.name}") {
                                Icon(sensor.iconVector, null, tint = Color.Black, modifier = Modifier.size(48.dp))
                            }
                        }
                    }
                    FlowRow(horizontalArrangement = Arrangement.Start) {
                        ProfileIcon.entries.forEach { icon ->
                            IconCell(icon.label, "profile-${icon.name}") {
                                Icon(icon.vector, null, tint = Color.Black, modifier = Modifier.size(48.dp))
                            }
                        }
                    }
                }
            }
        }
    }

    /** The share of pixels that are not white. */
    private fun inkShare(bitmap: Bitmap): Float {
        var ink = 0
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        pixels.forEach { if (AndroidColor.red(it) < 200) ink++ }
        return ink.toFloat() / pixels.size
    }

    @Test
    fun everyIconDrawsSomethingAndStaysInsideItsBox() {
        showAll()
        val tags = SensorType.entries.map { "sensor-${it.name}" } + ProfileIcon.entries.map { "profile-${it.name}" }
        tags.forEach { tag ->
            val bitmap = rule.onNodeWithTag(tag).captureToImage().asAndroidBitmap()
            val share = inkShare(bitmap)
            assertTrue("$tag is blank ($share)", share > 0.01f)
            assertTrue("$tag is a solid block ($share)", share < 0.5f)
            // Nothing may touch the edge of the cell: the drawing must not be clipped.
            val edge = IntArray(bitmap.width)
            for (row in listOf(0, bitmap.height - 1)) {
                bitmap.getPixels(edge, 0, bitmap.width, 0, row, bitmap.width, 1)
                assertTrue("$tag touches the top or bottom edge", edge.all { AndroidColor.red(it) >= 200 })
            }
        }
    }

    @Test
    fun savesAContactSheetOfAllTheIcons() {
        showAll()
        val bitmap = rule.onNodeWithTag("sheet").captureToImage().asAndroidBitmap()
        val dir = InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null)!!
        val file = File(dir, "icons.png")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        println("ICON SHEET: ${file.absolutePath}")
        assertTrue(file.length() > 1_000)
    }
}
