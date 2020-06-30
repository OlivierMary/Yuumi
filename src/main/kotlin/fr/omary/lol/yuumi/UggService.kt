package fr.omary.lol.yuumi

import com.beust.klaxon.JsonArray
import com.beust.klaxon.JsonObject
import com.beust.klaxon.Klaxon
import fr.omary.lol.yuumi.models.*
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import org.apache.http.client.methods.HttpGet
import org.apache.http.util.EntityUtils
import java.io.File

var lolUggVersion: String? = null
var dataUggVersion: String? = null

fun getUggDatas(champ: Champion): Deferred<List<ChampionPositionData>> = GlobalScope.async {
    checkUggVersions()
    val champRankedFile = File("$rankedDirectory/${champ.name}-${champ.id}.json")
    if (!champRankedFile.exists()) {
        champRankedFile.writeText(
            EntityUtils.toString(
                httpclient.execute(HttpGet("https://stats2.u.gg/lol/1.1/overview/$lolUggVersion/ranked_solo_5x5/${champ.id}/$dataUggVersion.json")).entity
            )
        )
    }

    val champARAMFile = File("$aramDirectory/${champ.name}-${champ.id}.json")
    if (!champARAMFile.exists()) {
        champARAMFile.writeText(
            EntityUtils.toString(
                httpclient.execute(HttpGet("https://stats2.u.gg/lol/1.1/overview/$lolUggVersion/normal_aram/${champ.id}/$dataUggVersion.json")).entity
            )
        )
    }

    parseDatas(
        champ,
        listOf(
            Klaxon().parseJsonObject(champRankedFile.reader()),
            Klaxon().parseJsonObject(champARAMFile.reader())
        )
    )
}

fun parseDatas(champ: Champion, datas: List<JsonObject>): List<ChampionPositionData> =
    datas.flatMap { parseZone(champ, it) }.toList()

fun parseZone(champ: Champion, datas: JsonObject): List<ChampionPositionData> =
    Zone.values().filter { zone -> datas.get(zone.keyUgg.toString()) != null }
        .flatMap { zone ->
            parseRank(
                champ,
                zone,
                datas[zone.keyUgg.toString()] as JsonObject
            )
        }.toList()

fun parseRank(champ: Champion, zone: Zone, datas: JsonObject): List<ChampionPositionData> =
    Rank.values().filter { rank -> datas.get(rank.keyUgg.toString()) != null }
        .flatMap { rank ->
            parsePosition(
                champ,
                zone,
                rank,
                datas.get(rank.keyUgg.toString()) as JsonObject
            )
        }.toList()

fun parsePosition(champ: Champion, zone: Zone, rank: Rank, datas: JsonObject): List<ChampionPositionData> =
    YPosition.values().filter { position -> datas.get(position.keyUgg.toString()) != null }
        .map { position ->
            parseUggDatas(
                champ,
                zone,
                rank,
                position,
                ((datas.get(position.keyUgg.toString())) as JsonArray<Any>?)?.getOrNull(0) as JsonArray<Any>?
            )
        }.toList()


fun parseUggDatas(
    champ: Champion,
    zoneIn: Zone,
    rankIn: Rank,
    positionIn: YPosition,
    datas: JsonArray<Any>?
): ChampionPositionData = ChampionPositionData(champ,
    positionIn, rankIn, zoneIn,
    Rune(
        (datas?.getOrNull(0) as JsonArray<Any>?)?.getOrNull(2) as Int?,
        (datas?.getOrNull(0) as JsonArray<Any>?)?.getOrNull(3) as Int?,
        run {
            val result =
                ((datas?.getOrNull(0) as JsonArray<Any>?)?.getOrNull(4) as JsonArray<Int>?)?.toList() as MutableList<Int>?
            ((datas?.getOrNull(8) as JsonArray<Any>?)?.getOrNull(2) as JsonArray<Int>?)?.toList()
                ?.let { result?.addAll(it) }
            result
        }
    ),
    ((datas?.getOrNull(1) as JsonArray<Any>?)?.getOrNull(2) as JsonArray<Int>?)?.toList(),
    ((datas?.getOrNull(2) as JsonArray<Any>?)?.getOrNull(2) as JsonArray<Int>?)?.toList(),
    ((datas?.getOrNull(3) as JsonArray<Any>?)?.getOrNull(2) as JsonArray<Int>?)?.toList(),
    ((datas?.getOrNull(5) as JsonArray<JsonArray<JsonArray<Int>>>?)?.getOrNull(0)?.map { it[0] })?.toList(),
    ((datas?.getOrNull(5) as JsonArray<JsonArray<JsonArray<Int>>>?)?.getOrNull(1)?.map { it[0] })?.toList(),
    ((datas?.getOrNull(5) as JsonArray<JsonArray<JsonArray<Int>>>?)?.getOrNull(2)?.map { it[0] })?.toList(),
    Skills(
        (datas?.getOrNull(4) as JsonArray<Any>?)?.getOrNull(3) as String?,
        ((datas?.getOrNull(4) as JsonArray<Any>?)?.getOrNull(2) as JsonArray<String>?)?.toList()
    ),
    (datas?.getOrNull(6) as JsonArray<Int>?)?.getOrNull(1)
)


fun getUggVersions(): Deferred<Pair<String, String>> =
    GlobalScope.async {
        val mainHtml = EntityUtils.toString(httpclient.execute(HttpGet("https://u.gg")).entity)
        val mainScript = EntityUtils.toString(
            httpclient.execute(
                HttpGet(
                    ".*src=\"(.*/main\\.\\w*\\.js).*".toRegex().find(mainHtml)?.groupValues?.get(1)
                )
            ).entity
        )
        // p=[{value:"10_12"
        lolUggVersion = ".*=\\[\\{value:\"(\\d+_\\d+)\".*".toRegex().find(mainScript)?.groupValues?.get(1)
        dataUggVersion =
            ".*latest\":\\s*\\{\"overview\":\"(\\d+\\.\\d+.\\d+)\".*".toRegex()
                .find(mainScript)?.groupValues?.get(1)

        lolUggVersion!! to dataUggVersion!!
    }

suspend fun checkUggVersions() {
    if (lolUggVersion == null || dataUggVersion == null) {
        println(getUggVersions().await())
    }
}
