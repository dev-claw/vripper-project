package me.vripper.gui.utils

import com.sun.jna.WString
import me.vripper.gui.VripperGuiApplication

fun openFileDirectory(path: String) {
    val os = System.getProperty("os.name")
    if(os.contains("Windows")) {
        Shell32.INSTANCE.ShellExecuteW(null, WString("open"), WString(path), null, null, 1)
    } else if(os.contains("Linux")) {
        Runtime.getRuntime().exec(arrayOf("xdg-open", path))
    } else if(os.contains("Mac")) {
        Runtime.getRuntime().exec("open -R $path")
    }
}

fun openLink(path: String) {
    VripperGuiApplication.APP_INSTANCE.hostServices.showDocument(path)
}