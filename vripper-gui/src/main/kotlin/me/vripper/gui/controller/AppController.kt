package me.vripper.gui.controller

import me.vripper.gui.utils.AppEndpointManager.currentAppEndpointService
import tornadofx.Controller

class AppController : Controller() {
    suspend fun scan(postLinks: String) {
        runCatching { currentAppEndpointService().scanLinks(postLinks) }
    }
}