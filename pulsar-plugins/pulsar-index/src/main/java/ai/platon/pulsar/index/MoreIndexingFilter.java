package ai.platon.pulsar.index;

import ai.platon.pulsar.common.DateTimes;
import ai.platon.pulsar.common.HttpHeaders;
import ai.platon.pulsar.common.MimeUtil;
import ai.platon.pulsar.common.config.ImmutableConfig;
import ai.platon.pulsar.crawl.index.IndexDocument;
import ai.platon.pulsar.crawl.index.IndexingFilter;
import ai.platon.pulsar.persist.WebPage;
import org.apache.hadoop.conf.Configuration;
import org.apache.oro.text.regex.PatternMatcher;
import org.apache.oro.text.regex.Perl5Matcher;

import java.time.Instant;

import static ai.platon.pulsar.common.HttpHeaders.CONTENT_LENGTH;

/**
 * Add (or reset) a few metaData properties as respective fields (if they are
 * available), so that they can be accurately used within the search index.
 * <p>
 * 'lastModifed' is indexed to support query by date, 'contentLength' obtains
 * content length from the HTTP header, 'type' field is indexed to support query
 * by type and finally the 'title' field is an attempt to reset the title if a
 * content-disposition hint exists. The logic is that such a presence is
 * indicative that the content provider wants the filename therein to be used as
 * the title.
 * <p>
 * Still need to make content-length searchable!
 *
 * @author John Xing
 */
public class MoreIndexingFilter implements IndexingFilter {

    private ImmutableConfig conf;
    private MimeUtil MIME;
    private PatternMatcher matcher = new Perl5Matcher();

    public MoreIndexingFilter(ImmutableConfig conf) {
        this(new MimeUtil(conf), conf);
    }

    public MoreIndexingFilter(MimeUtil MIME, ImmutableConfig conf) {
        this.MIME = MIME;
        setup(conf);
    }

    /**
     * Utility method for splitting mime type into type and subtype.
     *
     * @param mimeType
     * @return
     */
    static String[] getParts(String mimeType) {
        return mimeType.split("/");
    }

    @Override
    public ImmutableConfig getConf() {
        return conf;
    }

    @Override
    public void setup(ImmutableConfig conf) {
        this.conf = conf;
        MIME = new MimeUtil(conf);
    }

    @Override
    public IndexDocument filter(IndexDocument doc, String url, WebPage page) {
        addTime(doc, page, url);
        addLength(doc, page, url);
        addType(doc, page, url);
        String filename = page.getHeaders().getDispositionFilename();
        if (filename != null) {
            doc.removeField("meta_title");
            doc.add("meta_title", filename);
        }

        return doc;
    }

    // Add time related meta info. Add last-modified if present. Index date as
    // last-modified, or, if that's not present, use fetch time.
    private IndexDocument addTime(IndexDocument doc, WebPage page, String url) {
        Instant time = Instant.EPOCH;

        String lastModified = page.getHeaders().get(HttpHeaders.LAST_MODIFIED);
        if (lastModified != null) {
            // try parse last-modified
            time = DateTimes.parseHttpDateTime(lastModified, Instant.EPOCH); // use as time
        }

        if (time.toEpochMilli() > 0) { // if no last-modified
            time = page.getModifiedTime(); // use Modified time
        }

        // un-stored, indexed and un-tokenized
        if (time.toEpochMilli() > 0) {
            doc.add("header_last_modified", DateTimes.isoInstantFormat(time));
            doc.add("last_modified_s", DateTimes.isoInstantFormat(time));
        }

        return doc;
    }

    // Add Content-Length
    private IndexDocument addLength(IndexDocument doc, WebPage page, String url) {
        CharSequence contentLength = page.getHeaders().get(CONTENT_LENGTH);
        if (contentLength != null) {
            String trimmed = contentLength.toString().trim();
            if (!trimmed.isEmpty())
                doc.add("content_length", trimmed);
        }

        return doc;
    }

    /**
     * <p>
     * Add Content-Type and its primaryType and subType add contentType,
     * primaryType and subType to field "type" as un-stored, indexed and
     * un-tokenized, so that search results can be confined by contentType or its
     * primaryType or its subType.
     * </p>
     * <p>
     * For example, if contentType is application/vnd.ms-powerpoint, search can be
     * done with one of the following qualifiers
     * type:application/vnd.ms-powerpoint type:application type:vnd.ms-powerpoint
     * all case insensitive. The query filter is implemented in
     * </p>
     *
     * @param doc
     * @param page
     * @param url
     * @return
     */
    private IndexDocument addType(IndexDocument doc, WebPage page, String url) {
        String mimeType;
        String contentType = page.getContentType();
        if (contentType.isEmpty()) {
//      contentType = page.getHeaders().get(new Utf8(HttpHeaders.CONTENT_TYPE));
            contentType = page.getHeaders().getOrDefault(HttpHeaders.CONTENT_TYPE, "");
        }

        if (contentType.isEmpty()) {
            // Note by Jerome Charron on 20050415:
            // Content Type not solved by a previous plugin
            // Or unable to solve it... Trying to find it
            // Should be better to use the doc content too
            // (using MimeTypes.getMimeType(byte[], String), but I don't know
            // which field it is?
            // if (MAGIC) {
            // contentType = MIME.getMimeType(url, content);
            // } else {
            // contentType = MIME.getMimeType(url);
            // }
            mimeType = MIME.getMimeType(url);
        } else {
            mimeType = MIME.forName(MimeUtil.cleanMimeType(contentType));
        }

        // Checks if we solved the content-type.
        if (mimeType == null) {
            return doc;
        }

        doc.add("mime_type", mimeType);

        // Check if we need to split the content type in sub parts
        if (conf.getBoolean("moreIndexingFilter.indexMimeTypeParts", true)) {
            String[] parts = getParts(mimeType);

            for (String part : parts) {
                doc.add("mime_type", part);
            }
        }

        // leave this for future improvement
        // MimeTypeParameterList parameterList = mimeType.getParameters()

        return doc;
    }

    public void addIndexBackendOptions(Configuration conf) {
    }

    @Override
    public String toString() {
        return getClass().getSimpleName();
    }
}
