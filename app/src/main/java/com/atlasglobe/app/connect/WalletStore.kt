package com.atlasglobe.app.connect

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.atlasglobe.app.data.ChatRepo
import org.json.JSONObject
import java.io.File

/** Portefeuille Atlas : solde rechargeable par Mobile Money, débité instantanément pour les achats Wi-Fi. */
object WalletStore {
    private lateinit var app: Context
    private val file get() = File(app.filesDir, "wallet.json")
    var balanceFc by mutableStateOf(0); private set

    fun init(c: Context) {
        if (::app.isInitialized) return
        app = c.applicationContext
        runCatching { balanceFc = JSONObject(file.readText()).optInt("balance", 0) }
    }

    /** Message lu par le serveur (mesh.js, parseOrder) : crédite le solde après approbation admin. */
    fun requestRecharge(c: Context, amountFc: Int, transactionId: String) {
        init(c)
        AtlasConfig.install(c)
        ChatRepo.send(AtlasConfig.ASSISTANT_ID, "text", "RECHARGE $amountFc\nID transaction : $transactionId")
    }

    /** Reçu du serveur (mesh.js) après approbation d'une recharge. */
    fun onCredited(amountFc: Int) {
        balanceFc += amountFc; save()
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
        runCatching { file.writeText(JSONObject().put("balance", balanceFc).toString()) }
    }
}
