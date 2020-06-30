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
import java.io.File
import java.io.StringReader
import java.nio.file.Files

val ARAM = LoLMap(12, "HA")
val SR = LoLMap(11, "SR")

private var championList = listOf<Champion>()

fun getRiotVersionAsync(): Deferred<String> = GlobalScope.async {
    val jsonVersion = EntityUtils.toString(
        httpclient.execute(HttpGet("https://ddragon.leagueoflegends.com/api/versions.json")).entity
    )
    Klaxon().parseArray<String>(jsonVersion)?.get(0)!!
}

fun getChampionList(force: Boolean = false): List<Champion> {
    if (force || championList.isEmpty()) {
        runBlocking {
            championList = GlobalScope.async {
                val championsJson = EntityUtils.toString(
                    httpclient.execute(HttpGet("https://ddragon.leagueoflegends.com/cdn/${getRiotVersionAsync().await()}/data/en_US/champion.json")).entity
                )
                val datas = Klaxon().parseJsonObject(StringReader(championsJson)).get("data") as JsonObject
                datas.entries.map {
                    Champion(
                        ((it.value as JsonObject).get("key") as String).toInt(),
                        ((it.value as JsonObject).get("name") as String),
                        ((it.value as JsonObject).get("id")) as String
                    )
                }.toList()
            }.await()
            championList.forEach { champion ->
                val champFile = File("$squareDirectory/${champion.key}.png")
                if (!champFile.exists()) {
                    Files.copy(
                        httpclient.execute(HttpGet("https://ddragon.leagueoflegends.com/cdn/${getRiotVersionAsync().await()}/img/champion/${champion.key}.png")).entity.content,
                        champFile.toPath()
                    )
                }
            }
        }
    }

    return championList.sortedBy { champion -> champion.name }
}


fun main() {
    runBlocking {
        getChampionList().forEach { println(it) }
    }
}
