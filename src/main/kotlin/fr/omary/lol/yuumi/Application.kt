package fr.omary.lol.yuumi

import fr.omary.lol.yuumi.models.Champion
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.apache.http.impl.client.CloseableHttpClient
import org.apache.http.impl.client.HttpClients
import java.awt.*
import java.awt.event.ActionListener
import java.awt.event.ItemEvent
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
private var champMenu = Menu("Send Synchronized Champion")
private var champMenuAtoG = Menu("[A-G]")
private var champMenuHtoM = Menu("[H-M]")
private var champMenuNtoS = Menu("[N-S]")
private var champMenuTtoZ = Menu("[T-Z]")
private const val yuumi = "Yuumi"
private const val defaultToolTip = "$yuumi is ready"
private const val waitingMessage = "Yuumi : Waiting LoL client to connect"
private const val lastSyncMessage = "Refresh - Last Sync : "
private val tmpDir = getenv("TMP")
private val yuumiTempDir = "${tmpDir}/yuumi"
private val yuumiConfig = "$yuumiTempDir/config"
val rankedDirectory = File("$yuumiTempDir/ranked")
val aramDirectory = File("$yuumiTempDir/aram")
val lastSync: File? = File("$yuumiTempDir/lastSync")
var lastSyncDate: String = "Never"
var sync = MenuItem("$lastSyncMessage $lastSyncDate")
var automatic: Boolean = true
var currentState = waiting
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
    }
}

fun storeConfig() {
    val properties = Properties()
    properties["sendNotifications"] = sendNotifications.toString()
    properties["automatic"] = automatic.toString()
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
}


private fun createAndShowGUI() {
    if (!SystemTray.isSupported()) {
        println("SystemTray is not supported")
        return
    }
    val popupMenu = PopupMenu()

    val tray = SystemTray.getSystemTray()

    val aboutItem = MenuItem("About")
    val history = MenuItem("History")

    val optionNotifications = CheckboxMenuItem("Enable Notifications")
    optionNotifications.state = sendNotifications
    val automaticSelection = CheckboxMenuItem("Automatic selection")
    automaticSelection.state = automatic
    val exitItem = MenuItem("Exit")

    popupMenu.add(aboutItem)
    popupMenu.addSeparator()
    popupMenu.add(optionNotifications)
    popupMenu.add(automaticSelection)
    popupMenu.add(history)
    popupMenu.add(sync)
    popupMenu.addSeparator()
    popupMenu.add(champMenu)
    popupMenu.addSeparator()
    popupMenu.add(exitItem)

    champMenu.add(champMenuAtoG)
    champMenu.add(champMenuHtoM)
    champMenu.add(champMenuNtoS)
    champMenu.add(champMenuTtoZ)

    trayIcon.popupMenu = popupMenu
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
        runBlocking {
            forceReSyncChampsDatas()
        }
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

fun automaticReSyncIfDataTooOld() {
    if (lastSyncDate == "Never" || LocalDateTime.now().isAfter(LocalDateTime.parse(lastSyncDate).plusDays(1))) {
        println("Force resync")
        forceReSyncChampsDatas()
        refreshLastSyncDate()
    }
}

fun forceReSyncChampsDatas() {
    //rankedDirectory.list()?.forEach { File("$rankedDirectory/$it").delete() }
    //aramDirectory.list()?.forEach { File("$aramDirectory/$it").delete() }
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
    champMenuAtoG.removeAll()
    champMenuHtoM.removeAll()
    champMenuNtoS.removeAll()
    champMenuTtoZ.removeAll()
    getChampionList().forEach {
        when (it.name.first()) {
            in 'A'..'G' -> champMenuAtoG.add(generateItemMenu(it))
            in 'H'..'M' -> champMenuHtoM.add(generateItemMenu(it))
            in 'N'..'S' -> champMenuNtoS.add(generateItemMenu(it))
            in 'T'..'Z' -> champMenuTtoZ.add(generateItemMenu(it))
        }
        GlobalScope.launch { processChampion(it) }
    }
}

fun generateChampPosition(champ: Champion, position: String): MenuItem {
    val menuItem = MenuItem(position)
    val listener = ActionListener {
        GlobalScope.launch {
            sendChampionPostion(champ, position.toLowerCase(), getCurrentGameMode().await())
        }
    }
    menuItem.addActionListener(listener)
    return menuItem
}


fun generateItemMenu(champ: Champion): MenuItem {
    val menuChamp = Menu(champ.name)
    listOf("Top", "Jungle", "Middle", "Bottom", "Utility", "ARAM").forEach {
        menuChamp.add(generateChampPosition(champ, it))
    }
    return menuChamp
}
