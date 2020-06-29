package fr.omary.lol.yuumi

import com.beust.klaxon.JsonObject
import com.beust.klaxon.Klaxon
import fr.omary.lol.yuumi.models.Champion
import fr.omary.lol.yuumi.models.LoLMap
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.apache.http.client.methods.HttpGet
import org.apache.http.util.EntityUtils
import java.io.StringReader

const val ARAM_MAP = "HA"
const val SR_MAP = "SR"

private var championList = listOf<Champion>()
private var mapList = listOf(LoLMap(12,ARAM_MAP), LoLMap(11,SR_MAP))

fun getRiotVersion(): Deferred<String> = GlobalScope.async {
    val jsonVersion = EntityUtils.toString(
        httpclient.execute(HttpGet("https://ddragon.leagueoflegends.com/api/versions.json")).entity
    )
    Klaxon().parseArray<String>(jsonVersion)?.get(0)!!
}

fun getChampionList(): List<Champion> {
    if (championList.isEmpty()) {
        runBlocking {
            championList = GlobalScope.async {
                val championsJson = EntityUtils.toString(
                    httpclient.execute(HttpGet("https://ddragon.leagueoflegends.com/cdn/${getRiotVersion().await()}/data/en_US/champion.json")).entity
                )

                val datas = Klaxon().parseJsonObject(StringReader(championsJson)).get("data") as JsonObject
                datas.entries.map { Champion(((it.value as JsonObject).get("key") as String).toInt(), ((it.value as JsonObject).get("name") as String)) }.toList()
            }.await()
        }
    }
    return championList.sortedBy { champion -> champion.name }
}

fun getMapsList(): List<LoLMap> {
    return mapList
}



fun main() {
    runBlocking {
        getChampionList().forEach { println(it) }
    }
}
