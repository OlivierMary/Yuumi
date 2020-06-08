package fr.omary.lol.yuumi

import com.beust.klaxon.Klaxon
import fr.omary.lol.yuumi.models.UggARAMDatas
import fr.omary.lol.yuumi.models.UggDatas
import generated.LolChampionsCollectionsChampion
import org.apache.http.client.methods.HttpGet
import org.apache.http.impl.client.CloseableHttpClient
import org.apache.http.impl.client.HttpClients
import org.apache.http.util.EntityUtils

val httpclient: CloseableHttpClient = HttpClients.createDefault()

fun getUggRankedOverviewDatas(champion: LolChampionsCollectionsChampion?): UggDatas? =
    Klaxon().parse<UggDatas>(
        EntityUtils.toString(
            httpclient.execute(HttpGet("https://stats2.u.gg/lol/1.1/overview/10_11/ranked_solo_5x5/${champion?.id}/1.4.0.json")).entity
        )
    )


fun getUggAramOverviewDatas(champion: LolChampionsCollectionsChampion?): UggARAMDatas? =
    Klaxon().parse<UggARAMDatas>(
        EntityUtils.toString(
            httpclient.execute(HttpGet("https://stats2.u.gg/lol/1.1/overview/10_11/normal_aram/${champion?.id}/1.4.0.json")).entity
        )
    )
