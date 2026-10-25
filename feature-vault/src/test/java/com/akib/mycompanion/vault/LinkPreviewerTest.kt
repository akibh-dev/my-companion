package com.akib.mycompanion.vault

import com.akib.mycompanion.vault.data.LinkPreview
import com.akib.mycompanion.vault.data.LinkPreviewer
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Test

class LinkPreviewerTest {

    private val server = MockWebServer().apply { start() }

    @After
    fun tearDown() = server.shutdown()

    @Test
    fun `open graph tags win over the page title and description`() {
        val html = """
            <title>Plain title</title>
            <meta name="description" content="Plain description">
            <meta property="og:title" content="OG title">
            <meta property="og:description" content="OG description">
            <meta property="og:site_name" content="Example News">
        """.trimIndent()

        assertThat(LinkPreviewer.parse(html, "example.com"))
            .isEqualTo(LinkPreview("OG title", "OG description", "Example News"))
    }

    @Test
    fun `without open graph the title tag and description are used, and the site is the host`() {
        val html = """<html><head><TITLE lang="en"> Tea &amp; Biscuits </TITLE>
            <META NAME='description' CONTENT='Best in &quot;Dhaka&quot;'></head></html>"""

        assertThat(LinkPreviewer.parse(html, "food.example"))
            .isEqualTo(LinkPreview("Tea & Biscuits", "Best in \"Dhaka\"", "food.example"))
    }

    @Test
    fun `a description alone is enough, a page with neither is no preview`() {
        assertThat(LinkPreviewer.parse("""<meta name="description" content="Only this">""", "x.y"))
            .isEqualTo(LinkPreview("", "Only this", "x.y"))
        assertThat(LinkPreviewer.parse("<html><body>Hello</body></html>", "x.y")).isNull()
        assertThat(LinkPreviewer.parse("<title>   </title>", "x.y")).isNull()
    }

    @Test
    fun `long titles and descriptions are cut`() {
        val html = "<title>${"t".repeat(500)}</title><meta name=\"description\" content=\"${"d".repeat(500)}\">"

        val preview = LinkPreviewer.parse(html, "x.y")!!

        assertThat(preview.title).hasLength(200)
        assertThat(preview.description).hasLength(300)
    }

    @Test
    fun `entities in titles are turned back into characters`() {
        val preview = LinkPreviewer.parse("<title>a &lt;b&gt; &#39;c&#39;</title>", "x.y")!!

        assertThat(preview.title).isEqualTo("a <b> 'c'")
    }

    @Test
    fun `an error page gives no preview`() = runTest {
        server.enqueue(MockResponse().setResponseCode(404).setBody("<title>Not found</title>"))

        assertThat(LinkPreviewer(OkHttpClient()).fetch(server.url("/missing").toString())).isNull()
    }

    @Test
    fun `an unreachable or broken link gives no preview instead of failing`() = runTest {
        val previewer = LinkPreviewer(OkHttpClient())
        val gone = MockWebServer().apply { start() }
        val closed = gone.url("/").toString()
        gone.shutdown()

        assertThat(previewer.fetch(closed)).isNull()
        assertThat(previewer.fetch("not a url")).isNull()
    }

    @Test
    fun `a page is fetched once, as a browser asking for html`() = runTest {
        server.enqueue(MockResponse().setBody("<title>Hi</title>"))

        val preview = LinkPreviewer(OkHttpClient()).fetch(server.url("/page").toString())

        val request = server.takeRequest()
        assertThat(preview!!.title).isEqualTo("Hi")
        assertThat(request.getHeader("Accept")).isEqualTo("text/html")
        assertThat(request.getHeader("User-Agent")).isEqualTo("Mozilla/5.0")
        assertThat(server.requestCount).isEqualTo(1)
    }
}
