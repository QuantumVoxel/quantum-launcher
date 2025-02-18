package dev.ultreon.launcher

import com.badlogic.gdx.ApplicationAdapter
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.InputAdapter
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.GlyphLayout
import com.badlogic.gdx.graphics.g2d.NinePatch
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.scenes.scene2d.utils.NinePatchDrawable
import com.badlogic.gdx.utils.JsonReader
import com.badlogic.gdx.utils.JsonValue
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import java.io.File
import java.io.RandomAccessFile
import java.net.URL
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.nio.file.attribute.PosixFilePermissions
import java.util.zip.GZIPInputStream
import java.util.zip.ZipInputStream
import kotlin.concurrent.thread
import kotlin.io.path.absolute
import kotlin.io.path.notExists
import kotlin.io.path.readText
import kotlin.streams.asSequence


abstract class Widget(var x: Float = 0f, var y: Float = 0f, var width: Float = 20f, var height: Float = 20f) {
  abstract fun render(batch: SpriteBatch, delta: Float)

  fun set(x: Float? = null, y: Float? = null, width: Float? = null, height: Float? = null) {
    x?.let { this.x = it }
    y?.let { this.y = it }
    width?.let { this.width = it }
    height?.let { this.height = it }
  }
}

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

var layout: GlyphLayout = GlyphLayout()

private fun BitmapFont.width(text: String): Float {
  layout.setText(this, text)
  return layout.width
}

var runningProcess: Process? = null

private fun launchGame(version: GameVersion, button: Button): Process {
  return if (version.id in arrayOf("0.0.0-indev", "0.0.1-indev") || version is ChannelVersion) {
    if (System.getProperty("os.name").startsWith("Windows")) {
      ProcessBuilder("cmd", "/c", "gradlew.bat --no-daemon lwjgl3:run").run {
        environment()["PATH"] = "$JAVA_HOME\\bin:${System.getenv("PATH")}"
        environment()["JAVA_HOME"] = JAVA_HOME
        directory(File("versions/${version.id}/"))
      }.inheritIO().start()
    } else if (System.getProperty("os.name").startsWith("Linux")) {
      ProcessBuilder("bash", "-c", "chmod +x gradlew && ./gradlew --no-daemon lwjgl3:run").run {
        environment()["PATH"] = "$JAVA_HOME/bin:${System.getenv("PATH")}"
        environment()["JAVA_HOME"] = JAVA_HOME
        directory(File("versions/${version.id}/"))
      }.inheritIO().start()
    } else if (System.getProperty("os.name").startsWith("Mac")) {
      ProcessBuilder("bash", "-c", "chmod +x gradlew && ./gradlew --no-daemon lwjgl3:run").run {
        environment()["PATH"] = "$JAVA_HOME/bin:${System.getenv("PATH")}"
        environment()["JAVA_HOME"] = JAVA_HOME
        directory(File("versions/${version.id}/"))
      }.inheritIO().start()
    } else {
      throw UnsupportedOperationException()
    }
  } else {
    if (System.getProperty("os.name").startsWith("Windows")) {
      ProcessBuilder(
        "$JAVA_HOME\\bin\\$JAVA_EXEC_NAME",
        "-cp",
        "lib/*",
        "dev.ultreon.quantum.lwjgl3.Lwjgl3Launcher"
      ).run {
        environment()["PATH"] = "${File(JAVA_HOME).absolutePath}\\bin:${System.getenv("PATH")}"
        environment()["JAVA_HOME"] = File(JAVA_HOME).absolutePath
        directory(File("versions/${version.id}/"))
      }.inheritIO().start()
    } else if (System.getProperty("os.name").startsWith("Linux")) {
      ProcessBuilder(
        "$JAVA_HOME/bin/$JAVA_EXEC_NAME",
        "-cp",
        "lib/*",
        "dev.ultreon.quantum.lwjgl3.Lwjgl3Launcher"
      ).run {
        environment()["PATH"] = "${File(JAVA_HOME).absolutePath}/bin:${System.getenv("PATH")}"
        environment()["JAVA_HOME"] = File(JAVA_HOME).absolutePath
        directory(File("versions/${version.id}/"))
      }.inheritIO().start()
    } else if (System.getProperty("os.name").startsWith("Mac")) {
      ProcessBuilder(
        "$JAVA_HOME/bin/$JAVA_EXEC_NAME",
        "-XstartOnFirstThread",
        "-cp",
        "lib/*",
        "dev.ultreon.quantum.lwjgl3.Lwjgl3Launcher"
      ).run {
        environment()["PATH"] = "${File(JAVA_HOME).absolutePath}/bin:${System.getenv("PATH")}"
        environment()["JAVA_HOME"] = File(JAVA_HOME).absolutePath
        directory(File("versions/${version.id}/"))
      }.inheritIO().start()
    } else {
      throw UnsupportedOperationException()
    }
  }.also {
    button.text = "Click to Stop"
    button.enabled = true
    runningProcess = it
  }
}

fun download(
  url: String,
  name: String,
  expectedBytes: Long = 0,
  onProgress: ((Float) -> Unit)? = null,
  onComplete: ((Path) -> Unit)? = null
) {
  var totalBytes: Long = expectedBytes
  var downloadedBytes: Long = 0
  thread(isDaemon = false) {
    val connection = URL(url).openConnection()
    totalBytes = if (connection.contentLengthLong == -1L) totalBytes else connection.contentLengthLong

    if (!Files.exists(Paths.get("temp").absolute())) {
      Files.createDirectories(Paths.get("temp").absolute())
    }

    val file = RandomAccessFile(File("temp/$name").absolutePath, "rw")

    connection.inputStream.use { inputStream ->
      val buffer = ByteArray(1024)
      var read: Int
      while (inputStream.read(buffer).also { read = it } != -1) {
        file.write(buffer, 0, read)
        downloadedBytes += read
        onProgress?.invoke(if (totalBytes == 0L) 0f else downloadedBytes.toFloat() / totalBytes)
      }
    }

    file.close()

    // Wait for the file to be found (some random issue on macOS cause the file not to be found immediately)
    while (Paths.get("temp/$name").absolute().notExists()) {
      Thread.sleep(100)
    }

    onComplete?.invoke(Paths.get("temp/$name").absolute().also {
      println("Downloaded $url -> $it")
    })
  }
}

private fun downloadGame(selectedVersion: GameVersion, button: Button, callback: () -> Unit) {

}

open class GameVersion(val id: String, val name: String, val gameUrl: String) {
  fun isDownloaded(): Boolean {
    return Files.exists(Paths.get("versions/$id"))
  }
}

class GameChannel(val name: String, val gameUrl: String)

open class ChannelVersion(gameChannel: GameChannel) :
  GameVersion(gameChannel.name, "Channel ${gameChannel.name}", gameChannel.gameUrl)

const val VERSION_API = "https://api.github.com/repos/QuantumVoxel/game/releases"
const val JDK_VERSION = "17.0.2_8"
val JDK_URL = if (System.getProperty("os.name").startsWith("Windows")) {
  "https://github.com/adoptium/temurin17-binaries/releases/download/jdk-${
    JDK_VERSION.replace(
      "_",
      "%2B"
    )
  }/OpenJDK17U-jdk_x64_windows_hotspot_$JDK_VERSION.zip"
} else if (System.getProperty("os.name").startsWith("Linux") && System.getProperty("os.arch") == "aarch64") {
  "https://github.com/adoptium/temurin17-binaries/releases/download/jdk-${
    JDK_VERSION.replace(
      "_",
      "%2B"
    )
  }/OpenJDK17U-jdk_aarch64_linux_hotspot_$JDK_VERSION.tar.gz"
} else if (System.getProperty("os.name").startsWith("Linux")) {
  "https://github.com/adoptium/temurin17-binaries/releases/download/jdk-${
    JDK_VERSION.replace(
      "_",
      "%2B"
    )
  }/OpenJDK17U-jdk_x64_linux_hotspot_$JDK_VERSION.tar.gz"
} else if (System.getProperty("os.name").startsWith("Mac") && System.getProperty("os.arch") == "aarch64") {
  "https://github.com/adoptium/temurin17-binaries/releases/download/jdk-${
    JDK_VERSION.replace(
      "_",
      "%2B"
    )
  }/OpenJDK17U-jdk_aarch64_mac_hotspot_$JDK_VERSION.tar.gz"
} else if (System.getProperty("os.name").startsWith("Mac")) {
  "https://github.com/adoptium/temurin17-binaries/releases/download/jdk-${
    JDK_VERSION.replace(
      "_",
      "%2B"
    )
  }/OpenJDK17U-jdk_x64_mac_hotspot_$JDK_VERSION.tar.gz"
} else {
  throw UnsupportedOperationException()
}

val JAVA_HOME = File(
  if (System.getProperty("os.name").startsWith("Windows")) {
    "jdk/jdk-${JDK_VERSION.replace("_", "+")}"
  } else if (System.getProperty("os.name").startsWith("Linux")) {
    "jdk/jdk-${JDK_VERSION.replace("_", "+")}"
  } else if (System.getProperty("os.name").startsWith("Mac")) {
    "jdk/jdk-${JDK_VERSION.replace("_", "+")}/Contents/Home"
  } else {
    throw UnsupportedOperationException()
  }
).absolutePath

val JAVA_EXEC_NAME = if (System.getProperty("os.name").startsWith("Windows")) {
  "javaw.exe"
} else if (System.getProperty("os.name").startsWith("Linux")) {
  "java"
} else if (System.getProperty("os.name").startsWith("Mac")) {
  "java"
} else {
  throw UnsupportedOperationException()
}

var cachedReleases: JsonValue? = if (Files.exists(Paths.get("releases.json"))) {
  JsonReader().parse(Paths.get("releases.json").readText())
} else {
  null
}

fun versionsFromGitHub(): List<GameVersion> {
  val list = mutableListOf<GameVersion>()
  val cache = cachedReleases
  if (cache != null && cache["cache_time"].asLong() + 600 * 1000 > System.currentTimeMillis()) {
    for (i in 0 until cache["releases"].size()) {
      if (cache["releases"][i]["tag_name"].asString() in arrayOf("0.0.0-indev", "0.0.1-indev")) {
        list.add(
          GameVersion(
            cache["releases"][i]["tag_name"].asString(),
            cache["releases"][i]["name"].asString(),
            cache["releases"][i]["zipball_url"].asString()
          )
        )
      } else {
        list.add(
          GameVersion(
            cache["releases"][i]["tag_name"].asString(),
            cache["releases"][i]["name"].asString(),
            "https://github.com/QuantumVoxel/game/releases/download/${cache["releases"][i]["tag_name"].asString()}/launcher-package.zip"
          )
        )
      }
    }
    return list
  }

  println("Fetching versions from GitHub...")

  try {

    val readText = URL(VERSION_API).readText()
    val json = JsonReader().parse(readText)

    for (i in 0 until json.size()) {
      if (json[i]["tag_name"].asString() in arrayOf("0.0.0-indev", "0.0.1-indev")) {
        list.add(
          GameVersion(
            json[i]["tag_name"].asString(),
            json[i]["name"].asString(),
            json[i]["zipball_url"].asString()
          )
        )
      } else {
        list.add(
          GameVersion(
            json[i]["tag_name"].asString(),
            json[i]["name"].asString(),
            "https://github.com/QuantumVoxel/game/releases/download/${json[i]["tag_name"].asString()}/launcher-package.zip"
          )
        )
      }
    }

    cachedReleases = JsonValue(JsonValue.ValueType.`object`).apply {
      addChild("releases", json)
      addChild("cache_time", JsonValue(System.currentTimeMillis().toString()))


      Files.writeString(Paths.get("releases.json"), this.toString(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)
    }
  } catch (e: Exception) {
    println("Failed to fetch versions from GitHub: ${e.message}")

    if (cache != null) {
      for (i in 0 until cache["releases"].size()) {
        if (cache["releases"][i]["tag_name"].asString() in arrayOf("0.0.0-indev", "0.0.1-indev")) {
          list.add(
            GameVersion(
              cache["releases"][i]["tag_name"].asString(),
              cache["releases"][i]["name"].asString(),
              cache["releases"][i]["zipball_url"].asString()
            )
          )
        } else {
          list.add(
            GameVersion(
              cache["releases"][i]["tag_name"].asString(),
              cache["releases"][i]["name"].asString(),
              "https://github.com/QuantumVoxel/game/releases/download/${cache["releases"][i]["tag_name"].asString()}/launcher-package.zip"
            )
          )
        }
      }
    }

    return list
  }

  return list
}

fun unpackGame(version: GameVersion): Int {
  val unpacked = unpackZip("temp/${version.id}", "temp/${version.id}-extract")
  if (unpacked != 0) {
    return 1
  }

  if (version.id in arrayOf("0.0.0-indev", "0.0.1-indev") || version is ChannelVersion) {
    if (move(Files.list(Paths.get("temp/${version.id}-extract")).findFirst().orElseThrow().toString(), "versions/${version.id}") != 0) {
      return 1
    }
  } else {
    if (move("temp/${version.id}-extract", "versions/${version.id}") != 0) {
      return 1
    }
  }

  return 0
}

fun unpack(path: String, dest: String): Int {
  if (!Gdx.files.local(path).exists()) {
    println("Failed to find $path")
    return -1
  }

  if (!Gdx.files.local(dest).exists()) {
    Gdx.files.local(dest).mkdirs()
    println("Created $dest")
  } else {
    println("Found $dest")
  }
  return if (System.getProperty("os.name").startsWith("Windows")) {
    unpackZip(path, dest)
  } else if (System.getProperty("os.name").startsWith("Linux")) {
    unpackTarGZ(path, dest)
  } else if (System.getProperty("os.name").startsWith("Mac")) {
    unpackTarGZ(path, dest)
  } else {
    throw UnsupportedOperationException()
  }
}

fun move(path: String, dest: String): Int {
  if (!Gdx.files.local(path).exists()) {
    println("Failed to find $path")
    return 1
  }

  if (Gdx.files.local(dest).exists()) {
    println("Failed to move $path to $dest, $dest already exists")
    return 1
  }

  try {
    Files.move(Paths.get(path), Paths.get(dest))
  } catch (e: Exception) {
    e.printStackTrace()
    return 1
  }

  return 0
}

fun rename(path: String, newName: String): Int {
  if (!Gdx.files.local(path).exists()) {
    println("Failed to find $path")
    return 1
  }

  try {
    Files.move(Paths.get(path), Paths.get(newName), StandardCopyOption.ATOMIC_MOVE)
  } catch (e: Exception) {
    e.printStackTrace()
    return 1
  }

  return 0
}

fun unpackZip(path: String, dest: String, subFolder: String = ""): Int {
  if (!Gdx.files.local(path).exists()) {
    println("Failed to find $path")
    return -1
  }

  if (!Gdx.files.local(dest).exists()) {
    Gdx.files.local(dest).mkdirs()
  }

  println("Extracting (zip) $path!/ -> $dest")

  ZipInputStream(Gdx.files.local(path).read()).use { zipStream ->
    try {
      var entry = zipStream.nextEntry
      while (entry != null) {
        if (!entry.name.startsWith(subFolder)) {
          entry = zipStream.nextEntry
          continue
        }

        val file = Gdx.files.local("$dest/${entry.name.substringAfter(subFolder)}")
        println("Extracting $path!/${entry.name} -> ${file.path()}")
        if (entry.isDirectory) {
          file.mkdirs()
        } else {
          if (!file.parent().exists())
            file.parent().mkdirs()
          file.write(false).use { output ->
            zipStream.copyTo(output)
          }
        }

        entry = zipStream.nextEntry
      }
    } catch (e: Exception) {
      e.printStackTrace()
      return 1
    }
  }
  return 0
}

fun unpackTarGZ(path: String, dest: String): Int {
  if (!Gdx.files.local(path).exists()) {
    println("Failed to find $path")
    return -1
  }

  if (!Gdx.files.local(dest).exists()) {
    Gdx.files.local(dest).mkdirs()
  }

  println("Extracting (tar.gz) $path!/ -> $dest")

  try {
    GZIPInputStream(Gdx.files.local(path).read()).use { gzipStream ->
      println("Extracting (tar) $path!/!/ -> $dest")

      try {
        val also = unpackTar(gzipStream, dest, path).also {
          if (it == 0) {
            println("Extracted (tar) $path!/!/ -> $dest")
          }
        }
        if (also != 0) {
          return also
        }
      } catch (e: Exception) {
        e.printStackTrace()
        return 1
      }
    }
  } catch (e: Exception) {
    e.printStackTrace()
    return 1
  }

  println("Extracted (tar.gz) $path!/ -> $dest")

  return 0
}

private fun unpackTar(gzipStream: GZIPInputStream, dest: String, path: String): Int {
  TarArchiveInputStream(gzipStream).use { tarStream ->
    try {
      var entry = tarStream.nextEntry
      while (entry != null) {
        val file = Gdx.files.local("$dest/${entry.name}")
        println("Extracting $path!/!/${entry.name} -> ${file.path()}")
        if (entry.isDirectory) {
          file.mkdirs()
        } else {
          if (!file.parent().exists())
            file.parent().mkdirs()
          file.write(false).use { output ->
            tarStream.copyTo(output)
          }
        }

        entry = tarStream.nextEntry
      }

      return 0
    } catch (e: Exception) {
      e.printStackTrace()
      return 1
    }
  }
}

fun killProcess(process: Process) {
  try {
    process.destroyForcibly()
    process.descendants().forEach {
      it.destroyForcibly()
    }
    process.waitFor()
  } catch (e: Exception) {
    println(e.message)
  }
}

/** [com.badlogic.gdx.ApplicationListener] implementation shared by all platforms. */
object Main : ApplicationAdapter() {

  private lateinit var looper: Thread
  private val spriteBatch by lazy { SpriteBatch() }

  private val font by lazy { BitmapFont(Gdx.files.internal("luna_pixel.fnt")) }

  private var selectedVersion: GameVersion? = null

  private var triedDestoyingOnce = false

  val playButton by lazy {
    Button(font, callback = {
      val runningProcess1 = runningProcess
      if (runningProcess1 != null && runningProcess1.isAlive) {
        try {
          killProcess(runningProcess1)
        } catch (e: Exception) {
          println(e.message)
        }
        return@Button
      }

      triedDestoyingOnce = false
      val version = selectedVersion ?: return@Button

      enabled = false

      if (version is ChannelVersion) {
        File("versions/${version.id}").deleteRecursively()
      }

      if (!version.isDownloaded()) {
        val name = version.id + if (System.getProperty("os.name").startsWith("Windows")) ".zip" else ""
        download(version.gameUrl, name, onProgress = { text = "Downloading Game (${(it * 100).toInt()}%)" }) {
          text = "Extracting Game"
          unpackGame(version)

          File("temp").deleteRecursively()
          text = "Launching ${version.name}"
          launchGame(version, this)
        }
      } else {
        text = "Launching ${version.name}"
        launchGame(version, this)
      }
    })
  }

  private val channels by lazy {
    val list = mutableListOf<String>()
    list += "edge"
    list += "beta"
    list += "release"

    list.map { GameChannel(it, "https://github.com/QuantumVoxel/game/archive/refs/heads/channels/$it.zip") }
  }

  private val availableVersions by lazy {
    val list = mutableListOf<GameVersion>()
    list += channels.map { ChannelVersion(it) }
    list += versionsFromGitHub()
    list
  }

  private val background by lazy { Texture(Gdx.files.internal("background.png")) }
  val width get() = Gdx.graphics.width.toFloat() / 2f

  val height get() = Gdx.graphics.height.toFloat() / 2f

  private var selectedButton: Button? = null

  private val versionButtons by lazy {
    val list = mutableListOf<Button>()
    availableVersions.map { version ->
      val button = Button(font, callback = {
        val sel = selectedButton
        if (sel != null) {
          sel.selected = false
        }
        selectedButton = this
        selected = true
        selectedVersion = version
      })
      button.text = version.name
      list += button
    }
    list
  }

  override fun create() {
    val versions = availableVersions
    if (versions.isNotEmpty()) {
      selectedVersion = versions[0]
      versionButtons[0].selected = true
    }

    File("versions").absoluteFile.mkdirs()
    File("temp").absoluteFile.mkdirs()

    playButton.text = "Play"
    if (!File("jdk").absoluteFile.exists()) {
      playButton.enabled = false

      download(JDK_URL, "jdk.tmp", onProgress = {
        playButton.text = "Downloading JDK (${(it * 100).toInt()}%)"
      }) {
        playButton.text = "Extracting JDK"
        if (unpack(it.toString(), Paths.get("jdk").toString()) != 0) {
          playButton.enabled = false
          playButton.text = "Failed to unpack JDK"

          return@download
        }

        Thread.sleep(10000)

        if (!System.getProperty("os.name").startsWith("Windows")) {
          playButton.text = "Setting permissions"

          val folder: Path = Paths.get("$JAVA_HOME/bin").absolute()
          Files.newDirectoryStream(folder).use { stream ->
            for (file in stream) {
              println("Setting permissions for ${file.absolute()}")
              Files.setAttribute(file.absolute(), "posix:permissions", PosixFilePermissions.fromString("rwxr-xr-x"))
            }
          }
        }

        playButton.enabled = true
        playButton.text = "Play"
      }
    }

    Gdx.input.inputProcessor = object : InputAdapter() {
      override fun scrolled(amountX: Float, amountY: Float): Boolean {
        scrollY += amountY * 2f
        return true
      }
    }

    looper = thread(isDaemon = false) {
      while (true) {
        if (runningProcess != null && !runningProcess!!.isAlive) {
          runningProcess = null
          playButton.enabled = true
          playButton.text = "Play"
        }

        try {
          Thread.sleep(1000)
        } catch (e: Exception) {
          return@thread
        }

        if (Thread.interrupted()) {
          return@thread
        }
      }
    }
  }

  var scrollY = 0f
    set(value) {
      if (value < 0) {
        field = 0f
        return
      }
      if (value > maxScrollY) {
        field = maxScrollY
        return
      }
      field = value
    }

  val maxScrollY
    get() = maxOf(
      20f * (availableVersions.size - 1) - height + 20f,
      0f
    )

  override fun render() {
    spriteBatch.projectionMatrix.scl(2f)
    spriteBatch.begin()

    spriteBatch.draw(background, 0f, 0f, width, height)

    versionButtons.forEachIndexed { index, button ->
      button.set(width = 160f, height = 20f)
      button.set(x = 0f, y = height - button.height - button.height * index + scrollY)
      button.render(spriteBatch, 0f)
    }

    try {
      playButton.set(width = 160f, height = 30f)
      playButton.set(x = width / 2f - playButton.width / 2f, y = playButton.height / 2f)

      playButton.render(spriteBatch, 0f)

      spriteBatch.projectionMatrix.scl(1 / 2f)
      spriteBatch.end()
    } catch (e: Exception) {
      e.printStackTrace()

      spriteBatch.projectionMatrix.scl(1 / 2f)
      spriteBatch.end()
    }
  }

  fun handleClose(): Boolean {
    val process = runningProcess
    if (process != null && process.isAlive) {
      killProcess(process)
      return false
    }
    if (this::looper.isInitialized) {
      looper.interrupt()
    }
    return true
  }
}
