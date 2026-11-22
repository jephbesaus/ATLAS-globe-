package com.atlasglobe.app.mesh

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.math.pow

data class Offer(val short: String, val id: String = "", val name: String = "", val slots: Int = 0,
                 val rating: Float = 0f, val speed: Float = 0f, val rssi: Int = -100,
                 val seen: Long = 0L, val loaded: Boolean = false) {
    /** Estimation grossière de la distance (le signal varie avec les murs) : fiable comme indication, pas au mètre. */
    val meters: Float get() = 10f.pow((-59 - rssi) / 25f)
    /** 0 = proche (vert), 1 = moyen, 2 = loin (rouge) */
    val zone: Int get() = when { rssi >= -70 -> 0; rssi >= -85 -> 1; else -> 2 }
    val full: Boolean get() = slots <= 0
}

/** Revendeurs détectés autour de l'utilisateur. */
object ResellerDirectory {
    var offers by mutableStateOf<List<Offer>>(emptyList()); private set

    fun clear() { offers = emptyList() }

    fun seen(short: String, rssi: Int, slots: Int) {
        val cur = offers.firstOrNull { it.short == short }
        val n = (cur ?: Offer(short)).copy(rssi = rssi, slots = slots, seen = System.currentTimeMillis())
        offers = if (cur == null) offers + n else offers.map { if (it.short == short) n else it }
    }

    fun profile(short: String, id: String, name: String, slots: Int, rating: Float, speed: Float) {
        val cur = offers.firstOrNull { it.short == short } ?: Offer(short)
        val n = cur.copy(id = id, name = name, slots = slots, rating = rating, speed = speed, loaded = true)
        offers = if (offers.any { it.short == short }) offers.map { if (it.short == short) n else it } else offers + n
    }

    /** Le meilleur choix : proche, de la place, bien noté, rapide. */
    fun recommended(): Offer? = offers.filter { !it.full }.maxByOrNull { (2 - it.zone) * 100 + it.rating * 10 + it.speed }
}

/** État du téléphone quand il est un revendeur approuvé et actif (rempli par l'espace revendeur). */
object ResellerState {
    var active by mutableStateOf(false)
    var name by mutableStateOf("")
    var slots by mutableIntStateOf(5)
    var rating by mutableFloatStateOf(0f)
    var speed by mutableFloatStateOf(0f)
    var mobile by mutableStateOf(false)          // en déplacement : 3 clients maximum
}
