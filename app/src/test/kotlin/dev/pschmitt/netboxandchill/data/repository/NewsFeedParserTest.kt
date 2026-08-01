package dev.pschmitt.netboxandchill.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NewsFeedParserTest {
    @Test
    fun `parses and cleans rss article fields`() {
        val items =
            parseNewsFeed(
                """
                <rss><channel><item>
                  <guid>article-1</guid>
                  <title>NetBox &amp; Friends</title>
                  <link>https://netboxlabs.com/blog/article-1/</link>
                  <pubDate>Wed, 01 Jan 2025 12:00:00 +0000</pubDate>
                  <description><![CDATA[<p>Useful &amp; practical.</p>]]></description>
                </item></channel></rss>
                """
            )

        assertEquals(1, items.size)
        assertEquals("article-1", items.single().guid)
        assertEquals("NetBox & Friends", items.single().title)
        assertEquals("Useful & practical.", items.single().summary)
        assertTrue(items.single().publishedAt > 0)
    }

    @Test
    fun `uses namespaced encoded content and link as fallback guid`() {
        val item =
            parseNewsFeed(
                """
                <rss><channel><item>
                  <title>Second</title>
                  <link>https://example.test/second</link>
                  <content:encoded><![CDATA[<p>Body</p>]]></content:encoded>
                </item></channel></rss>
                """
            ).single()

        assertEquals("https://example.test/second", item.guid)
        assertEquals("Body", item.summary)
    }
}
