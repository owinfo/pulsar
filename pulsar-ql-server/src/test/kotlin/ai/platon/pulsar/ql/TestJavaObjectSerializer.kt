package ai.platon.pulsar.ql

import ai.platon.pulsar.common.sql.ResultSetFormatter
import ai.platon.pulsar.dom.nodes.node.ext.uniqueName
import ai.platon.pulsar.ql.types.ValueDom
import org.h2.engine.SysProperties
import org.h2.util.JdbcUtils
import org.jsoup.Jsoup
import org.junit.Test
import java.sql.ResultSet
import java.sql.Types
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TestJavaObjectSerializer : TestBase() {

    @Test
    fun testLocalSerialization() {
        val serializer = ai.platon.pulsar.ql.PulsarObjectSerializer()

        val baseUri = "http://example.com/"
        val doc = Jsoup.parseBodyFragment("<div>Hello World</div>", baseUri)
        val baseDom = ValueDom.get(doc.body().selectFirst("div"))

        val bytes = serializer.serialize(baseDom)
        val obj = serializer.deserialize(bytes)
        assertTrue(obj is ValueDom)
        val dom = obj as ValueDom

        // println(dom.outHtml)

        val deserializeBytes = serializer.serialize(dom)
        val deserializeObject = serializer.deserialize(deserializeBytes)
        assertTrue(deserializeObject is ValueDom)
        val dom2 = deserializeObject as ValueDom
        assertTrue { dom == dom2 }

        assertTrue { obj !== deserializeObject }
        assertTrue { dom.element.baseUri() == dom2.element.baseUri() }
        // assertTrue { dom.element.ownerDocument() != dom2.element.ownerDocument() }
    }

    @Test
    fun testNetworkSerialization() {
        val conn = remoteDB.getConnection("testNetworkSerialization")
        val stat = conn.createStatement()
        stat.execute("create table t(id int, val other)")

        val baseUri = "http://example.com/"
        val doc = Jsoup.parseBodyFragment("<div>Hello World</div>", baseUri)
        val baseDom = ValueDom.get(doc.body().selectFirst("div"))

        val ins = conn.prepareStatement("insert into t(id, val) values(?, ?)")
        ins.setInt(1, 2)
        ins.setObject(2, baseDom, Types.JAVA_OBJECT)
        assertEquals(1, ins.executeUpdate())

        val stat2 = conn.createStatement()
        // val rs = s.executeQuery("select val from t")
        val rs = stat2.executeQuery("select val, id from t")

        assertTrue(rs.next())
        assertTrue(rs.getObject(1) is ValueDom)
        assertTrue((rs.getObject(1) as ValueDom).element.toString().contains("Hello World"))
    }

    @Test
    fun testNetworkSerialization2() {
        val conn = remoteDB.getConnection("testNetworkSerialization2")
        val stat = conn.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_READ_ONLY)

        val expr = "sibling > 20 && char > 40 && char < 100 && width > 200"
        val sql = """SELECT
            DOM, DOM_FIRST_HREF(DOM), TOP, LEFT, WIDTH, HEIGHT, CHAR, IMG, A, SIBLING, DOM_TEXT(DOM)
            FROM LOAD_AND_GET_FEATURES('$productIndexUrl', '*:expr($expr)')
            ORDER BY SIBLING DESC, CHAR DESC LIMIT 500"""
        val rs = stat.executeQuery(sql)

        assertTrue(rs.next())
        assertTrue(rs.getObject(1) is ValueDom)
        val dom = rs.getObject(1) as ValueDom
        assertTrue { dom.element.uniqueName.contains("nfItem") }

        rs.beforeFirst()
        println(ResultSetFormatter(rs).toString())

        println(sql)
        println(SysProperties.serializeJavaObject)
        println(JdbcUtils.serializer.javaClass.name)

        println(dom.element.uniqueName)
    }

    @Test
    fun testNetworkSerialization3() {
        val conn = remoteDB.getConnection("testNetworkSerialization3")
        val stat = conn.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_READ_ONLY)

        val expr = "sibling > 20 && char > 40 && char < 100 && width > 200"
        val sql = """SELECT
            DOM_FIRST_HREF(DOM), DOM_TEXT(DOM)
            FROM LOAD_OUT_PAGES('$productIndexUrl', '*:expr($expr)')
            LIMIT 30"""
        val rs = stat.executeQuery(sql)

        println(ResultSetFormatter(rs).toString())

        println(sql)
        println(SysProperties.serializeJavaObject)
        println(JdbcUtils.serializer.javaClass.name)
    }

    @Test
    fun testNetworkSerialization4() {
        val sql = """SELECT DOM_DOC_TITLE(DOM) FROM DOM_SELECT(DOM_LOAD('$productIndexUrl'), '.welcome');"""

        IntRange(1, 50).toList().parallelStream().forEach {
            runNetworkSerialization(sql)
        }
    }

    private fun runNetworkSerialization(sql: String) {
        val conn = remoteDB.getRandomConnection()
        val stat = conn.createStatement()

        val rs = stat.executeQuery(sql)

        assertTrue(rs.next())
        val value = rs.getString(1)
        println(value)
        assertTrue(value.length > 1)
        assertTrue(SysProperties.serializeJavaObject)
        assertEquals("ai.platon.pulsar.ql.PulsarObjectSerializer", JdbcUtils.serializer.javaClass.name)
    }
}
