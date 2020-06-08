package fr.omary.lol.yuumi

import com.beust.klaxon.JsonObject
import com.beust.klaxon.Klaxon
import com.stirante.lolclient.ClientApi
import com.stirante.lolclient.ClientConnectionListener
import com.stirante.lolclient.ClientWebSocket
import com.stirante.lolclient.ClientWebSocket.SocketListener
import fr.omary.lol.yuumi.models.ItemDatas2
import fr.omary.lol.yuumi.models.PositionDatas
import fr.omary.lol.yuumi.models.UggARAMDatas
import fr.omary.lol.yuumi.models.UggDatas
import generated.*
import org.apache.commons.lang3.builder.ToStringBuilder
import org.apache.commons.lang3.builder.ToStringStyle
import org.apache.http.client.methods.HttpGet
import org.apache.http.impl.client.CloseableHttpClient
import org.apache.http.impl.client.HttpClients
import org.apache.http.util.EntityUtils
import java.lang.Thread.sleep

val api = ClientApi()
val httpclient: CloseableHttpClient = HttpClients.createDefault()
var socket: ClientWebSocket? = null

val perksByIdChamp: MutableMap<Int, List<LolPerksPerkPageResource>> = mutableMapOf()
val championsByIdChamp: MutableMap<Int, LolChampionsCollectionsChampion> = mutableMapOf()
val itemsSetsByIdChamp: MutableMap<Int, List<LolItemSetsItemSet>> = mutableMapOf()
val summonerSpellsByChamp: MutableMap<Int, List<Pair<String?, List<Int>?>>> = mutableMapOf()

var uniqueMaps = mutableListOf<LolMapsMaps>()
lateinit var lolItemSetsItemSets: LolItemSetsItemSets
lateinit var summoner: LolSummonerSummoner
var gameMode: String? = null
var assignedPosition: String? = null

fun startYuumi() {
    println("Waiting for client connect.")
    ClientApi.setDisableEndpointWarnings(true)
    api.addClientConnectionListener(object : ClientConnectionListener {
        override fun onClientDisconnected() {
            println("Client disconnected")
            socket?.close()
        }

        override fun onClientConnected() {
            println("Client connected")
            try {
                openSocket()
                if (socket == null || !socket!!.isOpen) {
                    return
                }
                initStaticVariables()
                notifConnected()

                socket?.setSocketListener(object : SocketListener {
                    override fun onEvent(event: ClientWebSocket.Event) {
                        handleValidateChampion(event)
                        handleChooseChampion(event)
                    }

                    override fun onClose(code: Int, reason: String) {
                        println("Socket closed, reason: $reason")
                    }
                })
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    })
}

fun stopYuumi() {
    sendLoLNotif("Disconnected", "Disconnected to Yuumi, See you later", 1)
    socket?.close()
    api.stop()
}

private fun notifConnected() {
    sendLoLNotif("Connected", "Connected to Yuumi, Enjoy", 11)
}

fun sendLoLNotif(title: String, detail: String, idImage: Int) {
    val notif = PlayerNotificationsPlayerNotificationResource()
    notif.titleKey = "pre_translated_title"
    notif.detailKey = "pre_translated_details"
    val data = JsonObject()
    data["title"] = title
    data["details"] = detail
    notif.data = data
    notif.iconUrl = "https://ddragon.leagueoflegends.com/cdn/10.11.1/img/champion/Yuumi.png"
    notif.backgroundUrl = "https://ddragon.leagueoflegends.com/cdn/img/champion/splash/Yuumi_$idImage.jpg"
    api.executePost("/player-notifications/v1/notifications", notif)
}

private fun openSocket() {
    var maxTry = 20
    do {
        println("Socket not connected will (re)try...")
        sleep(1000)
        try {
            if (socket != null) {
                socket?.close()
            }
            socket = api.openWebSocket()
        } catch (e: Exception) {
            // nothing
        }
        maxTry--
    } while (socket == null || !socket?.isOpen!! || maxTry < 0)
    println("Socket connected")
}


private fun initStaticVariables() {
    var tempSummoner: LolSummonerSummoner?
    do {
        tempSummoner = setSummoner()
        sleep(1000)
    } while (tempSummoner == null)

    summoner = tempSummoner
    println("Summoner : ${summoner.displayName}")
    val maps = api.executeGet("/lol-maps/v2/maps", Array<LolMapsMaps>::class.java)
    val uniqueMapsId = maps.map(LolMapsMaps::id).distinct()
    uniqueMaps = uniqueMapsId.map { id -> maps.first { m -> m.id == id } }.toMutableList()
    for (uniqueMap in uniqueMaps) {
        println("Map : [${uniqueMap.gameMode}, ${uniqueMap.mapStringId}, ${uniqueMap.gameModeName}] ")
    }
    lolItemSetsItemSets = api.executeGet(
        "/lol-item-sets/v1/item-sets/${summoner.summonerId}/sets",
        LolItemSetsItemSets::class.java
    )
}

private fun setSummoner() = api.executeGet("/lol-summoner/v1/current-summoner", LolSummonerSummoner::class.java)

private fun handleValidateChampion(event: ClientWebSocket.Event) {
    if (event.eventType != "Delete" && event.uri == "/lol-champ-select/v1/current-champion") {
        validateChampion(event.data as Int)
    }
}

private fun handleChooseChampion(event: ClientWebSocket.Event) {
    if (event.eventType != "Delete" && event.uri.startsWith("/lol-champ-select/v1/grid-champions/")) {
        if (!championsByIdChamp.containsKey((event.data as LolChampSelectChampGridChampion).id) || championsByIdChamp[((event.data as LolChampSelectChampGridChampion).id)] == null) {
            processChampion((event.data as LolChampSelectChampGridChampion).id)
        } else {
            println("${championsByIdChamp[(event.data as LolChampSelectChampGridChampion).id]?.name} already processed")
        }
    }
}

private fun setCurrentGameMode() {
    gameMode = api.executeGet("/lol-lobby/v2/lobby", LolLobbyLobbyDto::class.java)?.gameConfig?.gameMode
    println("Game Mode : $gameMode")
}

private fun setAssignerPosition() {
    val me = api.executeGet(
        "/lol-lobby-team-builder/champ-select/v1/session",
        LolLobbyTeamBuilderChampSelectSession::class.java
    )?.myTeam?.first { x -> x.summonerId == summoner.summonerId }
    assignedPosition = me?.assignedPosition
    if (assignedPosition == null || assignedPosition == "") {
        assignedPosition = "ARAM"
    }
    println("Assigned position : $assignedPosition")
}

fun validateChampion(champId: Int) {
    startLoading("Send [${championsByIdChamp[champId]?.name}]")
    checkProcessedOrWait(champId)
    printStatus(champId)
    setPerks(champId)
    setItemsSets(champId)
    setSummonerSpells(champId)
    sendSystemNotification("Champ set ${championsByIdChamp[champId]?.name} : Sent", "INFO")
    stopLoading()
}

fun printStatus(champId: Int) {
    println(
        """-----------------------------------------------------------
    Run with:
    Game Mode : $gameMode
    Assigned Position : $assignedPosition
    Champion : ${reflexToString(championsByIdChamp[champId])}
    Perks : ${reflexToString(perksByIdChamp[champId])}
    Items : ${reflexToString(itemsSetsByIdChamp[champId])}
    Summoner Spells: ${reflexToString(summonerSpellsByChamp[champId])}
    -----------------------------------------------------------""".trimIndent()
    )

}

private fun reflexToString(l: List<Any?>?): String {
    return "[${l?.joinToString(",\n") { i -> reflexToString(i) }}]"
}

private fun reflexToString(o: Any?): String {
    return ToStringBuilder.reflectionToString(o, ToStringStyle.MULTI_LINE_STYLE)
}

private fun checkProcessedOrWait(champId: Int) {
    if (gameMode == null || gameMode == "") {
        setCurrentGameMode()
    }
    if (assignedPosition == null || assignedPosition == "") {
        setAssignerPosition()
    }
    // In case of select too fast if champ not already processed
    if (!championsByIdChamp.containsKey(champId) || championsByIdChamp[champId] == null) {
        processChampion(champId)
    } else {
        println("${championsByIdChamp[champId]?.name} already processed")
    }
    var timeout = 0
    while ((!perksByIdChamp.containsKey(champId) || !itemsSetsByIdChamp.containsKey(champId)) && timeout < 20) {
        println("Waiting process...")
        sleep(500)
        timeout++
    }
}

private fun setPerks(champId: Int) {
    if (perksByIdChamp.containsKey(champId) && perksByIdChamp[champId] != null) {
        resetGeneratedExistingPerks()
        setCurrentPerk(champId)
        commitPerks(champId)
    } else {
        println("Perks : no datas")
    }
}

private fun resetGeneratedExistingPerks() {
    val existingPerks = api.executeGet("/lol-perks/v1/pages", Array<LolPerksPerkPageResource>::class.java)
    existingPerks.filter { p -> p.name.startsWith("GENERATED") }
        .forEach { api.executeDelete("/lol-perks/v1/pages/${it.id}") }
}

private fun setCurrentPerk(champId: Int) {
    if (gameMode == "ARAM") {
        perksByIdChamp[champId]!!.first { p ->
            p.name.contains(gameMode!!) && p.name.contains("GENERATED") && p.name.contains(
                championsByIdChamp[champId]!!.name
            )
        }.current = true
    } else {
        perksByIdChamp[champId]!!.forEach { p ->
            run {
                p.current = assignedPosition?.let {
                    p.name.contains(it) && p.name.contains("GENERATED") && p.name.contains(championsByIdChamp[champId]!!.name)
                }
            }
        }
    }
}

private fun commitPerks(champId: Int) {
    perksByIdChamp[champId]!!.sortedBy { it.current }.map { it to tryAddPerk(it) }.filter { it.first.current }.forEach {
        run {
            sendSystemNotification("Champ set ${championsByIdChamp[champId]?.name} : Not Enough runes pages", "WARNING")
            resetGeneratedExistingPerks()
            tryAddPerk(it.first)
        }
    }
}

private fun tryAddPerk(perk: LolPerksPerkPageResource): Boolean {
    val resultAddPerk = api.executePost("/lol-perks/v1/pages", perk)
    println("Runes : $resultAddPerk")
    return resultAddPerk
}

private fun setItemsSets(champId: Int) {
    if (itemsSetsByIdChamp.containsKey(champId) && itemsSetsByIdChamp[champId] != null) {
        resetAndPopulateItemsSets(champId)
        commitItemsSets()
    } else {
        println("Items Set : no datas")
    }
}

private fun resetAndPopulateItemsSets(champId: Int) {
    lolItemSetsItemSets.itemSets?.clear()
    lolItemSetsItemSets.itemSets?.addAll(itemsSetsByIdChamp[champId]!!)
}

private fun commitItemsSets() {
    println(
        "Items Set : ${api.executePut(
            "/lol-item-sets/v1/item-sets/${summoner.summonerId}/sets",
            lolItemSetsItemSets
        )}"
    )
}

private fun setSummonerSpells(champId: Int) {
    if (summonerSpellsByChamp.containsKey(champId) && summonerSpellsByChamp[champId] != null) {
        val spells = prepareSummonerSpells(champId)
        commitSummonerSpells(spells)
    } else {
        println("Summoner Spells : no datas")
    }
}

private fun prepareSummonerSpells(champId: Int): JsonObject = JsonObject().apply {
    if (assignedPosition != null && assignedPosition != "") {
        val summonerSpells =
            summonerSpellsByChamp[champId]?.first { p -> p.first == assignedPosition }?.second
        this["spell1Id"] = summonerSpells?.get(0)
        this["spell2Id"] = summonerSpells?.get(1)
    }
}

private fun commitSummonerSpells(spells: JsonObject) {
    println(
        "Summoner Skills : ${api.executePatch(
            "/lol-lobby-team-builder/champ-select/v1/session/my-selection",
            spells
        )} "
    )
}

private fun processChampion(champId: Int) {
    startLoading("Process [$champId]...")
    if (champId < 1) {
        return
    }

    var champion = championsByIdChamp[champId]
    if (champion == null) {
        champion = getChampion(champId)
    }
    if (champion == null) {
        return // maybe client closed before process
    }
    championsByIdChamp[champId] = champion

    startLoading("Process [${champion.name}]...")
    println("${champion.name} process...")

    val rankedDatas = getUggRankedOverviewDatas(champion)
    val aramDatas = getUggAramOverviewDatas(champion)

    val newItemsList = mutableListOf<LolItemSetsItemSet>()
    val newPerks = mutableListOf<LolPerksPerkPageResource>()
    val newSummonerSpells = mutableListOf<Pair<String?, List<Int>?>>()


    for (uniqueMap in uniqueMaps) {
        when (uniqueMap.mapStringId) {
            "SR" -> {
                for (role in rankedDatas?.world?.platPlus?.allRoles()!!) {
                    newItemsList.add(generateItemsSet(champion, uniqueMap, role))
                    newPerks.add(generatePerk(role, champion, uniqueMap))
                    newSummonerSpells.add(generateSummoner(role))
                }
            }
            "HA" -> {
                newItemsList.add(generateItemsSet(champion, uniqueMap, aramDatas?.world?.aram?.aram()))
                newPerks.add(generatePerk(aramDatas?.world?.aram?.aram(), champion, uniqueMap))
                newSummonerSpells.add(generateSummoner(aramDatas?.world?.aram?.aram()))
            }
        }
    }
    itemsSetsByIdChamp[champId] = newItemsList
    perksByIdChamp[champId] = newPerks
    summonerSpellsByChamp[champId] = newSummonerSpells
    println("${champion.name} processed")
    refreshChampTray()
    stopLoading()
}

fun refreshChampTray() {
    refreshChampionList(championsByIdChamp.values.sortedBy { it.name }.map { it.id to it.name })
}

private fun generateItemsSet(
    champion: LolChampionsCollectionsChampion?,
    uniqueMap: LolMapsMaps, role: PositionDatas?
): LolItemSetsItemSet = LolItemSetsItemSet().apply {
    title = "${champion?.name} ${uniqueMap.mapStringId} ${role?.name}"
    associatedMaps = listOf(uniqueMap.id.toInt())
    associatedChampions = listOf(champion?.id)
    map = uniqueMap.mapStringId
    blocks = listOf(
        generateSkillsBlock(role),
        generateItemsBlock("Starter Items", role?.startItems?.itemsId),
        generateItemsBlock("Mains Items", role?.mainsItems?.itemsId),
        generateSecondariesBlock("Fourth Items", role?.secondaryItems?.fourth),
        generateSecondariesBlock("Fifth Items", role?.secondaryItems?.fith),
        generateSecondariesBlock("Sixth Items", role?.secondaryItems?.sixth)
    )
}

private fun generateSecondariesBlock(title: String, itemsIn: List<ItemDatas2>?): LolItemSetsItemSetBlock =
    LolItemSetsItemSetBlock().apply {
        type = title
        items = itemsIn?.map { generateItem(it.itemId) }?.toList().orEmpty()
    }

private fun generateItem(idItem: Int): LolItemSetsItemSetItem = LolItemSetsItemSetItem().apply {
    id = "$idItem"
    count = 1
}

private fun generateItemsBlock(title: String, itemsIn: List<Int>?): LolItemSetsItemSetBlock =
    LolItemSetsItemSetBlock().apply {
        type = title
        items = itemsIn?.map { generateItem(it) }?.toList().orEmpty()
    }

private fun generateSkillsBlock(role: PositionDatas?): LolItemSetsItemSetBlock = LolItemSetsItemSetBlock().apply {
    type = "Skills ${role?.skillsOrder?.skillPriority} : ${role?.skillsOrder?.skillOrder}"
    items = listOf(3364, 3340, 3363, 2055).map { generateItem(it) }.toList()
}

private fun generateSummoner(role: PositionDatas?) = role?.name to role?.summonerSpells?.summonerSpells

private fun generatePerk(
    role: PositionDatas?,
    champion: LolChampionsCollectionsChampion?,
    uniqueMap: LolMapsMaps
): LolPerksPerkPageResource = LolPerksPerkPageResource().apply {
    primaryStyleId = role?.runes?.masteryA
    subStyleId = role?.runes?.masteryB
    selectedPerkIds = role?.runes?.additionnalMastery
    name = "GENERATED ${champion?.name} ${uniqueMap.mapStringId} ${role?.name}"
}

private fun getUggRankedOverviewDatas(champion: LolChampionsCollectionsChampion?): UggDatas? =
    Klaxon().parse<UggDatas>(
        EntityUtils.toString(
            httpclient.execute(HttpGet("https://stats2.u.gg/lol/1.1/overview/10_11/ranked_solo_5x5/${champion?.id}/1.4.0.json")).entity
        )
    )


private fun getUggAramOverviewDatas(champion: LolChampionsCollectionsChampion?): UggARAMDatas? =
    Klaxon().parse<UggARAMDatas>(
        EntityUtils.toString(
            httpclient.execute(HttpGet("https://stats2.u.gg/lol/1.1/overview/10_11/normal_aram/${champion?.id}/1.4.0.json")).entity
        )
    )

private fun getChampion(champId: Int): LolChampionsCollectionsChampion? = api.executeGet(
    "/lol-champions/v1/inventories/${summoner.summonerId}/champions/${champId}",
    LolChampionsCollectionsChampion::class.java
)


