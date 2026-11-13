package id.recygo.backend

import com.google.gson.Gson
import java.nio.file.Files
import java.nio.file.Path
import java.sql.Connection
import java.sql.DriverManager

/** One connection, serialized transactions: read/check/write is atomic for the demo workload. */
class Store(path: Path) : AutoCloseable {
    private val gson = Gson()
    private val connection: Connection

    init {
        path.toAbsolutePath().parent?.let(Files::createDirectories)
        Class.forName("org.sqlite.JDBC")
        connection = DriverManager.getConnection("jdbc:sqlite:${path.toAbsolutePath()}")
        connection.createStatement().use { s ->
            s.execute("PRAGMA foreign_keys=ON")
            s.execute("PRAGMA busy_timeout=5000")
            s.execute("PRAGMA journal_mode=WAL")
            s.execute("CREATE TABLE IF NOT EXISTS users(id TEXT PRIMARY KEY, name TEXT NOT NULL, email TEXT UNIQUE NOT NULL, role TEXT NOT NULL, salt TEXT NOT NULL, hash TEXT NOT NULL)")
            s.execute("CREATE TABLE IF NOT EXISTS sessions(hash TEXT PRIMARY KEY, user_id TEXT NOT NULL REFERENCES users(id), expires INTEGER NOT NULL)")
            s.execute("CREATE TABLE IF NOT EXISTS orders(id TEXT PRIMARY KEY, owner_id TEXT NOT NULL REFERENCES users(id), collector_id TEXT REFERENCES users(id), status TEXT NOT NULL, payload TEXT NOT NULL, request_key TEXT NOT NULL, fingerprint TEXT NOT NULL, UNIQUE(owner_id, request_key))")
            s.execute("CREATE TABLE IF NOT EXISTS profiles(user_id TEXT PRIMARY KEY REFERENCES users(id), phone_hash TEXT UNIQUE NOT NULL, payload TEXT NOT NULL)")
            s.execute("CREATE TABLE IF NOT EXISTS auth_challenges(id TEXT PRIMARY KEY, purpose TEXT NOT NULL, email TEXT NOT NULL, code_hash TEXT NOT NULL, expires INTEGER NOT NULL, attempts INTEGER NOT NULL DEFAULT 0, resends INTEGER NOT NULL DEFAULT 0, last_sent INTEGER NOT NULL, payload TEXT NOT NULL, verified INTEGER NOT NULL DEFAULT 0, used INTEGER NOT NULL DEFAULT 0, grant_hash TEXT, grant_expires INTEGER NOT NULL DEFAULT 0)")
        }
    }

    @Synchronized
    fun <T> transaction(block: (Connection) -> T): T {
        connection.autoCommit = false
        return try { block(connection).also { connection.commit() } }
        catch (e: Exception) { connection.rollback(); throw e }
        finally { connection.autoCommit = true }
    }

    fun orders(c: Connection): List<Pickup> = c.prepareStatement("SELECT payload FROM orders ORDER BY rowid DESC").use { s ->
        s.executeQuery().use { r -> buildList { while (r.next()) add(gson.fromJson(r.getString(1), Pickup::class.java)) } }
    }

    fun find(c: Connection, id: String): Pickup = c.prepareStatement("SELECT payload FROM orders WHERE id=?").use { s ->
        s.setString(1, id)
        s.executeQuery().use { r -> if (r.next()) gson.fromJson(r.getString(1), Pickup::class.java) else throw ApiError(404, "Pesanan tidak ditemukan.") }
    }

    fun save(c: Connection, order: Pickup) {
        c.prepareStatement("UPDATE orders SET collector_id=?,status=?,payload=? WHERE id=?").use { s ->
            s.setString(1, order.collectorId); s.setString(2, order.status); s.setString(3, gson.toJson(order)); s.setString(4, order.id); s.executeUpdate()
        }
    }

    @Synchronized override fun close() = connection.close()
}
