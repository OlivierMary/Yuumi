package fr.omary.lol.yuumi

import com.beust.klaxon.Klaxon
import fr.omary.lol.yuumi.models.UggARAMDatas
import fr.omary.lol.yuumi.models.UggDatas
import generated.LolChampionsCollectionsChampion
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import org.apache.http.client.methods.HttpGet
import org.apache.http.impl.client.CloseableHttpClient
import org.apache.http.impl.client.HttpClients
import org.apache.http.util.EntityUtils
import java.io.File

val httpclient: CloseableHttpClient = HttpClients.createDefault()

fun getUggRankedOverviewDatas(champion: LolChampionsCollectionsChampion?): Deferred<UggDatas?> = GlobalScope.async {
    val champFile = File("$rankedDirectory/${champion?.name}-${champion?.id}.json")
    if (!champFile.exists()) {
        champFile.writeText(
            EntityUtils.toString(
                httpclient.execute(HttpGet("https://stats2.u.gg/lol/1.1/overview/10_11/ranked_solo_5x5/${champion?.id}/1.4.0.json")).entity
            )
        )
    }
    Klaxon().parse<UggDatas>(champFile.readText())
}


fun getUggAramOverviewDatas(champion: LolChampionsCollectionsChampion?): Deferred<UggARAMDatas?> = GlobalScope.async {
    val champFile = File("$aramDirectory/${champion?.name}-${champion?.id}.json")
    if (!champFile.exists()) {
        champFile.writeText(
            EntityUtils.toString(
                httpclient.execute(HttpGet("https://stats2.u.gg/lol/1.1/overview/10_11/normal_aram/${champion?.id}/1.4.0.json")).entity
            )
        )
    }
    Klaxon().parse<UggARAMDatas>(champFile.readText())
}
