package app.cairn.source.corosmcp

import app.cairn.core.datastore.StoredTokens
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class McpClientTest {
    private lateinit var server: MockWebServer
    private val store = MemoryStore()
    private lateinit var oauth: OAuthClient
    private lateinit var mcp: McpClient
    private lateinit var base: String

    @Before fun setUp() {
        server = MockWebServer().also { it.start() }
        base = server.url("").toString().trimEnd('/')
        store.save(StoredTokens("cid", base, "AT1", Long.MAX_VALUE / 2, "RT1"))
        oauth = OAuthClient(OkHttpClient(), store)
        mcp = McpClient(OkHttpClient(), oauth, "$base/mcp")
    }
    @After fun tearDown() = server.shutdown()

    private fun json(body: String) = MockResponse().setHeader("Content-Type", "application/json").setBody(body)
    private val init = """{"jsonrpc":"2.0","id":1,"result":{"protocolVersion":"2025-06-18"}}"""

    @Test fun listToolsJsonAndSessionHeader() = runBlocking<Unit> {
        server.enqueue(json(init).setHeader("Mcp-Session-Id", "S1"))
        server.enqueue(MockResponse().setResponseCode(202))
        server.enqueue(json("""{"jsonrpc":"2.0","id":2,"result":{"tools":[{"name":"a"},{"name":"b"}]}}"""))
        assertEquals(listOf("a", "b"), mcp.listTools())
        val r1 = server.takeRequest(); assertEquals("Bearer AT1", r1.getHeader("Authorization"))
        assertTrue(r1.body.readUtf8().contains("\"initialize\""))
        val r2 = server.takeRequest(); assertEquals("S1", r2.getHeader("Mcp-Session-Id"))
        assertTrue(r2.body.readUtf8().contains("notifications/initialized"))
        server.takeRequest()
    }

    @Test fun sseBody() = runBlocking<Unit> {
        server.enqueue(json(init))
        server.enqueue(MockResponse().setResponseCode(202))
        server.enqueue(
            MockResponse().setHeader("Content-Type", "text/event-stream").setBody(
                "event: message\ndata: {\"jsonrpc\":\"2.0\",\"method\":\"notifications/progress\"}\n\n" +
                    "event: message\ndata: {\"jsonrpc\":\"2.0\",\"id\":2,\n" +
                    "data: \"result\":{\"content\":[{\"type\":\"text\",\"text\":\"hi\"}]}}\n\n",
            ),
        )
        val r = mcp.callTool("x")
        assertEquals("hi", r["content"]!!.jsonArray[0].jsonObject["text"]!!.jsonPrimitive.content)
    }

    @Test fun refreshOnceOn401ThenRetry() = runBlocking<Unit> {
        server.enqueue(MockResponse().setResponseCode(401))                       // initialize rejected
        server.enqueue(json("""{"issuer":"$base","authorization_endpoint":"$base/a","token_endpoint":"$base/oauth2/token","code_challenge_methods_supported":["S256"]}"""))
        server.enqueue(json("""{"access_token":"AT2","refresh_token":"RT2","expires_in":3600}"""))
        server.enqueue(json(init))                                                // retried initialize
        server.enqueue(MockResponse().setResponseCode(202))
        server.enqueue(json("""{"jsonrpc":"2.0","id":2,"result":{"tools":[]}}"""))
        assertEquals(emptyList<String>(), mcp.listTools())
        server.takeRequest(); server.takeRequest(); server.takeRequest()
        assertEquals("Bearer AT2", server.takeRequest().getHeader("Authorization"))
    }

    @Test fun secondConsecutive401Fails() {
        server.enqueue(MockResponse().setResponseCode(401))
        server.enqueue(json("""{"issuer":"$base","authorization_endpoint":"$base/a","token_endpoint":"$base/oauth2/token","code_challenge_methods_supported":["S256"]}"""))
        server.enqueue(json("""{"access_token":"AT2","expires_in":3600}"""))
        server.enqueue(MockResponse().setResponseCode(401))
        try { runBlocking { mcp.listTools() }; fail() } catch (e: CorosException) { assertFalse(e.message!!.contains("AT")) }
    }

    @Test fun parseSseMultiline() {
        val ev = McpParsing.parseSse("data: a\ndata: b\n\ndata: c\n\n")
        assertEquals(listOf("a\nb", "c"), ev)
    }
}

class RedactorTest {
    @Test fun masksIdentifyingFields() {
        val out = Redactor.redactText("Contact me@example.com at 12.345678 id 123456789012\nname: Alice")
        assertFalse(out.contains("example.com")); assertFalse(out.contains("12.345678")); assertFalse(out.contains("123456789012")); assertFalse(out.contains("Alice"))
    }
    @Test fun masksJsonKeysAndRoundsTime() {
        val o = kotlinx.serialization.json.Json.parseToJsonElement("""{"nickname":"Bob","userId":"u1","startTime":1700003599000,"hrv":55,"lat":1.5}""")
        val s = Redactor.redactElement(null, o).toString()
        assertFalse(s.contains("Bob")); assertFalse(s.contains("u1")); assertTrue(s.contains("\"hrv\":55")); assertTrue(s.contains("1700002800000"))
    }
}
