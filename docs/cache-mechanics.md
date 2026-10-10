# Android-Cache: Messwert, Rest und Anzeige
Stand: 10. Oktober 2026. Bezug: Android 15; AOSP ist Referenz, kein Nachweis für jede Hersteller-Firmware.

## Kurzantwort

Ein verbleibender Cache-Wert ist nicht automatisch ein Fehler. Ebenso beweist eine kurz sichtbare "0 B" in den Android-Einstellungen keine tatsächliche Leerung. Android 15 AOSP setzt die Anzeige nach der Cache-Aktion ausdrücklich auf null; beim erneuten Öffnen wird wieder gemessen. Zusätzlich können Apps Cache neu schreiben und Messung/Löschung asynchron ablaufen. Aus einem unveränderten Vorher/Nachher-Wert allein lässt sich die Ursache nicht bestimmen.

## 1. Was misst "Cache"?

`StorageStats.getCacheBytes()` zählt Cache-Speicher für ein Paket/UID, einen Nutzer und ein Speichervolumen. Dokumentiert sind `getCacheDir()` und `getCodeCacheDir()`, außerdem `getExternalCacheDir()`, wenn der primäre gemeinsame Speicher auf diesem Speichervolumen liegt. Das ist kein RAM-Wert und keine Liste sämtlicher Dateien, die eine App inhaltlich als "Cache" bezeichnet. [1]

`getDataBytes()` enthält den Cache bereits. Cache nochmals zur vollständigen Datenmenge zu addieren würde doppelt zählen. AOSP Settings bildet Nutzerdaten deshalb als Daten minus Cache. Die App verwendet den Cache-Wert der Plattform, nicht eine eigene rekursive Dateisuche. [1, 2]

Fotos, Downloads, Offline-Inhalte, Datenbanken und Browser-Website-Daten sind nicht allein wegen ihres temporären Zwecks Bestandteil dieses Cache-Zählers. App-spezifischer externer Cache ist von gemeinsamem MediaStore-Speicher zu unterscheiden. Androids native Cache-Löschung behandelt `Android/data/<Paket>/cache` separat; `Android/media/<Paket>` gehört im untersuchten Quellcode nicht zum Cache-only-Zweig. [5]

WebView dokumentiert seinen Ressource-Cache als **pro App**: alle WebViews innerhalb dieser App, nicht automatisch ein gemeinsamer Cache sämtlicher fremder Apps. Daraus folgt keine besondere "WebView-Sperre" für Brave. Für dessen genaue Dateizuordnung oder die konkrete Firmware liegt hier kein Nachweis vor. [6]

## 2. Warum springt der Wert zurück?

### Nachgewiesene AOSP-Anzeigelogik

Android 15 `AppStorageSizesController` verwendet:

```java
long cacheSize = (mDataCleared || mCachedCleared)
        ? 0 : mLastResult.getCacheBytes();
```

Der Kommentar erklärt: beim Leeren neu erstellte leere Verzeichnisse belegen selbst Platz; die Anzeige wird für die Nutzererwartung auf null gesetzt. `AppStorageSettings` setzt das Cache-cleared-Flag nach der Rückmeldung der Löschaktion und deaktiviert den Cache-Button. In diesem Pfad wird das Flag auch dann gesetzt, wenn die Callback-Nachricht einen Misserfolg enthält. [2, 3]

**Folge:** "0 B" und ein deaktivierter Button auf derselben geöffneten Seite sind keine unabhängige Byte-Messung und kein sicherer Erfolgsbeweis. Erneutes Öffnen kann einen anderen, frisch berechneten Wert zeigen. Das gilt nachweislich für AOSP; ein ähnliches Bild auf Hersteller-Geräten ist ein Hinweis, kein Nachweis identischer Implementierung.

### Weitere mögliche Ursachen

- Die App oder Hintergrundarbeit legt wieder Cache-Dateien an. Cache ist absichtlich vergänglich; seine Existenz ist normal. Das ist plausibel, aber für einen bestimmten unveränderten Messwert nicht bewiesen. [1, 7]
- Die Löschung und die Größenabfrage laufen nicht als eine atomare Transaktion. Settings verwendet einen Callback und einen Loader; die Größenabfrage führt über den Systemdienst zu `installd`, gegebenenfalls mit Quota-Messung. Unterschiedliche Zeitpunkte können unterschiedliche Werte liefern. Eine feste universelle Wartezeit oder ein belegter pauschaler "StorageStats hängt x Sekunden hinterher" ergibt sich daraus nicht. [2, 4]
- Die Aktion wurde angenommen, aber die Löschung scheiterte, war unvollständig oder traf einen anderen UI-Knoten. Ein Accessibility-`ACTION_CLICK=true` bestätigt nur die Aktion am Knoten, nicht den Erfolg der System-Löschoperation.

Leere Verzeichnisse erklären kleine Reste, aber nicht ohne weitere Belege einen Rest von mehreren Dutzend MB. Große Reste brauchen Diagnose, keine automatische Erklärung.

## 3. Brave: warum manuell unverändert?

Aus einem unveränderten Wert nach dem manuellen Settings-Klick folgt weder "Android verbietet es" noch "Cache regeneriert sicher sofort". Ohne Byte-Zwischenmessung, System-Callback und Firmware-Details bleiben mehrere Erklärungen offen: Anzeige-Null, Löschfehler, erneute Writes oder nicht identische Messzeitpunkte.

Brave bietet eine eigene Android-Funktion unter Einstellungen > Brave Shields & Privacy > Delete browsing data. Dort werden Datentypen und Zeitraum ausgewählt. Das ist eine andere Funktion mit potenziell anderen Datenfolgen, keine garantierte Lösung für den Android-Cache-Zähler. Cookies, Verlauf oder Website-Daten dürfen nicht unbemerkt mitgelöscht werden. [8]

Kein belegter Grund, Brave als "geteilten WebView-Cache", "gepinnte App" oder generell "nicht leerbar" zu klassifizieren. Eine alte oder Desktop-spezifische Browser-Issue ist kein Nachweis für die aktuelle Android-App.

## 4. Konkrete Produktlogik

| Empfehlung | Nutzen / Grenze |
|---|---|
| Klick, gemessene Abnahme und Rest getrennt anzeigen | Keine falsche Erfolgs- oder Fehlerbehauptung. Zero ist kein Zielzwang. |
| Bestätigte Abnahme als abgeschlossen behalten | Der verbleibende Messwert ist eine Rest-Baseline, kein offener Fehler; frische Scans behalten ihre aktuellen Bytes. |
| Unveränderte Werte: "Keine Abnahme nach 2 Versuchen; Ursache offen" | Begrenzte Wiederholung statt Endlosschleife. Nicht als bereinigte Bytes zählen oder pauschal "nicht leerbar" nennen. |
| Settings-Text und exakten Klickknoten lokal festhalten | Hilft, Label-Zeile, Dialog und Messung zu trennen. Gekürzt, nur im explizit gestarteten Lauf; Nutzer prüft den Bericht vor dem Teilen. |
| Bei Bedarf langsamerer Diagnosemodus mit mehreren Zeitpunkten | Erst einen Rückgang und später einen Anstieg messen, bevor "Cache kehrt zurück" als beobachteter Zustand angezeigt wird. Gegenwärtig keine solche Ursache aus identischen Werten ableiten. |
| Kein automatisches Force-stop vor dem Leeren | Android 14+ erlaubt normalen Apps per `killBackgroundProcesses()` nur eigene Prozesse zu beenden. UI-Force-stop wäre eine neue, eingreifende Aktion und bleibt außerhalb der Cache-only-Automation. [9] |

Priorität: ehrliche Messung und nachvollziehbarer Klickpfad. Kein breiter Rewrite, keine Datenlöschung, keine Behauptung dauerhafter Speicher-, Akku- oder Geschwindigkeitsverbesserung. Ein längerer Abstand kann Messung verbessern, aber auch mehr Neuaufbau erfassen; mehr Warten ist nicht automatisch genauer.

## Quellen

1. Android Developers, StorageStats: https://developer.android.com/reference/android/app/usage/StorageStats
2. AOSP Android 15, AppStorageSettings: https://android.googlesource.com/platform/packages/apps/Settings/+/refs/tags/android-15.0.0_r1/src/com/android/settings/applications/AppStorageSettings.java
3. AOSP Android 15, AppStorageSizesController: https://android.googlesource.com/platform/packages/apps/Settings/+/refs/tags/android-15.0.0_r1/src/com/android/settings/applications/AppStorageSizesController.java
4. AOSP, StorageStatsService (aktueller Hauptzweig): https://android.googlesource.com/platform/frameworks/base/+/master/services/usage/java/com/android/server/usage/StorageStatsService.java
5. AOSP, InstalldNativeService (aktueller Hauptzweig): https://android.googlesource.com/platform/frameworks/native/+/refs/heads/main/cmds/installd/InstalldNativeService.cpp
6. Android WebView API-Spiegel, ClearCache: https://learn.microsoft.com/en-us/dotnet/api/android.webkit.webview.clearcache?view=net-android-35.0
7. Google Android-Hilfe, Cache versus Daten: https://support.google.com/android/answer/7431795
8. Brave Android-Hilfe, Browsing data: https://support.brave.app/hc/en-us/articles/360054509991-How-do-I-manage-Cookies-and-Site-data-in-Brave-on-Android
9. Android Developers, ActivityManager: https://developer.android.com/reference/android/app/ActivityManager

Alle verwendeten Seiten wurden gelesen. Allgemeine API-Verträge und Android-15-Quellcode haben mehr Gewicht als Community-Vermutungen. Offene Grenze: keine Analyse der geschlossenen Hersteller-Firmware, keine Root-Dateimessung, kein Nachweis einer konkreten Brave-Ursache.
