package ai.platon.pulsar.browser.driver.chrome

import com.fasterxml.jackson.annotation.JsonProperty
import io.netty.channel.DefaultEventLoopGroup
import io.netty.channel.EventLoopGroup
import java.time.Duration

class ChromeVersion {
    @JsonProperty("Browser")
    val browser: String? = null
    @JsonProperty("Protocol-Version")
    val protocolVersion: String? = null
    @JsonProperty("User-Agent")
    val userAgent: String? = null
    @JsonProperty("V8-Version")
    val v8Version: String? = null
    @JsonProperty("WebKit-Version")
    val webKitVersion: String? = null
    @JsonProperty("webSocketDebuggerUrl")
    val webSocketDebuggerUrl: String? = null
}

class ChromeTab {
    var id: String = ""
    var parentId: String? = null
    var description: String? = null
    var title: String? = null
    var type: String? = null
    var url: String? = null
    var devtoolsFrontendUrl: String? = null
    var webSocketDebuggerUrl: String? = null
    var faviconUrl: String? = null

    val isPageType: Boolean
        get() = PAGE_TYPE == type

    companion object {
        const val PAGE_TYPE = "page"
    }
}

class MethodInvocation(
        var id: Long = 0,
        var method: String,
        var params: Map<String, Any>? = null
)

class DevToolsConfig(
        var workerGroup: EventLoopGroup = DefaultEventLoopGroup(),
        var readTimeout: Duration = Duration.ofMinutes(READ_TIMEOUT_MINUTES)
) {
    companion object {
        private const val READ_TIMEOUT_PROPERTY = "chrome.browser.services.config.readTimeout"
        private val READ_TIMEOUT_MINUTES = System.getProperty(READ_TIMEOUT_PROPERTY, "0").toLong()
    }
}
