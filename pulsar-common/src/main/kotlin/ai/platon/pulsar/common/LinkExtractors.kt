package ai.platon.pulsar.common

import java.nio.file.Files
import java.nio.file.Path
import java.util.regex.Pattern

open class UrlExtractor {
    companion object {
        val urlPattern = Pattern.compile(
                "(?:^|[\\W])((ht|f)tp(s?):\\/\\/|www\\.)"
                        + "(([\\w\\-]+\\.){1,}?([\\w\\-.~]+\\/?)*"
                        + "[\\p{Alnum}.,%_=?&#\\-+()\\[\\]\\*$~@!:/{};']*)",
                Pattern.CASE_INSENSITIVE or Pattern.MULTILINE or Pattern.DOTALL)
    }

    protected fun extractTo(line: String, urls: MutableSet<String>) {
        val matcher = urlPattern.matcher(line)
        while (matcher.find()) {
            val start = matcher.start(1)
            val end = matcher.end()
            urls.add(line.substring(start, end))
        }
    }
}

internal class ResourceExtractor(val resource: String): UrlExtractor() {
    fun extract(): Set<String> {
        val urls = mutableSetOf<String>()
        ResourceLoader.readAllLines(resource).forEach { extractTo(it, urls) }
        return urls
    }
}

internal class DirectoryExtractor(val baseDir: Path): UrlExtractor() {
    fun extract(): Set<String> {
        val urls = mutableSetOf<String>()
        Files.list(baseDir).filter { Files.isRegularFile(it) }.forEach { path ->
            Files.newBufferedReader(path).forEachLine {
                extractTo(it, urls)
            }
        }
        return urls
    }
}

object LinkExtractors {
    fun fromResource(resource: String) = ResourceExtractor(resource).extract()
    fun fromDirectory(baseDir: Path) = DirectoryExtractor(baseDir).extract()
}
