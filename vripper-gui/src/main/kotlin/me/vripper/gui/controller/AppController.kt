package me.vripper.gui.controller

import me.vripper.gui.utils.AppEndpointManager.currentAppEndpointService
import me.vripper.gui.utils.GuiErrorReporter
import me.vripper.services.IPostCommandEndpoint
import me.vripper.gui.fx.Controller

class AppController : Controller() {
    suspend fun scan(postLinks: String) {
        GuiErrorReporter.call("links.scan") {
            currentAppEndpointService().scanLinks(
                postLinks,
                IPostCommandEndpoint.ScanOrigin.DROPPED
            )
        }
    }
}