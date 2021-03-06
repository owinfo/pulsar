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
package ai.platon.pulsar.crawl.common

import ai.platon.pulsar.common.config.MutableConfig
import org.apache.hadoop.conf.Configurable
import org.apache.hadoop.io.DataInputBuffer
import org.apache.hadoop.io.DataOutputBuffer
import org.apache.hadoop.io.Writable
import org.junit.Assert

object WritableTestUtils {
    var defaultConf = MutableConfig()
    /**
     * Utility method for testing writables.
     */
    /**
     * Utility method for testing writables.
     */
    @JvmOverloads
    @Throws(Exception::class)
    fun testWritable(before: Writable, conf: MutableConfig = defaultConf) {
        Assert.assertEquals(before, writeRead(before, conf))
    }

    /**
     * Utility method for testing writables.
     */
    @Throws(Exception::class)
    fun writeRead(before: Writable, conf: MutableConfig): Writable {
        val dob = DataOutputBuffer()
        before.write(dob)
        val dib = DataInputBuffer()
        dib.reset(dob.data, dob.length)
        val after = before.javaClass.newInstance()
        (after as Configurable).conf = conf.unbox()
        after.readFields(dib)
        return after
    }
}