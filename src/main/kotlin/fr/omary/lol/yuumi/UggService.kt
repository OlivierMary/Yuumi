package fr.omary.lol.yuumi

import com.beust.klaxon.Klaxon
import fr.omary.lol.yuumi.models.UggARAMDatas
import fr.omary.lol.yuumi.models.UggDatas
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import org.apache.http.client.methods.HttpGet
import org.apache.http.impl.client.CloseableHttpClient
import org.apache.http.impl.client.HttpClients
import org.apache.http.util.EntityUtils
import java.io.File

var lolUggVersion: String? = null
var dataUggVersion: String? = null
val httpclient: CloseableHttpClient = HttpClients.createDefault()

fun getUggRankedOverviewDatas(championName: String?, championId: Int?): Deferred<UggDatas?> = GlobalScope.async {
    checkUggVersions()
    val champFile = File("$rankedDirectory/$championName-$championId.json")
    if (!champFile.exists()) {
        champFile.writeText(
            EntityUtils.toString(
                httpclient.execute(HttpGet("https://stats2.u.gg/lol/1.1/overview/$lolUggVersion/ranked_solo_5x5/$championId/$dataUggVersion.json")).entity
            )
        )
    }
    Klaxon().parse<UggDatas>(champFile.readText())
}


fun getUggAramOverviewDatas(champtionName: String?, champtionId: Int?): Deferred<UggARAMDatas?> = GlobalScope.async {
    checkUggVersions()
    val champFile = File("$aramDirectory/$champtionName-$champtionId.json")
    if (!champFile.exists()) {
        champFile.writeText(
            EntityUtils.toString(
                httpclient.execute(HttpGet("https://stats2.u.gg/lol/1.1/overview/$lolUggVersion/normal_aram/$champtionId/$dataUggVersion.json")).entity
            )
        )
    }
    Klaxon().parse<UggARAMDatas>(champFile.readText())
}

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
        dataUggVersion = ".*latest\":\\s*\\{\"overview\":\"(\\d+\\.\\d+.\\d+)\".*".toRegex().find(mainScript)?.groupValues?.get(1)

        lolUggVersion!! to dataUggVersion!!
    }

suspend fun checkUggVersions(){
    if (lolUggVersion == null || dataUggVersion == null){
        println(getUggVersions().await())
    }
}
