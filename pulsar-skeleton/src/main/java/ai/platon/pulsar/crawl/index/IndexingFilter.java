package ai.platon.pulsar.crawl.index;

/**
 * Created by vincent on 16-8-1.
 */

import ai.platon.pulsar.common.config.Configurable;
import ai.platon.pulsar.common.config.Parameterized;
import ai.platon.pulsar.common.config.ReloadableParameterized;
import ai.platon.pulsar.crawl.common.JobInitialized;
import ai.platon.pulsar.persist.WebPage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// AppConstants imports

/**
 * Extension point for indexing. Permits one to add metadata to the indexed
 * fields. All plugins found which implement this extension point are run
 * sequentially on the parse.
 */
public interface IndexingFilter extends Parameterized, JobInitialized, Configurable {

    Logger LOG = LoggerFactory.getLogger(IndexingFilter.class);

    /**
     * Adds fields or otherwise modifies the document that will be indexed for a
     * parse. Unwanted documents can be removed from indexing by returning a null
     * value.
     *
     * @param doc  document instance for collecting fields
     * @param url  page url
     * @param page
     * @return modified (or a new) document instance, or null (meaning the
     * document should be discarded)
     * @throws IndexingException
     */
    IndexDocument filter(IndexDocument doc, String url, WebPage page) throws IndexingException;
}
