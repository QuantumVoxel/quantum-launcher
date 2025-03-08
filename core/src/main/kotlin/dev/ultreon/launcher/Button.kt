package dev.ultreon.launcher

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.NinePatch
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.scenes.scene2d.utils.NinePatchDrawable

class Button(
  private val font: BitmapFont,
  var text: String = "...",
  val callback: Button.() -> Unit = {},
  x: Float = 0f,
  y: Float = 0f,
  width: Float = 20f,
  height: Float = 20f
) : Widget(x, y, width, height) {
  private val ninePatch: NinePatchDrawable =
    NinePatchDrawable(NinePatch(Texture(Gdx.files.internal("btn/dark.png")), 7, 7, 4, 6))
  private val ninePatchPressed: NinePatchDrawable =
    NinePatchDrawable(NinePatch(Texture(Gdx.files.internal("btn/dark_pressed.png")), 7, 7, 4, 6))
  private val ninePatchDisabled: NinePatchDrawable =
    NinePatchDrawable(NinePatch(Texture(Gdx.files.internal("btn/dark_disabled.png")), 7, 7, 4, 6))
  private val ninePatchHover: NinePatchDrawable =
    NinePatchDrawable(NinePatch(Texture(Gdx.files.internal("btn/dark_hover.png")), 7, 7, 4, 6))
  private val ninePatchHoverPressed: NinePatchDrawable =
    NinePatchDrawable(NinePatch(Texture(Gdx.files.internal("btn/dark_hover_pressed.png")), 7, 7, 4, 6))
  private val ninePatchSelect: NinePatchDrawable =
    NinePatchDrawable(NinePatch(Texture(Gdx.files.internal("btn/dark_select.png")), 7, 7, 4, 6))
  private val ninePatchHoverSelect: NinePatchDrawable =
    NinePatchDrawable(NinePatch(Texture(Gdx.files.internal("btn/dark_hover_select.png")), 7, 7, 4, 6))
  private val ninePatchPressedSelect: NinePatchDrawable =
    NinePatchDrawable(NinePatch(Texture(Gdx.files.internal("btn/dark_pressed_select.png")), 7, 7, 4, 6))
  private val ninePatchHoverPressedSelect: NinePatchDrawable =
    NinePatchDrawable(NinePatch(Texture(Gdx.files.internal("btn/dark_hover_pressed_select.png")), 7, 7, 4, 6))
  private var wasPressed: Boolean = false

  private val pressed: Boolean
    get() {
      if (!enabled) return false

      if (Gdx.input.isButtonPressed(Input.Buttons.LEFT)) {
        if (Gdx.input.x / 2f > x && (Gdx.graphics.height - Gdx.input.y) / 2f > y && Gdx.input.x / 2f < x + width && (Gdx.graphics.height - Gdx.input.y) / 2f < y + height) {
          wasPressed = true
          return true
        }
      } else {
        if (wasPressed) {
          callback()
        }
        wasPressed = false
      }

      return false
    }
  var enabled: Boolean = true

  var selected: Boolean = false

  override fun render(batch: SpriteBatch, delta: Float) {
    (if (pressed) {
      pressed()
    } else if (enabled) {
      enabled()
    } else {
      ninePatchDisabled
    }).draw(
      batch,
      x,
      y,
      width,
      height
    )

    font.draw(
      batch,
      text,
      x + (width - font.width(text)) / 2f,
      y + 10 + (height - 9) / 2f - font.lineHeight / 2f + 6 - (if (pressed) 2 else 0)
    )
  }

  private fun enabled() =
    if (Gdx.input.x / 2f > x && (Gdx.graphics.height - Gdx.input.y) / 2f > y && Gdx.input.x / 2f < x + width && (Gdx.graphics.height - Gdx.input.y) / 2f < y + height) {
      if (selected) ninePatchHoverSelect else ninePatchHover
    } else if (selected) {
      ninePatchSelect
    } else {
      ninePatch
    }
  private fun pressed() =
    if (Gdx.input.x / 2f > x && (Gdx.graphics.height - Gdx.input.y) / 2f > y && Gdx.input.x / 2f < x + width && (Gdx.graphics.height - Gdx.input.y) / 2f < y + height) {
      if (selected) ninePatchHoverPressedSelect else ninePatchHoverPressed
    } else if (selected) {
      ninePatchPressedSelect
    } else {
      ninePatchPressed
    }
}
