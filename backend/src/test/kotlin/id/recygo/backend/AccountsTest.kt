package id.recygo.backend

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

class AccountsTest {
    @TempDir lateinit var dir: Path
    private lateinit var store: Store
    private lateinit var service: Service
    private lateinit var accounts: Accounts
    private val codes=mutableMapOf<String,String>()
    private val clock=MutableClock()
    private var pwd=""
    private fun input(vararg fields: Pair<String,String>)=JsonObject().apply { fields.forEach { (k,v)->addProperty(k,v) } }
    private fun register(role: String="RESIDENT")=input("name" to "Pengguna Demo","email" to "new@example.test","password" to pwd,"role" to role,"phone" to "080000000003","address" to "Jalan Contoh Demo Nomor 12","rt" to "01","rw" to "04","vehicle" to "motor","nik" to "0000000000000003").apply {
        addProperty("purpose","REGISTER"); addProperty("consent",true); add("acceptedMaterials",JsonArray().apply { add("plastic") })
    }
    private fun begin(body: JsonObject=register())=accounts.start(body)["challengeId"] as String
    private fun verify(id: String,code: String=codes.getValue(id))=accounts.verify(input("challengeId" to id,"code" to code))
    private fun fail(code: Int,block: ()->Unit) { assertEquals(code,assertThrows(ApiError::class.java,block).code) }
    @BeforeEach fun setup() {
        store=Store(dir.resolve("accounts.sqlite")); service=Service(store,clock); pwd=Security.token()+"7a"
        accounts=Accounts(store,service,Security.token(),OtpDelivery { id,_,_,code,_->codes[id]=code },clock)
    }
    @AfterEach fun close()=store.close()

    @Test fun `registration creates no account before OTP and verifies only once`() {
        val id=begin()
        fail(401) { service.login(input("email" to "new@example.test","password" to pwd)) }
        val session=verify(id); val user=service.authenticate(session["token"] as String)
        assertEquals("RESIDENT",user.role)
        assertEquals("+6280000000003",accounts.profile(user).get("phone").asString)
        assertEquals(user.id,(accounts.passwordLogin(input("identifier" to "080000000003","password" to pwd))["user"] as Account).id)
        fail(400) { verify(id) }
    }

    @Test fun `wrong attempts commit and lock challenge after five guesses`() {
        val id=begin(); val wrong="%06d".format((codes.getValue(id).toInt()+1)%1_000_000)
        repeat(5) { fail(400) { verify(id,wrong) } }
        fail(429) { verify(id) }
    }

    @Test fun `OTP cannot bypass password login including legacy challenges`() {
        fail(400) { begin(input("purpose" to "LOGIN","email" to "new@example.test")) }
        assertTrue(codes.isEmpty())
        val id=begin()
        store.transaction { c -> c.prepareStatement("UPDATE auth_challenges SET purpose='LOGIN' WHERE id=?").use { it.setString(1,id); it.executeUpdate() } }
        fail(400) { verify(id) }
        clock.advance(45_001)
        fail(400) { accounts.resend(input("challengeId" to id)) }
        fail(401) { service.login(input("email" to "new@example.test","password" to pwd)) }
    }

    @Test fun `expired code cannot create account`() {
        val id=begin(); clock.advance(300_001)
        fail(400) { verify(id) }
        fail(401) { service.login(input("email" to "new@example.test","password" to pwd)) }
    }

    @Test fun `resend requires cooldown and invalidates previous code`() {
        val id=begin(); val old=codes.getValue(id)
        fail(429) { accounts.resend(input("challengeId" to id)) }
        clock.advance(45_001); accounts.resend(input("challengeId" to id))
        if(codes.getValue(id)!=old)fail(400) { verify(id,old) }
        assertNotNull(verify(id)["token"])
    }

    @Test fun `reset grant changes password revokes old sessions and cannot replay`() {
        service.seed("r","Resident","old@example.test","RESIDENT",pwd)
        val oldSession=service.login(input("email" to "old@example.test","password" to pwd))["token"] as String
        val id=begin(input("purpose" to "RESET","email" to "old@example.test"))
        val verified=verify(id)
        assertFalse(verified.containsKey("token")); val grant=verified["resetToken"] as String
        fail(400) { verify(id) }
        val next=Security.token()+"9b"
        accounts.reset(input("resetToken" to grant,"password" to next))
        fail(401) { service.authenticate(oldSession) }
        fail(401) { service.login(input("email" to "old@example.test","password" to pwd)) }
        assertNotNull(service.login(input("email" to "old@example.test","password" to next))["token"])
        fail(400) { accounts.reset(input("resetToken" to grant,"password" to next)) }
    }

    @Test fun `unknown reset address has no delivered code and response does not expose OTP`() {
        val result=accounts.start(input("purpose" to "RESET","email" to "unknown@example.test"))
        assertFalse(result.containsKey("code")); assertFalse(result.containsKey("token")); assertTrue(codes.isEmpty())
        assertEquals("LOCAL_DEMO_ONLY",result["delivery"])
    }

    @Test fun `collector identity is demo only and sensitive profile is encrypted`() {
        val id=begin(register("COLLECTOR")); val user=service.authenticate(verify(id)["token"] as String)
        val profile=accounts.profile(user)
        assertEquals("DEMO_ONLY",profile.get("verification").asString)
        assertEquals("0003",profile.get("nikLast4").asString); assertFalse(profile.has("nikHash")); assertFalse(profile.has("nik"))
        store.transaction { c -> c.prepareStatement("SELECT payload FROM profiles WHERE user_id=?").use { s ->
            s.setString(1,user.id); s.executeQuery().use { r -> assertTrue(r.next()); assertFalse(r.getString(1).contains("Jalan Contoh Demo")); assertFalse(r.getString(1).contains("0000000000000003")) }
        } }
    }

    @Test fun `consent phone uniqueness and immutable role are enforced`() {
        fail(400) { begin(register().apply { addProperty("consent",false) }) }
        val id=begin(); val user=service.authenticate(verify(id)["token"] as String)
        clock.advance(45_001)
        fail(409) { begin(register().apply { addProperty("email","second@example.test") }) }
        accounts.updateProfile(user,input("name" to "Nama Baru","role" to "COLLECTOR"))
        assertEquals("RESIDENT",accounts.profile(user).get("role").asString)
    }

    @Test fun `invalid coordinates and fake image headers are rejected`() {
        fail(400) { begin(register().apply { addProperty("latitude","91"); addProperty("longitude","110") }) }
        val fake=java.util.Base64.getEncoder().encodeToString(byteArrayOf(0xFF.toByte(),0xD8.toByte(),0xFF.toByte(),0,0))
        fail(400) { begin(register().apply { addProperty("avatarBase64",fake) }) }
    }

    private class MutableClock: Clock() {
        private var time=Instant.parse("2026-11-12T06:00:00Z")
        override fun getZone(): ZoneId=ZoneOffset.UTC
        override fun withZone(zone: ZoneId): Clock=this
        override fun instant(): Instant=time
        fun advance(millis: Long) { time=time.plusMillis(millis) }
    }
}
