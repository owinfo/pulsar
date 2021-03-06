package ai.platon.pulsar.browser.driver.chrome

import ai.platon.pulsar.browser.driver.chrome.LauncherConfig.Companion.CHROME_BINARY_SEARCH_PATHS
import ai.platon.pulsar.browser.driver.chrome.impl.Chrome
import ai.platon.pulsar.browser.driver.chrome.util.ChromeProcessException
import ai.platon.pulsar.browser.driver.chrome.util.ChromeProcessTimeoutException
import ai.platon.pulsar.common.AppPaths
import ai.platon.pulsar.common.Strings
import org.apache.commons.io.FileUtils
import org.slf4j.LoggerFactory
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardOpenOption
import java.time.Duration
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern
import java.util.stream.Collectors
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.streams.toList

class LauncherConfig {
    var startupWaitTime = DEFAULT_STARTUP_WAIT_TIME
    var shutdownWaitTime = DEFAULT_SHUTDOWN_WAIT_TIME
    var threadWaitTime = THREAD_JOIN_WAIT_TIME
    var userDataDirPath = AppPaths.CHROME_TMP_DIR

    companion object {
        /** Default startup wait time in seconds.  */
        val DEFAULT_STARTUP_WAIT_TIME = Duration.ofSeconds(60)
        /** Default shutdown wait time in seconds.  */
        val DEFAULT_SHUTDOWN_WAIT_TIME = Duration.ofSeconds(60)
        /** 5 seconds wait time for threads to stop.  */
        val THREAD_JOIN_WAIT_TIME = Duration.ofSeconds(5)

        val CHROME_BINARY_SEARCH_PATHS = arrayOf(
                "/mnt/data/workspace/chromium/src/out/Release/chrome",
                "/mnt/data/workspace/chromium/src/out/Default/chrome",
                "/usr/bin/chromium",
                "/usr/bin/chromium-browser",
                "/usr/bin/google-chrome-stable",
                "/usr/bin/google-chrome",
                "/opt/google/chrome/chrome",
                "/Applications/Chromium.app/Contents/MacOS/Chromium",
                "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome",
                "/Applications/Google Chrome Canary.app/Contents/MacOS/Google Chrome Canary",
                "C:/Program Files (x86)/Google/Chrome/Application/chrome.exe"
        )
    }
}

/** Chrome argument */
@kotlin.annotation.Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FIELD)
annotation class ChromeParameter(val value: String)

class ChromeDevtoolsOptions(
        @ChromeParameter("headless")
        var headless: Boolean = true,
        @ChromeParameter("remote-debugging-port")
        var remoteDebuggingPort: Int = 0,
        @ChromeParameter("no-default-browser-check")
        var noDefaultBrowserCheck: Boolean = true,
        @ChromeParameter("no-first-run")
        var noFirstRun: Boolean = true,
        @ChromeParameter(ARG_USER_DATA_DIR)
        var userDataDir: String = AppPaths.CHROME_TMP_DIR.toString(),
        @ChromeParameter("incognito")
        var incognito: Boolean = false,
        @ChromeParameter("disable-gpu")
        var disableGpu: Boolean = true,
        @ChromeParameter("hide-scrollbars")
        var hideScrollbars: Boolean = true,
        @ChromeParameter("mute-audio")
        var muteAudio: Boolean = true,
        @ChromeParameter("disable-background-networking")
        var disableBackgroundNetworking: Boolean = true,
        @ChromeParameter("disable-background-timer-throttling")
        var disableBackgroundTimerThrottling: Boolean = true,
        @ChromeParameter("disable-client-side-phishing-detection")
        var disableClientSidePhishingDetection: Boolean = true,
        @ChromeParameter("disable-default-apps")
        var disableDefaultApps: Boolean = true,
        @ChromeParameter("disable-extensions")
        var disableExtensions: Boolean = true,
        @ChromeParameter("disable-hang-monitor")
        var disableHangMonitor: Boolean = true,
        @ChromeParameter("disable-popup-blocking")
        var disablePopupBlocking: Boolean = true,
        @ChromeParameter("disable-prompt-on-repost")
        var disablePromptOnRepost: Boolean = true,
        @ChromeParameter("disable-sync")
        var disableSync: Boolean = true,
        @ChromeParameter("disable-translate")
        var disableTranslate: Boolean = true,
        @ChromeParameter("metrics-recording-only")
        var metricsRecordingOnly: Boolean = true,
        @ChromeParameter("safebrowsing-disable-auto-update")
        var safebrowsingDisableAutoUpdate: Boolean = true,
        @ChromeParameter("ignore-certificate-errors")
        var ignoreCertificateErrors: Boolean = true
) {
    val additionalArguments: MutableMap<String, Any?> = mutableMapOf()

    fun addArguments(key: String, value: String? = null): ChromeDevtoolsOptions {
        additionalArguments[key] = value
        return this
    }

    fun removeArguments(key: String): ChromeDevtoolsOptions {
        additionalArguments.remove(key)
        return this
    }

    fun merge(args: Map<String, Any?>) {
        args.forEach { (key, value) -> addArguments(key, value?.toString()) }
    }

    fun toMap(): Map<String, Any?> {
        val args = ChromeDevtoolsOptions::class.java.declaredFields
                .filter { it.annotations.any { it is ChromeParameter } }
                .onEach { it.isAccessible = true }
                .associateTo(mutableMapOf()) { it.getAnnotation(ChromeParameter::class.java).value to it.get(this) }

        args.putAll(additionalArguments)

        return args
    }

    fun toList(): List<String> {
        return toList(toMap())
    }

    private fun toList(args: Map<String, Any?>): List<String> {
        val result = ArrayList<String>()
        for ((key, value) in args) {
            if (value != null && false != value) {
                if (true == value) {
                    result.add("--$key")
                } else {
                    result.add("--$key=$value")
                }
            }
        }
        return result
    }

    override fun toString(): String {
        return toList().joinToString(" ") { it }
    }

    companion object {
        const val ARG_USER_DATA_DIR = "user-data-dir"
    }
}

class ProcessLauncher {
    @Throws(IOException::class)
    fun launch(program: String, args: List<String>): Process {
        val arguments = mutableListOf<String>().apply { add(program); addAll(args) }
        val processBuilder = ProcessBuilder()
                .command(arguments)
                .redirectErrorStream(true)
                .redirectOutput(ProcessBuilder.Redirect.PIPE)
        return processBuilder.start()
    }
}

class ChromeLauncher(
        private val processLauncher: ProcessLauncher = ProcessLauncher(),
        private val shutdownHookRegistry: ShutdownHookRegistry = RuntimeShutdownHookRegistry(),
        private val config: LauncherConfig = LauncherConfig()
) : AutoCloseable {

    companion object {
        const val ENV_CHROME_PATH = "CHROME_PATH"
        private val DEVTOOLS_LISTENING_LINE_PATTERN = Pattern.compile("^DevTools listening on ws:\\/\\/.+:(\\d+)\\/")
    }

    private val log = LoggerFactory.getLogger(ChromeLauncher::class.java)
    private var chromeProcess: Process? = null
    private var userDataDirPath = config.userDataDirPath
    private val shutdownHookThread = Thread { this.close() }

    fun launch(chromeBinaryPath: Path, options: ChromeDevtoolsOptions): RemoteChrome {
        val port = launchChromeProcess(chromeBinaryPath, options)
        userDataDirPath = Paths.get(options.userDataDir)
        return Chrome(port)
    }

    fun launch(options: ChromeDevtoolsOptions): RemoteChrome = launch(searchChromeBinary(), options)

    fun launch(headless: Boolean): RemoteChrome {
        return launch(searchChromeBinary(), ChromeDevtoolsOptions().also { it.headless = headless })
    }

    fun launch(): RemoteChrome = launch(true)

    /**
     * Returns the chrome binary path.
     *
     * @return Chrome binary path.
     */
    private fun searchChromeBinary(): Path {
        val envChrome = System.getProperty(ENV_CHROME_PATH)
        if (envChrome != null) {
            envChrome.let { Paths.get(it) }
                    .takeIf { Files.isExecutable(it) }
                    ?.toAbsolutePath()
                    ?:throw RuntimeException("CHROME_PATH is not executable | $envChrome")
        }

        return CHROME_BINARY_SEARCH_PATHS.map { Paths.get(it) }
                .firstOrNull { Files.isExecutable(it) }
                ?.toAbsolutePath()
                ?:throw RuntimeException("Could not find chrome binary in search path. Try setting CHROME_PATH environment value")
    }

    override fun close() {
        val process = chromeProcess?:return
        chromeProcess = null
        if (process.isAlive) {
            destroyChrome(process)
            kotlin.runCatching { shutdownHookRegistry.remove(shutdownHookThread) }
        }
    }

    private fun destroyChrome(process: Process) {
        process.destroy()
        try {
            if (!process.waitFor(config.shutdownWaitTime.seconds, TimeUnit.SECONDS)) {
                process.destroyForcibly()
                process.waitFor(config.shutdownWaitTime.seconds, TimeUnit.SECONDS)
            }

            log.info("Chrome processes are exit")
        } catch (e: InterruptedException) {
            log.error("Interrupted while waiting for chrome process to shutdown", e)
            process.destroyForcibly()
        } finally {
            kotlin.runCatching {
                // wait for 1 second to hope every thing is clean
                Thread.sleep(1000)

                FileUtils.deleteQuietly(userDataDirPath.toFile())
                if (Files.exists(userDataDirPath)) {
                    log.warn("Failed to delete browser data, try again | {}", userDataDirPath)
                    forceDeleteDirectory(userDataDirPath)

                    if (Files.exists(userDataDirPath)) {
                        log.error("Can not delete browser data | {}", userDataDirPath)
                    }
                }
            }
        }
    }

    /**
     * Returns an exit value. This is just proxy to [Process.exitValue].
     *
     * @return Exit value of the process if exited.
     * @throws [IllegalThreadStateException] if the subprocess has not yet terminated. [     ] If the process hasn't even started.
     */
    fun exitValue(): Int {
        checkNotNull(chromeProcess) { "Chrome process has not been started" }
        return chromeProcess!!.exitValue()
    }

    /**
     * Tests whether the subprocess is alive. This is just proxy to [Process.isAlive].
     *
     * @return True if the subprocess has not yet terminated.
     * @throws IllegalThreadStateException if the subprocess has not yet terminated.
     */
    val isAlive: Boolean
        get() = chromeProcess != null && chromeProcess!!.isAlive

    /**
     * Launches a chrome process given a chrome binary and its arguments.
     *
     * @param chromeBinary Chrome binary path.
     * @param chromeOptions Chrome arguments.
     * @return Port on which devtools is listening.
     * @throws IllegalStateException If chrome process has already been started.
     * @throws ChromeProcessException If an I/O error occurs during chrome process start.
     * @throws ChromeProcessTimeoutException If timeout expired while waiting for chrome to start.
     */
    @Throws(ChromeProcessException::class)
    private fun launchChromeProcess(chromeBinary: Path, chromeOptions: ChromeDevtoolsOptions): Int {
        check(!isAlive) { "Chrome process has already been started" }
        shutdownHookRegistry.register(shutdownHookThread)
        val arguments = chromeOptions.toList()

        log.info("Launching chrome:\n{} {}", chromeBinary, arguments.joinToString(" ") { it })
        return try {
            chromeProcess = processLauncher.launch(chromeBinary.toString(), arguments)
            waitForDevToolsServer(chromeProcess!!)
        } catch (e: IOException) {
            // Unsubscribe from registry on exceptions.
            shutdownHookRegistry.remove(shutdownHookThread)
            throw ChromeProcessException("Failed starting chrome process", e)
        } catch (e: Exception) {
            close()
            throw e
        }
    }

    /**
     * Waits for DevTools server is up on chrome process.
     *
     * @param process Chrome process.
     * @return DevTools listening port.
     * @throws ChromeProcessTimeoutException If timeout expired while waiting for chrome process.
     */
    @Throws(ChromeProcessTimeoutException::class)
    private fun waitForDevToolsServer(process: Process): Int {
        var port = 0
        val chromeOutput = StringBuilder()
        val readLineThread = Thread {
            BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
                // Wait for DevTools listening line and extract port number.
                var line: String
                while (reader.readLine().also { line = it } != null) {
                    if (line.isNotBlank()) log.info(line)
                    val matcher = DEVTOOLS_LISTENING_LINE_PATTERN.matcher(line)
                    if (matcher.find()) {
                        port = matcher.group(1).toInt()
                        break
                    }
                    chromeOutput.appendln(line)
                }
            }
        }
        readLineThread.start()

        try {
            readLineThread.join(config.startupWaitTime.toMillis())
            if (port == 0) {
                close(readLineThread)
                throw ChromeProcessTimeoutException("Timeout to waiting for chrome to start. Chrome output: \n>>>$chromeOutput")
            }
        } catch (e: InterruptedException) {
            close(readLineThread)
            log.error("Interrupted while waiting for dev tools server", e)
            throw RuntimeException("Interrupted while waiting for dev tools server", e)
        }
        return port
    }

    private fun close(thread: Thread) {
        try {
            thread.join(config.threadWaitTime.toMillis())
        } catch (ignored: InterruptedException) {}
    }

    /**
     * Force delete all browser data
     * */
    private fun forceDeleteDirectory(dirToDelete: Path) {
        // TODO: delete data only if they contain privacy data, cookies, sessions, local storage, etc
        synchronized(ChromeLauncher::class.java) {
            val lock = dirToDelete.parent.resolve("${dirToDelete.fileName}.lock")

            val maxTry = 10
            var i = 0
            while (i++ < maxTry && Files.exists(dirToDelete)) {
                FileChannel.open(lock, StandardOpenOption.APPEND).use {
                    it.lock()
                    kotlin.runCatching { FileUtils.deleteDirectory(dirToDelete.toFile()) }
                            .onFailure { log.warn(Strings.simplifyException(it)) }
                }

                Thread.sleep(500)
            }

            require(Files.exists(lock))
        }
    }

    interface ShutdownHookRegistry {
        fun register(thread: Thread) {
            Runtime.getRuntime().addShutdownHook(thread)
        }

        fun remove(thread: Thread) {
            Runtime.getRuntime().removeShutdownHook(thread)
        }
    }

    class RuntimeShutdownHookRegistry : ShutdownHookRegistry
}
