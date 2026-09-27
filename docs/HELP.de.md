# Scooter Link - Hilfe

Eine ausführlichere Ergänzung zur Hilfe in der App (Bereich „Hilfe" im Seitenmenü). Zu Installation,
unterstützten Modellen und technischem Hintergrund siehe die [Haupt-README](../README.de.md).

## Verbinden

- **Beim ersten Mal**: „Scooter hinzufügen" → Cloud-Login (Passwort oder QR-Code) → die App holt den
  Bluetooth-Schlüssel (`ltmk`) einmal ab und speichert ihn verschlüsselt auf dem Handy. Danach
  spricht sie für diesen Scooter nie wieder mit der Cloud.
- **Jedes weitere Mal**: auf die Kachel des Scooters in der Geräteliste tippen. Die App wartet auf
  die Bluetooth-Ankündigung des Scooters und verbindet dann direkt - das heißt auch, der Scooter
  muss erreichbar sein (eingeschaltet, oder - bei Modellen, die das unterstützen - gesperrt und im
  Ruhezustand, siehe „Standby" unten) und in Reichweite liegen.
- **„Verbinde per Bluetooth ..." dauert lange**: Ein Verbindungsversuch wird bis zu 5-mal wiederholt,
  je 3 Sekunden. Bei schwacher oder weiter entfernter Verbindung kann das 15 Sekunden oder länger
  dauern, bis er entweder gelingt oder mit einer Fehlermeldung aufgibt.
- **Die Verbindung reißt von selbst ab**: Die App verbindet sich einmal automatisch neu, wenn die
  Verbindung abreißt, während die Geräteliste angezeigt wird (z. B. direkt nachdem der Scooter aus
  dem Ruhezustand aufgewacht ist, was sein Bluetooth zurücksetzt). Scheitert auch dieser eine
  Versuch, zeigt die Kachel den Grund an.
- **Ein Scooter, der 15 Sekunden lang nicht antwortet**, gilt als getrennt, selbst wenn die
  Bluetooth-Verbindung selbst formal nie einen Abbruch meldet - das ist Absicht, damit die App
  niemals Werte weiter anzeigt, die in Wirklichkeit veraltet sind.

## Die Dashboard-Tabs

Jeder Tab spiegelt eine Gruppe der Eigenschaften des Scooters. Alle werden auch in der Hilfe der App
selbst mit dem jeweils aktuellen Wortlaut zusammengefasst; dieser Abschnitt geht bei den Teilen, die
sich nicht allein aus dem Namen erschließen, etwas tiefer.

### Übersicht
Die beiden großen Zahlen sind Reichweite und Akkustand. Die Reichweite wechselt automatisch zum
eigenen gemessenen Verbrauch, sobald für den aktuellen Fahrmodus genug Daten vorliegen (siehe
„Verlauf" unten) - bis dahin, oder wenn diese Funktion aus ist, zeigt sie die Schätzung des Scooters
selbst. „Fahrzeit" auf diesem Tab ist der eigene Timer der App (siehe „Der Fahrt-Timer" unten), nicht
der rohe Wert des Scooters.

### Fahrt / Akku / Einstellungen / Fahrzeug / Identifikation
Reine Listen der Eigenschaften des Scooters, nach Thema gruppiert statt nach der internen
Nummerierung des Scooters (die an manchen Stellen sachfremde Dinge zusammenwirft). Hier ist alles
ein direktes Lesen (oder, wo einstellbar, Schreiben) einer Eigenschaft - dazwischen passiert keine
Interpretation, außer bei der oben erwähnten „Fahrzeit"-Kachel.

Zwei Hinweise:
- **Tempomat und Rücklicht** zeigen vor dem ersten Einschalten einen rechtlichen Hinweis, weil deren
  Erlaubtheit vom jeweiligen Land und den dortigen Regeln abhängt - die App kann deine
  Rechtsordnung nicht kennen und fragt deshalb lieber nach, statt beides stillschweigend zu
  erlauben oder zu blockieren.
- **Standby**: Meldet der Scooter seinen Ruhezustand (auf dem Fahrzeug-Tab sichtbar), sind alle
  seine gemeldeten Werte eingefroren - er behauptet zum Beispiel weiter zu laden, obwohl das
  Ladegerät längst abgezogen wurde. Die App erkennt das und zeigt „Standby" statt jedes Werts,
  anstatt veraltete Zahlen als aktuell auszugeben; nur „Scooter suchen" funktioniert weiter. Sobald
  der Scooter aufwacht, wird alles neu gelesen.

### Fahrtenbuch
Der Scooter selbst merkt sich nur seine 5 letzten Fahrten (die älteste wird überschrieben, sobald
eine neue endet). Diese App kopiert bei jedem Auslesen neue Fahrten in ein eigenes, unbegrenztes
Fahrtenbuch auf dem Handy, nach Tagen gruppiert. Eine Fahrt, die im Speicher des Scooters schon
überschrieben wurde, bevor die App sie lesen konnte, ist unwiederbringlich verloren.

Zwei bekannte, bewusst in Kauf genommene Grenzen, keine Fehler:
- Fahrten, die aufgezeichnet wurden, bevor es das Fahrtenbuch auf diesem Handy gab, haben kein
  Datum (stehen unter „Ohne Datum").
- Wurden die 5 Plätze des Scooters ausgelesen, während eine Fahrt noch geschrieben wurde (ein kurzer
  Halt mit anschließender Weiterfahrt, oder ein erneutes Verbinden mitten in der Fahrt), konnten
  sehr alte Versionen der App dieselbe, wachsende Fahrt mehrfach speichern. Das ist ab Version 3.7
  behoben, bestehende Fahrtenbücher bekommen dafür beim ersten Start nach dem Update eine einmalige
  automatische Bereinigung.

### Verlauf
Das ist die eigene Verbrauchsanalyse der App, unabhängig vom Fahrtenbuch oben - sie entsteht live
aus den eigenen aufgezeichneten Fahrten, solange das Handy während einer Fahrt verbunden bleibt und
die Einstellung „Fahrt-Aufzeichnung" an ist. Sie betrachtet dabei immer nur die letzten 300 km je
Fahrmodus, damit ein alternder Akku oder abgefahrene Reifen von selbst in den Zahlen auffallen, ohne
dass jemand manuell etwas zurücksetzen muss. „Verlauf zurücksetzen" ist stattdessen für ein
bekanntes, einmaliges Ereignis gedacht (z. B. einen Akkutausch) - es verwirft alles bisher
Aufgezeichnete für einen sauberen Neustart.

Das Diagramm und die Karten der letzten Fahrten auf diesem Tab nutzen dieselben Daten, zu „Fahrten"
gruppiert (eine Fahrt besteht aus einem oder mehreren dieser aufgezeichneten Stücke mit höchstens
10 Minuten Pause dazwischen). Weil diese Liste nur Fahrten enthält, die wirklich mit bestehender
Verbindung aufgezeichnet wurden, stimmt sie meist **nicht** exakt mit dem Fahrtenbuch oben überein -
das stammt aus der eigenen, unabhängigen Erinnerung des Scooters, die auch ohne verbundenes Handy
weiterzählt. Keine der beiden Listen ist falsch, sie messen einfach unterschiedliche Dinge.

Eine damit zusammenhängende, bewusste Genauigkeitsgrenze: Eine sehr kurze Fahrt, die unterbrochen
wird, bevor der Akku auch nur ein volles Prozent gefallen ist, wird komplett verworfen statt
teilweise gezählt - das verhindert, dass eine Fahrt, die zufällig über einen Ladevorgang hinweg
reicht, den Verbrauchsdurchschnitt verfälscht. Im Alltag mit häufigen kurzen Stopps (Ampeln etc.)
bedeutet das, dass die Summen im Verlauf-Tab tendenziell etwas niedriger ausfallen als das, was der
Kilometerzähler oder das Fahrtenbuch des Scooters für denselben Zeitraum zeigen würden - auch das
ist kein Fehler, nur eine bewusst gezogene Grenze.

### App-Einstellungen
Das „Overlay mit Restkilometern" verdient hier eine eigene Erwähnung: einmal eingeschaltet, legt es
ein kleines, verschiebbares Fenster mit Reichweite, Akkustand, Fahrstrecke und dem eigenen
Fahrt-Timer über alles, was du sonst gerade nutzt (z. B. eine Navigations-App), und aktualisiert
sich weiter, auch wenn Scooter Link selbst minimiert ist - dasselbe Prinzip hält generell auch die
Bluetooth-Verbindung und die Fahrtaufzeichnung im Hintergrund am Leben, über eine kleine
Dauerbenachrichtigung, die Android dafür verlangt. Antippen holt die App wieder in den Vordergrund,
Ziehen verschiebt es. Es braucht die Berechtigung „Über anderen Apps einblenden", die beim ersten
Einschalten einmalig angefragt wird.

## Der Fahrt-Timer
Der eigene „Fahrzeit"-Wert des Scooters springt auf 0 zurück, sobald er auch nur kurz keine Bewegung
mehr meldet - etwa an einer roten Ampel - selbst mitten in der Fahrt. Die App führt stattdessen einen
eigenen, getrennten Timer: Er startet, sobald eine Fahrt beginnt, läuft bei einem kurzen Halt einfach
weiter und endet erst, wenn der Scooter volle 10 Minuten am Stück gestanden hat (dieselbe Grenze, die
auch die Fahrten-Gruppierung im Verlauf-Tab nutzt) oder die Verbindung endet. Er steht auf der
Übersichts-Kachel „Fahrzeit" und, falls eingeschaltet, im schwebenden Overlay - er hat keinerlei
Einfluss auf die oben beschriebene Verbrauchsberechnung, die davon vollständig getrennt und
unangetastet bleibt.

## Sicherung, Export und Datenschutz

- **Export** (pro Scooter): ein Code mit dem Bluetooth-Schlüssel dieses Scooters, zum Teilen des
  Zugangs mit jemandem, der ihn ebenfalls rechtmäßig nutzen darf (Familie usw.) - kein eigenes
  Cloud-Konto auf der anderen Seite nötig.
- **Gesamtsicherung** (alle Scooter auf einmal): eine Datei mit Schlüssel, Dokumenten, Verlauf und
  den App-Einstellungen jedes gespeicherten Scooters, optional mit Passwort geschützt.
- Beides bleibt vollständig auf den beteiligten Geräten - es gibt keinen eigenen Server dieses
  Projekts, und keine der beiden Funktionen lädt irgendetwas irgendwohin hoch. Die vollständige
  Datenschutzaussage steht in der Haupt-README.

## Weitere Hilfe

Falls hier nicht die passende Antwort dabei war: Der Knopf „Diagnose kopieren" in den
App-Einstellungen stellt einen kurzen, bereinigten Bericht zusammen (App-, Handy- und
Scooter-Modell, deine Einstellungen und die letzten paar Fehlermeldungen - ohne MAC-Adresse,
Schlüssel oder Dokumente), der sich gut an ein GitHub-Issue anhängen lässt.
