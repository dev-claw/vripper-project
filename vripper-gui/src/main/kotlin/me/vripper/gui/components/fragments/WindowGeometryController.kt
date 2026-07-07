package me.vripper.gui.components.fragments

import javafx.animation.PauseTransition
import javafx.beans.value.ChangeListener
import javafx.stage.Screen
import javafx.stage.Stage
import javafx.util.Duration
import me.vripper.gui.model.settings.WorkspaceSettingsModel

/**
 * Persists and restores the workspace window geometry.
 *
 * The window is shown windowed first (even when it was maximized last time) so
 * the window manager records the correct restore bounds, then maximized after a
 * short delay. Normal bounds are tracked while the window is not maximized so
 * closing it while maximized still remembers the size/position the user chose.
 */
internal class WindowGeometryController(
    private val primaryStage: () -> Stage?,
    private val settings: () -> WorkspaceSettingsModel,
    private val persist: () -> Unit
) {

    var stage: Stage? = null
        private set

    private var normalX = 0.0
    private var normalY = 0.0
    private var normalWidth = DEFAULT_WINDOW_WIDTH
    private var normalHeight = DEFAULT_WINDOW_HEIGHT
    private var maximized = false
    private var applying = false

    private val geometryListener = ChangeListener<Number> { _, _, _ ->
        val current = stage
        if (current != null && !applying && !maximized) {
            normalX = current.x
            normalY = current.y
            normalWidth = current.width
            normalHeight = current.height
        }
    }

    private val maximizedListener = ChangeListener<Boolean> { _, _, isMaximized ->
        if (!applying) {
            maximized = isMaximized
        }
    }

    fun attach(newStage: Stage) {
        stage = newStage
        applySavedGeometry(newStage)
        newStage.widthProperty().addListener(geometryListener)
        newStage.heightProperty().addListener(geometryListener)
        newStage.xProperty().addListener(geometryListener)
        newStage.yProperty().addListener(geometryListener)
        newStage.maximizedProperty().addListener(maximizedListener)
    }

    fun detach() {
        save()
        stage?.let { current ->
            current.widthProperty().removeListener(geometryListener)
            current.heightProperty().removeListener(geometryListener)
            current.xProperty().removeListener(geometryListener)
            current.yProperty().removeListener(geometryListener)
            current.maximizedProperty().removeListener(maximizedListener)
        }
        stage = null
    }

    private fun applySavedGeometry(target: Stage) {
        applying = true
        try {
            target.minWidth = MIN_WINDOW_SIZE
            target.minHeight = MIN_WINDOW_SIZE

            val saved = settings()
            target.width = saneWindowSize(saved.windowWidth, DEFAULT_WINDOW_WIDTH)
            target.height = saneWindowSize(saved.windowHeight, DEFAULT_WINDOW_HEIGHT)

            val x = saved.windowX
            val y = saved.windowY
            // Only restore the position if it is still on a connected screen
            // (the monitor layout may have changed since it was saved).
            if (x.isFinite() && y.isFinite() && Screen.getScreens().any {
                    it.visualBounds.intersects(x, y, MIN_WINDOW_SIZE, MIN_WINDOW_SIZE)
                }) {
                target.x = x
                target.y = y
            } else {
                centerOnMainWindow(target)
            }

            normalX = target.x
            normalY = target.y
            normalWidth = target.width
            normalHeight = target.height
            maximized = saved.windowMaximized
        } finally {
            applying = false
        }

        if (maximized) {
            // Show the window windowed for a moment first so the window manager
            // registers the normal (restore) geometry before we maximize it.
            PauseTransition(Duration.millis(INITIAL_MAXIMIZE_DELAY_MS)).apply {
                setOnFinished {
                    if (stage === target) {
                        target.isMaximized = true
                    }
                }
                play()
            }
        }
    }

    /**
     * The window is opened without an owner so it can be minimized
     * independently (JavaFX does not allow iconifying owned stages), which also
     * means TornadoFX no longer centers it over the main window. Center it
     * ourselves when no saved position is available.
     */
    private fun centerOnMainWindow(target: Stage) {
        val mainStage = primaryStage()
        val screen = mainStage?.let { main ->
            val centerX = main.x + main.width / 2
            val centerY = main.y + main.height / 2
            Screen.getScreens().firstOrNull { it.bounds.contains(centerX, centerY) }
        } ?: Screen.getPrimary()
        val bounds = screen.visualBounds
        target.x = bounds.minX + (bounds.width - target.width) / 2
        target.y = bounds.minY + (bounds.height - target.height) / 2
    }

    private fun save() {
        val current = stage ?: return
        val saved = settings()
        val isMaximized = maximized

        // While maximized, keep the last normal bounds so un-maximizing the
        // window next time restores the size the user actually chose.
        val x = if (isMaximized) normalX else current.x
        val y = if (isMaximized) normalY else current.y
        val width = if (isMaximized) normalWidth else current.width
        val height = if (isMaximized) normalHeight else current.height

        var changed = false
        if (saved.windowX != x) {
            saved.windowX = x
            changed = true
        }
        if (saved.windowY != y) {
            saved.windowY = y
            changed = true
        }
        if (saved.windowWidth != width) {
            saved.windowWidth = width
            changed = true
        }
        if (saved.windowHeight != height) {
            saved.windowHeight = height
            changed = true
        }
        if (saved.windowMaximized != isMaximized) {
            saved.windowMaximized = isMaximized
            changed = true
        }
        if (changed) {
            persist()
        }
    }

    private fun saneWindowSize(value: Double, fallback: Double): Double =
        if (value.isFinite() && value >= MIN_WINDOW_SIZE) value else fallback

    private companion object {
        private const val DEFAULT_WINDOW_WIDTH = 800.0
        private const val DEFAULT_WINDOW_HEIGHT = 600.0
        private const val MIN_WINDOW_SIZE = 100.0

        // How long the window stays windowed at startup before a maximized
        // state is re-applied, so the window manager can record the restore
        // geometry.
        private const val INITIAL_MAXIMIZE_DELAY_MS = 10.0
    }
}
