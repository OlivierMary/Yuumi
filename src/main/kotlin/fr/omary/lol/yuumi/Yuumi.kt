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
val invocatorsSpellsByChamp: MutableMap<Int, List<Pair<String?, List<Int>?>>> = mutableMapOf()

var uniqueMaps = mutableListOf<LolMapsMaps>()
var lolItemSetsItemSets: LolItemSetsItemSets? = null
var summoner: LolSummonerSummoner? = null
var assignedPositon: String? = null
var gameMode: String? = null

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

fun stopYuumi(){
    sendLoLNotif("Disconnected", "Disconnected to Yuumi, See you later",1)
    socket?.close()
    api.stop()
}

private fun notifConnected() {
    sendLoLNotif("Connected", "Connected to Yuumi, Enjoy",11)
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
    do {
        setSummoner()
        sleep(1000)
    } while (summoner == null)

    val maps = api.executeGet("/lol-maps/v2/maps", Array<LolMapsMaps>::class.java)
    val uniqueMapsId = maps.map(LolMapsMaps::id).distinct()
    uniqueMaps = uniqueMapsId.map { id -> maps.first { m -> m.id == id } }.toMutableList()
    for (uniqueMap in uniqueMaps) {
        println("Map : [${uniqueMap.gameMode}, ${uniqueMap.mapStringId}, ${uniqueMap.gameModeName}] ")
    }
    lolItemSetsItemSets = api.executeGet(
        "/lol-item-sets/v1/item-sets/${summoner?.summonerId}/sets",
        LolItemSetsItemSets::class.java
    )
}

private fun setSummoner() {
    summoner = api.executeGet("/lol-summoner/v1/current-summoner", LolSummonerSummoner::class.java)
    println("Summoner : ${summoner?.displayName}")
}

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
    )?.myTeam?.first { x -> x.summonerId == summoner?.summonerId }
    assignedPositon = me?.assignedPosition
    if (assignedPositon == null || assignedPositon == "") {
        assignedPositon = "ARAM"
    }
    println("Assigned position : $assignedPositon")
}

fun validateChampion(champId: Int) {
    startLoading("Send [${championsByIdChamp[champId]?.name}]")
    checkProcessedOrWait(champId)
    printStatus(champId)
    setPerks(champId)
    setItemsSets(champId)
    setInvocatorSpells(champId)
    sendSystemNotification("Champ set ${championsByIdChamp[champId]?.name} : Sent","INFO")
    stopLoading()
}

fun printStatus(champId: Int) {
    println("-----------------------------------------------------------")
    println("Run with:")
    println("Game Mode : $gameMode")
    println("Assigned Position : $assignedPositon")
    println("Champion : ${reflexToString(championsByIdChamp[champId])}")
    println("Perks : ${reflexToString(perksByIdChamp[champId])}")
    println("Items : ${reflexToString(itemsSetsByIdChamp[champId])}")
    println("Invocators Spells: ${reflexToString(invocatorsSpellsByChamp[champId])}")
    println("-----------------------------------------------------------")

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
    if (assignedPositon == null || assignedPositon == "") {
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
                p.current = assignedPositon?.let {
                    p.name.contains(it) && p.name.contains("GENERATED") && p.name.contains(championsByIdChamp[champId]!!.name)
                }
            }
        }
    }
}

private fun commitPerks(champId: Int) {
    for (perk in perksByIdChamp[champId]!!.sortedBy { it.current }) {
        val resultAddPerk = tryAddPerk(perk)
        if (!resultAddPerk && perk.current == true) {
            sendSystemNotification("Champ set ${championsByIdChamp[champId]?.name} : Not Enough runes pages","WARNING")
            resetGeneratedExistingPerks()
            tryAddPerk(perk)
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
    lolItemSetsItemSets?.itemSets?.clear()
    lolItemSetsItemSets?.itemSets?.addAll(itemsSetsByIdChamp[champId]!!)
}

private fun commitItemsSets() {
    println(
        "Items Set : ${api.executePut(
            "/lol-item-sets/v1/item-sets/${summoner?.summonerId}/sets",
            lolItemSetsItemSets
        )}"
    )
}

private fun setInvocatorSpells(champId: Int) {
    if (invocatorsSpellsByChamp.containsKey(champId) && invocatorsSpellsByChamp[champId] != null) {
        val spells = prepareInvocatorSpells(champId)
        commitInvocatorSpells(spells)
    } else {
        println("Invocator Skills : no datas")
    }
}

private fun prepareInvocatorSpells(champId: Int): JsonObject {
    val spells = JsonObject()
    if (assignedPositon != null && assignedPositon != "") {
        val invocatorsSpells =
            invocatorsSpellsByChamp[champId]?.first { p -> p.first == assignedPositon }?.second
        spells["spell1Id"] = invocatorsSpells?.get(0)
        spells["spell2Id"] = invocatorsSpells?.get(1)
    }
    return spells
}

private fun commitInvocatorSpells(spells: JsonObject) {
    println(
        "Invocator Skills : ${api.executePatch(
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

    startLoading("Process [${champion.name}]...")
    println("${champion.name} process...")

    val rankedDatas = getUggRankedOverviewDatas(champion)
    val aramDatas = getUggAramOverviewDatas(champion)

    val newItemsList = mutableListOf<LolItemSetsItemSet>()
    val newPerks = mutableListOf<LolPerksPerkPageResource>()
    val newInvocators = mutableListOf<Pair<String?, List<Int>?>>()


    for (uniqueMap in uniqueMaps) {
        when (uniqueMap.mapStringId) {
            "SR" -> {
                for (role in rankedDatas?.world?.platPlus?.allRoles()!!) {
                    newItemsList.add(generateItemsSet(champion, uniqueMap, role))
                    newPerks.add(generatePerk(role, champion, uniqueMap))
                    newInvocators.add(generateInvocator(role))
                }
            }
            "HA" -> {
                newItemsList.add(generateItemsSet(champion, uniqueMap, aramDatas?.world?.aram?.aram()))
                newPerks.add(generatePerk(aramDatas?.world?.aram?.aram(), champion, uniqueMap))
                newInvocators.add(generateInvocator(aramDatas?.world?.aram?.aram()))
            }
            else -> {
            }

        }

    }
    itemsSetsByIdChamp[champId] = newItemsList
    perksByIdChamp[champId] = newPerks
    invocatorsSpellsByChamp[champId] = newInvocators
    println("${champion.name} processed")
    refreshChampTray()
    stopLoading()
}

fun refreshChampTray(){
    refreshChampionList(championsByIdChamp.values.sortedBy { it.name }.map { Pair(it.id,it.name) })
}

private fun generateItemsSet(
    champion: LolChampionsCollectionsChampion?,
    uniqueMap: LolMapsMaps,
    role: PositionDatas?
): LolItemSetsItemSet {
    val itemSetnew = LolItemSetsItemSet()
    itemSetnew.title = "${champion?.name} ${uniqueMap.mapStringId} ${role?.name}"
    itemSetnew.associatedMaps = listOf(uniqueMap.id.toInt())
    itemSetnew.associatedChampions = listOf(champion?.id)
    itemSetnew.map = uniqueMap.mapStringId
    itemSetnew.blocks = listOf(
        generateSkillsBlock(role),
        generateItemsBlock("Starter Items", role?.startItems?.itemsId),
        generateItemsBlock("Mains Items", role?.mainsItems?.itemsId),
        generateSecondariesBlock("Fourth Items", role?.secondaryItems?.fourth),
        generateSecondariesBlock("Fifth Items", role?.secondaryItems?.fith),
        generateSecondariesBlock("Sixth Items", role?.secondaryItems?.sixth)
    )
    return itemSetnew
}

private fun generateSecondariesBlock(title: String, items: List<ItemDatas2>?): LolItemSetsItemSetBlock {
    val result = LolItemSetsItemSetBlock()
    result.type = title
    result.items = items?.map { generateItem(it.itemId) }?.toList().orEmpty()
    return result
}

private fun generateItem(id: Int): LolItemSetsItemSetItem {
    val item = LolItemSetsItemSetItem()
    item.id = "$id"
    item.count = 1
    return item
}

private fun generateItemsBlock(title: String, items: List<Int>?): LolItemSetsItemSetBlock {
    val result = LolItemSetsItemSetBlock()
    result.type = title
    result.items = items?.map { generateItem(it) }?.toList().orEmpty()
    return result
}

private fun generateSkillsBlock(role: PositionDatas?): LolItemSetsItemSetBlock {
    val skills = LolItemSetsItemSetBlock()
    skills.type = "Skills ${role?.skillsOrder?.skillPriority} : ${role?.skillsOrder?.skillOrder}"
    skills.items = listOf(3364, 3340, 3363, 2055).map { generateItem(it) }.toList()
    return skills
}

private fun generateInvocator(role: PositionDatas?) = Pair(role?.name, role?.invoc?.invocators)

private fun generatePerk(
    role: PositionDatas?,
    champion: LolChampionsCollectionsChampion?,
    uniqueMap: LolMapsMaps
): LolPerksPerkPageResource {
    val perk = LolPerksPerkPageResource()
    perk.primaryStyleId = role?.runes?.masteryA
    perk.subStyleId = role?.runes?.masteryB
    perk.selectedPerkIds = role?.runes?.additionnalMastery
    perk.name = "GENERATED ${champion?.name} ${uniqueMap.mapStringId} ${role?.name}"
    return perk
}

private fun getUggRankedOverviewDatas(champion: LolChampionsCollectionsChampion?): UggDatas? {
    val response =
        httpclient.execute(HttpGet("https://stats2.u.gg/lol/1.1/overview/10_11/ranked_solo_5x5/${champion?.id}/1.4.0.json"))
    return Klaxon().parse<UggDatas>(EntityUtils.toString(response.entity))
}

private fun getUggAramOverviewDatas(champion: LolChampionsCollectionsChampion?): UggARAMDatas? {
    val response =
        httpclient.execute(HttpGet("https://stats2.u.gg/lol/1.1/overview/10_11/normal_aram/${champion?.id}/1.4.0.json"))
    return Klaxon().parse<UggARAMDatas>(EntityUtils.toString(response.entity))
}

private fun getChampion(champId: Int): LolChampionsCollectionsChampion? {
    val result = api.executeGet(
        "/lol-champions/v1/inventories/${summoner?.summonerId}/champions/${champId}",
        LolChampionsCollectionsChampion::class.java
    )
    championsByIdChamp[champId] = result
    return result
}


