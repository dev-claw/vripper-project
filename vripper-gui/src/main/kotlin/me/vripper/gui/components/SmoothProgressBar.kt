package me.vripper.gui.components

import javafx.animation.Interpolator
import javafx.animation.KeyFrame
import javafx.animation.KeyValue
import javafx.animation.Timeline
import javafx.scene.control.ProgressBar
import javafx.util.Duration

class SmoothProgressBar : ProgressBar() {

    private var timeline: Timeline? = null

    fun animateProgress(target: Double, duration: Double = 250.0) {
        val value = target.coerceIn(0.0, 1.0)
        if (progress == value) return
        timeline?.stop()
        timeline = Timeline(
            KeyFrame(
                Duration.millis(duration),
                KeyValue(progressProperty(), value, Interpolator.EASE_BOTH),
            )
        ).also { it.play() }
    }

    fun stopAnimation() {
        timeline?.stop()
        timeline = null
    }
}
