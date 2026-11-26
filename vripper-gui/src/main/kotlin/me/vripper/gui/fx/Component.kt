package me.vripper.gui.fx

import javafx.beans.property.SimpleBooleanProperty
import javafx.beans.property.StringProperty
import javafx.beans.property.SimpleStringProperty
import javafx.event.EventDispatchChain
import javafx.event.EventTarget
import javafx.scene.Node
import javafx.scene.Parent
import javafx.scene.Scene
import javafx.scene.control.ScrollPane
import javafx.scene.control.SplitPane
import javafx.scene.control.Tab
import javafx.scene.control.ToolBar
import javafx.scene.input.KeyCode
import javafx.scene.input.KeyEvent
import javafx.scene.layout.BorderPane
import javafx.scene.layout.Pane
import javafx.stage.Modality
import javafx.stage.Stage
import javafx.stage.StageStyle
import javafx.stage.Window
import org.koin.core.context.GlobalContext
import org.koin.core.qualifier.named
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

interface ScopedInstance

abstract class Component {
    val params: Map<String, Any?> = FX.inheritParamHolder.get() ?: emptyMap()

    inline fun <reified T : Component> find(
        params: Map<*, Any?>? = null,
        op: T.() -> Unit = {},
    ): T = find(T::class, params).apply(op)

    inline fun <reified T> inject(
        params: Map<String, Any?>? = null,
    ): ReadOnlyProperty<Component, T>
            where T : Component, T : ScopedInstance = object : ReadOnlyProperty<Component, T> {
        private var instance: T? = null

        override fun getValue(thisRef: Component, property: KProperty<*>): T =
            instance ?: find(T::class, params).also { instance = it }
    }

    inline fun <reified T> param(defaultValue: T? = null): ReadOnlyProperty<Component, T> =
        object : ReadOnlyProperty<Component, T> {
            override fun getValue(thisRef: Component, property: KProperty<*>): T {
                val parameter = thisRef.params[property.name] as? T
                if (parameter != null) return parameter
                if (defaultValue != null) return defaultValue
                @Suppress("UNCHECKED_CAST")
                if (property.returnType.isMarkedNullable) return defaultValue as T
                throw IllegalStateException("param for name [${property.name}] has not been set")
            }
        }

    inline fun <reified T : Any> di(name: String? = null): ReadOnlyProperty<Component, T> =
        object : ReadOnlyProperty<Component, T> {
            private var injected: T? = null

            override fun getValue(thisRef: Component, property: KProperty<*>): T {
                if (injected == null) {
                    injected = if (name == null) {
                        GlobalContext.get().get(T::class)
                    } else {
                        GlobalContext.get().get(T::class, named(name))
                    }
                    FxLog.debug {
                        "Injected ${T::class.simpleName}${name?.let { " named $it" }.orEmpty()}"
                    }
                }
                return injected!!
            }
        }

    fun EventTarget.add(uiComponent: UIComponent) = addChildIfPossible(uiComponent.root)

    fun EventTarget.add(child: Node) = addChildIfPossible(child)

    operator fun EventTarget.plusAssign(child: Node) = addChildIfPossible(child)

    operator fun EventTarget.plusAssign(uiComponent: UIComponent) = addChildIfPossible(uiComponent.root)

    val primaryStage: Stage
        get() = FX.primaryStage
            ?: throw IllegalStateException("Primary stage is not available")
}

abstract class Controller : Component(), ScopedInstance

abstract class UIComponent(
    viewTitle: String? = "",
) : Component(), EventTarget {

    override fun buildEventDispatchChain(tail: EventDispatchChain?): EventDispatchChain =
        throw UnsupportedOperationException("UIComponent is not an event source")

    abstract val root: Parent

    open val titleProperty: StringProperty = SimpleStringProperty(viewTitle ?: "")
    var title: String
        get() = titleProperty.get() ?: ""
        set(value) {
            if (titleProperty.isBound) titleProperty.unbind()
            titleProperty.set(value)
        }

    var modalStage: Stage? = null

    val isDockedProperty = SimpleBooleanProperty(false)
    val isDocked: Boolean
        get() = isDockedProperty.value

    val currentWindow: Window?
        get() = modalStage ?: root.scene?.window ?: FX.primaryStage

    val currentStage: Stage?
        get() = currentWindow as? Stage

    open fun onBeforeShow() = Unit

    open fun onDock() = Unit

    open fun onUndock() = Unit

    internal fun callOnDock() {
        if (isDockedProperty.value) return
        isDockedProperty.value = true
        FxLog.debug { "Docked ${javaClass.simpleName}" }
        onDock()
    }

    internal fun callOnUndock() {
        if (!isDockedProperty.value) return
        isDockedProperty.value = false
        FxLog.debug { "Undocked ${javaClass.simpleName}" }
        onUndock()
    }

    @JvmOverloads
    fun openWindow(
        stageStyle: StageStyle = StageStyle.DECORATED,
        modality: Modality = Modality.NONE,
        escapeClosesWindow: Boolean = true,
        owner: Window? = currentWindow,
        block: Boolean = false,
        resizable: Boolean? = null,
    ): Stage? = openModal(stageStyle, modality, escapeClosesWindow, owner, block, resizable)

    @JvmOverloads
    fun openModal(
        stageStyle: StageStyle = StageStyle.DECORATED,
        modality: Modality = Modality.APPLICATION_MODAL,
        escapeClosesWindow: Boolean = true,
        owner: Window? = currentWindow,
        block: Boolean = false,
        resizable: Boolean? = null,
    ): Stage? {
        val stage = modalStage ?: Stage(stageStyle).also { modalStage = it }
        if (stage.isShowing) return stage

        FxLog.debug {
            "Opening ${javaClass.simpleName} modality=${modality.name} owner=${owner?.javaClass?.simpleName}"
        }
        if (resizable != null) stage.isResizable = resizable
        stage.titleProperty().apply {
            if (isBound) unbind()
            bind(titleProperty)
        }
        stage.initModality(modality)
        if (owner != null) stage.initOwner(owner)
        if (escapeClosesWindow) {
            stage.addEventFilter(KeyEvent.KEY_PRESSED) { event ->
                if (event.code == KeyCode.ESCAPE) close()
            }
        }

        stage.scene = root.scene ?: Scene(root)
        FX.primaryStage?.icons?.let { stage.icons.addAll(it) }

        onBeforeShow()
        stage.setOnShown {
            if (owner != null) {
                stage.x = owner.x + (owner.width - stage.width) / 2
                stage.y = owner.y + (owner.height - stage.height) / 2
            }
            callOnDock()
        }
        stage.setOnHidden {
            modalStage = null
            callOnUndock()
        }

        if (block) stage.showAndWait() else stage.show()
        return stage
    }

    fun close() {
        FxLog.debug { "Closing ${javaClass.simpleName}" }
        (modalStage ?: currentWindow as? Stage)?.close()
        modalStage = null
    }

    inline fun <reified T : UIComponent> replaceWith(sizeToScene: Boolean = false, centerOnScreen: Boolean = false) =
        replaceWith(find(T::class), sizeToScene, centerOnScreen)

    fun replaceWith(
        replacement: UIComponent,
        sizeToScene: Boolean = false,
        centerOnScreen: Boolean = false,
    ) {
        FxLog.debug {
            "Replacing ${javaClass.simpleName} with ${replacement.javaClass.simpleName}"
        }
        replacement.onBeforeShow()
        val scene = root.scene
        if (scene != null && scene.root === root) {
            scene.root = replacement.root
            (scene.window as? Stage)?.titleProperty()?.apply {
                if (isBound) unbind()
                bind(replacement.titleProperty)
            }
            if (sizeToScene) scene.window?.sizeToScene()
            if (centerOnScreen) scene.window?.centerOnScreen()
        } else {
            val parent = root.parent
            if (parent is Pane) {
                val index = parent.children.indexOf(root)
                if (index >= 0) {
                    parent.children.removeAt(index)
                    parent.children.add(index, replacement.root)
                }
            }
        }
        replacement.callOnDock()
    }
}

abstract class View @JvmOverloads constructor(
    title: String? = null,
) : UIComponent(title), ScopedInstance

abstract class Fragment @JvmOverloads constructor(
    title: String? = null,
) : UIComponent(title)

inline fun <T : Node> attachTo(parent: EventTarget, node: T, op: T.() -> Unit = {}): T {
    parent.addChildIfPossible(node)
    op(node)
    return node
}

inline fun <T : Node> T.attachTo(parent: EventTarget, op: T.() -> Unit = {}): T =
    attachTo(parent, this, op)

fun EventTarget.addChildIfPossible(node: Node) {
    when (this) {
        is UIComponent -> root?.addChildIfPossible(node)
        is ScrollPane -> content = node
        is Tab -> content = node
        is BorderPane -> Unit
        is SplitPane -> items.add(node)
        is ToolBar -> items.add(node)
        is Field -> inputContainer.addChildIfPossible(node)
        is Fieldset -> addContent(node)
        is Pane -> if (!children.contains(node)) children.add(node)
    }
}

fun EventTarget.add(node: Node) = addChildIfPossible(node)

fun EventTarget.add(uiComponent: UIComponent) = addChildIfPossible(uiComponent.root)

operator fun EventTarget.plusAssign(node: Node) = addChildIfPossible(node)

operator fun EventTarget.plusAssign(uiComponent: UIComponent) = addChildIfPossible(uiComponent.root)
