@file:JvmName("Lwjgl3Launcher")

package dev.ultreon.launcher.quantum.lwjgl3

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Window
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3WindowAdapter
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3WindowListener
import dev.ultreon.launcher.*

/** Launches the desktop (LWJGL3) application. */
fun main() {
  // This handles macOS support and helps on Windows.
  if (StartupHelper.startNewJvmIfRequired())
    return

  NAME = "Quantum Voxel"
  NAME_ID = "QuantumVoxel"
  IDENTIFIER = "quantumvoxel"
  ARTIFACT = "dev.ultreon.quantumvoxel"
  GH_REPO = "QuantumVoxel/game"

  LANG = Lang.JVM
  SDK_VERSION = "17.0.2+8"

  Lwjgl3Application(Launcher, Lwjgl3ApplicationConfiguration().apply {
    setTitle("Quantum Launcher")
    setWindowedMode(1280, 640)
    setWindowIcon(*(arrayOf(128, 64, 32, 16).map { "libgdx$it.png" }.toTypedArray()))
    setResizable(false)

    setWindowListener(object : Lwjgl3WindowAdapter() {
      override fun closeRequested(): Boolean {
        return Launcher.handleClose()
      }
    })
  })
}
