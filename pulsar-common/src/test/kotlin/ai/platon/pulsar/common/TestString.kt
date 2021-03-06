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
package ai.platon.pulsar.common

import ai.platon.pulsar.common.ResourceLoader.readAllLines
import ai.platon.pulsar.common.config.ImmutableConfig
import org.apache.commons.io.FileUtils
import org.apache.commons.lang3.StringUtils
import org.junit.Assert
import org.junit.Test
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.nio.file.Files
import java.util.*
import java.util.regex.Matcher
import java.util.regex.Pattern
import java.util.stream.Collectors
import java.util.stream.IntStream
import java.util.stream.Stream
import kotlin.test.assertTrue

/**
 * Unit tests for StringUtil methods.
 */
class TestString {
    var conf = ImmutableConfig()
    @Test
    fun testValueOf() {
        val i = 18760506
        Assert.assertEquals("18760506", i.toString())
    }

    @Test
    fun testSubstring() {
        var s = "a,b,c,d"
        s = StringUtils.substringAfter(s, ",")
        Assert.assertEquals("b,c,d", s)
        s = "a\nb\nc\nd\ne\nf\ng\n"
        // assume the avarage lenght of a link is 100 characters
        val pos = StringUtils.indexOf(s, '\n'.toInt(), s.length / 2)
        s = s.substring(pos + 1)
        Assert.assertEquals("e\nf\ng\n", s)
    }

    @Test
    fun testToHexString() {
        val buffer = ByteBuffer.wrap("".toByteArray())
        Assert.assertEquals("", Strings.toHexString(buffer))
    }

    @Test
    fun testPadding() {
        val strings = arrayOf(
                "1.\thttp://v.ifeng.com/\t凤凰视频首页-最具媒体价值的视频门户-凤凰网",
                "2.\thttp://fo.ifeng.com/\t佛教首页_佛教频道__凤凰网",
                "3.\thttp://www.ifeng.com/\t凤凰网",
                "24.\thttp://fashion.ifeng.com/health/\t凤凰健康_关注全球华人健康"
        )
        IntStream.range(0, strings.size).forEach { i: Int -> strings[i] = StringUtils.rightPad(strings[i], 60) }
        Stream.of(*strings).forEach { x: String? -> println(x) }
    }

    @Test
    fun testRegex() {
        var text = "http://aitxt.com/book/12313413874"
        var regex = "http://(.*)aitxt.com(.*)"
        Assert.assertTrue(text.matches(regex.toRegex()))
        text = "http://aitxt.com/book/12313413874"
        regex = ".*"
        Assert.assertTrue(text.matches(regex.toRegex()))
        regex = "aitxt"
        Assert.assertFalse(text.matches(regex.toRegex()))
        text = "abcde"
        regex = "[a-zA-Z](?!\\d+).+"
        Assert.assertTrue(text.matches(regex.toRegex()))
        text = "ab12212cde"
        regex = "ab\\d+.+"
        Assert.assertTrue(text.matches(regex.toRegex()))
        regex = "(>|<|>=|<=)*([*\\d+]),(>|<|>=|<=)*([*\\d+]),(>|<|>=|<=)*([*\\d+]),(>|<|>=|<=)*([*\\d+])"
        Assert.assertTrue(">1,2,3,4".matches(regex.toRegex()))
        Assert.assertTrue(">=1,*,3,4".matches(regex.toRegex()))
        Assert.assertTrue("1,2,*,4".matches(regex.toRegex()))
        Assert.assertTrue("1,*,*,*".matches(regex.toRegex()))
        var PATTERN_RECT = Pattern.compile(regex, Pattern.CASE_INSENSITIVE)
        text = ">=1,*,3,4"
        Assert.assertTrue(PATTERN_RECT.matcher(text).matches())
        val matcher: Matcher
        //        matcher = PATTERN_RECT.matcher(text);
//        if (matcher.find()) {
//          System.out.println(matcher.group(0));
//          System.out.println(matcher.group(1) + matcher.group(2));
//          System.out.println(matcher.group(3) + matcher.group(4));
//          System.out.println(matcher.group(5) + matcher.group(6));
//          System.out.println(matcher.group(7) + matcher.group(8));
//        }
        text = "*,*,230,420"
        val REGEX_RECT = "([+-])?(\\*|\\d+),([+-])?(\\*|\\d+),([+-])?(\\*|\\d+),([+-])?(\\*|\\d+)"
        PATTERN_RECT = Pattern.compile(REGEX_RECT, Pattern.CASE_INSENSITIVE)
        matcher = PATTERN_RECT.matcher(text)
        if (matcher.find()) {
            println(matcher.group(0))
            println(matcher.group(1) + matcher.group(2))
            println(matcher.group(3) + matcher.group(4))
            println(matcher.group(5) + matcher.group(6))
            println(matcher.group(7) + matcher.group(8))
        }
    }

    @Test
    fun testReplaceCharsetInHtml() {
        val lines = readAllLines("data/html-charsets.txt")
        for (line in lines) {
            val l = Strings.replaceCharsetInHtml(line, "UTF-8")
            assertTrue(l.contains("UTF-8"))
        }
    }

    @Test
    fun testAvailableCharsets() {
        var charsets = Charset.availableCharsets().values.stream()
                .map { obj: Charset -> obj.name() }
                .collect(Collectors.joining("|"))
        var charsetPattern = Pattern.compile(charsets, Pattern.CASE_INSENSITIVE)
        Assert.assertEquals(Charset.availableCharsets().size.toLong(), StringUtils.countMatches(charsets, "|") + 1.toLong())
        Assert.assertTrue(charsetPattern.matcher("gb2312").matches())
        Assert.assertTrue(charsetPattern.matcher("UTF-8").matches())
        Assert.assertTrue(charsetPattern.matcher("windows-1257").matches())
        charsets = ("UTF-8|GB2312|GB18030|GBK|Big5|ISO-8859-1"
                + "|windows-1250|windows-1251|windows-1252|windows-1253|windows-1254|windows-1257"
                + "|UTF-8")
        charsets = charsets.replace("UTF-8\\|?".toRegex(), "")
        charsetPattern = Pattern.compile(charsets, Pattern.CASE_INSENSITIVE)
        Assert.assertTrue(charsetPattern.matcher("gb2312").matches())
        Assert.assertTrue(charsetPattern.matcher("windows-1257").matches())
        Assert.assertFalse(charsetPattern.matcher("UTF-8").matches())
        Assert.assertFalse(charsetPattern.matcher("nonsense").matches())
    }

    @Test
    fun testPricePattern() {
        val text = "￥799.00 (降价通知)"
        // text = text.replaceAll("¥|,|'", "");
// System.out.println(text);
        val matcher = Strings.PRICE_PATTERN.matcher(text)
        var count = 0
        while (matcher.find()) {
            count++
            // System.out.println("Found Price : " + count + " : " + matcher.start() + " - " + matcher.end() + ", " + matcher.group());
        }
        Assert.assertTrue(count > 0)
    }

    @Test
    fun testParseVersion() { // assertTrue(Math.abs(StringUtil.tryParseDouble("0.2.0") - 0.2) < 0.0000001);
//    System.out.println(Lists.newArrayList("0.2.0".split("\\.")));
//    System.out.println("0.2.0".split("\\.").length);
        Assert.assertEquals("0.2.0".split("\\.").toTypedArray().size.toLong(), 3)
    }

    @Test
    fun testtrimNonCJKChar() {
        val text = "天王表 正品热卖 机械表 全自动 男士商务气派钢带手表GS5733T/D尊贵大气 个性表盘  "
        Assert.assertEquals(text.trim { it <= ' ' }, "天王表 正品热卖 机械表 全自动 男士商务气派钢带手表GS5733T/D尊贵大气 个性表盘  ")
        Assert.assertEquals(Strings.trimNonCJKChar(text), "天王表 正品热卖 机械表 全自动 男士商务气派钢带手表GS5733T/D尊贵大气 个性表盘")
    }

    @Test
    fun testStripNonChar() {
        val texts = arrayOf(
                "天王表 正品热卖 机械表 全自动 男士商务气派钢带手表GS5733T/D尊贵大气 个性表盘  ",
                "天王表 正品热卖 \uE004主要职责：  OK"
        )
        for (text in texts) {
            println(Strings.stripNonCJKChar(text, Strings.DEFAULT_KEEP_CHARS))
        }
        //    assertEquals(text.trim(), "天王表 正品热卖 机械表 全自动 男士商务气派钢带手表GS5733T/D尊贵大气 个性表盘  ");
//    assertEquals(StringUtil.stripNonCJKChar(text), "天王表 正品热卖 机械表 全自动 男士商务气派钢带手表GS5733T/D尊贵大气 个性表盘");
    }

    @Test
    fun testIsChinese() {
        val texts = arrayOf(
                "关注全球华人健康"
        )
        for (text in texts) {
            Assert.assertTrue(Strings.isChinese(text))
        }
        val noChineseTexts = arrayOf(
                "1234534",
                "alphabetical",
                "1b关注全球华人健康",
                "a关注全球华人健康"
        )
        for (text in noChineseTexts) { // TODO: noChineseTexts assertion failed
// assertFalse(text, StringUtil.isChinese(text));
        }
        val mainlyChineseTexts = arrayOf(
                "1234关注全球华人健康关注全球华人健康",
                "alpha关注全球华人健康关注全球华人健康",
                "1b关注全球华人健康",
                "a关注全球华人健康"
        )
        for (text in mainlyChineseTexts) {
            println(Strings.countChinese(text).toString() + "/" + text.length
                    + "=" + Strings.countChinese(text) * 1.0 / text.length + "\t" + text)
            Assert.assertTrue(text, Strings.isMainlyChinese(text, 0.6))
        }
    }

    @Test
    fun testStringFormat() {
        println(String.format("%1$,20d", -3123))
        println(String.format("%1$9d", -31))
        println(String.format("%1$-9d", -31))
        println(String.format("%1$(9d", -31))
        println(String.format("%1$#9x", 5689))
    }

    @Test
    fun testSplit() {
        var s = "TestStringUtil"
        val r = s.split("(?=\\p{Upper})").toTypedArray()
        Assert.assertArrayEquals(arrayOf("Test", "String", "Util"), r)
        Assert.assertEquals("Test.String.Util", StringUtils.join(r, "."))
        var url = "http://t.tt/\t-i 1m"
        var parts = StringUtils.split(url, "\t")
        Assert.assertEquals("http://t.tt/", parts[0])
        Assert.assertEquals("-i 1m", parts[1])
        url = "http://t.tt/"
        parts = StringUtils.split(url, "\t")
        Assert.assertEquals("http://t.tt/", parts[0])
        s = "ld,-o,-s,-w:hello,-a:b,-c"
        val options = StringUtils.replaceChars(s, ":,", Strings.padding[2]).split(" ").toTypedArray()
        println(StringUtils.join(options, " "))
    }

    @Test
    fun testCsslize() {
        var s = "-TestStringUtil"
        Assert.assertEquals("-test-string-util", Strings.csslize(s))
        s = "TestStringUtil-a"
        Assert.assertEquals("test-string-util-a", Strings.csslize(s))
        s = "TestStringUtil-"
        Assert.assertEquals("test-string-util-", Strings.csslize(s))
    }

    @Test
    fun testCsslize2() {
        val cases: MutableMap<String, String> = HashMap()
        cases["nav_top"] = "nav-top"
        cases["mainMenu"] = "main-menu"
        cases["image-detail"] = "image-detail"
        cases["image      detail"] = "image-detail"
        for ((key, value) in cases) {
            Assert.assertEquals(value, Strings.csslize(key))
        }
    }

    @Test
    fun testHumanize() {
        val s = "TestStringUtil"
        Assert.assertEquals("test string util", Strings.humanize(s))
        Assert.assertEquals("test.string.util", Strings.humanize(s, "."))
    }

    @Test
    fun testParseKvs() {
        val kvs: MutableMap<String, String> = HashMap()
        kvs["a"] = "1"
        kvs["b"] = "2"
        kvs["c"] = "3"
        Assert.assertEquals(kvs, Strings.parseKvs("a=1 b=2 c=3"))
        Assert.assertEquals(kvs, Strings.parseKvs("a:1\nb:2\tc:3", ":"))
        Assert.assertTrue(Strings.parseKvs("abcd1234*&#$").isEmpty())
        println(Strings.parseKvs("a=1 b=2 c=3 c=4  d e f"))
        println(SParser.wrap("a=1 b=2 c=3,c=4 d e f").getKvs("="))
        println(SParser.wrap("a=1 b=2 c=3 c=4,d= e f").getKvs("="))
        println(SParser.wrap("").kvs)
        val kvs2 = arrayOf(
                "a=1 b=2 c=3,c=4 d e f",
                "a=1 b=2 c=3 c=4 d= e f",
                "a=1,b=2,c=3,c=4,d= e f.3     ",
                "   a=1     b=2\tc=3 c=4 d= e =3     ")
        for (i in kvs2.indices) {
            val kv = SParser.wrap(kvs2[i]).getKvs("=")
            Assert.assertEquals(i.toString() + "th [" + kvs2[i] + "]", "{a=1, b=2, c=4}", kv.toString())
        }
    }

    @Test
    fun testParseOptions() {
        val kvs: MutableMap<String, String> = HashMap()
        kvs["-a"] = "1"
        kvs["-b"] = "2"
        kvs["-c"] = "3"
        kvs["-isX"] = "true"
        // assertEquals(kvs, StringUtil.parseOptions("-a 1 -b 2 -c 3 -isX"));
        Assert.assertTrue(Strings.parseKvs("abcd1234*&#$").isEmpty())
    }

    @Test
    fun testGetUnslashedLines() {
        var s = "http://www.sxrb.com/sxxww/\t--fetch-interval=1s --fetch-priority=1010 \\\n" +
                "    --follow-dom=:root --follow-url=.+ --follow-anchor=8,40 \\\n" +
                "    --entity=#content --entity-fields=title:#title,content:#content,publish_time:#publish_time \\\n" +
                "    --collection=#comments --collection-item=.comment --collection-item-fields=publish_time:.comment_publish_time,author:.author,content:.content\n" +
                "http://news.qq.com/\t--fetch-interval=1h --entity=#content\n" +
                "http://news.youth.cn/\t--fetch-interval=1h --entity=#content\\\n" +
                "    --collection=#comments\n" +
                "http://news.163.com/\t--fetch-interval=1h --entity=#content" +
                "\n"
        var lines = Strings.getUnslashedLines(s)
        Assert.assertEquals(4, lines.size.toLong())
        s = "http://sz.sxrb.com/sxxww/dspd/szpd/bwch/\n" +
                "http://sz.sxrb.com/sxxww/dspd/szpd/fcjjjc/\n" +
                "http://sz.sxrb.com/sxxww/dspd/szpd/hydt/"
        lines = Strings.getUnslashedLines(s)
        Assert.assertEquals(3, lines.size.toLong())
    }

    @Test
    @Throws(IOException::class)
    fun testLoadSlashedLines() {
        var seeds = "@data/lines-with-slashes.txt"
        if (seeds.startsWith("@")) {
            seeds = java.lang.String.join("\n", readAllLines(seeds.substring(1)))
        }
        val seedFile = File.createTempFile("seed", ".txt")
        val unslashedLines = Strings.getUnslashedLines(seeds)
        //        for (int i = 0; i < unslashedLines.size(); i++) {
//            System.out.println(i + "\t" + unslashedLines.get(i));
//        }
        Assert.assertEquals(111, unslashedLines.size.toLong())
        FileUtils.writeLines(seedFile, unslashedLines)
        Assert.assertEquals(111, Files.readAllLines(seedFile.toPath()).size.toLong())
        //    System.out.println(StringUtil.getUnslashedLines(seeds).size());
//    System.out.println(seedFile.getAbsolutePath());
    }

    @Test
    fun testGetFirstInteger() {
        val s = "-hello world 999 i love you 520 forever"
        Assert.assertEquals(999, Strings.getFirstInteger(s, -1).toLong())
    }

    @Test
    fun testGetFirstFloatNumber() {
        val s = "-hello world 999.00.0 i love you 520.0 forever"
        Assert.assertEquals(999.00, Strings.getFirstFloatNumber(s, Float.MIN_VALUE).toDouble(), 0.1)
    }
}
