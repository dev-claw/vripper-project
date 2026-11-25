package me.vripper.gui.fx

import javafx.application.Platform
import javafx.stage.Stage
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
import kotlin.reflect.KClass
import kotlin.reflect.KProperty
import kotlin.jvm.java

object FxLog {
    private val log = LoggerFactory.getLogger("me.vripper.gui.fx")

    fun debug(message: () -> String) {
        if (log.isDebugEnabled) log.debug(message())
    }
}

/**
 * Small runtime shared by the custom JavaFX helpers.
 */
object FX {
    internal val components = ConcurrentHashMap<KClass<*>, ScopedInstance>()
    internal val inheritParamHolder = ThreadLocal<Map<String, Any?>?>()

    @Volatile
    private var registeredPrimaryStage: Stage? = null

    internal fun registerPrimaryStage(stage: Stage) {
        registeredPrimaryStage = stage
        FxLog.debug { "Registered primary stage" }
    }

    val primaryStage: Stage?
        get() = registeredPrimaryStage
}

inline fun <reified T : Component> find(
    params: Map<*, Any?>? = null,
): T = find(T::class, params)

fun <T : Component> find(
    type: KClass<T>,
    params: Map<*, Any?>? = null,
): T {
    val stringKeyedMap = params
        ?.mapKeys { (key, _) -> (key as? KProperty<*>)?.name ?: key.toString() }
        .orEmpty()

    val previousParams = FX.inheritParamHolder.get()
    FX.inheritParamHolder.set(stringKeyedMap)
    try {
        if (ScopedInstance::class.java.isAssignableFrom(type.java)) {
            @Suppress("UNCHECKED_CAST")
            val scopedType = type as KClass<out ScopedInstance>
            val instance = FX.components[scopedType] ?: synchronized(FX) {
                FX.components.getOrPut(scopedType) { create(type) as ScopedInstance }
            }
            @Suppress("UNCHECKED_CAST")
            return instance as T
        }

        return create(type)
    } finally {
        FX.inheritParamHolder.set(previousParams)
    }
}

private fun <T : Component> create(type: KClass<T>): T {
    FxLog.debug { "Creating ${type.simpleName}" }
    val constructor = type.java.getDeclaredConstructor()
    constructor.isAccessible = true
    return constructor.newInstance()
}

fun runLater(op: () -> Unit) {
    if (Platform.isFxApplicationThread()) {
        op()
    } else {
        Platform.runLater(op)
    }
}
