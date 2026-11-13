package id.recygo.backend

import com.google.gson.Gson
import com.google.gson.JsonObject
import java.sql.Connection
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

class Service(private val store: Store, private val clock: Clock = Clock.systemUTC()) {
    private val gson = Gson()
    private fun now() = Instant.now(clock).toString()

    fun seed(id: String, name: String, email: String, role: String, password: String) {
        require(password.length >= 12) { "Demo account passwords must contain at least 12 characters." }
        store.transaction { c ->
            val exists = c.prepareStatement("SELECT id FROM users WHERE id=?").use { s -> s.setString(1,id); s.executeQuery().use { it.next() } }
            if (!exists) {
                val salt = Security.token()
                c.prepareStatement("INSERT INTO users VALUES(?,?,?,?,?,?)").use { s ->
                    listOf(id,name,email,role,salt,Security.password(password,salt)).forEachIndexed { i,v -> s.setString(i+1,v) }; s.executeUpdate()
                }
            }
        }
    }

    fun login(body: JsonObject): Map<String, Any> {
        val email = body.text("email", 3, 254).lowercase()
        val password = body.text("password", 1, 256, trim = false)
        return store.transaction { c ->
            c.prepareStatement("SELECT * FROM users WHERE email=?").use { s ->
                s.setString(1,email)
                s.executeQuery().use { r ->
                    if (!r.next()) throw ApiError(401, "Email atau password tidak sesuai.")
                    val expected = Security.password(password,r.getString("salt"))
                    if (!Security.matches(expected,r.getString("hash"))) throw ApiError(401,"Email atau password tidak sesuai.")
                    val account = Account(r.getString("id"),r.getString("name"),r.getString("email"),r.getString("role"))
                    issueSession(c,account)
                }
            }
        }
    }

    internal fun issueSession(c: Connection,account: Account): Map<String,Any> {
        val token=Security.token()
        c.prepareStatement("DELETE FROM sessions WHERE expires<=?").use { it.setLong(1,clock.millis()); it.executeUpdate() }
        c.prepareStatement("INSERT INTO sessions VALUES(?,?,?)").use { session ->
            session.setString(1,Security.digest(token)); session.setString(2,account.id); session.setLong(3,clock.millis()+86_400_000); session.executeUpdate()
        }
        return mapOf("token" to token,"user" to account)
    }

    fun authenticate(token: String): Account = store.transaction { c ->
        c.prepareStatement("SELECT u.* FROM sessions s JOIN users u ON s.user_id=u.id WHERE s.hash=? AND s.expires>?").use { s ->
            s.setString(1,Security.digest(token)); s.setLong(2,clock.millis())
            s.executeQuery().use { r -> if (r.next()) Account(r.getString("id"),r.getString("name"),r.getString("email"),r.getString("role")) else throw ApiError(401,"Sesi berakhir. Silakan masuk lagi.") }
        }
    }

    fun logout(token: String) = store.transaction { c -> c.prepareStatement("DELETE FROM sessions WHERE hash=?").use { it.setString(1,Security.digest(token)); it.executeUpdate() }; Unit }

    private fun visible(user: Account, p: Pickup) = if (user.role == "RESIDENT") p.ownerId == user.id else p.collectorId == user.id || (p.status == "REQUESTED" && p.collectorId == null)
    fun list(user: Account): List<Pickup> = store.transaction { c -> store.orders(c).filter { visible(user,it) } }
    fun get(user: Account,id: String): Pickup = store.transaction { c -> store.find(c,id).also { if (!visible(user,it)) throw ApiError(404,"Pesanan tidak ditemukan.") } }

    fun create(user: Account, body: JsonObject, key: String): Pickup {
        if (user.role != "RESIDENT") throw ApiError(403,"Hanya warga dapat mengajukan penjemputan.")
        if (!key.matches(Regex("[a-zA-Z0-9-]{16,80}"))) throw ApiError(400,"Kunci pengiriman tidak valid.")
        val address = body.text("address",10,400)
        val date = body.text("date",10,10)
        val parsedDate = try { LocalDate.parse(date) } catch (_: Exception) { throw ApiError(400,"Tanggal tidak valid.") }
        val slot = body.text("slot",1,40)
        if (slot !in pickupSlots) throw ApiError(400,"Pilih waktu penjemputan yang tersedia.")
        val notes = body.optionalText("notes",500)
        val items = items(body,false)
        val fingerprint = Security.digest(gson.toJson(listOf(address,date,slot,notes,items)))
        return store.transaction { c ->
            c.prepareStatement("SELECT payload,fingerprint FROM orders WHERE owner_id=? AND request_key=?").use { s ->
                s.setString(1,user.id); s.setString(2,key)
                s.executeQuery().use { r ->
                    if (r.next()) {
                        if (r.getString(2) != fingerprint) throw ApiError(409,"Permintaan sebelumnya berbeda. Periksa pesanan sebelum mengirim ulang.")
                        return@transaction gson.fromJson(r.getString(1),Pickup::class.java)
                    }
                }
            }
            val today = LocalDate.now(clock.withZone(ZoneId.of("Asia/Jakarta")))
            if (parsedDate.isBefore(today) || parsedDate.isAfter(today.plusDays(30))) throw ApiError(400,"Pilih tanggal hari ini sampai 30 hari ke depan.")
            val order = Pickup(UUID.randomUUID().toString(),user.id,user.name,address,date,slot,notes,items,now(),now())
            c.prepareStatement("INSERT INTO orders VALUES(?,?,?,?,?,?,?)").use { s ->
                s.setString(1,order.id); s.setString(2,user.id); s.setString(3,null); s.setString(4,order.status); s.setString(5,gson.toJson(order)); s.setString(6,key); s.setString(7,fingerprint); s.executeUpdate()
            }
            order
        }
    }

    fun transition(user: Account,id: String,action: String,body: JsonObject): Pickup = store.transaction { c ->
        val p = store.find(c,id)
        if (!visible(user,p)) throw ApiError(404,"Pesanan tidak ditemukan.")
        fun resident() { if (user.role != "RESIDENT" || p.ownerId != user.id) throw ApiError(403,"Tindakan ini hanya untuk pemilik pesanan.") }
        fun collector(allowUnassigned: Boolean = false) { if (user.role != "COLLECTOR" || !(p.collectorId == user.id || (allowUnassigned && p.collectorId == null))) throw ApiError(403,"Tindakan ini hanya untuk pengepul yang ditugaskan.") }
        fun state(vararg allowed: String) { if (p.status !in allowed) throw ApiError(409,"Status pesanan berubah. Muat ulang pesanan.") }
        when (action) {
            "accept" -> { collector(true); state("REQUESTED"); p.collectorId=user.id; p.collectorName=user.name; p.status="SCHEDULED" }
            "reject" -> { collector(true); state("REQUESTED"); p.reason=body.text("reason",5,300); p.collectorId=user.id; p.collectorName=user.name; p.status="REJECTED" }
            "cancel" -> { resident(); state("REQUESTED","SCHEDULED"); p.reason=body.text("reason",5,300); p.status="CANCELLED" }
            "start" -> { collector(); state("SCHEDULED"); p.status="ON_THE_WAY" }
            "arrive" -> { collector(); state("ON_THE_WAY"); p.status="ARRIVED" }
            "weigh" -> {
                collector(); state("ARRIVED")
                val actual=items(body,true)
                if (actual.map { it.materialId }.toSet() != p.items.map { it.materialId }.toSet()) throw ApiError(400,"Timbang setiap jenis sampah dalam pesanan.")
                p.actualItems=actual; p.points=actual.sumOf { item -> item.grams * catalog.first { it.id==item.materialId }.pointsPerKg / 1000 }; p.status="AWAITING_CONFIRMATION"
            }
            "confirm" -> { resident(); state("AWAITING_CONFIRMATION"); p.status="COMPLETED" }
            else -> throw ApiError(404,"Tindakan tidak ditemukan.")
        }
        p.updatedAt=now(); store.save(c,p); p
    }

    private fun items(body: JsonObject, allowZero: Boolean): List<WasteItem> {
        val values=body.get("items")
        if (values == null || !values.isJsonArray || values.asJsonArray.size() !in 1..catalog.size) throw ApiError(400,"Pilih satu sampai empat jenis sampah.")
        val items=values.asJsonArray.map { value ->
            if (!value.isJsonObject) throw ApiError(400,"Jenis sampah tidak valid.")
            val obj=value.asJsonObject
            val id=obj.text("materialId",1,30)
            if (catalog.none { it.id==id }) throw ApiError(400,"Jenis sampah belum didukung.")
            val grams=obj.get("grams")
            if (grams == null || !grams.isJsonPrimitive || !grams.asJsonPrimitive.isNumber) throw ApiError(400,"Berat harus berupa angka gram.")
            val integer=try { grams.asBigDecimal.intValueExact() } catch (_: Exception) { throw ApiError(400,"Berat harus berupa bilangan bulat gram.") }
            if (integer !in (if (allowZero) 0 else 100)..100_000) throw ApiError(400,"Berat per jenis harus ${if (allowZero) "0" else "0,1"}–100 kg.")
            WasteItem(id,integer)
        }
        if (items.map { it.materialId }.distinct().size != items.size || items.sumOf { it.grams } !in 100..200_000) throw ApiError(400,"Jenis tidak boleh berulang dan total berat harus 0,1–200 kg.")
        return items.sortedBy { it.materialId }
    }
}

fun JsonObject.text(key: String,min: Int,max: Int,trim: Boolean = true): String {
    val value=get(key)
    if (value == null || !value.isJsonPrimitive || !value.asJsonPrimitive.isString) throw ApiError(400,"Isian $key wajib berupa teks.")
    val text=value.asString.let { if(trim) it.trim() else it }
    if (text.length !in min..max || text.any { it.code<32 && it!='\n' }) throw ApiError(400,"Panjang atau isi $key tidak valid.")
    return text
}
fun JsonObject.optionalText(key: String,max: Int): String = if (!has(key)) "" else text(key,0,max)
