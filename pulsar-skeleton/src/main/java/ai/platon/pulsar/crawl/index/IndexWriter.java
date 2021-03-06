package ai.platon.pulsar.crawl.index;

import ai.platon.pulsar.common.config.ImmutableConfig;
import ai.platon.pulsar.common.config.Parameterized;
import ai.platon.pulsar.crawl.common.JobInitialized;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * Created by vincent on 16-8-1.
 */
public interface IndexWriter extends Parameterized, JobInitialized, AutoCloseable {

    Logger LOG = LoggerFactory.getLogger(IndexWriter.class);

    default String getName() {
        return getClass().getSimpleName();
    }

    default boolean isActive() {
        return true;
    }

    void open(ImmutableConfig conf) throws IOException;

    void open(String indexerUrl) throws IOException;

    void write(IndexDocument doc) throws IOException;

    void delete(String key) throws IOException;

    void update(IndexDocument doc) throws IOException;

    void commit() throws IOException;

    @Override
    void close() throws IOException;

    /**
     * Returns a String describing the IndexWriter instance and the specific
     * parameters it can take
     */
    String describe();
}
