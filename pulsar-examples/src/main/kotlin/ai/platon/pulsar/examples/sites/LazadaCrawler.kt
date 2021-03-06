package ai.platon.pulsar.examples.sites

import ai.platon.pulsar.examples.common.Crawler

/**
 * Test for Thai language
 * */
fun main() {
    val portalUrl = "https://www.lazada.com.my/shop-pressure-cookers/"
    val args = """
        -i 1s -ii 1s -ol ".product-recommend-items__item-wrapper > a" -query .product-briefing
    """.trimIndent()
    Crawler().loadOutPages(portalUrl, args)
}
