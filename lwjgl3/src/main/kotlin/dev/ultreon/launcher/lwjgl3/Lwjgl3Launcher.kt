@file:JvmName("Lwjgl3Launcher")

package dev.ultreon.launcher.lwjgl3

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration
import dev.ultreon.launcher.Main

/** Launches the desktop (LWJGL3) application. */
fun main() {
  // This handles macOS support and helps on Windows.
  if (StartupHelper.startNewJvmIfRequired())
    return
  Lwjgl3Application(Main(), Lwjgl3ApplicationConfiguration().apply {
    setTitle("Quantum Launcher")
    setWindowedMode(1280, 640)
    setWindowIcon(*(arrayOf(128, 64, 32, 16).map { "libgdx$it.png" }.toTypedArray()))
    setResizable(false)
  })
}
