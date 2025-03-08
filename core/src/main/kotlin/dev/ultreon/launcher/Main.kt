package dev.ultreon.launcher

import com.badlogic.gdx.ApplicationAdapter
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.InputAdapter
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.GlyphLayout
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.utils.JsonReader
import com.badlogic.gdx.utils.JsonValue
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import java.io.RandomAccessFile
import java.net.URL
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardOpenOption
import java.nio.file.attribute.PosixFilePermissions
import java.util.zip.GZIPInputStream
import java.util.zip.ZipInputStream
import kotlin.concurrent.thread
import kotlin.io.path.*


private val userHome = System.getProperty("user.home")

val root: Path by lazy {
  when {
    osName.startsWith("Windows") -> Paths.get(System.getenv("APPDATA"), "QuantumVoxel")
    osName.startsWith("Linux") -> Paths.get(userHome, ".config/QuantumVoxel")
    osName.startsWith("Mac") -> Paths.get(userHome, "Library/Application Support/QuantumVoxel")
    else -> throw UnsupportedOperationException()
  }.also {
    if (Files.notExists(it)) {
      Files.createDirectories(it)
    }
  }
}

var layout: GlyphLayout = GlyphLayout()

fun BitmapFont.width(text: String): Float {
  layout.setText(this, text)
  return layout.width
}

var runningProcess: Process? = null

private val osName = System.getProperty("os.name")

private fun launchGame(version: GameVersion, button: Button): Process {
  if (path("versions/${version.id}/").notExists()) {
    throw Exception("Version ${version.id} does not exist")
  }

  try {
    val b = version.id in arrayOf("0.0.0-indev", "0.0.1-indev")
    return if (b || version is ChannelVersion) {
      if (osName.startsWith("Windows")) {
        ProcessBuilder("cmd", "/c", "gradlew.bat --no-daemon lwjgl3:run").run {
          environment()["PATH"] = "$JAVA_HOME/bin:${System.getenv("PATH")}"
          environment()["GRADLE_OPTS"] = "-Dorg.gradle.daemon=false -Djdk.lang.Process.launchMechanism=fork"
          directory(path("versions/${version.id}/").toFile())
        }.inheritIO().start()
      } else if (osName.startsWith("Linux")) {
        ProcessBuilder("bash", "-c", "chmod +x gradlew && chmod +x ./gradle/wrapper/gradle-wrapper.jar && ./gradlew --info --stacktrace --console=plain --no-daemon lwjgl3:run").run {
          environment()["PATH"] = "$JAVA_HOME/bin:${System.getenv("PATH")}"
          environment()["GRADLE_OPTS"] = "-Dorg.gradle.daemon=false -Djdk.lang.Process.launchMechanism=fork"
          directory(path("versions/${version.id}/").toFile())
        }.inheritIO().start()
      } else if (osName.startsWith("Mac")) {
        ProcessBuilder("zsh", "-c", "GRADLE_OPTS=\"-Dorg.gradle.daemon=false -Djdk.lang.Process.launchMechanism=fork\" chmod +x gradlew && ./gradlew --no-daemon --stacktrace lwjgl3:run").run {
          environment()["PATH"] = "$JAVA_HOME/bin:${System.getenv("PATH")}"
          environment()["GRADLE_OPTS"] = "-Dorg.gradle.daemon=false -Djdk.lang.Process.launchMechanism=fork"
          directory(path("versions/${version.id}/").toFile())
        }.inheritIO().start()
      } else {
        throw UnsupportedOperationException()
      }
    } else {
      if (osName.startsWith("Windows")) {
        ProcessBuilder(
          "$JAVA_HOME\\bin\\$JAVA_EXEC_NAME",
          "-cp",
          "lib/*",
          "dev.ultreon.quantum.lwjgl3.Lwjgl3Launcher"
        ).run {
          environment()["PATH"] = "$JAVA_HOME\\bin:${System.getenv("PATH")}"
          environment()["JAVA_HOME"] = JAVA_HOME.toString()
          directory(path("versions\\${version.id}/").toFile())
        }.inheritIO().start()
      } else if (osName.startsWith("Linux")) {
        ProcessBuilder(
          "$JAVA_HOME/bin/$JAVA_EXEC_NAME",
          "-cp",
          "lib/*",
          "dev.ultreon.quantum.lwjgl3.Lwjgl3Launcher"
        ).run {
          environment()["PATH"] = "$JAVA_HOME/bin:${System.getenv("PATH")}"
          environment()["JAVA_HOME"] = JAVA_HOME.toString()
          directory(path("versions/${version.id}/").toFile())
        }.inheritIO().start()
      } else if (osName.startsWith("Mac")) {
        ProcessBuilder(
          "$JAVA_HOME/bin/$JAVA_EXEC_NAME",
          "-XstartOnFirstThread",
          "-cp",
          "lib/*",
          "dev.ultreon.quantum.lwjgl3.Lwjgl3Launcher"
        ).run {
          environment()["PATH"] = "$JAVA_HOME/bin:${System.getenv("PATH")}"
          environment()["JAVA_HOME"] = JAVA_HOME.toString()
          directory(path("versions/${version.id}/").toFile())
        }.inheritIO().start()
      } else {
        throw UnsupportedOperationException()
      }
    }.also {
      button.text = "Click to Stop"
      button.enabled = true
      runningProcess = it
    }
  } catch (e: Exception) {
    Main.playButton.text = "Failed to launch game"
    Main.playButton.enabled = false
    throw e
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

    if (path("temp").absolute().notExists()) {
      path("temp").absolute().createDirectories()
    }

    val absolute = path("temp/$name").absolute()
    val file = absolute.outputStream()

    connection.inputStream.use { inputStream ->
      val buffer = ByteArray(1024)
      var read: Int
      while (inputStream.read(buffer).also { read = it } != -1) {
        file.write(buffer, 0, read)
        downloadedBytes += read
        onProgress?.invoke(if (totalBytes == 0L) 0f else downloadedBytes.toFloat() / totalBytes)
      }
    }

    file.flush()
    file.close()

    // Wait for the file to be found (some random issue on macOS cause the file not to be found immediately)
    while (absolute.notExists()) {
      Thread.sleep(100)
    }

    onComplete?.invoke(absolute.also {
      log("Downloaded $url -> $it")
    })
  }
}

val logFile = RandomAccessFile(path("log.txt").absolute().toString(), "rw").also {
  it.seek(0)
  it.setLength(0)

  it.write("QuantumVoxel Launcher\n".toByteArray())

  Runtime.getRuntime().addShutdownHook(Thread {
    it.close()
  })
}

fun log(text: String?) {
  synchronized(logFile) {
    logFile.seek(logFile.length())
    logFile.write("INFO: $text\n".toByteArray())

    println("INFO: $text")
  }
}

fun log(text: Throwable) {
  synchronized(logFile) {
    logFile.seek(logFile.length())
    logFile.write("ERROR: ${text.javaClass.name}: ${text.message}\n${text.stackTraceToString()}\n".toByteArray())
  }
}

fun logError(text: String?) {
  synchronized(logFile) {
    logFile.seek(logFile.length())
    logFile.write("ERROR: $text\n".toByteArray())
  }
}

fun logError(text: Throwable) {
  synchronized(logFile) {
    logFile.seek(logFile.length())
    logFile.write("ERROR: ${text.javaClass.name}: ${text.message}\n${text.stackTraceToString()}\n".toByteArray())
  }
}

fun path(path: String): Path {
  return root.resolve(path)
}

open class GameVersion(val id: String, val name: String, val gameUrl: String) {
  fun isDownloaded(): Boolean {
    return Files.exists(path("versions/$id"))
  }
}

class GameChannel(val name: String, val gameUrl: String)

open class ChannelVersion(gameChannel: GameChannel) :
  GameVersion(gameChannel.name, "Channel ${gameChannel.name}", gameChannel.gameUrl)

const val VERSION_API = "https://api.github.com/repos/QuantumVoxel/game/releases"
const val JDK_VERSION = "17.0.2_8"
val JDK_URL = if (osName.startsWith("Windows")) {
  "https://github.com/adoptium/temurin17-binaries/releases/download/jdk-${
    JDK_VERSION.replace(
      "_",
      "%2B"
    )
  }/OpenJDK17U-jdk_x64_windows_hotspot_$JDK_VERSION.zip"
} else if (osName.startsWith("Linux") && System.getProperty("os.arch") == "aarch64") {
  "https://github.com/adoptium/temurin17-binaries/releases/download/jdk-${
    JDK_VERSION.replace(
      "_",
      "%2B"
    )
  }/OpenJDK17U-jdk_aarch64_linux_hotspot_$JDK_VERSION.tar.gz"
} else if (osName.startsWith("Linux")) {
  "https://github.com/adoptium/temurin17-binaries/releases/download/jdk-${
    JDK_VERSION.replace(
      "_",
      "%2B"
    )
  }/OpenJDK17U-jdk_x64_linux_hotspot_$JDK_VERSION.tar.gz"
} else if (osName.startsWith("Mac") && System.getProperty("os.arch") == "aarch64") {
  "https://github.com/adoptium/temurin17-binaries/releases/download/jdk-${
    JDK_VERSION.replace(
      "_",
      "%2B"
    )
  }/OpenJDK17U-jdk_aarch64_mac_hotspot_$JDK_VERSION.tar.gz"
} else if (osName.startsWith("Mac")) {
  "https://github.com/adoptium/temurin17-binaries/releases/download/jdk-${
    JDK_VERSION.replace(
      "_",
      "%2B"
    )
  }/OpenJDK17U-jdk_x64_mac_hotspot_$JDK_VERSION.tar.gz"
} else {
  throw UnsupportedOperationException()
}

val JAVA_HOME = path(if (osName.startsWith("Windows")) {
    "jdk/jdk-${JDK_VERSION.replace("_", "+")}"
  } else if (osName.startsWith("Linux")) {
    "jdk/jdk-${JDK_VERSION.replace("_", "+")}"
  } else if (osName.startsWith("Mac")) {
    "jdk/jdk-${JDK_VERSION.replace("_", "+")}/Contents/Home"
  } else {
    throw UnsupportedOperationException()
  }
)

val JAVA_EXEC_NAME = if (osName.startsWith("Windows")) {
  "javaw.exe"
} else if (osName.startsWith("Linux")) {
  "java"
} else if (osName.startsWith("Mac")) {
  "java"
} else {
  throw UnsupportedOperationException()
}

var cachedReleases: JsonValue? = if (Files.exists(path("releases.json"))) {
  JsonReader().parse(path("releases.json").readText())
} else {
  null
}

fun versionsFromGitHub(): List<GameVersion> {
  val list = mutableListOf<GameVersion>()
  val cache = cachedReleases
  if (cache != null && cache["cache_time"].asLong() + 600 * 1000 > System.currentTimeMillis()) {
    for (i in 0 until cache["releases"].size) {
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

  log("Fetching versions from GitHub...")

  try {

    val readText = URL(VERSION_API).readText()
    val json = JsonReader().parse(readText)

    for (i in 0 until json.size) {
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


      Files.writeString(path("releases.json"), this.toString(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)
    }
  } catch (e: Exception) {
    log("Failed to fetch versions from GitHub: ${e.message}")

    if (cache != null) {
      for (i in 0 until cache["releases"].size) {
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
  val unpacked = unpackZip(path("temp/${version.id}").absolute(), path("temp/${version.id}-extract").absolute())
  if (unpacked != 0) {
    return 1
  }

  if (version.id in arrayOf("0.0.0-indev", "0.0.1-indev") || version is ChannelVersion) {
    if (move(Files.list(path("temp/${version.id}-extract").absolute()).findFirst().orElseThrow(), path("versions/${version.id}").absolute()) != 0) {
      return 1
    }
  } else {
    if (move(path("temp/${version.id}-extract").absolute(), path("versions/${version.id}").absolute()) != 0) {
      return 1
    }
  }

  return 0
}

fun unpack(path: Path, dest: Path): Int {
  if (Files.notExists(path)) {
    log("Failed to find $path")
    return -1
  }

  if (Files.notExists(dest)) {
    Files.createDirectories(dest)
    log("Created $dest")
  } else {
    log("Found $dest")
  }
  return if (osName.startsWith("Windows")) {
    unpackZip(path, dest)
  } else if (osName.startsWith("Linux")) {
    unpackTarGZ(path, dest)
  } else if (osName.startsWith("Mac")) {
    unpackTarGZ(path, dest)
  } else {
    throw UnsupportedOperationException()
  }
}

fun move(path: Path, dest: Path): Int {
  if (Files.notExists(path)) {
    log("Failed to find $path")
    return 1
  }

  if (Files.exists(dest)) {
    log("Failed to move $path to $dest, $dest already exists")
    return 1
  }

  try {
    Files.move(path, dest)
  } catch (e: Exception) {
    e.printStackTrace()
    return 1
  }

  return 0
}

fun unpackZip(path: Path, dest: Path, subFolder: String = ""): Int {
  if (Files.notExists(path)) {
    log("Failed to find $path")
    return -1
  }

  if (Files.notExists(dest)) {
    Files.createDirectories(dest)
    log("Created $dest")
  } else {
    log("Found $dest")
  }

  log("Extracting (zip) $path!/ -> $dest")

  ZipInputStream(Files.newInputStream(path)).use { zipStream ->
    try {
      var entry = zipStream.nextEntry
      while (entry != null) {
        if (!entry.name.startsWith(subFolder)) {
          entry = zipStream.nextEntry
          continue
        }

        val file = dest.resolve(entry.name.substringAfter(subFolder))
        log("Extracting $path!/${entry.name} -> $file")
        if (entry.isDirectory) {
          Files.createDirectories(file)
        } else {
          if (Files.notExists(file.parent))
            Files.createDirectories(file.parent)

          Files.newOutputStream(file, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING).use { output ->
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

fun unpackTarGZ(path: Path, dest: Path): Int {
  if (Files.notExists(path)) {
    log("Failed to find $path")
    return -1
  }

  if (Files.notExists(dest)) {
    Files.createDirectories(dest)
    log("Created $dest")
  } else {
    log("Found $dest")
  }

  log("Extracting (tar.gz) $path!/ -> $dest")

  try {
    GZIPInputStream(Files.newInputStream(path)).use { gzipStream ->
      log("Extracting (tar) $path!/!/ -> $dest")

      try {
        val also = unpackTar(gzipStream, dest, path).also {
          if (it == 0) {
            log("Extracted (tar) $path!/!/ -> $dest")
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

  log("Extracted (tar.gz) $path!/ -> $dest")

  return 0
}

private fun unpackTar(gzipStream: GZIPInputStream, dest: Path, path: Path): Int {
  TarArchiveInputStream(gzipStream).use { tarStream ->
    try {
      var entry = tarStream.nextEntry
      while (entry != null) {
        val file = path("$dest/${entry.name}")
        log("Extracting $path!/!/${entry.name} -> $file")
        if (entry.isDirectory) {
          Files.createDirectories(file)
        } else {
          if (Files.notExists(file.parent))
            Files.createDirectories(file.parent)

          Files.newOutputStream(file, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING).use { output ->
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
    log(e.message)
  }
}

/** [com.badlogic.gdx.ApplicationListener] implementation shared by all platforms. */
object Main : ApplicationAdapter() {

  private lateinit var looper: Thread
  private val spriteBatch by lazy { SpriteBatch() }

  private val font by lazy { BitmapFont(Gdx.files.internal("luna_pixel.fnt")) }

  private var selectedVersion: GameVersion? = null

  private var triedDestoyingOnce = false

  @OptIn(ExperimentalPathApi::class)
  val playButton by lazy {
    Button(font, callback = {
      val runningProcess1 = runningProcess
      if (runningProcess1 != null && runningProcess1.isAlive) {
        try {
          killProcess(runningProcess1)
        } catch (e: Exception) {
          log(e.message)
        }
        return@Button
      }

      triedDestoyingOnce = false
      val version = selectedVersion ?: return@Button

      enabled = false

      if (version is ChannelVersion) {
        path("versions/${version.id}").deleteRecursively()
      }

      if (!version.isDownloaded()) {
        val name = version.id
        download(version.gameUrl, name, onProgress = { text = "Downloading Game (${(it * 100).toInt()}%)" }) {
          text = "Extracting Game"
          unpackGame(version)

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
//    list += "beta"
//    list += "release"

    list.map { GameChannel(it, "https://github.com/QuantumVoxel/game/archive/refs/heads/channels/$it.zip") }
  }

  private val availableVersions by lazy {
    val list = mutableListOf<GameVersion>()
    list += channels.map { ChannelVersion(it) }
    list += versionsFromGitHub().filter { it.id != "0.0.0-indev" && it.id != "0.0.1-indev" && it.id != "0.0.2-indev" && it.id != "0.0.3-indev" }
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

    path("versions").createDirectories()
    path("temp").createDirectories()

    playButton.text = "Play"
    if (path("jdk").notExists()) {
      playButton.enabled = false

      download(JDK_URL, "jdk.tmp", onProgress = {
        playButton.text = "Downloading JDK (${(it * 100).toInt()}%)"
      }) {
        playButton.text = "Extracting JDK"
        if (unpack(it, path("jdk")) != 0) {
          playButton.enabled = false
          playButton.text = "Failed to unpack JDK"

          return@download
        }

        Thread.sleep(10000)

        if (!osName.startsWith("Windows")) {
          playButton.text = "Setting permissions"

          val folder: Path = path("$JAVA_HOME/bin").absolute()
          Files.newDirectoryStream(folder).use { stream ->
            for (file in stream) {
              log("Setting permissions for ${file.absolute()}")
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

  private val maxScrollY
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
