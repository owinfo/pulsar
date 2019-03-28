package ai.platon.pulsar.ql.h2.udfs

import ai.platon.pulsar.ql.annotation.UDFGroup
import ai.platon.pulsar.ql.annotation.UDFunction
import ai.platon.pulsar.ql.h2.H2SessionFactory
import ai.platon.pulsar.persist.WebPageFormatter
import org.h2.engine.Session
import org.h2.ext.pulsar.annotation.H2Context

@UDFGroup(namespace = "META")
object MetadataFunctions {

    @UDFunction
    @JvmStatic
    operator fun get(@H2Context h2session: Session, url: String): String {
        val page = H2SessionFactory.getSession(h2session.id).load(url)
        return WebPageFormatter(page).toString()
    }
}
