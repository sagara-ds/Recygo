package id.recygo.backend

data class Account(val id: String, val name: String, val email: String, val role: String)
data class Material(val id: String, val name: String, val pointsPerKg: Int, val guide: String)
data class WasteItem(val materialId: String, val grams: Int)
data class Pickup(
    val id: String,
    val ownerId: String,
    val ownerName: String,
    val address: String,
    val date: String,
    val slot: String,
    val notes: String,
    val items: List<WasteItem>,
    val createdAt: String,
    var updatedAt: String,
    var status: String = "REQUESTED",
    var collectorId: String? = null,
    var collectorName: String? = null,
    var actualItems: List<WasteItem> = emptyList(),
    var points: Int = 0,
    var reason: String = ""
)

val catalog = listOf(
    Material("plastic", "Botol Plastik", 500, "Kosongkan, bilas, dan keringkan botol. Pisahkan dari sampah organik."),
    Material("paper", "Kertas", 300, "Jaga tetap kering. Pisahkan kertas bersih dari tisu dan kemasan berminyak."),
    Material("glass", "Kaca", 600, "Pisahkan menurut jenis. Bungkus pecahan dan beri tanda agar aman ditangani."),
    Material("metal", "Logam", 700, "Kosongkan dan keringkan kaleng. Lindungi bagian yang tajam.")
)
val terminalStatuses = setOf("COMPLETED", "CANCELLED", "REJECTED")
val pickupSlots = setOf("08.00–10.00", "10.00–12.00", "13.00–15.00", "15.00–17.00")

class ApiError(val code: Int, override val message: String) : RuntimeException(message)

