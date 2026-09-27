package com.scooterre.client.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** One card of the in-app help: a heading and a few short, self-contained points - not prose. */
data class HelpSection(val title: String, val bullets: List<String>)

fun helpTitle(lang: Lang) = if (lang == Lang.DE) "Hilfe" else "Help"
fun helpIntro(lang: Lang) = if (lang == Lang.DE) {
    "Kurze Erklärung zu jedem Bereich der App. Für mehr Tiefe (Einrichtung, Fehlerbehebung, Modell-Liste) siehe die vollständige Dokumentation."
} else {
    "A short explanation of every part of the app. For more depth (setup, troubleshooting, model list) see the full documentation."
}
fun helpFullDocsLabel(lang: Lang) = if (lang == Lang.DE) "Vollständige Dokumentation auf GitHub" else "Full documentation on GitHub"
fun helpDocUrl(lang: Lang) = if (lang == Lang.DE) {
    "https://github.com/desperado0044/xiaomi-scooter-link/blob/master/docs/HELP.de.md"
} else {
    "https://github.com/desperado0044/xiaomi-scooter-link/blob/master/docs/HELP.md"
}

fun helpSections(lang: Lang): List<HelpSection> = if (lang == Lang.DE) helpSectionsDe else helpSectionsEn

private val helpSectionsDe = listOf(
    HelpSection(
        "🏠 Übersicht",
        listOf(
            "Die große blaue Zahl ist die Reichweite: entweder nach deinem eigenen gemessenen Verbrauch (sobald " +
                "genug Fahrten in diesem Modus aufgezeichnet sind) oder, solange das noch nicht der Fall ist, die " +
                "Schätzung des Scooters selbst - die dann immer als kleine Zahl darunter mit angezeigt wird.",
            "Die grüne Zahl ist der Akkustand. Lädt der Scooter, steht dort „Lädt gerade\" statt „Akkustand\".",
            "Der Fahrmodus (Walk/Drive/Sport) und die Rekuperation lassen sich direkt umschalten.",
            "Die Kacheln darunter (Fahrstrecke, Fahrzeit, Ø-/Max-Tempo, Sperre, Scooter-Suche) zeigen die wichtigsten " +
                "Werte auf einen Blick; „Fahrzeit\" ist dabei die eigene Fahrzeit der App (siehe unten), nicht die " +
                "des Scooters.",
        ),
    ),
    HelpSection(
        "🛴 Fahrt",
        listOf(
            "Alle rohen Fahrwerte des Scooters: Fahrzustand, Geschwindigkeiten, Fahrstrecke, Kilometerstand, Fehlerstatus.",
            "Die Fahrzeit hier ist der Wert des Scooters selbst - er springt bei einem kurzen Halt (z. B. an einer " +
                "Ampel) zurück auf 0. Die Übersicht zeigt stattdessen die eigene Fahrzeit der App, die einen kurzen " +
                "Halt übersteht und erst nach 10 Minuten Stillstand oder beim Trennen endet.",
        ),
    ),
    HelpSection(
        "🔋 Akku",
        listOf(
            "Akkustand, Restkapazität, Spannung, Strom, Leistung, Temperatur, Ladezustand, Ladezyklen und Akku-Gesundheit (SOH).",
            "Diese Werte werden nur live (alle paar Sekunden) gelesen, solange dieser Tab offen ist - sonst nur einmal pro Minute.",
        ),
    ),
    HelpSection(
        "⚙️ Einstellungen",
        listOf(
            "Fahrmodus, Sperre, Tempomat, Rücklicht, Rekuperation, ASR, Auto-Licht, TCS, Bergab-Assistent, " +
                "Bergstütze, Ambiente-Licht, Bluetooth-Suche und die Anzeigeeinheit des Scooters.",
            "Bei Tempomat und Rücklicht erscheint vor dem Einschalten ein Hinweis, weil deren Erlaubtheit je nach " +
                "Land/Region unterschiedlich ist - die App kann das nicht für dich beurteilen.",
        ),
    ),
    HelpSection(
        "🚨 Fahrzeug",
        listOf(
            "Scootertemperatur, Schloss-Warnsignal, Reifenwartung (Erinnerung ein/aus, Intervall) und der Ruhezustand.",
            "Meldet der Scooter seinen Ruhezustand, zeigt das ganze Dashboard „Standby\" statt Werten - sie wären " +
                "sonst eingefroren (der Scooter meldet z. B. weiter „lädt\", obwohl der Stecker längst gezogen ist). " +
                "Nur „Scooter suchen\" bleibt bedienbar.",
        ),
    ),
    HelpSection(
        "🪪 Identifikation",
        listOf("Herstellungsdatum, Aktivierungsdatum, Seriennummern und Firmware-Versionen von Scooter und Akku - reine Anzeige, nichts einstellbar."),
    ),
    HelpSection(
        "📖 Fahrtenbuch",
        listOf(
            "Der Scooter merkt sich selbst nur die letzten 5 Fahrten und überschreibt die ältesten. Diese App kopiert " +
                "neue Fahrten bei jedem Auslesen in ein eigenes Fahrtenbuch auf dem Handy, damit keine verloren gehen.",
            "Nach Tagen gruppiert, mit Strecke, Fahrzeit und Durchschnittstempo. Fahrten, die schon vor dem ersten " +
                "Start der App im Scooter waren, haben kein Datum.",
            "Als Textdatei exportierbar. Eine Fahrt, die aus den 5 Plätzen des Scooters herausgerutscht ist, bevor " +
                "die App ihn wieder ausliest, ist unwiederbringlich verloren.",
        ),
    ),
    HelpSection(
        "📈 Verlauf",
        listOf(
            "Zeigt den eigenen, gemessenen Verbrauch je Fahrmodus (nicht die Schätzung des Scooters) - berechnet " +
                "aus echten Fahrten der letzten 300 km, die live aufgezeichnet wurden (Handy verbunden, App offen, " +
                "„Fahrt-Aufzeichnung\" in den App-Einstellungen an). Das ist die Grundlage für die Reichweite auf der Übersicht.",
            "Darunter ein Diagramm und Karten der letzten Fahrten mit Verbrauch, Strecke, Fahrzeit und Tempo.",
            "Wichtig: Diese Fahrtenliste stammt aus einer anderen Quelle als das Fahrtenbuch (siehe oben) - sie " +
                "zählt nur, solange das Handy verbunden ist, während das Fahrtenbuch die Erinnerung des Scooters " +
                "selbst kopiert. Die beiden Listen müssen nicht exakt übereinstimmen.",
            "Die Akku-Gesundheit darunter bekommt nur eine neue Zeile, wenn sich Gesundheit oder Zyklen ändern.",
            "„Verlauf zurücksetzen\" löscht die aufgezeichnete Strecke/den Verbrauch unwiderruflich - sinnvoll z. B. " +
                "nach einem Akkutausch, damit alte Werte die neue Rechnung nicht verfälschen.",
        ),
    ),
    HelpSection(
        "🎛️ App-Einstellungen",
        listOf(
            "Sprache, Design (hell/dunkel/System), Bildschirmausrichtung, automatische Helligkeit, Einheiten (metrisch/imperial).",
            "„Bildschirm anlassen\": verhindert das Abschalten des Displays, solange der Scooter verbunden ist und " +
                "die App im Vordergrund bleibt.",
            "„Overlay mit Restkilometern\": ein kleines, verschiebbares Fenster über anderen Apps (z. B. einer " +
                "Navigations-App) mit Reichweite, Akkustand, Fahrstrecke und eigener Fahrzeit - läuft auch im " +
                "Hintergrund weiter, solange der Scooter verbunden bleibt. Braucht einmalig die Berechtigung " +
                "„Über anderen Apps einblenden\". Antippen holt die App zurück, Ziehen verschiebt es.",
            "„Aktualisierung im Stand\": wie oft Werte im Stand neu gelesen werden, die im Stand wenig eilig sind " +
                "(Fahrstatus, Akkustand und Fahrmodus werden immer alle 2,5 s gelesen, egal welche Einstellung).",
            "„Automatisch verbinden\": verbindet beim App-Start gleich mit dem zuletzt genutzten Scooter.",
            "„Bestätigung vor kritischen Aktionen\": fragt vor dem Sperren/Entsperren einmal nach.",
            "„Fahrt-Aufzeichnung\": schaltet die eigene Verbrauchsanalyse (Verlauf-Tab, eigene Reichweite) ein oder aus.",
            "„Nach Updates suchen\": prüft täglich auf GitHub nach einer neueren Version.",
            "App-Sperre: verlangt Fingerabdruck oder Geräte-PIN beim App-Start.",
            "Versicherungserinnerung: optionale Erinnerung ans Kennzeichen erneuern.",
            "Sicherung/Wiederherstellung: alle Scooter mit Schlüsseln, Dokumenten, Verlauf und Einstellungen in " +
                "einer Datei - bleibt komplett auf dem Handy, es gibt keinen eigenen Server.",
            "„Werte erkunden\": ein technischer, reiner Lesebericht aller Rohwerte - nützlich für einen Fehlerbericht, " +
                "nicht für den Alltag gedacht.",
        ),
    ),
)

private val helpSectionsEn = listOf(
    HelpSection(
        "🏠 Overview",
        listOf(
            "The big blue number is the range: at your own measured consumption once enough rides in that mode " +
                "are recorded, or, until then, the scooter's own estimate - which is then always shown as a small " +
                "number underneath.",
            "The green number is the battery level. While charging it says \"Charging now\" instead of \"Battery level\".",
            "Riding mode (Walk/Drive/Sport) and recuperation can be switched right here.",
            "The tiles below (trip, ride time, avg/top speed, lock, find scooter) show the key values at a glance; " +
                "\"Ride time\" there is the app's own ride timer (see below), not the scooter's own.",
        ),
    ),
    HelpSection(
        "🛴 Ride",
        listOf(
            "All the scooter's raw ride values: riding state, speeds, trip, odometer, fault status.",
            "The ride time here is the scooter's own value - it jumps back to 0 on a short stop (e.g. a red " +
                "light). The overview shows the app's own ride timer instead, which survives a short stop and " +
                "only ends after 10 minutes standing still or on disconnect.",
        ),
    ),
    HelpSection(
        "🔋 Battery",
        listOf(
            "Battery level, remaining capacity, voltage, current, power, temperature, charging state, cycles and battery health (SOH).",
            "These are only read live (every few seconds) while this tab is open - otherwise just once a minute.",
        ),
    ),
    HelpSection(
        "⚙️ Settings",
        listOf(
            "Riding mode, lock, cruise control, tail light, recuperation, ASR, auto light, TCS, downhill assist, " +
                "hill parking, ambient light, Bluetooth search and the scooter's own display unit.",
            "Cruise control and the tail light show a notice before turning on, because whether they're allowed " +
                "depends on your country/region - the app can't judge that for you.",
        ),
    ),
    HelpSection(
        "🚨 Vehicle",
        listOf(
            "Scooter temperature, lock warning, tyre maintenance (reminder on/off, interval) and the sleep state.",
            "When the scooter reports its sleep state, the whole dashboard shows \"Standby\" instead of values - " +
                "they would otherwise be frozen (e.g. it keeps saying \"charging\" long after the plug was pulled). " +
                "Only \"Find scooter\" stays usable.",
        ),
    ),
    HelpSection(
        "🪪 Identification",
        listOf("Production date, activation date, serial numbers and firmware versions of the scooter and battery - display only, nothing settable."),
    ),
    HelpSection(
        "📖 Ride log",
        listOf(
            "The scooter itself only remembers its last 5 rides and overwrites the oldest. This app copies new " +
                "rides into its own ride book on the phone every time it reads them, so none get lost.",
            "Grouped by day, with distance, ride time and average speed. Rides that were already in the scooter " +
                "before the app's first start have no date.",
            "Exportable as a text file. A ride that has already dropped out of the scooter's 5 slots before the " +
                "app reads it again is lost for good.",
        ),
    ),
    HelpSection(
        "📈 History",
        listOf(
            "Shows your own measured consumption per riding mode (not the scooter's estimate) - calculated from " +
                "real rides over the last 300 km, recorded live (phone connected, app open, \"ride tracking\" on " +
                "in App settings). This is what the overview's own range is based on.",
            "Below that, a chart and cards of recent rides with consumption, distance, ride time and speed.",
            "Important: this ride list comes from a different source than the ride book (above) - it only counts " +
                "while the phone stays connected, while the ride book copies the scooter's own memory. The two " +
                "lists don't have to match exactly.",
            "The battery health table below only gets a new row when health or cycles actually change.",
            "\"Reset history\" permanently deletes the recorded distance/consumption - useful e.g. after a battery " +
                "replacement, so old values don't skew the new calculation.",
        ),
    ),
    HelpSection(
        "🎛️ App settings",
        listOf(
            "Language, theme (light/dark/system), screen orientation, automatic brightness, units (metric/imperial).",
            "\"Keep screen on\": stops the display from turning off while the scooter is connected and the app stays in the foreground.",
            "\"Range overlay\": a small, draggable window over other apps (e.g. a navigation app) with the range, " +
                "battery level, trip distance and the app's own ride time - keeps running in the background too, " +
                "as long as the scooter stays connected. Needs the \"display over other apps\" permission once. " +
                "Tap it to bring the app back, drag it to move it.",
            "\"Refresh while parked\": how often values that aren't urgent while parked are re-read (riding state, " +
                "battery level and riding mode are always read every 2.5 s regardless of this setting).",
            "\"Connect automatically\": connects to the last used scooter right when the app starts.",
            "\"Confirm before critical actions\": asks once before locking/unlocking.",
            "\"Ride tracking\": turns your own consumption analysis (History tab, own range) on or off.",
            "\"Check for updates\": checks GitHub once a day for a newer version.",
            "App lock: requires fingerprint or device PIN when the app starts.",
            "Insurance reminder: an optional reminder to renew a registration plate.",
            "Backup/restore: every scooter with its keys, documents, history and settings in one file - stays " +
                "entirely on the phone, there is no server of this project's own.",
            "\"Explore values\": a technical, read-only report of every raw value - useful for a bug report, not meant for everyday use.",
        ),
    ),
)

/** The in-app help: one card per dashboard tab / settings screen, plus a link to the fuller documentation on
 * GitHub. No network access - entirely built from strings in this file, so it always works, even offline. */
@Composable
fun HelpContentScreen(lang: Lang, isLandscape: Boolean) {
    val uriHandler = LocalUriHandler.current
    val sections = helpSections(lang)
    val scrollState = rememberScrollState()
    val cards: @Composable () -> Unit = {
        Text(helpIntro(lang), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        for (section in sections) HelpCard(section)
        TextButton(onClick = { uriHandler.openUri(helpDocUrl(lang)) }) { Text(helpFullDocsLabel(lang)) }
    }
    if (isLandscape) {
        Row(
            modifier = Modifier.fillMaxWidth().verticalScroll(scrollState).padding(top = 8.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val half = (sections.size + 1) / 2
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(helpIntro(lang), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                for (section in sections.subList(0, half)) HelpCard(section)
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                for (section in sections.subList(half, sections.size)) HelpCard(section)
                TextButton(onClick = { uriHandler.openUri(helpDocUrl(lang)) }) { Text(helpFullDocsLabel(lang)) }
            }
        }
    } else {
        Column(
            modifier = Modifier.fillMaxWidth().verticalScroll(scrollState).padding(top = 12.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            content = { cards() },
        )
    }
}

@Composable
private fun HelpCard(section: HelpSection) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(section.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
            for (bullet in section.bullets) {
                Row {
                    Text("•  ", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(bullet, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
