/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package ai.platon.pulsar.filter

import ai.platon.pulsar.common.Strings
import ai.platon.pulsar.crawl.filter.UrlFilter
import org.junit.Assert
import java.io.BufferedReader
import java.io.FileReader
import java.io.IOException
import java.io.Reader
import java.nio.file.Paths
import java.util.function.BiConsumer
import java.util.function.Consumer
import java.util.function.Supplier
import kotlin.streams.toList

// JDK imports
abstract class RegexUrlFilterBaseTest : UrlFilterTestBase {
    protected var sampleDir = Paths.get(TEST_DIR, "sample").toString()

    protected constructor() {}
    protected constructor(sampleDir: String) {
        this.sampleDir = sampleDir
    }

    protected abstract fun getURLFilter(reader: Reader): UrlFilter

    protected fun bench(loops: Int, file: String) {
        try {
            val rulesPath = Paths.get(sampleDir, "$file.rules")
            val urlPath = Paths.get(sampleDir, "$file.urls")
            bench(loops, FileReader(rulesPath.toFile()), FileReader(urlPath.toFile()))
        } catch (e: Exception) {
            Assert.fail(e.toString())
        }
    }

    protected fun bench(loops: Int, reader: Reader, urls: Reader) {
        val start = System.currentTimeMillis()
        try {
            val filter = getURLFilter(reader)
            val expected = readURLFile(urls)
            for (i in 0 until loops) {
                test(filter, expected)
            }
        } catch (e: Exception) {
            Assert.fail(e.toString())
        }
        LOG.info("bench time (" + loops + ") " + (System.currentTimeMillis() - start) + "ms")
    }

    protected fun test(file: String) {
        try {
            val rulesPath = Paths.get(sampleDir, "$file.rules")
            val urlPath = Paths.get(sampleDir, "$file.urls")
            LOG.info("Rules File : $rulesPath")
            LOG.info("Urls File : $urlPath")
            test(FileReader(rulesPath.toFile()), FileReader(urlPath.toFile()))
        } catch (e: Exception) {
            Assert.fail(e.toString())
        }
    }

    protected fun test(reader: Reader, urls: Reader) {
        try {
            test(getURLFilter(reader), readURLFile(urls))
        } catch (e: Exception) {
            Assert.fail(Strings.stringifyException(e))
        }
    }

    protected fun test(filter: UrlFilter, expected: List<FilteredURL>) {
        expected.forEach(Consumer { url: FilteredURL ->
            val result = filter.filter(url.url)
            if (result != null) {
                Assert.assertTrue(url.url, url.sign)
            } else {
                Assert.assertFalse(url.url, url.sign)
            }
        })
    }

    class FilteredURL(line: String) {
        var sign = false
        var url: String

        init {
            when (line[0]) {
                '+' -> sign = true
                '-' -> sign = false
                else -> {
                }
            }
            url = line.substring(1)
        }
    }

    companion object {
        private fun readURLFile(reader: Reader): List<FilteredURL> {
            return BufferedReader(reader).lines().map { line: String -> FilteredURL(line) }.toList()
        }
    }
}
