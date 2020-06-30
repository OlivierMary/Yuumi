package fr.omary.lol.yuumi

import fr.omary.lol.yuumi.models.Champion
import fr.omary.lol.yuumi.models.Rank
import fr.omary.lol.yuumi.models.YPosition
import fr.omary.lol.yuumi.models.Zone
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.apache.http.impl.client.CloseableHttpClient
import org.apache.http.impl.client.HttpClients
import java.awt.AWTException
import java.awt.Image
import java.awt.SystemTray
import java.awt.TrayIcon
import java.awt.event.ActionListener
import java.awt.event.ItemEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.io.File
import java.io.FileReader
import java.io.FileWriter
import java.lang.System.getenv
import java.time.LocalDateTime
import java.util.*
import javax.swing.*
import kotlin.system.exitProcess


// Source Example : https://docs.oracle.com/javase/tutorial/displayCode.html?code=https://docs.oracle.com/javase/tutorial/uiswing/examples/misc/TrayIconDemoProject/src/misc/TrayIconDemo.java

var sendNotifications: Boolean = true
private val connected = createImage("images/Yuumi.png", "tray icon")
private val waiting = createImage("images/waitingConnect.png", "tray icon")
private val loading = createImage("images/loading.gif", "tray icon")
private val automaticDisabled = createImage("images/automaticDisabled.png", "tray icon")
private val trayIcon = TrayIcon(waiting.image)
private val messages: MutableList<Pair<LocalDateTime, String>> = mutableListOf()
private var champMenu = JMenu("Send Champion")
private var rankMenu = JMenu("Rank")
private var zoneMenu = JMenu("Zone")
private var champMenuAtoF = JMenu("[A-F]")
private var champMenuGtoK = JMenu("[G-K]")
private var champMenuNtoQ = JMenu("[L-Q]")
private var champMenuRtoS = JMenu("[R-S]")
private var champMenuTtoZ = JMenu("[T-Z]")
private const val yuumi = "Yuumi"
private const val defaultToolTip = "$yuumi is ready"
private const val waitingMessage = "Yuumi : Waiting LoL client to connect"
private const val lastSyncMessage = "Refresh - Last Sync : "
private val tmpDir = getenv("TMP")
private val yuumiTempDir = "${tmpDir}/yuumi"
private val yuumiConfig = "$yuumiTempDir/config"
val rankedDirectory = File("$yuumiTempDir/ranked")
val aramDirectory = File("$yuumiTempDir/aram")
val squareDirectory = File("$yuumiTempDir/square")
val lastSync: File? = File("$yuumiTempDir/lastSync")
var lastSyncDate: String = "Never"
var sync = JMenuItem("$lastSyncMessage $lastSyncDate")
var automatic: Boolean = true
var currentState = waiting
var currentZone: Zone = Zone.ALL
var currentRank: Rank = Rank.PLATINE_PLUS
val httpclient: CloseableHttpClient = HttpClients.createDefault()

fun main() {
    setupLookAndFeel()
    createTempsDirs()
    loadConfig()
    SwingUtilities.invokeLater { createAndShowGUI() }
    refreshChampionList()
    refreshLastSyncDate()
    automaticReSyncIfDataTooOld()
    startYuumi()
}

fun loadConfig() {
    if (File(yuumiConfig).exists()) {
        val properties = Properties()
        properties.load(FileReader(yuumiConfig))
        if (properties["sendNotifications"] != null) {
            sendNotifications = properties["sendNotifications"].toString().toBoolean()
        }
        if (properties["automatic"] != null) {
            automatic = properties["automatic"].toString().toBoolean()
        }
        setAutomaticIcon()
        if (properties["zone"] != null) {
            currentZone = Zone.values().first { z -> z.keyUgg == properties["zone"].toString().toInt() }
        }
        if (properties["rank"] != null) {
            currentRank = Rank.values().first { r -> r.keyUgg == properties["rank"].toString().toInt() }
        }
    }
}

fun storeConfig() {
    val properties = Properties()
    properties["sendNotifications"] = sendNotifications.toString()
    properties["automatic"] = automatic.toString()
    properties["zone"] = currentZone.keyUgg.toString()
    properties["rank"] = currentRank.keyUgg.toString()
    properties.store(FileWriter(yuumiConfig), "Store Properties")
}

private fun setupLookAndFeel() {
    try {
        UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName())
    } catch (ex: UnsupportedLookAndFeelException) {
        ex.printStackTrace()
    } catch (ex: IllegalAccessException) {
        ex.printStackTrace()
    } catch (ex: InstantiationException) {
        ex.printStackTrace()
    } catch (ex: ClassNotFoundException) {
        ex.printStackTrace()
    }
    UIManager.put("swing.boldMetal", false)
}

private fun refreshLastSyncDate() {
    lastSyncDate = if (lastSync?.exists()!!) {
        lastSync.readText()
    } else {
        "Never"
    }
    sync.label = "$lastSyncMessage $lastSyncDate"
}

private fun createTempsDirs() {
    rankedDirectory.mkdirs()
    aramDirectory.mkdirs()
    squareDirectory.mkdirs()
}


private fun createAndShowGUI() {
    if (!SystemTray.isSupported()) {
        println("SystemTray is not supported")
        return
    }
    val popupMenu = JPopupMenu()

    val tray = SystemTray.getSystemTray()

    val aboutItem = JMenuItem("About")
    val history = JMenuItem("History")

    val optionNotifications = JCheckBoxMenuItem("Enable Notifications")
    optionNotifications.state = sendNotifications
    val automaticSelection = JCheckBoxMenuItem("Automatic selection")
    automaticSelection.state = automatic
    val exitItem = JMenuItem("Exit")

    popupMenu.add(aboutItem)
    popupMenu.addSeparator()
    popupMenu.add(optionNotifications)
    popupMenu.add(automaticSelection)
    popupMenu.add(history)
    popupMenu.addSeparator()
    popupMenu.add(sync)
    popupMenu.add(zoneMenu)
    popupMenu.add(rankMenu)
    popupMenu.addSeparator()
    popupMenu.add(champMenu)
    popupMenu.addSeparator()
    popupMenu.add(exitItem)

    champMenu.add(champMenuAtoF)
    champMenu.add(champMenuGtoK)
    champMenu.add(champMenuNtoQ)
    champMenu.add(champMenuRtoS)
    champMenu.add(champMenuTtoZ)

    Zone.values().sortedBy { zone -> zone.title }.forEach { zone -> zoneMenu.add(generateZoneMenu(zone)) }
    Rank.values().sortedBy { rank -> rank.title }.forEach { rank -> rankMenu.add(generateRankMenu(rank)) }

    updateOptionsMenu()


    trayIcon.addMouseListener(object : MouseAdapter() {
        override fun mouseReleased(e: MouseEvent) {
            maybeShowPopup(e)
        }

        override fun mousePressed(e: MouseEvent) {
            maybeShowPopup(e)
        }

        private fun maybeShowPopup(e: MouseEvent) {
            if (e.isPopupTrigger()) {
                popupMenu.setLocation(e.getX(), e.getY() - 250)
                popupMenu.setInvoker(popupMenu)
                popupMenu.setVisible(true)
            }
        }
    })

    trayIcon.isImageAutoSize = true
    trayIcon.toolTip = waitingMessage
    try {
        tray.add(trayIcon)
    } catch (e: AWTException) {
        println("TrayIcon could not be added.")
        throw e
    }
    aboutItem.addActionListener {
        showYuumiDialog(
            """
                        Yuumi's informations:
                        Version : [$VERSION]
                        Commit : [$GIT_SHA]
                        Date Commit : [$GIT_DATE]
                        Date Build : [$BUILD_DATE]
                        Dirty : [$DIRTY]
                    """.trimIndent(),
            "About Yuumi"
        )
    }
    history.addActionListener {
        showHistory()
    }

    sync.addActionListener {
        forceReSyncChampsDatas()
    }

    optionNotifications.addItemListener { e ->
        sendNotifications = e.stateChange == ItemEvent.SELECTED
        storeConfig()
    }
    automaticSelection.addItemListener { e ->
        automatic = e.stateChange == ItemEvent.SELECTED
        setAutomaticIcon()
        storeConfig()
    }

    exitItem.addActionListener {
        stopYuumi()
        tray.remove(trayIcon)
        exitProcess(0)
    }
}

private fun updateOptionsMenu() {
    zoneMenu.label = "Zone [ ${currentZone.title} ]"
    rankMenu.label = "Rank [ ${currentRank.title} ]"
    storeConfig()
}

fun automaticReSyncIfDataTooOld() {
    if (lastSyncDate == "Never" || LocalDateTime.now().isAfter(LocalDateTime.parse(lastSyncDate).plusDays(1))) {
        println("Force resync")
        forceReSyncChampsDatas()
        refreshLastSyncDate()
    }
}

fun forceReSyncChampsDatas() {
    startLoading("Retrieve all champions assets")
    rankedDirectory.list()?.forEach { File("$rankedDirectory/$it").delete() }
    aramDirectory.list()?.forEach { File("$aramDirectory/$it").delete() }
    squareDirectory.list()?.forEach { File("$squareDirectory/$it").delete() }
    getChampionList(true) // reset cache of champions
    lastSync?.writeText(LocalDateTime.now().toString())
    refreshLastSyncDate()
    refreshChampionList()
}


private fun showYuumiDialog(message: String, title: String) {
    JOptionPane.showMessageDialog(
        null,
        message,
        title,
        0,
        connected
    )
}

private fun showHistory() {
    showYuumiDialog(
        "History: \n${messages.joinToString("\n")}",
        "Yuumi History"
    )
}

fun createImage(path: String, description: String?): ImageIcon =
    ImageIcon(Thread.currentThread().contextClassLoader.getResource(path), description)

fun createImage(file: File): ImageIcon = resizeIcon(ImageIcon(file.path))

fun createImage(path: String): ImageIcon =
    resizeIcon(ImageIcon(Thread.currentThread().contextClassLoader.getResource(path)))

private fun resizeIcon(i: ImageIcon): ImageIcon {
    val image: Image = i.image
    val newimg = image.getScaledInstance(20, 20, Image.SCALE_SMOOTH)
    return ImageIcon(newimg)
}

fun sendSystemNotification(message: String, level: String) {
    if (sendNotifications) {
        messages.add(Pair(LocalDateTime.now(), message))
        trayIcon.displayMessage(
            yuumi,
            message, TrayIcon.MessageType.valueOf(level.toUpperCase())
        )
    }
}

fun waitingConnect() {
    currentState = waiting
    trayIcon.image = currentState.image
    trayIcon.toolTip = waitingMessage
}

fun connect() {
    currentState = connected
    trayIcon.image = currentState.image
    trayIcon.toolTip = defaultToolTip
}

fun setAutomaticIcon() {
    currentState = if (automatic) {
        if (apiIsConnected()) {
            connected
        } else {
            waiting
        }
    } else {
        automaticDisabled
    }
    trayIcon.image = currentState.image
    trayIcon.toolTip = if (automatic) {
        defaultToolTip
    } else {
        "Automatic disabled"
    }
}

fun startLoading(actionMessage: String = "Processing...") {
    trayIcon.image = loading.image
    trayIcon.toolTip = actionMessage
}

fun ready() {
    trayIcon.image = currentState.image
    trayIcon.toolTip = defaultToolTip
}

fun refreshChampionList() {
    champMenuAtoF.removeAll()
    champMenuGtoK.removeAll()
    champMenuNtoQ.removeAll()
    champMenuRtoS.removeAll()
    champMenuTtoZ.removeAll()
    getChampionList().forEach {
        when (it.name.first()) {
            in 'A'..'F' -> champMenuAtoF.add(generateChampionMenu(it))
            in 'G'..'K' -> champMenuGtoK.add(generateChampionMenu(it))
            in 'L'..'Q' -> champMenuNtoQ.add(generateChampionMenu(it))
            in 'R'..'S' -> champMenuRtoS.add(generateChampionMenu(it))
            in 'T'..'Z' -> champMenuTtoZ.add(generateChampionMenu(it))
        }
        GlobalScope.launch { processChampion(it) }
    }
}

fun generateChampPosition(champ: Champion, position: YPosition): JMenuItem {
    val menuItem = JMenuItem(position.title)
    menuItem.icon = createImage("images/${position.iconName}")
    val listener = ActionListener {
        GlobalScope.launch {
            sendChampionPostion(champ, position, getCurrentGameMode().await())
        }
    }
    menuItem.addActionListener(listener)
    return menuItem
}


fun generateChampionMenu(champ: Champion): JMenu {
    val menuChamp = JMenu(champ.name)
    val champFile = File("$squareDirectory/${champ.key}.png")
    if (champFile.exists()) {
        menuChamp.icon = createImage(champFile)
    }
    YPosition.values().forEach {
        menuChamp.add(generateChampPosition(champ, it))
    }
    return menuChamp
}

fun generateZoneMenu(zon: Zone): JMenuItem {
    val menuZone = JMenuItem(zon.title)
    menuZone.addActionListener {
        currentZone = zon
        updateOptionsMenu()
    }
    return menuZone
}

fun generateRankMenu(ran: Rank): JMenuItem {
    val menuRank = JMenuItem(ran.title)
    menuRank.addActionListener {
        currentRank = ran
        updateOptionsMenu()
    }
    return menuRank
}

