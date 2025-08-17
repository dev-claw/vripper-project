package me.vripper.gui.components

import javafx.scene.Node
import javafx.scene.Parent
import javafx.scene.Scene
import javafx.stage.Modality
import javafx.stage.Stage
import me.vripper.gui.VripperGuiApplication.Companion.PRIMARY_STAGE


abstract class Fragment(
    private val title: String = "",
    private val width: Double = 0.0,
    private val height: Double = 0.0
) {
    abstract val root: Node

    fun openModal(owner: Stage = PRIMARY_STAGE): Stage {
        val modalStage = Stage()
        modalStage.initModality(Modality.WINDOW_MODAL)
        modalStage.initOwner(owner)
        modalStage.title = title
        modalStage.setScene(Scene(root as Parent, width, height))

        modalStage.x = owner.x + (owner.width / 2) - width / 2
        modalStage.y = owner.y + (owner.height / 2) - height / 2
        modalStage.show()
        return modalStage
    }
}