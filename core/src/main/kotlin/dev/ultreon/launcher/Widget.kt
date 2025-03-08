package dev.ultreon.launcher

import com.badlogic.gdx.graphics.g2d.SpriteBatch

abstract class Widget(var x: Float = 0f, var y: Float = 0f, var width: Float = 20f, var height: Float = 20f) {

  abstract fun render(batch: SpriteBatch, delta: Float)
  fun set(x: Float? = null, y: Float? = null, width: Float? = null, height: Float? = null) {
    x?.let { this.x = it }
    y?.let { this.y = it }
    width?.let { this.width = it }
    height?.let { this.height = it }
  }
}
