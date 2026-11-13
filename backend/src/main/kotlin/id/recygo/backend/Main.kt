package id.recygo.backend

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.nio.file.Path
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

class ApiServer(private val service: Service,host: String="127.0.0.1",port: Int=8080, private val demo: Boolean=true, private val accounts: Accounts?=null) : AutoCloseable {
    private val gson=Gson()
    private val attempts=mutableMapOf<String, Pair<Long,Int>>()
    private val server=HttpServer.create(InetSocketAddress(host,port),32)
    private val executor=ThreadPoolExecutor(4,8,30,TimeUnit.SECONDS,ArrayBlockingQueue(64),ThreadPoolExecutor.CallerRunsPolicy())
    val port: Int get()=server.address.port
    init { server.executor=executor; server.createContext("/",::handle) }
    fun start()=server.start()
    private fun handle(e: HttpExchange) {
        try {
            val path=e.requestURI.path
            val method=e.requestMethod
            if (path=="/health" && method=="GET") { send(e,200,mapOf("status" to "ok","demo" to demo)); return }
            if (path=="/api/auth/login" && method=="POST") {
                limit(e.remoteAddress.address.hostAddress)
                val input=body(e)
                send(e,200,accounts?.passwordLogin(input)?:service.login(input)); return
            }
            if (path in setOf("/api/auth/start","/api/auth/resend","/api/auth/verify","/api/auth/reset") && method=="POST") {
                val auth=accounts?:throw ApiError(503,"Layanan akun belum dikonfigurasi.")
                if(path.endsWith("start") || path.endsWith("resend"))limit(e.remoteAddress.address.hostAddress)
                val input=body(e,if(path.endsWith("start"))262_144 else 16_384)
                val result=when(path.substringAfterLast('/')) {
                    "start" -> auth.start(input)
                    "resend" -> auth.resend(input)
                    "verify" -> auth.verify(input)
                    else -> auth.reset(input)
                }
                send(e,200,result); return
            }
            val token=e.requestHeaders.getFirst("Authorization")?.takeIf { it.startsWith("Bearer ") }?.substring(7) ?: throw ApiError(401,"Silakan masuk terlebih dahulu.")
            val user=service.authenticate(token)
            val result: Any=when {
                path=="/api/auth/logout" && method=="POST" -> { service.logout(token); mapOf("ok" to true) }
                path=="/api/me" && method=="GET" -> mapOf("user" to user,"demo" to demo)
                path=="/api/profile" && method=="GET" -> mapOf("profile" to (accounts?:throw ApiError(503,"Profil belum dikonfigurasi.")).profile(user))
                path=="/api/profile" && method=="POST" -> mapOf("profile" to (accounts?:throw ApiError(503,"Profil belum dikonfigurasi.")).updateProfile(user,body(e,262_144)))
                path=="/api/catalog" && method=="GET" -> mapOf("materials" to catalog,"slots" to pickupSlots,"demo" to demo)
                path=="/api/orders" && method=="GET" -> mapOf("orders" to service.list(user))
                path=="/api/orders" && method=="POST" -> mapOf("order" to service.create(user,body(e),e.requestHeaders.getFirst("Idempotency-Key")?:""))
                path.matches(Regex("/api/orders/[a-f0-9-]{36}")) && method=="GET" -> mapOf("order" to service.get(user,path.substringAfterLast('/')))
                path.matches(Regex("/api/orders/[a-f0-9-]{36}/[a-z]+")) && method=="POST" -> {
                    val parts=path.split('/'); mapOf("order" to service.transition(user,parts[3],parts[4],body(e)))
                }
                else -> throw ApiError(404,"Endpoint tidak ditemukan.")
            }
            send(e,200,result)
        } catch (ex: ApiError) { send(e,ex.code,mapOf("error" to ex.message)) }
        catch (_: com.google.gson.JsonParseException) { send(e,400,mapOf("error" to "Format JSON tidak valid.")) }
        catch (_: Exception) { System.err.println("Request failed; details omitted to protect personal data."); send(e,500,mapOf("error" to "Server mengalami gangguan. Silakan coba lagi.")) }
        finally { e.close() }
    }
    @Synchronized private fun limit(key: String) {
        val now=System.currentTimeMillis()
        attempts.entries.removeIf { now-it.value.first>60_000 }
        val old=attempts[key]?: (now to 0)
        if (old.second>=15) throw ApiError(429,"Terlalu banyak percobaan. Tunggu satu menit.")
        attempts[key]=old.first to old.second+1
    }
    private fun body(e: HttpExchange,maxBytes: Int=16_384): JsonObject {
        if (e.requestHeaders.getFirst("Content-Type")?.substringBefore(';')?.trim()?.lowercase()!="application/json") throw ApiError(415,"Gunakan application/json.")
        val bytes=e.requestBody.readNBytes(maxBytes+1)
        if (bytes.size>maxBytes) throw ApiError(413,"Permintaan terlalu besar.")
        val value=JsonParser.parseString(bytes.toString(Charsets.UTF_8))
        if (!value.isJsonObject) throw ApiError(400,"JSON harus berupa objek.")
        return value.asJsonObject
    }
    private fun send(e: HttpExchange,status: Int,value: Any) {
        val bytes=gson.toJson(value).toByteArray(Charsets.UTF_8)
        e.responseHeaders.set("Content-Type","application/json; charset=utf-8")
        e.responseHeaders.set("Cache-Control","no-store")
        e.responseHeaders.set("X-Content-Type-Options","nosniff")
        e.sendResponseHeaders(status,bytes.size.toLong()); e.responseBody.use { it.write(bytes) }
    }
    override fun close() { server.stop(0); executor.shutdownNow() }
}

fun main() {
    val env=System.getenv()
    require(env["DEMO_MODE"]=="true") { "This MVP requires explicit DEMO_MODE=true. It is not configured for real dispatch." }
    val store=Store(Path.of(env["DATA_DIR"].orEmpty().ifBlank { "data" },"recygo.sqlite"))
    val service=Service(store)
    service.seed("resident-demo","Risma Yanti","warga@recygo.demo","RESIDENT",env["DEMO_RESIDENT_PASSWORD"]?:error("Set DEMO_RESIDENT_PASSWORD"))
    service.seed("collector-demo","Afif Arizky","pengepul@recygo.demo","COLLECTOR",env["DEMO_COLLECTOR_PASSWORD"]?:error("Set DEMO_COLLECTOR_PASSWORD"))
    val data=Path.of(env["DATA_DIR"].orEmpty().ifBlank { "data" })
    val accounts=Accounts(store,service,env["OTP_SIGNING_KEY"]?:error("Set OTP_SIGNING_KEY using setup-demo.ps1"),DemoOutbox(data.resolve("outbox")))
    accounts.ensureDemoProfile("resident-demo","080000000001")
    accounts.ensureDemoProfile("collector-demo","080000000002")
    val server=ApiServer(service,env["HOST"].orEmpty().ifBlank { "127.0.0.1" },env["PORT"].orEmpty().ifBlank { "8080" }.toInt(),accounts=accounts)
    Runtime.getRuntime().addShutdownHook(Thread { server.close(); store.close() })
    server.start()
    println("Recygo demo API ready on port ${server.port}. No real pickup will be dispatched.")
}
