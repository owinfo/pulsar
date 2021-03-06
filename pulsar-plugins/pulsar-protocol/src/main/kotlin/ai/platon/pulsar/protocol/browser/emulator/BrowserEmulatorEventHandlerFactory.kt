package ai.platon.pulsar.protocol.browser.emulator

import ai.platon.pulsar.common.config.CapabilityTypes
import ai.platon.pulsar.common.config.ImmutableConfig
import ai.platon.pulsar.common.message.MiscMessageWriter
import ai.platon.pulsar.protocol.browser.driver.WebDriverManager

class BrowserEmulatorEventHandlerFactory(
        private val driverManager: WebDriverManager,
        private val messageWriter: MiscMessageWriter,
        private val immutableConfig: ImmutableConfig
) {
    val eventHandler by lazy {
        val clazz = immutableConfig.getClass(
                CapabilityTypes.BROWSER_EMULATE_EVENT_HANDLER, BrowserEmulateEventHandler::class.java)
        clazz.constructors.first { it.parameters.size == 3 }
                .newInstance(driverManager, messageWriter, immutableConfig) as BrowserEmulateEventHandler
    }
}
