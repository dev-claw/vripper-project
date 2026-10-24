package me.vripper.gui.utils

import atlantafx.base.theme.CupertinoDark
import atlantafx.base.theme.CupertinoLight
import atlantafx.base.theme.Dracula
import atlantafx.base.theme.NordDark
import atlantafx.base.theme.NordLight
import atlantafx.base.theme.PrimerDark
import atlantafx.base.theme.PrimerLight
import atlantafx.base.theme.Theme
import javafx.application.Application
import javafx.application.Platform
import javafx.collections.ListChangeListener
import javafx.css.PseudoClass
import javafx.scene.Parent
import javafx.scene.Scene
import javafx.scene.paint.Color
import javafx.stage.Window
import me.vripper.gui.model.WidgetsViewModel
import java.nio.charset.StandardCharsets
import java.util.Base64
import java.util.Locale
import kotlin.math.roundToInt

object AppThemeManager {

    data class ThemeOption(val id: String, val label: String, val dark: Boolean)

    val THEMES = listOf(
        ThemeOption("CupertinoLight", "Cupertino Light", false),
        ThemeOption("CupertinoDark", "Cupertino Dark", true),
        ThemeOption("NordLight", "Nord Light", false),
        ThemeOption("NordDark", "Nord Dark", true),
        ThemeOption("PrimerLight", "Primer Light", false),
        ThemeOption("PrimerDark", "Primer Dark", true),
        ThemeOption("Dracula", "Dracula", true),
    )

    const val DEFAULT_FONT_SIZE = 14
    const val MIN_FONT_SIZE = 8
    const val MAX_FONT_SIZE = 28

    private val DARK = PseudoClass.getPseudoClass("dark")
    private val USER_CUSTOM = PseudoClass.getPseudoClass("user-custom")

    private val SCALED_ICON_CLASSES = mapOf(
        "vripper-icon-thumb" to 22.0,
        "vripper-icon-status" to 11.0,
        "vripper-icon-meta" to 10.0,
        "vripper-icon-empty" to 44.0,
        "vripper-icon-nav" to 15.0,
        "vripper-icon-statusbar" to 13.0,
    )

    private var scene: Scene? = null
    private var themeName = "CupertinoLight"
    private var darkMode = false
    private var fontFamily = ""
    private var fontSize = DEFAULT_FONT_SIZE
    private var accentColor = ""

    private var appliedTheme: String? = null
    private var appliedDarkMode: Boolean? = null
    private var windowListenerInstalled = false

    fun prepare(settings: WidgetsViewModel) {
        updateState(settings)
        Application.setUserAgentStylesheet(themeFor(themeName).userAgentStylesheet)
        appliedTheme = themeName
        appliedDarkMode = darkMode
    }

    fun attach(scene: Scene, settings: WidgetsViewModel) {
        this.scene = scene
        appliedTheme = null
        appliedDarkMode = null
        apply(settings)
    }

    fun apply(settings: WidgetsViewModel) {
        updateState(settings)
        installWindowListener()
        applyThemeIfNeeded()

        val roots = linkedSetOf<Parent>()
        scene?.root?.let(roots::add)
        Window.getWindows().forEach { window ->
            window.scene?.root?.let(roots::add)
        }
        roots.forEach(::applyToRoot)
    }

    private fun installWindowListener() {
        if (windowListenerInstalled) return
        windowListenerInstalled = true
        Window.getWindows().addListener(ListChangeListener { change ->
            var added = false
            while (change.next()) {
                if (change.wasAdded()) {
                    added = true
                }
            }
            if (added) {
                Platform.runLater {
                    applyThemeIfNeeded()
                    Window.getWindows().forEach { window ->
                        window.scene?.root?.let(::applyToRoot)
                    }
                }
            }
        })
    }

    private fun applyThemeIfNeeded() {
        if (themeName != appliedTheme || darkMode != appliedDarkMode) {
            Application.setUserAgentStylesheet(themeFor(themeName).userAgentStylesheet)
            appliedTheme = themeName
            appliedDarkMode = darkMode
        }
    }

    private fun applyToRoot(root: Parent) {
        root.pseudoClassStateChanged(DARK, darkMode)
        applyCustomizations(root)
    }

    private fun updateState(settings: WidgetsViewModel) {
        themeName = settings.theme
        darkMode = settings.darkMode
        fontFamily = settings.fontFamily
        fontSize = settings.fontSize.coerceIn(MIN_FONT_SIZE, MAX_FONT_SIZE)
        accentColor = settings.accentColor
    }

    private fun applyCustomizations(root: Parent) {
        val declarations = linkedMapOf<String, String>()
        if (fontFamily.isNotBlank()) {
            declarations["-fx-font-family"] = cssString(fontFamily)
        }
        declarations["-fx-font-size"] = "${fontSize}px"
        accentColor.takeIf { it.isNotBlank() }?.let { value ->
            runCatching { Color.web(value) }.getOrNull()?.let { accent ->
                accentDeclarations(accent).forEach { (name, color) ->
                    declarations[name] = color
                }
            }
        }

        val css = buildString {
            append(".root:user-custom {\n")
            declarations.forEach { (name, value) ->
                append("    ").append(name).append(": ").append(value).append(";\n")
            }
            append("}\n")
            append(".root:user-custom .ikonli-font-icon {\n")
            append("    -fx-icon-size: ").append(fontSize + 2).append("px;\n")
            append("}\n")
            SCALED_ICON_CLASSES.forEach { (styleClass, baseSize) ->
                append(".root:user-custom .ikonli-font-icon.").append(styleClass).append(" {\n")
                append("    -fx-icon-size: ")
                    .append((baseSize * fontSize / DEFAULT_FONT_SIZE).roundToInt())
                    .append("px;\n")
                append("}\n")
            }
        }

        root.stylesheets.removeIf { it.startsWith("data:text/css") }
        root.stylesheets.add(
            "data:text/css;base64," +
                Base64.getEncoder().encodeToString(css.toByteArray(StandardCharsets.UTF_8))
        )
        root.pseudoClassStateChanged(USER_CUSTOM, true)
    }

    private fun accentDeclarations(accent: Color): Map<String, String> {
        val scale = listOf(
            mix(accent, Color.WHITE, 0.90),
            mix(accent, Color.WHITE, 0.80),
            mix(accent, Color.WHITE, 0.70),
            mix(accent, Color.WHITE, 0.55),
            mix(accent, Color.WHITE, 0.35),
            accent,
            mix(accent, Color.BLACK, 0.15),
            mix(accent, Color.BLACK, 0.30),
            mix(accent, Color.BLACK, 0.45),
            mix(accent, Color.BLACK, 0.60),
        )
        val emphasis = if (darkMode) mix(accent, Color.WHITE, 0.15) else accent
        val foreground = if (darkMode) mix(accent, Color.WHITE, 0.30) else accent

        return buildMap {
            scale.forEachIndexed { index, color ->
                put("-color-accent-$index", cssColor(color))
            }
            put("-color-accent-emphasis", cssColor(emphasis))
            put("-color-accent-fg", cssColor(foreground))
            put("-color-accent-muted", cssColor(foreground.deriveColor(0.0, 1.0, 1.0, 0.4)))
            put("-color-accent-subtle", cssColor(foreground.deriveColor(0.0, 1.0, 1.0, 0.15)))
        }
    }

    private fun mix(color: Color, other: Color, ratio: Double): Color {
        val t = ratio.coerceIn(0.0, 1.0)
        return Color.color(
            color.red + (other.red - color.red) * t,
            color.green + (other.green - color.green) * t,
            color.blue + (other.blue - color.blue) * t,
            color.opacity + (other.opacity - color.opacity) * t,
        )
    }

    private fun cssColor(color: Color): String {
        val red = (color.red * 255).toInt().coerceIn(0, 255)
        val green = (color.green * 255).toInt().coerceIn(0, 255)
        val blue = (color.blue * 255).toInt().coerceIn(0, 255)
        val alpha = color.opacity
        return if (alpha >= 1.0) {
            String.format(Locale.ROOT, "#%02x%02x%02x", red, green, blue)
        } else {
            String.format(Locale.ROOT, "rgba(%d, %d, %d, %.3f)", red, green, blue, alpha)
        }
    }

    private fun cssString(value: String): String =
        "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

    private fun themeFor(name: String): Theme = when (name) {
        "CupertinoDark" -> CupertinoDark()
        "NordLight" -> NordLight()
        "NordDark" -> NordDark()
        "PrimerLight" -> PrimerLight()
        "PrimerDark" -> PrimerDark()
        "Dracula" -> Dracula()
        else -> CupertinoLight()
    }
}
