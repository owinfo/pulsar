package ai.platon.pulsar.crawl.fetch

import ai.platon.pulsar.common.*
import ai.platon.pulsar.common.AppPaths.PATH_UNREACHABLE_HOSTS
import ai.platon.pulsar.common.config.CapabilityTypes
import ai.platon.pulsar.common.config.CapabilityTypes.PARSE_MAX_URL_LENGTH
import ai.platon.pulsar.common.config.ImmutableConfig
import ai.platon.pulsar.common.config.Parameterized
import ai.platon.pulsar.common.config.Params
import ai.platon.pulsar.common.message.MiscMessageWriter
import ai.platon.pulsar.crawl.common.URLUtil
import ai.platon.pulsar.persist.WebPage
import com.codahale.metrics.SharedMetricRegistries
import com.google.common.collect.ConcurrentHashMultiset
import org.slf4j.LoggerFactory
import oshi.SystemInfo
import java.nio.file.Files
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentSkipListSet
import java.util.concurrent.atomic.AtomicBoolean

class FetchMetrics(
        private val metricsManagement: MetricsManagement,
        private val messageWriter: MiscMessageWriter,
        conf: ImmutableConfig
): Parameterized, AutoCloseable {

    private val log = LoggerFactory.getLogger(FetchMetrics::class.java)!!
    private val groupMode = conf.getEnum(CapabilityTypes.PARTITION_MODE_KEY, URLUtil.GroupMode.BY_HOST)
    private val systemInfo = SystemInfo()
    private val metricRegistry = SharedMetricRegistries.getOrCreate("pulsar")
    /**
     * The limitation of url length
     */
    private var maxUrlLength: Int = conf.getInt(PARSE_MAX_URL_LENGTH, 1024)

    val startTime = Instant.now()
    val elapsedTime get() = Duration.between(startTime, Instant.now())

    /**
     * Tracking statistics for each host
     */
    val urlStatistics = ConcurrentHashMap<String, UrlStat>()
    /**
     * Tracking unreachable hosts
     */
    val unreachableHosts = ConcurrentSkipListSet<String>()
    /**
     * Tracking hosts who is failed to fetch tasks.
     * A host is considered to be a unreachable host if there are too many failure
     */
    val timeoutUrls = ConcurrentSkipListSet<String>()
    val movedUrls = ConcurrentSkipListSet<String>()
    val failedUrls = ConcurrentSkipListSet<String>()
    val deadUrls = ConcurrentSkipListSet<String>()
    val failedHosts = ConcurrentHashMultiset.create<String>()

    /**
     * The total bytes of page content of all success web pages
     * */
    private val tasks = metricRegistry.meter(prependReadableClassName(this,"tasks"))
    private val successTasks = metricRegistry.meter(prependReadableClassName(this,"successTasks"))
    private val finishedTasks = metricRegistry.meter(prependReadableClassName(this,"finishedTasks"))
    private val meterContentBytes = metricRegistry.meter(prependReadableClassName(this,"mContentBytes"))
    private val histogramContentBytes = metricRegistry.histogram(prependReadableClassName(this,"hContentBytes"))

    private val pageImages = metricRegistry.histogram(prependReadableClassName(this, "pageImages"))
    private val pageAnchors = metricRegistry.histogram(prependReadableClassName(this, "pageAnchors"))
    private val pageNumbers = metricRegistry.histogram(prependReadableClassName(this, "pageNumbers"))
    private val pageSmallTexts = metricRegistry.histogram(prependReadableClassName(this, "pageSmallTexts"))
    private val pageHeights = metricRegistry.histogram(prependReadableClassName(this, "pageHeights"))

    private val realTimeSystemNetworkBytesRecv get() = systemInfo.hardware.networkIFs.sumBy { it.bytesRecv.toInt() }.toLong()
    /**
     * The total all bytes received by the hardware at the application startup
     * */
    private val initSystemNetworkBytesRecv by lazy { realTimeSystemNetworkBytesRecv }
    /**
     * The total all bytes received by the hardware last read from system
     * */
    @Volatile
    private var systemNetworkBytesRecv = 0L

    /**
     * The total bytes received by the hardware from the application startup
     * */
    val networkBytesRecv
        get() = (systemNetworkBytesRecv - initSystemNetworkBytesRecv).coerceAtLeast(0L)
    val networkBytesRecvPerSecond
        get() = networkBytesRecv / elapsedTime.seconds
    val networkBytesRecvPerPage
        get() = networkBytesRecv / successTasks.count.coerceAtLeast(1)

    private val closed = AtomicBoolean()

    init {
        Files.readAllLines(PATH_UNREACHABLE_HOSTS).mapTo(unreachableHosts) { it }

        systemNetworkBytesRecv = initSystemNetworkBytesRecv

        params.withLogger(log).info(true)
    }

    override fun getParams(): Params {
        return Params.of(
                "unreachableHosts", unreachableHosts.size,
                "maxUrlLength", maxUrlLength,
                "unreachableHostsPath", PATH_UNREACHABLE_HOSTS,
                "timeoutUrls", timeoutUrls.size,
                "failedUrls", failedUrls.size,
                "deadUrls", deadUrls.size
        )
    }

    fun isReachable(host: String): Boolean {
        return !unreachableHosts.contains(host)
    }

    fun isGone(host: String): Boolean {
        return unreachableHosts.contains(host)
    }

    fun isFailed(url: String): Boolean {
        return failedUrls.contains(url)
    }

    fun trackFailed(url: String) {
        failedUrls.add(url)
    }

    fun trackFailed(urls: Collection<String>) {
        failedUrls.addAll(urls)
    }

    fun isTimeout(url: String): Boolean {
        return timeoutUrls.contains(url)
    }

    fun markTaskStart() {
        tasks.mark()
    }

    fun markTaskStart(size: Int) {
        tasks.mark(size.toLong())
    }

    /**
     * Available hosts statistics
     */
    fun trackSuccess(page: WebPage) {
        // TODO: ensure the page is updated

        successTasks.mark()
        finishedTasks.mark()

        val bytes = page.contentBytes.toLong()
        histogramContentBytes.update(bytes)
        meterContentBytes.mark(bytes)

        page.activeDomMultiStatus?.lastStat?.apply {
            pageAnchors.update(na)
            pageImages.update(ni)
            pageNumbers.update(nnm)
            pageSmallTexts.update(nst)
            pageHeights.update(h)
        }

        val i = finishedTasks.count

        if (log.isInfoEnabled && i % 20 == 0L) {
            log.info(formatTraffic())
        }

        val url = page.url
        val host = URLUtil.getHost(url, groupMode)

        if (host == null || host.isEmpty()) {
            log.warn("Bad host in url : $url")
            return
        }

        val fetchStatus = urlStatistics.computeIfAbsent(host) { UrlStat(it) }

        ++fetchStatus.urls
        ++fetchStatus.pageViews

        // PageCategory pageCategory = CrawlFilter.sniffPageCategory(page);
        val pageCategory = page.pageCategory
        when {
            pageCategory.isIndex -> ++fetchStatus.indexUrls
            pageCategory.isDetail -> ++fetchStatus.detailUrls
            pageCategory.isMedia -> ++fetchStatus.mediaUrls
            pageCategory.isSearch -> ++fetchStatus.searchUrls
            pageCategory.isBBS -> ++fetchStatus.bbsUrls
            pageCategory.isTieBa -> ++fetchStatus.tiebaUrls
            pageCategory.isBlog -> ++fetchStatus.blogUrls
            pageCategory.isUnknown -> ++fetchStatus.unknownUrls
        }

        val depth = page.distance
        if (depth == 1) {
            ++fetchStatus.urlsFromSeed
        }

        if (url.length > maxUrlLength) {
            ++fetchStatus.urlsTooLong
            messageWriter.debugLongUrls(url)
        }

        urlStatistics[host] = fetchStatus

        // The host is reachable
        unreachableHosts.remove(host)

        failedHosts.remove(host)
    }

    fun trackTimeout(url: String) {
        timeoutUrls.add(url)
    }

    fun trackMoved(url: String) {
        movedUrls.add(url)
    }

    /**
     * @param url The url
     * @return True if the host is gone
     */
    fun trackHostGone(url: String): Boolean {
        val host = URLUtil.getHost(url, groupMode)
        if (host == null || host.isEmpty()) {
            log.warn("Malformed url identified as gone | <{}>", url)
            return false
        }

        if (unreachableHosts.contains(host)) {
            return false
        }

        failedHosts.add(host)
        // Only the exception occurs for unknownHostEventCount, it's really add to the black list
        val failureHostEventCount = 3
        if (failedHosts.count(host) > failureHostEventCount) {
            log.info("Host unreachable | {}", host)
            unreachableHosts.add(host)
            return true
        }

        return false
    }

    fun countHostTasks(host: String): Int {
        val failedTasks = failedHosts.count(host)
        val (_, numUrls) = urlStatistics[host] ?: return failedTasks
        return numUrls + failedTasks
    }

    fun formatTraffic(): String {
        updateNetworkTraffic()

        val seconds = elapsedTime.seconds.coerceAtLeast(1)
        val count = successTasks.count.coerceAtLeast(1)
        val bytes = meterContentBytes.count
        return String.format("Fetched total %d pages in %s(%.2f pages/s) successfully | content: %s, %s/s, %s/p" +
                " | net recv: %s, %s/s, %s/p | total net recv: %s",
                count,
                elapsedTime.readable(),
                successTasks.meanRate,
                Strings.readableBytes(bytes),
                Strings.readableBytes(bytes / seconds),
                Strings.readableBytes(bytes / count),
                Strings.readableBytes(networkBytesRecv),
                Strings.readableBytes(networkBytesRecvPerSecond),
                Strings.readableBytes(networkBytesRecvPerPage),
                Strings.readableBytes(systemNetworkBytesRecv))
    }

    override fun close() {
        if (closed.compareAndSet(false, true)) {
            log.info(formatTraffic())

            log.info("There are " + unreachableHosts.size + " unreachable hosts")
            AppFiles.logUnreachableHosts(this.unreachableHosts)

            logAvailableHosts()
        }
    }

    private fun updateNetworkTraffic() {
        systemNetworkBytesRecv = systemInfo.hardware.networkIFs.sumBy { it.bytesRecv.toInt() }.toLong()
                .coerceAtLeast(systemNetworkBytesRecv)
    }

    private fun logAvailableHosts() {
        val report = StringBuilder("Total " + urlStatistics.size + " available hosts")
        report.append('\n')

        urlStatistics.values.sorted()
                .map { (hostName, urls, indexUrls, detailUrls, searchUrls, mediaUrls,
                               bbsUrls, blogUrls, tiebaUrls, _, urlsTooLong) ->
                    String.format("%40s -> %-15s %-15s %-15s %-15s %-15s %-15s %-15s %-15s %-15s",
                            hostName,
                            "total : $urls",
                            "index : $indexUrls",
                            "detail : $detailUrls",
                            "search : $searchUrls",
                            "media : $mediaUrls",
                            "bbs : $bbsUrls",
                            "tieba : $tiebaUrls",
                            "blog : $blogUrls",
                            "long : $urlsTooLong")
                }.joinTo(report, "\n") { it }

        log.info(report.toString())
    }
}
