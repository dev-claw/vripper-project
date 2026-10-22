package me.vripper.gui.components.fragments

import atlantafx.base.theme.Styles
import atlantafx.base.util.IntegerStringConverter
import javafx.collections.FXCollections
import javafx.geometry.Pos
import javafx.scene.control.Button
import javafx.scene.control.ColorPicker
import javafx.scene.control.ComboBox
import javafx.scene.control.Spinner
import javafx.scene.layout.HBox
import javafx.scene.layout.Region
import javafx.scene.paint.Color
import javafx.scene.text.Font
import javafx.util.StringConverter
import me.vripper.gui.controller.WidgetsController
import me.vripper.gui.utils.AppThemeManager
import org.kordamp.ikonli.feather.Feather
import org.kordamp.ikonli.javafx.FontIcon
import tornadofx.*
import java.util.Locale
import kotlin.math.roundToInt

class AppearanceSettingsFragment : Fragment("Appearance") {

    private val widgetsController: WidgetsController by inject()
    private val settings get() = widgetsController.currentSettings

    private val themeOptions = AppThemeManager.THEMES

    private val accentPresets = listOf(
        "#007aff" to "Blue",
        "#8250df" to "Purple",
        "#bf3989" to "Pink",
        "#c4432b" to "Coral",
        "#1a7f37" to "Green",
        "#bc4c00" to "Orange",
    )

    override val root = vbox {}

    init {
        with(root) {
            form {
                fieldset("Theme") {
                    field("Color theme") {
                        add(buildThemeSelector())
                    }
                    field("Accent color") {
                        add(buildAccentSelector())
                    }
                }
                fieldset("Typography") {
                    field("Font family") {
                        add(buildFontFamilySelector())
                    }
                    field("Font size") {
                        add(buildFontSizeSelector())
                    }
                }
            }
        }
    }

    private fun buildThemeSelector(): ComboBox<AppThemeManager.ThemeOption> {
        val selector = ComboBox(FXCollections.observableArrayList(themeOptions))
        selector.converter = object : StringConverter<AppThemeManager.ThemeOption>() {
            override fun toString(theme: AppThemeManager.ThemeOption?): String = theme?.label ?: ""
            override fun fromString(label: String?): AppThemeManager.ThemeOption? =
                themeOptions.firstOrNull { it.label == label }
        }
        selector.value = themeOptions.firstOrNull { it.id == settings.theme } ?: themeOptions.first()
        selector.valueProperty().onChange { theme ->
            if (theme != null) {
                settings.theme = theme.id
                settings.darkMode = theme.dark
            }
        }
        return selector
    }

    private fun buildAccentSelector(): Region {
        var syncingPicker = false
        val picker = ColorPicker().apply {
            value = settings.accentColor
                .takeIf { it.isNotBlank() }
                ?.let { runCatching { Color.web(it) }.getOrNull() }
                ?: Color.web("#007aff")
            valueProperty().onChange { color ->
                if (!syncingPicker && color != null) {
                    settings.accentColor = color.toHex()
                }
            }
        }

        val swatches = HBox(6.0).apply {
            alignment = Pos.CENTER_LEFT
            accentPresets.forEach { (value, name) ->
                children.add(Button(null, colorSwatch(value)).apply {
                    tooltip(name)
                    addClass(Styles.FLAT)
                    setOnAction {
                        syncingPicker = true
                        picker.value = Color.web(value)
                        syncingPicker = false
                        settings.accentColor = value
                    }
                })
            }
        }

        val reset = Button(null, FontIcon.of(Feather.REFRESH_CCW)).apply {
            tooltip("Use the theme's default accent color")
            addClass(Styles.FLAT)
            setOnAction {
                syncingPicker = true
                picker.value = Color.web("#007aff")
                syncingPicker = false
                settings.accentColor = ""
            }
        }

        return HBox(8.0, swatches, picker, reset).apply {
            alignment = Pos.CENTER_LEFT
        }
    }

    private fun buildFontFamilySelector(): ComboBox<String> {
        val defaultLabel = "System default"
        val families = FXCollections.observableArrayList(listOf(defaultLabel) + Font.getFamilies())
        val selector = ComboBox(families)
        selector.visibleRowCount = 15
        selector.value = settings.fontFamily.takeIf { it.isNotBlank() && it in families } ?: defaultLabel
        selector.valueProperty().onChange { family ->
            if (family != null) {
                settings.fontFamily = if (family == defaultLabel) "" else family
            }
        }
        return selector
    }

    private fun buildFontSizeSelector(): Spinner<Int> {
        val spinner = Spinner<Int>(
            AppThemeManager.MIN_FONT_SIZE,
            AppThemeManager.MAX_FONT_SIZE,
            settings.fontSize.coerceIn(AppThemeManager.MIN_FONT_SIZE, AppThemeManager.MAX_FONT_SIZE)
        )
        spinner.isEditable = true
        IntegerStringConverter.createFor(spinner)
        spinner.valueProperty().onChange { size ->
            if (size != null) {
                settings.fontSize = size
            }
        }
        return spinner
    }

    private fun colorSwatch(value: String): Region = Region().apply {
        style = "-fx-background-color: $value; -fx-background-radius: 4;"
        prefWidth = 18.0
        prefHeight = 18.0
        minWidth = 18.0
        minHeight = 18.0
    }

    private fun Color.toHex(): String = String.format(
        Locale.ROOT,
        "#%02x%02x%02x",
        (red * 255).roundToInt().coerceIn(0, 255),
        (green * 255).roundToInt().coerceIn(0, 255),
        (blue * 255).roundToInt().coerceIn(0, 255),
    )
}
