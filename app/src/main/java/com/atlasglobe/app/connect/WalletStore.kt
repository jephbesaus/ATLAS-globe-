package com.atlasglobe.app.connect

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.atlasglobe.app.data.ChatRepo
import org.json.JSONObject
import java.io.File

/** Portefeuille Atlas : solde rechargeable par Mobile Money, débité instantanément pour les achats Wi-Fi. */
data class Recharge(val id: String, val amountFc: Int, val transactionId: String, val createdAt: Long, val status: String)

object WalletStore {
    private lateinit var app: Context
    private val file get() = File(app.filesDir, "wallet.json")
    var balanceFc by mutableStateOf(0); private set
    var recharges by mutableStateOf<List<Recharge>>(emptyList()); private set

    fun init(c: Context) {
        if (::app.isInitialized) return
        app = c.applicationContext
        runCatching {
            val j = JSONObject(file.readText())
            balanceFc = j.optInt("balance", 0)
            recharges = j.optJSONArray("recharges")?.let { a -> (0 until a.length()).map { i -> a.getJSONObject(i).let { o ->
                Recharge(o.getString("id"), o.getInt("amount"), o.getString("tid"), o.getLong("t"), o.getString("status")) } } } ?: emptyList()
        }
    }

    /** Message lu par le serveur (mesh.js, parseOrder) : crédite le solde après approbation admin. */
    fun requestRecharge(c: Context, amountFc: Int, transactionId: String) {
        init(c)
        AtlasConfig.install(c)
        val id = "RCH-" + java.util.UUID.randomUUID().toString().take(8).uppercase()
        recharges = listOf(Recharge(id, amountFc, transactionId, System.currentTimeMillis(), "awaiting")) + recharges
        save()
        ChatRepo.send(AtlasConfig.ASSISTANT_ID, "text", "RECHARGE $amountFc $id\nID transaction : $transactionId")
    }

    /** Reçu du serveur (mesh.js) après approbation d'une recharge. */
    fun onCredited(orderId: String, amountFc: Int) {
        balanceFc += amountFc
        recharges = recharges.map { if (it.id == orderId) it.copy(status = "approved") else it }
        save()
    }

    fun onRefused(orderId: String) {
        recharges = recharges.map { if (it.id == orderId) it.copy(status = "refused") else it }
        save()
    }

    /** Débité quand le serveur confirme un achat payé par le portefeuille (voir onGrant/wallet_buy). */
    fun onDebited(amountFc: Int) {
        balanceFc = (balanceFc - amountFc).coerceAtLeast(0); save()
    }

    /** Demande d'achat instantané via le portefeuille (le serveur vérifie et débite réellement). */
    fun buy(planId: String, resellerId: String, resellerName: String) {
        com.atlasglobe.app.mesh.MeshRouter.sendControlTo(AtlasConfig.ASSISTANT_ID,
            JSONObject().put("type", "wallet_buy").put("planId", planId).put("resellerName", resellerName))
    }

    private fun save() {
        val arr = JSONArray(); recharges.forEach { arr.put(JSONObject().put("id", it.id).put("amount", it.amountFc).put("tid", it.transactionId).put("t", it.createdAt).put("status", it.status)) }
        runCatching { file.writeText(JSONObject().put("balance", balanceFc).put("recharges", arr).toString()) }
    }
}
