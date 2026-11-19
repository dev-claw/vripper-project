package me.vripper.utilities

import me.vripper.exception.HtmlProcessorException
import org.htmlcleaner.CleanerProperties
import org.htmlcleaner.DomSerializer
import org.htmlcleaner.HtmlCleaner
import org.w3c.dom.Document
import java.io.InputStream

internal object HtmlUtils {

    @Throws(HtmlProcessorException::class)
    fun clean(htmlContent: InputStream): Document {
        return try {
            htmlContent.use { stream ->
                val clean = HtmlCleaner().clean(stream)
                DomSerializer(CleanerProperties()).createDOM(clean)
            }
        } catch (e: Exception) {
            throw HtmlProcessorException(e)
        }
    }

    @Throws(HtmlProcessorException::class)
    fun clean(htmlContent: String): Document {
        return try {
            val clean = HtmlCleaner().clean(htmlContent)
            DomSerializer(CleanerProperties()).createDOM(clean)

        } catch (e: Exception) {
            throw HtmlProcessorException(e)
        }
    }
}