package me.vripper.gui.model

import javafx.beans.property.SimpleBooleanProperty
import javafx.beans.property.SimpleDoubleProperty
import javafx.beans.property.SimpleIntegerProperty
import javafx.beans.property.SimpleStringProperty
import me.vripper.gui.model.settings.ImageViewerSettingsModel
import me.vripper.gui.model.settings.WorkspaceSettingsModel
import me.vripper.gui.utils.WidgetSettings.ImageViewerSettings
import me.vripper.gui.utils.WidgetSettings.ImagesTableColumns
import me.vripper.gui.utils.WidgetSettings.ImagesTableColumnsWidth
import me.vripper.gui.utils.WidgetSettings.LogsTableColumns
import me.vripper.gui.utils.WidgetSettings.LogsTableColumnsWidth
import me.vripper.gui.utils.WidgetSettings.PostsTableColumns
import me.vripper.gui.utils.WidgetSettings.PostsTableColumnsWidth
import me.vripper.gui.utils.WidgetSettings.RemoteSession
import me.vripper.gui.utils.WidgetSettings.ThreadSelectionTableColumns
import me.vripper.gui.utils.WidgetSettings.ThreadSelectionTableColumnsWidth
import me.vripper.gui.utils.WidgetSettings.ThreadsTableColumns
import me.vripper.gui.utils.WidgetSettings.ThreadsTableColumnsWidth
import me.vripper.gui.utils.WidgetSettings.WorkspaceSettings
import tornadofx.getValue
import tornadofx.setValue

class WidgetsViewModel(
    localSession: Boolean,
    visibleInfoPanel: Boolean,
    visibleToolbarPanel: Boolean,
    visibleStatusBarPanel: Boolean,
    darkMode: Boolean,
    theme: String,
    accentColor: String,
    fontFamily: String,
    fontSize: Int,
    infoPanelDividerPosition: Double,
    width: Double,
    height: Double,
    cachePath: String,
    postsTableColumns: PostsTableColumns,
    imagesTableColumns: ImagesTableColumns,
    threadsTableColumns: ThreadsTableColumns,
    logsTableColumns: LogsTableColumns,
    threadSelectionColumns: ThreadSelectionTableColumns,
    remoteSession: RemoteSession,
    postsTableColumnsWidth: PostsTableColumnsWidth,
    imagesTableColumnsWidth: ImagesTableColumnsWidth,
    threadsTableColumnsWidth: ThreadsTableColumnsWidth,
    threadSelectionColumnsWidth: ThreadSelectionTableColumnsWidth,
    logsTableColumnsWidth: LogsTableColumnsWidth,
    imageViewerSettings: ImageViewerSettings,
    workspaceSettings: WorkspaceSettings,
    postsViewMode: String,
    postsGridTileSize: Double,
    postsPreviewPopup: Boolean,
) {

    val localSessionProperty = SimpleBooleanProperty(localSession)
    var localSession: Boolean by localSessionProperty

    val visibleInfoPanelProperty = SimpleBooleanProperty(visibleInfoPanel)
    var visibleInfoPanel: Boolean by visibleInfoPanelProperty

    val postsViewModeProperty = SimpleStringProperty(postsViewMode)
    var postsViewMode: String by postsViewModeProperty

    val postsGridTileSizeProperty = SimpleDoubleProperty(postsGridTileSize)
    var postsGridTileSize: Double by postsGridTileSizeProperty

    val postsPreviewPopupProperty = SimpleBooleanProperty(postsPreviewPopup)
    var postsPreviewPopup: Boolean by postsPreviewPopupProperty

    val visibleToolbarPanelProperty = SimpleBooleanProperty(visibleToolbarPanel)
    var visibleToolbarPanel: Boolean by visibleToolbarPanelProperty

    val visibleStatusBarPanelProperty = SimpleBooleanProperty(visibleStatusBarPanel)
    var visibleStatusBarPanel: Boolean by visibleStatusBarPanelProperty

    val infoPanelDividerPositionProperty = SimpleDoubleProperty(infoPanelDividerPosition)
    var infoPanelDividerPosition: Double by infoPanelDividerPositionProperty

    val darkModeProperty = SimpleBooleanProperty(darkMode)
    var darkMode: Boolean by darkModeProperty

    val themeProperty = SimpleStringProperty(theme)
    var theme: String by themeProperty

    val accentColorProperty = SimpleStringProperty(accentColor)
    var accentColor: String by accentColorProperty

    val fontFamilyProperty = SimpleStringProperty(fontFamily)
    var fontFamily: String by fontFamilyProperty

    val fontSizeProperty = SimpleIntegerProperty(fontSize)
    var fontSize: Int by fontSizeProperty

    val widthProperty = SimpleDoubleProperty(width)
    var width: Double by widthProperty

    val heightProperty = SimpleDoubleProperty(height)
    var height: Double by heightProperty

    val cachePathProperty = SimpleStringProperty(cachePath)
    var cachePath: String by cachePathProperty

    val postsColumnsModel = PostsColumnsModel(
        postsTableColumns.preview,
        postsTableColumns.title,
        postsTableColumns.progress,
        postsTableColumns.status,
        postsTableColumns.path,
        postsTableColumns.total,
        postsTableColumns.hosts,
        postsTableColumns.addedOn,
        postsTableColumns.order,
    )

    val postsColumnsWidthModel = PostsColumnsWidthModel(
        postsTableColumnsWidth.preview,
        postsTableColumnsWidth.title,
        postsTableColumnsWidth.progress,
        postsTableColumnsWidth.status,
        postsTableColumnsWidth.path,
        postsTableColumnsWidth.total,
        postsTableColumnsWidth.hosts,
        postsTableColumnsWidth.addedOn,
        postsTableColumnsWidth.order,
    )

    val imagesColumnsModel = ImagesColumnsModel(
        imagesTableColumns.preview,
        imagesTableColumns.index,
        imagesTableColumns.link,
        imagesTableColumns.progress,
        imagesTableColumns.filename,
        imagesTableColumns.status,
        imagesTableColumns.size,
        imagesTableColumns.downloaded,
    )

    val imagesColumnsWidthModel = ImagesColumnsWidthModel(
        imagesTableColumnsWidth.preview,
        imagesTableColumnsWidth.index,
        imagesTableColumnsWidth.link,
        imagesTableColumnsWidth.progress,
        imagesTableColumnsWidth.filename,
        imagesTableColumnsWidth.status,
        imagesTableColumnsWidth.size,
        imagesTableColumnsWidth.downloaded,
    )

    val threadsColumnsModel = ThreadsColumnsModel(
        threadsTableColumns.title,
        threadsTableColumns.link,
        threadsTableColumns.count,
    )

    val threadsColumnsWidthModel = ThreadsColumnsWidthModel(
        threadsTableColumnsWidth.title,
        threadsTableColumnsWidth.link,
        threadsTableColumnsWidth.count,
    )

    val logsColumnsModel = LogsColumnsModel(
        logsTableColumns.time,
        logsTableColumns.threadName,
        logsTableColumns.loggerName,
        logsTableColumns.levelString,
        logsTableColumns.message,
    )

    val logsColumnsWidthModel = LogsColumnsWidthModel(
        logsTableColumnsWidth.time,
        logsTableColumnsWidth.threadName,
        logsTableColumnsWidth.loggerName,
        logsTableColumnsWidth.levelString,
        logsTableColumnsWidth.message,
    )

    val threadSelectionColumnsModel = ThreadSelectionColumnsModel(
        threadSelectionColumns.preview,
        threadSelectionColumns.index,
        threadSelectionColumns.title,
        threadSelectionColumns.link,
        threadSelectionColumns.hosts,
    )

    val threadSelectionColumnsWidthModel = ThreadSelectionColumnsWidthModel(
        threadSelectionColumnsWidth.preview,
        threadSelectionColumnsWidth.index,
        threadSelectionColumnsWidth.title,
        threadSelectionColumnsWidth.link,
        threadSelectionColumnsWidth.hosts,
    )

    val remoteSessionModel = RemoteSessionModel(
        remoteSession.host,
        remoteSession.port,
        remoteSession.passPhrase,
    )

    val imageViewerSettingsModel = ImageViewerSettingsModel().apply {
        decodedCacheMb = imageViewerSettings.decodedCacheMb
        bytePreloadRadius = imageViewerSettings.bytePreloadRadius
        bytePreloadConcurrency = imageViewerSettings.bytePreloadConcurrency
        decodePreloadConcurrency = imageViewerSettings.decodePreloadConcurrency
        diskCacheMb = imageViewerSettings.diskCacheMb
        fadeAnimation = imageViewerSettings.fadeAnimation
        windowWidth = imageViewerSettings.windowWidth
        windowHeight = imageViewerSettings.windowHeight
        windowX = imageViewerSettings.windowX
        windowY = imageViewerSettings.windowY
        windowMaximized = imageViewerSettings.windowMaximized
    }

    val workspaceSettingsModel = WorkspaceSettingsModel().apply {
        browseLayout = workspaceSettings.browseLayout
        detailsPanelVisible = workspaceSettings.detailsPanelVisible
        detailsPanelDividerPosition = workspaceSettings.detailsPanelDividerPosition
        overviewGridTileSize = workspaceSettings.overviewGridTileSize
        windowWidth = workspaceSettings.windowWidth
        windowHeight = workspaceSettings.windowHeight
        windowX = workspaceSettings.windowX
        windowY = workspaceSettings.windowY
        windowMaximized = workspaceSettings.windowMaximized
    }

    val workspaceImagesColumnsModel = ImagesColumnsModel(
        workspaceSettings.imagesTableColumns.preview,
        workspaceSettings.imagesTableColumns.index,
        workspaceSettings.imagesTableColumns.link,
        workspaceSettings.imagesTableColumns.progress,
        workspaceSettings.imagesTableColumns.filename,
        workspaceSettings.imagesTableColumns.status,
        workspaceSettings.imagesTableColumns.size,
        workspaceSettings.imagesTableColumns.downloaded,
    )

    val workspaceImagesColumnsWidthModel = ImagesColumnsWidthModel(
        workspaceSettings.imagesTableColumnsWidth.preview,
        workspaceSettings.imagesTableColumnsWidth.index,
        workspaceSettings.imagesTableColumnsWidth.link,
        workspaceSettings.imagesTableColumnsWidth.progress,
        workspaceSettings.imagesTableColumnsWidth.filename,
        workspaceSettings.imagesTableColumnsWidth.status,
        workspaceSettings.imagesTableColumnsWidth.size,
        workspaceSettings.imagesTableColumnsWidth.downloaded,
    )
}