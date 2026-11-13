package id.recygo.backend

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.nio.file.Files
import java.nio.file.Path
import java.security.SecureRandom
import java.sql.Connection
import java.time.Clock
import java.time.Instant
import java.util.Base64
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

fun interface OtpDelivery {
    fun deliver(id: String,email: String,purpose: String,code: String,expires: Long)
}

/** An explicitly local mailbox. No email/WhatsApp service is contacted. */
class DemoOutbox(private val directory: Path): OtpDelivery {
    override fun deliver(id: String,email: String,purpose: String,code: String,expires: Long) {
        require(id.matches(Regex("[a-f0-9-]{36}")))
        Files.createDirectories(directory)
        val json=JsonObject().apply {
            addProperty("challengeId",id); addProperty("email",email); addProperty("purpose",purpose)
            addProperty("code",code); addProperty("expiresAt",Instant.ofEpochMilli(expires).toString())
            addProperty("delivery","LOCAL_DEMO_ONLY")
        }
        Files.writeString(directory.resolve("$id.json"),Gson().toJson(json))
    }
}

/** Encrypt profile/pending-registration data; purpose-separated MACs protect OTP/indexes. */
private class AccountCrypto(secret: String) {
    private val key=secret.toByteArray(Charsets.UTF_8)
    private val random=SecureRandom()
    init { require(key.size>=32) { "OTP_SIGNING_KEY must contain at least 32 bytes." } }
    fun mac(purpose: String,value: String): String {
        val mac=Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(key,"HmacSHA256")) }
        return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal("$purpose\u0000$value".toByteArray(Charsets.UTF_8)))
    }
    private fun encryptionKey()=SecretKeySpec(Base64.getUrlDecoder().decode(mac("derive","recygo-profile-v1")),"AES")
    fun encrypt(value: String): String {
        val iv=ByteArray(12).also(random::nextBytes)
        val cipher=Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE,encryptionKey(),GCMParameterSpec(128,iv)) }
        return Base64.getEncoder().encodeToString(iv+cipher.doFinal(value.toByteArray(Charsets.UTF_8)))
    }
    fun decrypt(value: String): String {
        val bytes=Base64.getDecoder().decode(value)
        val cipher=Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE,encryptionKey(),GCMParameterSpec(128,bytes.copyOfRange(0,12))) }
        return String(cipher.doFinal(bytes.copyOfRange(12,bytes.size)),Charsets.UTF_8)
    }
}

class Accounts(
    private val store: Store,
    private val service: Service,
    secret: String,
    private val delivery: OtpDelivery,
    private val clock: Clock=Clock.systemUTC()
) {
    private val crypto=AccountCrypto(secret)
    private val random=SecureRandom()
    private val gson=Gson()
    private fun json(value: String)=JsonParser.parseString(value).asJsonObject
    private fun account(c: Connection,email: String): Account? = c.prepareStatement("SELECT * FROM users WHERE email=?").use { s ->
        s.setString(1,email); s.executeQuery().use { r -> if(r.next())Account(r.getString("id"),r.getString("name"),r.getString("email"),r.getString("role")) else null }
    }
    private fun email(value: String): String {
        val email=value.trim().lowercase()
        if(email.length>254 || !email.matches(Regex("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")))throw ApiError(400,"Alamat email tidak valid.")
        return email
    }
    private fun password(value: String): String {
        if(value.length !in 8..128 || !value.any(Char::isLetter) || !value.any(Char::isDigit))throw ApiError(400,"Password minimal 8 karakter, berisi huruf dan angka.")
        return value
    }

    fun passwordLogin(body: JsonObject): Map<String,Any> {
        if(body.has("email"))return service.login(body)
        val identifier=body.text("identifier",3,254)
        val resolved=if('@' in identifier)email(identifier) else store.transaction { c ->
            c.prepareStatement("SELECT u.email FROM profiles p JOIN users u ON p.user_id=u.id WHERE p.phone_hash=?").use { s ->
                s.setString(1,crypto.mac("phone",normalizePhone(identifier)))
                s.executeQuery().use { r -> if(r.next())r.getString(1) else throw ApiError(401,"Nomor atau password tidak sesuai.") }
            }
        }
        return service.login(JsonObject().apply { addProperty("email",resolved); addProperty("password",body.text("password",1,256,false)) })
    }

    fun ensureDemoProfile(id: String,phone: String) {
        store.transaction { c ->
            val exists=c.prepareStatement("SELECT user_id FROM profiles WHERE user_id=?").use { it.setString(1,id); it.executeQuery().use { r -> r.next() } }
            if(!exists)saveProfile(c,id,JsonObject().apply {
                addProperty("phone",normalizePhone(phone)); addProperty("address",""); addProperty("rt",""); addProperty("rw","")
                addProperty("vehicle","motor"); addProperty("area","Wilayah demo"); addProperty("avatarBase64",""); addProperty("identityImageBase64","")
            },true)
        }
    }

    fun profile(user: Account): JsonObject=store.transaction { c -> readProfile(c,user.id).apply {
        remove("nikHash")
        addProperty("name",user.name); addProperty("email",user.email); addProperty("role",user.role); addProperty("verification","DEMO_ONLY")
    } }

    fun updateProfile(user: Account,body: JsonObject): JsonObject {
        store.transaction { c ->
            val current=readProfile(c,user.id)
            listOf("phone","address","rt","rw","area","vehicle","avatarBase64","latitude","longitude").forEach { if(body.has(it))current.add(it,body.get(it)) }
            validateProfile(current,user.role,false)
            val name=if(body.has("name"))body.text("name",2,80) else user.name
            saveProfile(c,user.id,current,false)
            c.prepareStatement("UPDATE users SET name=? WHERE id=?").use { it.setString(1,name); it.setString(2,user.id); it.executeUpdate() }
        }
        val updated=store.transaction { c -> account(c,user.email)?:throw ApiError(404,"Akun tidak ditemukan.") }
        return profile(updated)
    }

    private fun readProfile(c: Connection,id: String): JsonObject=c.prepareStatement("SELECT payload FROM profiles WHERE user_id=?").use { s ->
        s.setString(1,id); s.executeQuery().use { r -> if(r.next())json(crypto.decrypt(r.getString(1))) else JsonObject().apply { addProperty("phone",""); addProperty("address","") } }
    }
    private fun saveProfile(c: Connection,id: String,profile: JsonObject,insert: Boolean) {
        val phone=normalizePhone(profile.text("phone",8,20)); profile.addProperty("phone",phone)
        val duplicate=c.prepareStatement("SELECT user_id FROM profiles WHERE phone_hash=? AND user_id<>?").use { s ->
            s.setString(1,crypto.mac("phone",phone)); s.setString(2,id); s.executeQuery().use { it.next() }
        }
        if(duplicate)throw ApiError(409,"Nomor tersebut sudah digunakan akun lain.")
        val sql=if(insert)"INSERT INTO profiles(phone_hash,payload,user_id) VALUES(?,?,?)" else "UPDATE profiles SET phone_hash=?,payload=? WHERE user_id=?"
        c.prepareStatement(sql).use { s -> s.setString(1,crypto.mac("phone",phone)); s.setString(2,crypto.encrypt(profile.toString())); s.setString(3,id); s.executeUpdate() }
    }

    private fun validateProfile(p: JsonObject,role: String,registration: Boolean) {
        p.addProperty("phone",normalizePhone(p.text("phone",8,20)))
        p.text("address",if(registration)10 else 0,400)
        for(key in listOf("rt","rw")) {
            val value=p.optionalText(key,3)
            if(value.isNotEmpty() && !value.matches(Regex("\\d{1,3}")))throw ApiError(400,"RT/RW harus berupa angka.")
            p.addProperty(key,value)
        }
        p.addProperty("area",p.optionalText("area",120))
        val vehicle=p.optionalText("vehicle",30)
        if(role=="COLLECTOR" && vehicle !in setOf("motor","pickup","truck"))throw ApiError(400,"Pilih jenis kendaraan.")
        p.addProperty("vehicle",vehicle)
        val latitude=p.optionalText("latitude",32)
        val longitude=p.optionalText("longitude",32)
        if(latitude.isNotEmpty() || longitude.isNotEmpty()) {
            val lat=latitude.toDoubleOrNull(); val lon=longitude.toDoubleOrNull()
            if(lat==null || lon==null || !lat.isFinite() || !lon.isFinite() || lat !in -90.0..90.0 || lon !in -180.0..180.0)throw ApiError(400,"Koordinat lokasi tidak valid.")
        }
        p.addProperty("latitude",latitude); p.addProperty("longitude",longitude)
        for(key in listOf("avatarBase64","identityImageBase64")) {
            val value=p.optionalText(key,120_000)
            if(value.isNotEmpty()) {
                val bytes=try { Base64.getDecoder().decode(value) } catch (_: Exception) { throw ApiError(400,"Format gambar tidak valid.") }
                val jpeg=bytes.size>=3 && bytes[0]==0xFF.toByte() && bytes[1]==0xD8.toByte() && bytes[2]==0xFF.toByte()
                val png=bytes.size>=8 && bytes.copyOfRange(0,8).contentEquals(byteArrayOf(0x89.toByte(),0x50,0x4E,0x47,0x0D,0x0A,0x1A,0x0A))
                if(bytes.size>90_000 || (!jpeg && !png))throw ApiError(400,"Gunakan gambar JPEG/PNG maksimal 90 KB.")
                val validDimensions=runCatching {
                    javax.imageio.ImageIO.createImageInputStream(java.io.ByteArrayInputStream(bytes)).use { stream ->
                        val readers=javax.imageio.ImageIO.getImageReaders(stream)
                        if(!readers.hasNext())false else {
                            val reader=readers.next()
                            try { reader.input=stream; reader.getWidth(0) in 1..2048 && reader.getHeight(0) in 1..2048 } finally { reader.dispose() }
                        }
                    }
                }.getOrDefault(false)
                if(!validDimensions)throw ApiError(400,"Gambar tidak valid atau dimensinya melebihi 2048 piksel.")
            }
            p.addProperty(key,value)
        }
    }

    private fun registration(body: JsonObject): JsonObject {
        val name=body.text("name",2,80)
        val email=email(body.text("email",3,254))
        val role=body.text("role",1,20)
        if(role !in setOf("RESIDENT","COLLECTOR"))throw ApiError(400,"Peran tidak valid.")
        val pwd=password(body.text("password",8,128,false))
        if(!body.has("consent") || !body.get("consent").isJsonPrimitive || !body.getAsJsonPrimitive("consent").isBoolean || !body.get("consent").asBoolean)throw ApiError(400,"Persetujuan penggunaan data demo diperlukan.")
        val profile=JsonObject()
        for(key in listOf("phone","address","rt","rw","area","vehicle","avatarBase64","identityImageBase64","latitude","longitude"))if(body.has(key))profile.add(key,body.get(key))
        validateProfile(profile,role,true)
        if(role=="COLLECTOR") {
            val nik=body.text("nik",16,16)
            if(!nik.all(Char::isDigit))throw ApiError(400,"NIK contoh harus berisi 16 digit.")
            profile.addProperty("nikLast4",nik.takeLast(4)); profile.addProperty("nikHash",crypto.mac("nik",nik))
            val types=body.get("acceptedMaterials")
            if(types==null || !types.isJsonArray || types.asJsonArray.size() !in 1..catalog.size)throw ApiError(400,"Pilih jenis sampah yang diterima.")
            val ids=types.asJsonArray.map { if(it.isJsonPrimitive && it.asJsonPrimitive.isString)it.asString else "" }
            if(ids.distinct().size!=ids.size || ids.any { id -> catalog.none { it.id==id } })throw ApiError(400,"Jenis sampah belum didukung demo.")
            profile.add("acceptedMaterials",types)
        }
        profile.addProperty("consentAt",Instant.now(clock).toString())
        val salt=Security.token()
        return JsonObject().apply {
            addProperty("name",name); addProperty("email",email); addProperty("role",role)
            addProperty("salt",salt); addProperty("passwordHash",Security.password(pwd,salt)); add("profile",profile)
        }
    }

    fun start(body: JsonObject): Map<String,Any> {
        val purpose=body.text("purpose",1,20)
        if(purpose !in setOf("REGISTER","RESET"))throw ApiError(400,"OTP hanya untuk pendaftaran dan lupa password.")
        val registration=if(purpose=="REGISTER")registration(body) else null
        val email=registration?.get("email")?.asString?:email(body.text("email",3,254))
        return store.transaction { c ->
            c.prepareStatement("DELETE FROM auth_challenges WHERE expires<?").use { it.setLong(1,clock.millis()-86_400_000); it.executeUpdate() }
            if(registration!=null) {
                if(account(c,email)!=null)throw ApiError(409,"Email sudah digunakan. Silakan masuk.")
                val phoneHash=crypto.mac("phone",registration.getAsJsonObject("profile").get("phone").asString)
                val duplicate=c.prepareStatement("SELECT user_id FROM profiles WHERE phone_hash=?").use { it.setString(1,phoneHash); it.executeQuery().use { r -> r.next() } }
                if(duplicate)throw ApiError(409,"Nomor sudah digunakan. Silakan masuk.")
            }
            val existing=c.prepareStatement("SELECT last_sent FROM auth_challenges WHERE email=? ORDER BY last_sent DESC LIMIT 1").use { it.setString(1,email); it.executeQuery().use { r -> if(r.next())r.getLong(1) else 0L } }
            if(clock.millis()-existing<45_000)throw ApiError(429,"Tunggu 45 detik sebelum meminta kode lagi.")
            val id=UUID.randomUUID().toString(); val code="%06d".format(random.nextInt(1_000_000)); val expires=clock.millis()+300_000
            val eligible=registration!=null || account(c,email)!=null
            val payload=JsonObject().apply { addProperty("eligible",eligible); if(registration!=null)add("registration",registration) }
            c.prepareStatement("INSERT INTO auth_challenges(id,purpose,email,code_hash,expires,last_sent,payload) VALUES(?,?,?,?,?,?,?)").use { s ->
                s.setString(1,id); s.setString(2,purpose); s.setString(3,email); s.setString(4,crypto.mac("otp","$id:$code")); s.setLong(5,expires); s.setLong(6,clock.millis()); s.setString(7,crypto.encrypt(payload.toString())); s.executeUpdate()
            }
            if(eligible)delivery.deliver(id,email,purpose,code,expires)
            mapOf("challengeId" to id,"expiresIn" to 300,"resendAfter" to 45,"delivery" to "LOCAL_DEMO_ONLY","email" to email)
        }
    }

    fun resend(body: JsonObject): Map<String,Any> = store.transaction { c ->
        val id=body.text("challengeId",36,36)
        c.prepareStatement("SELECT * FROM auth_challenges WHERE id=?").use { s ->
            s.setString(1,id); s.executeQuery().use { r ->
                if(!r.next() || r.getInt("used")!=0 || r.getInt("verified")!=0)throw ApiError(400,"Permintaan kode tidak berlaku.")
                if(r.getInt("resends")>=5)throw ApiError(429,"Batas kirim ulang tercapai. Mulai ulang setelah beberapa saat.")
                if(clock.millis()-r.getLong("last_sent")<45_000)throw ApiError(429,"Tunggu 45 detik sebelum mengirim ulang.")
                val purpose=r.getString("purpose"); val email=r.getString("email"); val payload=json(crypto.decrypt(r.getString("payload")))
                if(purpose !in setOf("REGISTER","RESET"))throw ApiError(400,"Tujuan OTP tidak berlaku.")
                val code="%06d".format(random.nextInt(1_000_000)); val expires=clock.millis()+300_000
                c.prepareStatement("UPDATE auth_challenges SET code_hash=?,expires=?,last_sent=?,attempts=0,resends=resends+1 WHERE id=?").use { u ->
                    u.setString(1,crypto.mac("otp","$id:$code")); u.setLong(2,expires); u.setLong(3,clock.millis()); u.setString(4,id); u.executeUpdate()
                }
                if(payload.get("eligible").asBoolean)delivery.deliver(id,email,purpose,code,expires)
                mapOf("challengeId" to id,"expiresIn" to 300,"resendAfter" to 45,"delivery" to "LOCAL_DEMO_ONLY","email" to email)
            }
        }
    }

    fun verify(body: JsonObject): Map<String,Any> {
        val id=body.text("challengeId",36,36); val code=body.text("code",6,6)
        if(!code.all(Char::isDigit))throw ApiError(400,"Masukkan enam digit kode.")
        val outcome: Result<Map<String,Any>> = store.transaction { c ->
            c.prepareStatement("SELECT * FROM auth_challenges WHERE id=?").use { s ->
                s.setString(1,id); s.executeQuery().use { r ->
                    if(!r.next() || r.getInt("used")!=0 || r.getInt("verified")!=0 || r.getLong("expires")<=clock.millis())throw ApiError(400,"Kode tidak berlaku atau sudah kedaluwarsa.")
                    if(r.getInt("attempts")>=5)throw ApiError(429,"Terlalu banyak kode salah. Minta kode baru.")
                    if(!Security.matches(r.getString("code_hash"),crypto.mac("otp","$id:$code"))) {
                        c.prepareStatement("UPDATE auth_challenges SET attempts=attempts+1 WHERE id=?").use { it.setString(1,id); it.executeUpdate() }
                        return@transaction Result.failure(ApiError(400,"Kode OTP tidak sesuai."))
                    }
                    val email=r.getString("email"); val purpose=r.getString("purpose"); val payload=json(crypto.decrypt(r.getString("payload")))
                    if(purpose !in setOf("REGISTER","RESET"))throw ApiError(400,"Tujuan OTP tidak berlaku.")
                    if(!payload.get("eligible").asBoolean)throw ApiError(400,"Kode tidak berlaku.")
                    if(purpose=="RESET") {
                        val grant=Security.token()
                        c.prepareStatement("UPDATE auth_challenges SET verified=1,grant_hash=?,grant_expires=?,code_hash='',payload='' WHERE id=?").use { u ->
                            u.setString(1,crypto.mac("reset",grant)); u.setLong(2,clock.millis()+300_000); u.setString(3,id); u.executeUpdate()
                        }
                        Result.success(mapOf("resetToken" to grant,"expiresIn" to 300))
                    } else {
                        val reg=payload.getAsJsonObject("registration")
                        if(account(c,email)!=null)throw ApiError(409,"Email sudah digunakan. Silakan masuk.")
                        val user=Account(UUID.randomUUID().toString(),reg.get("name").asString,email,reg.get("role").asString)
                        c.prepareStatement("INSERT INTO users VALUES(?,?,?,?,?,?)").use { u ->
                            listOf(user.id,user.name,user.email,user.role,reg.get("salt").asString,reg.get("passwordHash").asString).forEachIndexed { i,v -> u.setString(i+1,v) }; u.executeUpdate()
                        }
                        saveProfile(c,user.id,reg.getAsJsonObject("profile"),true)
                        c.prepareStatement("UPDATE auth_challenges SET verified=1,used=1,code_hash='',payload='' WHERE id=?").use { it.setString(1,id); it.executeUpdate() }
                        Result.success(service.issueSession(c,user))
                    }
                }
            }
        }
        return outcome.getOrThrow()
    }

    fun reset(body: JsonObject): Map<String,Boolean> {
        val token=body.text("resetToken",20,200); val pwd=password(body.text("password",8,128,false))
        store.transaction { c ->
            val email=c.prepareStatement("SELECT email FROM auth_challenges WHERE grant_hash=? AND purpose='RESET' AND verified=1 AND used=0 AND grant_expires>?").use { s ->
                s.setString(1,crypto.mac("reset",token)); s.setLong(2,clock.millis()); s.executeQuery().use { if(it.next())it.getString(1) else throw ApiError(400,"Izin reset sudah kedaluwarsa. Minta OTP baru.") }
            }
            val user=account(c,email)?:throw ApiError(400,"Akun tidak ditemukan.")
            val salt=Security.token()
            c.prepareStatement("UPDATE users SET salt=?,hash=? WHERE id=?").use { it.setString(1,salt); it.setString(2,Security.password(pwd,salt)); it.setString(3,user.id); it.executeUpdate() }
            c.prepareStatement("DELETE FROM sessions WHERE user_id=?").use { it.setString(1,user.id); it.executeUpdate() }
            c.prepareStatement("UPDATE auth_challenges SET used=1,grant_hash=NULL,code_hash='',payload='' WHERE email=?").use { it.setString(1,email); it.executeUpdate() }
        }
        return mapOf("ok" to true)
    }
}

fun normalizePhone(value: String): String {
    val raw=value.trim().replace(" ","").replace("-","")
    if(!raw.matches(Regex("\\+?\\d{9,15}")))throw ApiError(400,"Nomor WhatsApp tidak valid.")
    val digits=raw.removePrefix("+")
    val canonical=when { digits.startsWith("62")->"+$digits"; digits.startsWith("0")->"+62${digits.drop(1)}"; else->"+62$digits" }
    if(canonical.length !in 11..16)throw ApiError(400,"Nomor WhatsApp tidak valid.")
    return canonical
}
