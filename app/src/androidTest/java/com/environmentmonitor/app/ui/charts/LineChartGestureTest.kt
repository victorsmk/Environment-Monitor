package com.environmentmonitor.app.ui.charts

import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pinch
import androidx.compose.ui.test.center
import androidx.compose.ui.test.click
import androidx.compose.ui.test.swipe
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.environmentmonitor.app.data.TimedValue
import com.environmentmonitor.app.ui.theme.EnvironmentMonitorTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Real touches on a real chart: a two-finger pinch zooms in, the controls that appear while zoomed
 * work, and charts with too few readings offer no zoom. (Where the marker lands and what the bubble
 * says is covered by the unit tests of the maths behind it.)
 */
@RunWith(AndroidJUnit4::class)
class LineChartGestureTest {

    @get:Rule
    val rule = createComposeRule()

    /** 60 readings, ten seconds apart. */
    private val manyReadings = List(60) { TimedValue(it * 10_000L, 20f + it % 10) }
    private val fewReadings = List(3) { TimedValue(it * 10_000L, 20f + it) }

    private fun showChart(points: List<TimedValue>) {
        rule.setContent {
            EnvironmentMonitorTheme {
                LineChart(
                    points = points,
                    timeStart = 0,
                    timeEnd = points.last().timestamp,
                    axis = linearAxis(ValueRange(18f, 30f), listOf(20f, 25f, 30f)) { it.toInt().toString() },
                    bands = emptyList(),
                    lineColor = Color.Black,
                    modifier = Modifier.testTag("chart"),
                    tooltip = { ChartTooltip(it.value.toString()) },
                )
            }
        }
    }

    private fun pinchOut() = rule.onNodeWithTag("chart").performTouchInput {
        pinch(
            start0 = center + Offset(-40f, 0f),
            end0 = center + Offset(-220f, 0f),
            start1 = center + Offset(40f, 0f),
            end1 = center + Offset(220f, 0f),
        )
    }

    @Test
    fun aChartWithManyReadingsOffersZoom() {
        showChart(manyReadings)
        rule.onNodeWithText("Pinch to zoom").assertIsDisplayed()
        rule.onNodeWithText("Reset zoom").assertDoesNotExist()
    }

    @Test
    fun aChartWithTooFewReadingsOffersNothingToZoom() {
        showChart(fewReadings)
        rule.onNodeWithText("Pinch to zoom").assertDoesNotExist()
        pinchOut()
        rule.onNodeWithText("Reset zoom").assertDoesNotExist()
    }

    @Test
    fun pinchingOutZoomsIn() {
        showChart(manyReadings)
        pinchOut()
        rule.onNodeWithText("Reset zoom").assertIsDisplayed()
        rule.onNodeWithText("Pinch to zoom").assertDoesNotExist()
    }

    @Test
    fun resetZoomGoesBackToTheWholeSession() {
        showChart(manyReadings)
        pinchOut()
        rule.onNodeWithText("Reset zoom").performClick()
        rule.onNodeWithText("Pinch to zoom").assertIsDisplayed()
        rule.onNodeWithText("Reset zoom").assertDoesNotExist()
    }

    @Test
    fun theArrowsAppearWhileZoomedAndKeepTheChartZoomed() {
        showChart(manyReadings)
        pinchOut()
        rule.onNodeWithContentDescription("Earlier").assertIsDisplayed()
        rule.onNodeWithContentDescription("Later").assertIsDisplayed()
        rule.onNodeWithContentDescription("Later").performClick()
        rule.onNodeWithContentDescription("Earlier").performClick()
        // Still zoomed in after stepping around.
        rule.onNodeWithText("Reset zoom").assertIsDisplayed()
    }

    @Test
    fun pinchingInAgainOnAZoomedChartZoomsBackOut() {
        showChart(manyReadings)
        pinchOut()
        rule.onNodeWithText("Reset zoom").assertIsDisplayed()
        // A big pinch in (fingers moving together) returns to the whole session.
        repeat(3) {
            rule.onNodeWithTag("chart").performTouchInput {
                pinch(
                    start0 = center + Offset(-220f, 0f),
                    end0 = center + Offset(-20f, 0f),
                    start1 = center + Offset(220f, 0f),
                    end1 = center + Offset(20f, 0f),
                )
            }
        }
        rule.onNodeWithText("Pinch to zoom").assertIsDisplayed()
    }

    @Test
    fun tappingAndDraggingDoNotCrashOrZoom() {
        showChart(manyReadings)
        rule.onNodeWithTag("chart").performTouchInput { click(center) }
        rule.onNodeWithTag("chart").performTouchInput {
            swipe(start = center + Offset(-100f, 0f), end = center + Offset(100f, 0f), durationMillis = 300)
        }
        rule.onNodeWithText("Pinch to zoom").assertIsDisplayed()
    }
}
