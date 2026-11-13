package id.recygo.backend

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Path
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import java.util.concurrent.Executors

class WorkflowTest {
    @TempDir lateinit var dir: Path
    private lateinit var store: Store
    private lateinit var service: Service
    private val resident=Account("resident","Risma","r@example.test","RESIDENT")
    private val other=Account("other","Other","o@example.test","RESIDENT")
    private val collector=Account("collector","Afif","c@example.test","COLLECTOR")
    private val secondCollector=Account("second","Second","s@example.test","COLLECTOR")
    private val clock=Clock.fixed(Instant.parse("2026-11-12T06:00:00Z"),ZoneOffset.UTC)
    private lateinit var password: String
    private fun obj(value: String)=JsonParser.parseString(value).asJsonObject
    private fun request()=obj("""{"address":"Jalan Contoh 8 RT 01 RW 04","date":"2026-11-13","slot":"08.00–10.00","notes":"Demo","items":[{"materialId":"plastic","grams":3000},{"materialId":"paper","grams":2000}]}""")
    private fun create(key: String=UUID.randomUUID().toString())=service.create(resident,request(),key)
    private fun action(id: String,action: String,actor: Account=collector,body: JsonObject=JsonObject())=service.transition(actor,id,action,body)
    private fun expect(code: Int,block: ()->Unit) { assertEquals(code,assertThrows(ApiError::class.java,block).code) }

    @BeforeEach fun setup() {
        store=Store(dir.resolve("demo.sqlite")); service=Service(store,clock); password=Security.token()
        listOf(resident,other,collector,secondCollector).forEach { service.seed(it.id,it.name,it.email,it.role,password) }
    }
    @AfterEach fun teardown()=store.close()

    @Test fun `complete workflow preserves verified weights and only awards after confirmation`() {
        val order=create()
        assertEquals("REQUESTED",order.status)
        action(order.id,"accept"); action(order.id,"start"); action(order.id,"arrive")
        val weighed=action(order.id,"weigh",body=obj("""{"items":[{"materialId":"plastic","grams":2500},{"materialId":"paper","grams":1500}]}"""))
        assertEquals("AWAITING_CONFIRMATION",weighed.status); assertEquals(1700,weighed.points)
        assertFalse(service.list(resident).any { it.status=="COMPLETED" })
        val done=action(order.id,"confirm",resident)
        assertEquals("COMPLETED",done.status); assertEquals(4000,done.actualItems.sumOf { it.grams })
        expect(409) { action(order.id,"confirm",resident) }
    }

    @Test fun `owners and collector assignment are enforced`() {
        val p=create()
        expect(404) { service.get(other,p.id) }
        expect(404) { action(p.id,"cancel",other,obj("""{"reason":"Not my order"}""")) }
        expect(403) { action(p.id,"accept",resident) }
        action(p.id,"accept")
        expect(404) { action(p.id,"start",secondCollector) }
        expect(403) { action(p.id,"start",resident) }
        assertTrue(service.list(other).isEmpty()); assertTrue(service.list(secondCollector).isEmpty())
    }

    @Test fun `state cannot be skipped and cancellation stops processing`() {
        val p=create()
        expect(403) { action(p.id,"weigh",body=obj("""{"items":[]}""")) }
        action(p.id,"accept")
        expect(409) { action(p.id,"arrive") }
        action(p.id,"cancel",resident,obj("""{"reason":"Jadwal perlu diganti"}"""))
        expect(409) { action(p.id,"start") }
        assertEquals("CANCELLED",service.get(resident,p.id).status)
    }

    @Test fun `idempotency survives retries and rejects a different payload`() {
        val key=UUID.randomUUID().toString()
        val first=create(key); assertEquals(first.id,create(key).id); assertEquals(1,service.list(resident).size)
        val changed=request().apply { addProperty("address","Jalan Berbeda 9 RT 02") }
        expect(409) { service.create(resident,changed,key) }
    }

    @Test fun `concurrent collectors cannot claim the same request`() {
        val p=create(); val pool=Executors.newFixedThreadPool(2)
        try {
            val results=pool.invokeAll(listOf(collector,secondCollector).map { actor -> java.util.concurrent.Callable { runCatching { action(p.id,"accept",actor) }.isSuccess } }).map { it.get() }
            assertEquals(1,results.count { it }); assertEquals("SCHEDULED",service.get(resident,p.id).status)
        } finally { pool.shutdownNow() }
    }

    @Test fun `invalid weight date and material data are rejected`() {
        val badItems=listOf("""[{"materialId":"plastic","grams":-1}]""","""[{"materialId":"plastic","grams":1.5}]""","""[{"materialId":"battery","grams":1000}]""","""[{"materialId":"plastic","grams":1000},{"materialId":"plastic","grams":1000}]""")
        badItems.forEach { items -> expect(400) { service.create(resident,request().apply { add("items",JsonParser.parseString(items)) },UUID.randomUUID().toString()) } }
        expect(400) { service.create(resident,request().apply { addProperty("date","2026-11-11") },UUID.randomUUID().toString()) }
        expect(400) { service.create(resident,request().apply { addProperty("slot","midnight") },UUID.randomUUID().toString()) }
    }

    @Test fun `weighing must cover exact order types and contain a positive total`() {
        val p=create(); action(p.id,"accept"); action(p.id,"start"); action(p.id,"arrive")
        expect(400) { action(p.id,"weigh",body=obj("""{"items":[{"materialId":"plastic","grams":1000}]}""")) }
        expect(400) { action(p.id,"weigh",body=obj("""{"items":[{"materialId":"plastic","grams":0},{"materialId":"paper","grams":0}]}""")) }
        assertEquals("ARRIVED",service.get(resident,p.id).status)
    }

    @Test fun `database and session survive reopen and logout revokes token`() {
        val p=create(); val login=service.login(JsonObject().apply { addProperty("email",resident.email); addProperty("password",password) })
        val token=login["token"] as String
        store.close(); store=Store(dir.resolve("demo.sqlite")); service=Service(store,clock)
        assertEquals(resident.id,service.authenticate(token).id); assertEquals(p.id,service.list(resident).single().id)
        assertEquals(p.id,createRequestAgain(p,token).id)
        service.logout(token); expect(401) { service.authenticate(token) }
    }
    private fun createRequestAgain(p: Pickup,token: String): Pickup=service.get(service.authenticate(token),p.id)

    @Test fun `HTTP boundary enforces auth JSON body limits and credentials`() {
        ApiServer(service,port=0).use { server ->
            server.start(); val client=HttpClient.newHttpClient()
            fun call(path: String,body: String?=null,contentType: String="application/json",token: String?=null): HttpResponse<String> {
                val b=HttpRequest.newBuilder(URI("http://127.0.0.1:${server.port}$path"))
                if(token!=null)b.header("Authorization","Bearer $token")
                if(body!=null)b.header("Content-Type",contentType).POST(HttpRequest.BodyPublishers.ofString(body)) else b.GET()
                return client.send(b.build(),HttpResponse.BodyHandlers.ofString())
            }
            assertEquals(200,call("/health").statusCode()); assertEquals(401,call("/api/orders").statusCode())
            assertEquals(415,call("/api/auth/login","{}","text/plain").statusCode())
            assertEquals(400,call("/api/auth/login","[]").statusCode()); assertEquals(413,call("/api/auth/login"," ".repeat(17000)).statusCode())
            assertEquals(401,call("/api/auth/login",JsonObject().apply { addProperty("email",resident.email); addProperty("password",Security.token()) }.toString()).statusCode())
            val login=call("/api/auth/login",JsonObject().apply { addProperty("email",resident.email); addProperty("password",password) }.toString())
            assertEquals(200,login.statusCode()); val token=obj(login.body()).get("token").asString
            assertEquals(200,call("/api/orders",token=token).statusCode()); assertEquals("no-store",login.headers().firstValue("Cache-Control").orElse(""))
        }
    }
}
