package id.recygo.app

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class ApiFailure(val status: Int,override val message: String): Exception(message)
data class User(val id: String,val name: String,val email: String,val role: String) {
    val collector get()=role=="COLLECTOR"
    companion object { fun from(j: JSONObject)=User(j.getString("id"),j.getString("name"),j.getString("email"),j.getString("role")) }
}
data class Waste(val id: String,val name: String,val rate: Int,val guide: String)
data class Item(val materialId: String,val grams: Int)
data class Order(
    val id: String,val ownerName: String,val collectorName: String,val status: String,
    val address: String,val date: String,val slot: String,val notes: String,val reason: String,
    val items: List<Item>,val actual: List<Item>,val points: Int,val updatedAt: String
) {
    val shortId get()=id.take(8).uppercase()
    val complete get()=status in setOf("COMPLETED","CANCELLED","REJECTED")
    companion object {
        private fun items(a: JSONArray)=List(a.length()) { i -> a.getJSONObject(i).let { Item(it.getString("materialId"),it.getInt("grams")) } }
        fun from(j: JSONObject)=Order(j.getString("id"),j.getString("ownerName"),j.optString("collectorName").takeUnless { it=="null" }.orEmpty(),j.getString("status"),j.getString("address"),j.getString("date"),j.getString("slot"),j.getString("notes"),j.optString("reason"),items(j.getJSONArray("items")),items(j.getJSONArray("actualItems")),j.getInt("points"),j.getString("updatedAt"))
    }
}

class Api {
    @Volatile var token: String=""
    fun request(path: String,body: JSONObject?=null,key: String?=null): JSONObject {
        val connection=(URL(BuildConfig.API_BASE_URL.trimEnd('/')+path).openConnection() as HttpURLConnection)
        connection.connectTimeout=12_000; connection.readTimeout=12_000
        connection.requestMethod=if(body==null) "GET" else "POST"
        connection.setRequestProperty("Accept","application/json")
        if(token.isNotEmpty()) connection.setRequestProperty("Authorization","Bearer $token")
        if(key!=null) connection.setRequestProperty("Idempotency-Key",key)
        try {
            if(body!=null) {
                connection.doOutput=true; connection.setRequestProperty("Content-Type","application/json; charset=utf-8")
                connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            }
            val status=connection.responseCode
            val data=(if(status in 200..299) connection.inputStream else connection.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
            val result=try { JSONObject(data) } catch (_: Exception) { throw ApiFailure(status,"Respons server tidak dapat dibaca. Coba lagi.") }
            if(status !in 200..299) throw ApiFailure(status,result.optString("error","Permintaan gagal. Coba lagi."))
            return result
        } finally { connection.disconnect() }
    }
}

fun List<Item>.json()=JSONArray().also { a -> forEach { a.put(JSONObject().put("materialId",it.materialId).put("grams",it.grams)) } }
fun statusLabel(status: String)=when(status) {
    "REQUESTED" -> "Diajukan"; "SCHEDULED" -> "Dijadwalkan"; "ON_THE_WAY" -> "Dijemput"
    "ARRIVED" -> "Pengepul tiba"; "AWAITING_CONFIRMATION" -> "Ditimbang"; "COMPLETED" -> "Selesai"
    "REJECTED" -> "Ditolak"; "CANCELLED" -> "Dibatalkan"; else -> status
}
