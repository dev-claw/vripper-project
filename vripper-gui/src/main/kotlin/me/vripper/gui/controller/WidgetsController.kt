package me.vripper.gui.controller

import me.vripper.gui.model.WidgetsViewModel
import me.vripper.gui.utils.WidgetSettings
import me.vripper.gui.utils.WidgetSettings.loadSettings
import tornadofx.onChange

class WidgetsController {

    var currentSettings: WidgetsViewModel = loadSettings().let {
        WidgetsViewModel(
            it.localSession,
            it.visibleInfoPanel,
            it.visibleToolbarPanel,
            it.visibleStatusBarPanel,
            it.darkMode,
            it.infoPanelDividerPosition,
            it.width,
            it.height,
            it.cachePath,
            it.postsTableColumns,
            it.imagesTableColumns,
            it.threadsTableColumns,
            it.logsTableColumns,
            it.threadSelectionColumns,
            it.remoteSession,
            it.postsTableColumnsWidth,
            it.imagesTableColumnsWidth,
            it.threadsTableColumnsWidth,
            it.threadSelectionColumnsWidth,
            it.logsTableColumnsWidth
        )
    }

    init {
        currentSettings.localSessionProperty.onChange {
            WidgetSettings.update(currentSettings)
        }
        currentSettings.visibleInfoPanelProperty.onChange {
            WidgetSettings.update(currentSettings)
        }
        currentSettings.visibleToolbarPanelProperty.onChange {
            WidgetSettings.update(currentSettings)
        }
        currentSettings.visibleStatusBarPanelProperty.onChange {
            WidgetSettings.update(currentSettings)
        }
        currentSettings.darkModeProperty.onChange {
            WidgetSettings.update(currentSettings)
        }
        currentSettings.postsColumnsModel.previewProperty.onChange {
            WidgetSettings.update(currentSettings)
        }
        currentSettings.postsColumnsModel.progressProperty.onChange {
            WidgetSettings.update(currentSettings)
        }
        currentSettings.postsColumnsModel.addedOnProperty.onChange {
            WidgetSettings.update(currentSettings)
        }
        currentSettings.postsColumnsModel.hostsProperty.onChange {
            WidgetSettings.update(currentSettings)
        }
        currentSettings.postsColumnsModel.orderProperty.onChange {
            WidgetSettings.update(currentSettings)
        }
        currentSettings.postsColumnsModel.pathProperty.onChange {
            WidgetSettings.update(currentSettings)
        }
        currentSettings.postsColumnsModel.statusProperty.onChange {
            WidgetSettings.update(currentSettings)
        }
        currentSettings.postsColumnsModel.titleProperty.onChange {
            WidgetSettings.update(currentSettings)
        }
        currentSettings.postsColumnsModel.totalProperty.onChange {
            WidgetSettings.update(currentSettings)
        }
        currentSettings.postsColumnsWidthModel.previewProperty.onChange {
            WidgetSettings.update(currentSettings)
        }
        currentSettings.postsColumnsWidthModel.progressProperty.onChange {
            WidgetSettings.update(currentSettings)
        }
        currentSettings.postsColumnsWidthModel.addedOnProperty.onChange {
            WidgetSettings.update(currentSettings)
        }
        currentSettings.postsColumnsWidthModel.hostsProperty.onChange {
            WidgetSettings.update(currentSettings)
        }
        currentSettings.postsColumnsWidthModel.orderProperty.onChange {
            WidgetSettings.update(currentSettings)
        }
        currentSettings.postsColumnsWidthModel.pathProperty.onChange {
            WidgetSettings.update(currentSettings)
        }
        currentSettings.postsColumnsWidthModel.statusProperty.onChange {
            WidgetSettings.update(currentSettings)
        }
        currentSettings.postsColumnsWidthModel.titleProperty.onChange {
            WidgetSettings.update(currentSettings)
        }
        currentSettings.postsColumnsWidthModel.totalProperty.onChange {
            WidgetSettings.update(currentSettings)
        }
        currentSettings.imagesColumnsModel.previewProperty.onChange {
            WidgetSettings.update(currentSettings)
        }
        currentSettings.imagesColumnsModel.indexProperty.onChange {
            WidgetSettings.update(currentSettings)
        }
        currentSettings.imagesColumnsModel.linkProperty.onChange {
            WidgetSettings.update(currentSettings)
        }
        currentSettings.imagesColumnsModel.progressProperty.onChange {
            WidgetSettings.update(currentSettings)
        }
        currentSettings.imagesColumnsModel.filenameProperty.onChange {
            WidgetSettings.update(currentSettings)
        }
        currentSettings.imagesColumnsModel.statusProperty.onChange {
            WidgetSettings.update(currentSettings)
        }
        currentSettings.imagesColumnsModel.sizeProperty.onChange {
            WidgetSettings.update(currentSettings)
        }
        currentSettings.imagesColumnsModel.downloadedProperty.onChange {
            WidgetSettings.update(currentSettings)
        }
        currentSettings.imagesColumnsWidthModel.previewProperty.onChange {
            WidgetSettings.update(currentSettings)
        }
        currentSettings.imagesColumnsWidthModel.indexProperty.onChange {
            WidgetSettings.update(currentSettings)
        }
        currentSettings.imagesColumnsWidthModel.linkProperty.onChange {
            WidgetSettings.update(currentSettings)
        }
        currentSettings.imagesColumnsWidthModel.progressProperty.onChange {
            WidgetSettings.update(currentSettings)
        }
        currentSettings.imagesColumnsWidthModel.filenameProperty.onChange {
            WidgetSettings.update(currentSettings)
        }
        currentSettings.imagesColumnsWidthModel.statusProperty.onChange {
            WidgetSettings.update(currentSettings)
        }
        currentSettings.imagesColumnsWidthModel.sizeProperty.onChange {
            WidgetSettings.update(currentSettings)
        }
        currentSettings.imagesColumnsWidthModel.downloadedProperty.onChange {
            WidgetSettings.update(currentSettings)
        }
        currentSettings.threadsColumnsModel.titleProperty.onChange {
            WidgetSettings.update(currentSettings)
        }
        currentSettings.threadsColumnsModel.linkProperty.onChange {
            WidgetSettings.update(currentSettings)
        }
        currentSettings.threadsColumnsModel.countProperty.onChange {
            WidgetSettings.update(currentSettings)
        }
        currentSettings.threadsColumnsWidthModel.titleProperty.onChange {
            WidgetSettings.update(currentSettings)
        }
        currentSettings.threadsColumnsWidthModel.linkProperty.onChange {
            WidgetSettings.update(currentSettings)
        }
        currentSettings.threadsColumnsWidthModel.countProperty.onChange {
            WidgetSettings.update(currentSettings)
        }
        currentSettings.threadSelectionColumnsModel.previewProperty.onChange {
            WidgetSettings.update(currentSettings)
        }
        currentSettings.threadSelectionColumnsModel.indexProperty.onChange {
            WidgetSettings.update(currentSettings)
        }
        currentSettings.threadSelectionColumnsModel.titleProperty.onChange {
            WidgetSettings.update(currentSettings)
        }
        currentSettings.threadSelectionColumnsModel.linkProperty.onChange {
            WidgetSettings.update(currentSettings)
        }
        currentSettings.threadSelectionColumnsModel.hostsProperty.onChange {
            WidgetSettings.update(currentSettings)
        }
        currentSettings.threadSelectionColumnsWidthModel.previewProperty.onChange {
            WidgetSettings.update(currentSettings)
        }
        currentSettings.threadSelectionColumnsWidthModel.indexProperty.onChange {
            WidgetSettings.update(currentSettings)
        }
        currentSettings.threadSelectionColumnsWidthModel.titleProperty.onChange {
            WidgetSettings.update(currentSettings)
        }
        currentSettings.threadSelectionColumnsWidthModel.linkProperty.onChange {
            WidgetSettings.update(currentSettings)
        }
        currentSettings.threadSelectionColumnsWidthModel.hostsProperty.onChange {
            WidgetSettings.update(currentSettings)
        }
        currentSettings.logsColumnsModel.timeProperty.onChange {
            WidgetSettings.update(currentSettings)
        }
        currentSettings.logsColumnsModel.threadNameProperty.onChange {
            WidgetSettings.update(currentSettings)
        }
        currentSettings.logsColumnsModel.loggerNameProperty.onChange {
            WidgetSettings.update(currentSettings)
        }
        currentSettings.logsColumnsModel.levelStringProperty.onChange {
            WidgetSettings.update(currentSettings)
        }
        currentSettings.logsColumnsModel.messageProperty.onChange {
            WidgetSettings.update(currentSettings)
        }
        currentSettings.logsColumnsWidthModel.timeProperty.onChange {
            WidgetSettings.update(currentSettings)
        }
        currentSettings.logsColumnsWidthModel.threadNameProperty.onChange {
            WidgetSettings.update(currentSettings)
        }
        currentSettings.logsColumnsWidthModel.loggerNameProperty.onChange {
            WidgetSettings.update(currentSettings)
        }
        currentSettings.logsColumnsWidthModel.levelStringProperty.onChange {
            WidgetSettings.update(currentSettings)
        }
        currentSettings.logsColumnsWidthModel.messageProperty.onChange {
            WidgetSettings.update(currentSettings)
        }
        currentSettings.remoteSessionModel.hostProperty.onChange {
            WidgetSettings.update(currentSettings)
        }
        currentSettings.remoteSessionModel.portProperty.onChange {
            WidgetSettings.update(currentSettings)
        }
        currentSettings.remoteSessionModel.passcodeProperty.onChange {
            WidgetSettings.update(currentSettings)
        }
        currentSettings.cachePathProperty.onChange {
            WidgetSettings.update(currentSettings)
        }
    }
}