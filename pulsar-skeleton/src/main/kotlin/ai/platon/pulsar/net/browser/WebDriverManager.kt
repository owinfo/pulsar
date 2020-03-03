package ai.platon.pulsar.net.browser

import ai.platon.pulsar.common.DateTimeUtil
import ai.platon.pulsar.common.Freezable
import ai.platon.pulsar.common.config.ImmutableConfig
import ai.platon.pulsar.common.config.Parameterized
import ai.platon.pulsar.common.config.VolatileConfig
import ai.platon.pulsar.proxy.ProxyManager
import org.slf4j.LoggerFactory
import java.time.Duration
import java.time.Instant
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Created by vincent on 18-1-1.
 * Copyright @ 2013-2017 Platon AI. All rights reserved
 */
class WebDriverManager(
        val driverControl: WebDriverControl,
        val proxyManager: ProxyManager,
        val conf: ImmutableConfig
): Parameterized, Freezable(), AutoCloseable {
    private val log = LoggerFactory.getLogger(WebDriverManager::class.java)

    val driverFactory = WebDriverFactory(driverControl, proxyManager, conf)
    val driverPool = WebDriverPool(driverFactory, conf)

    private val closed = AtomicBoolean()
    private var lastActiveTime = Instant.now()
    private var idleTimeout = Duration.ofMinutes(5)

    val startTime = Instant.now()
    val numReset = AtomicInteger()
    val pageViews = AtomicInteger()
    val isIdle get() = driverPool.numWorking == 0 && idleTime > idleTimeout
    val idleTime get() = Duration.between(lastActiveTime, Instant.now())
    val elapsedTime get() = Duration.between(startTime, Instant.now())
    val speed get() = 1.0 * pageViews.get() / elapsedTime.seconds
    val isClosed get() = closed.get()

    /**
     * Allocate [n] drivers with priority [priority]
     * */
    fun allocate(priority: Int, n: Int, conf: ImmutableConfig) {
        whenUnfrozen {
            repeat(n) { driverPool.poll(priority, conf)?.let { driverPool.put(it) } }
        }
    }

    /**
     * Run an action in this pool
     * */
    fun <R> run(priority: Int, volatileConfig: VolatileConfig, action: (driver: ManagedWebDriver) -> R): R {
        return whenUnfrozen {
            val driver = driverPool.poll(priority, volatileConfig)
                    ?: throw WebDriverPoolExhaust(formatStatus(verbose = true))
            try {
                driver.startWork()
                action(driver)
            } finally {
                lastActiveTime = Instant.now()

                driverPool.put(driver)

                driver.stat.pageViews++
                pageViews.incrementAndGet()
            }
        }
    }

    /**
     * Cancel the fetch task specified by [url] remotely
     * */
    fun cancel(url: String): ManagedWebDriver? {
        return freeze {
            driverPool.workingDrivers.values.firstOrNull { it.url == url }?.also { it.cancel() }
        }
    }

    /**
     * Cancel all the fetch tasks remotely
     * */
    fun cancelAll() {
        freeze {
            driverPool.workingDrivers.values.forEach { it.cancel() }
        }
    }

    /**
     * Cancel all running tasks and close all web drivers
     * */
    fun reset() {
        freeze {
            numReset.incrementAndGet()
            cancelAll()
            driverPool.closeAll(incognito = true)
        }
    }

    fun closeAll(incognito: Boolean = true, processExit: Boolean = false) {
        freeze {
            log.info("Closing all web drivers ... {}", formatStatus(verbose = true))
            driverPool.closeAll(incognito, processExit)
            log.info("Total ${driverPool.numQuit} drivers are quit | {}", formatStatus(true))
        }
    }

    fun report() {
        log.info(formatStatus(verbose = true))

        val sb = StringBuilder()

//        driverPool.onlineDrivers.forEach { driver ->
//            driver.driver.manage().cookies.joinTo(sb, "Cookies in driver #${driver.id}: ") { it.toString() }
//        }

        if (sb.isNotBlank()) {
            log.info("Cookies: \n{}", sb)
        } else {
            log.info("All drivers have no cookie")
        }
    }

    override fun close() {
        if (closed.compareAndSet(false, true)) {
            driverPool.closeAll(true, true)
        }
    }

    override fun toString(): String {
        return formatStatus(true)
    }

    private fun formatStatus(verbose: Boolean = false): String {
        return if (verbose) {
            String.format("total: %d free: %d working: %d online: %d" +
                    " crashed: %d retired: %d quit: %d reset: %d" +
                    " pageViews: %d elapsed: %s speed: %.2f page/s",
                    driverPool.numOnline, driverPool.numFree, driverPool.numWorking, driverPool.numActive,
                    driverPool.numCrashed.get(), driverPool.numRetired.get(), driverPool.numQuit.get(), numReset.get(),
                    pageViews.get(), DateTimeUtil.readableDuration(elapsedTime), speed
            )
        } else {
            String.format("%d/%d/%d/%d/%d/%d (free/working/active/online/crashed/retired)",
                    driverPool.numFree, driverPool.numWorking, driverPool.numActive, driverPool.numOnline,
                    driverPool.numCrashed.get(), driverPool.numRetired.get())
        }
    }
}