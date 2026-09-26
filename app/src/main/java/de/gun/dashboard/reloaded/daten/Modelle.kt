package de.gun.dashboard.reloaded.daten

import kotlinx.serialization.Serializable

/*
 * Datenmodell. Aufbau und Feldnamen entsprechen bewusst genau der
 * Sicherungsdatei des alten Soldaten Dashboards – so lässt sich eine dort
 * exportierte .json unverändert einlesen und umgekehrt.
 */

typealias FText = @Serializable(with = FlexText::class) String
typealias FZahl = @Serializable(with = FlexZahl::class) Double
typealias FZahlN = @Serializable(with = FlexZahlOderNull::class) Double?
typealias FGanzN = @Serializable(with = FlexGanzOderNull::class) Int?
typealias FBool = @Serializable(with = FlexBool::class) Boolean

/** Dateianhang. In der App liegt die Datei unter files/anhaenge, im
 *  Datensatz nur der Verweis (dateiname). In Sicherungsdateien steht der
 *  Inhalt als data:-URL in "daten". */
@Serializable
data class Anhang(
    val name: FText = "",
    val groesse: FZahl = 0.0,
    val typ: FText = "",
    val daten: String? = null,
    val dateiname: String? = null,
)

// ---------------- Urlaub und Mehrarbeit ----------------

@Serializable
data class UrlaubZugang(
    val id: FText = "",
    val datum: FText = "",
    val tage: FZahl = 0.0,
    val notiz: FText = "",
)

@Serializable
data class UrlaubZeitraum(
    val id: FText = "",
    val von: FText = "",
    val bis: FText = "",
    val tage: FZahl = 0.0,
    val notiz: FText = "",
    val status: FText = "eingetragen",
    val herkunft: FText = "",
    val quelle: FText = "",
)

@Serializable
data class Urlaub(
    val jahresanspruch: FZahl = 30.0,
    val zugaenge: List<UrlaubZugang> = emptyList(),
    val zeitraeume: List<UrlaubZeitraum> = emptyList(),
    val geplant: List<UrlaubZeitraum> = emptyList(),
    val letztesJahr: FGanzN = null,
)

@Serializable
data class UeberstundenEintrag(
    val id: FText = "",
    val datum: FText = "",
    val stunden: FZahl = 0.0,
    val notiz: FText = "",
    val geplant: FBool = false,
    val herkunft: FText = "",
    val quelle: FText = "",
)

@Serializable
data class Ueberstunden(
    val eintraege: List<UeberstundenEintrag> = emptyList(),
    val startwert: FZahl = 0.0,
)

// ---------------- Lehrgänge und Dokumente ----------------

@Serializable
data class AblaufEintrag(
    val id: FText = "",
    val art: FText = "",
    val gueltigVon: FText = "",
    val dauerWert: FText = "",
    val dauerEinheit: FText = "J",
    val gueltigBis: FText = "",
    val notiz: FText = "",
)

@Serializable
data class Dokument(
    val id: FText = "",
    val art: FText = "",
    val inhaber: FText = "",
    val nummer: FText = "",
    val gueltigVon: FText = "",
    val dauerWert: FText = "",
    val dauerEinheit: FText = "J",
    val gueltigBis: FText = "",
    val notiz: FText = "",
    val dateien: List<Anhang> = emptyList(),
)

// ---------------- Aufgaben, Notizen, Checklisten ----------------

@Serializable
data class Aufgabe(
    val id: FText = "",
    val text: FText = "",
    val faellig: FText = "",
    val uhrzeit: FText = "",
    val prio: FText = "mittel",
    val notiz: FText = "",
    val anhaenge: List<Anhang> = emptyList(),
    val erledigt: FBool = false,
    val erstellt: FText = "",
    val erledigtAm: FText = "",
)

@Serializable
data class Aufgaben(val eintraege: List<Aufgabe> = emptyList())

@Serializable
data class Notiz(
    val id: FText = "",
    val titel: FText = "",
    val kategorie: FText = "",
    val text: FText = "",
    val erstellt: FText = "",
    val geaendert: FText = "",
)

@Serializable
data class Notizen(val eintraege: List<Notiz> = emptyList())

@Serializable
data class ChecklistenPunkt(
    val id: FText = "",
    val text: FText = "",
    val ab: FBool = false,
)

@Serializable
data class Checkliste(
    val id: FText = "",
    val name: FText = "",
    val erstellt: FText = "",
    val punkte: List<ChecklistenPunkt> = emptyList(),
)

@Serializable
data class Checklisten(val listen: List<Checkliste> = emptyList())

// ---------------- Kalender ----------------

/** Termin einer eingebundenen ICS-Quelle. start/end als ISO-Zeitpunkt (UTC). */
@Serializable
data class QuellenTermin(
    val uid: FText = "",
    val titel: FText = "",
    val start: String? = null,
    val end: String? = null,
    val allDay: FBool = false,
    val rrule: String? = null,
    val ort: FText = "",
    val beschreibung: FText = "",
)

@Serializable
data class KalenderQuelle(
    val id: FText = "",
    val name: FText = "",
    val farbe: FText = "#5fb4ff",
    val istUrlaub: FBool = false,
    val url: FText = "",
    val aktiv: Boolean = true,
    val events: List<QuellenTermin> = emptyList(),
    val aktualisiert: FText = "",
)

/** Selbst angelegter Termin. Liegt er im Gerätekalender, steht dessen
 *  Kennung in nativId. */
@Serializable
data class EigenerTermin(
    val id: FText = "",
    val uid: FText = "",
    val sequence: FGanzN = 0,
    val erstellt: FText = "",
    val titel: FText = "",
    val von: FText = "",
    val bis: FText = "",
    val ganztags: FBool = false,
    val zeitVon: FText = "",
    val zeitBis: FText = "",
    val ort: FText = "",
    val notiz: FText = "",
    val anhaenge: List<Anhang> = emptyList(),
    val kalenderId: FText = "",
    val wiederholung: FText = "",
    val nativId: FText = "",
    val nativ: FBool = false,
)

@Serializable
data class Kalender(
    val quellen: List<KalenderQuelle> = emptyList(),
    val eigene: List<EigenerTermin> = emptyList(),
    val farben: Map<String, String> = emptyMap(),
    val fvdStunden: FZahl = 8.0,
    val schriftgroesse: FGanzN = 100,
    val todosImKalender: Boolean = true,
    val erledigteImKalender: Boolean = false,
)

// ---------------- Heute / Wetter ----------------

@Serializable
data class Ort(
    val name: FText = "Berlin",
    val breite: FZahl = 52.52,
    val laenge: FZahl = 13.405,
)

@Serializable
data class Dashboard(
    val wetterUrl: FText = "",
    val quelle: FText = "openmeteo",
    val ort: Ort = Ort(),
    val wetterGeleert: FBool = false,
    /** Amtliche Warnungen des DWD für den Wetter-Ort anzeigen. */
    val dwdWarnungen: FBool = true,
    /** Benachrichtigung bei neuen Unwetterwarnungen. */
    val dwdPush: FBool = true,
)

// ---------------- Akte ----------------

@Serializable
data class Person(
    val name: FText = "",
    val dienstgrad: FText = "",
    val einheit: FText = "",
    val geburt: FText = "",
    val alter: FGanzN = null,
    val geschlecht: FText = "m",
)

/** Gemeinsamer Aufbau für IGF, ICCS, AVU/WFV und Impfungen. */
@Serializable
data class MedEintrag(
    val id: FText = "",
    val art: FText = "",
    val datum: FText = "",
    val gueltigBis: FText = "",
    val intervall: FText = "",
    val ergebnis: FText = "",
    val notiz: FText = "",
    val befund: FText = "",
    val stelle: FText = "",
    val jahre: FGanzN = null,
    val dosis: FText = "",
)

@Serializable
data class BftGrenzen(
    val maxSprint: FZahl = 60.0,
    val bestSprint: FZahl = 43.0,
    val minKlimm: FZahl = 5.0,
    val bestKlimm: FZahl = 70.0,
    val maxLauf: FText = "6:30",
    val bestLauf: FText = "3:30",
)

val IGF_PFLICHT_STANDARD = listOf("BFT", "Schießausbildung", "Sanitätsausbildung", "ABC-Selbst- und Kameradenhilfe")

@Serializable
data class Medizin(
    val person: Person = Person(),
    val igf: List<MedEintrag> = emptyList(),
    val iccs: List<MedEintrag> = emptyList(),
    val avu: List<MedEintrag> = emptyList(),
    val impfungen: List<MedEintrag> = emptyList(),
    val bft: BftGrenzen = BftGrenzen(),
    val igfPflicht: List<String> = IGF_PFLICHT_STANDARD,
)

// ---------------- Tools ----------------

@Serializable
data class AvzZeitraum(
    val id: FText = "",
    val von: FText = "",
    val bis: FText = "",
    val tage: FGanzN = 0,
    val einsatz: FText = "",
    val stufe: FText = "1",
    val satz: FZahlN = null,
    val notiz: FText = "",
)

@Serializable
data class Avz(val zeitraeume: List<AvzZeitraum> = emptyList())

@Serializable
data class DuzDienst(
    val id: FText = "",
    val datum: FText = "",
    val von: FText = "",
    val bis: FText = "",
    val notiz: FText = "",
)

@Serializable
data class Duz(
    val dienste: List<DuzDienst> = emptyList(),
    val saetze: Map<String, Double> = emptyMap(),
)

// ---------------- Einstellungen ----------------

@Serializable
data class WidgetEinstellungen(
    val modus: FText = "dunkel",
    val deckkraft: FGanzN = 85,
    val aufgaben: FBool = false,
    val tage: FGanzN = 1,
    val endzeit: FBool = false,
)

@Serializable
data class FeiertagsLand(val land: FText = "BW")

/** Einstellungen zum Gerätekalender (nicht Teil der Sicherungsdatei). */
@Serializable
data class NativEinstellungen(
    val zielKalenderId: FText = "",
    val versteckt: List<String> = emptyList(),
    val entfernt: List<String> = emptyList(),
    val geburtstage: Boolean = true,
    val kontaktdaten: Boolean = true,
)

@Serializable
data class Benachrichtigungen(
    val vorlaufMin: Int = 15,
    val ganztagsZeit: String = "21:00",
    val an: Boolean = true,
)

@Serializable
data class FerienAbschnitt(val name: String = "Ferien", val von: String = "", val bis: String = "")

@Serializable
data class Ferien(
    val an: Boolean = true,
    val land: String = "",
    val jahre: Map<String, List<FerienAbschnitt>> = emptyMap(),
)

@Serializable
data class SicherungsStand(
    val letzte: String = "",
    val popupZuletzt: Long = 0,
)

@Serializable
data class UpdateEinstellungen(
    val repo: String = "GUN642/Soldaten-Dashboard-reloaded",
    val uebersprungen: String = "",
    val letztePruefung: Long = 0,
)

/** Darstellung: Thema, Akzentfarbe, Punktschrift. */
@Serializable
data class Design(
    val thema: String = "nothing",
    val akzent: String = "rot",
    val punktSchrift: Boolean = true,
    val punktRaster: Boolean = true,
    /** Reiterleiste unten statt oben. */
    val reiterUnten: Boolean = false,
)

@Serializable
data class Einrichtung(
    val erledigt: Boolean = false,
    val am: String = "",
)

/** Gesamter Datenbestand der App, als eine Datei gespeichert. */
@Serializable
data class AppDaten(
    val ablaufregister: List<AblaufEintrag> = emptyList(),
    val dokumente: List<Dokument> = emptyList(),
    val urlaub: Urlaub = Urlaub(),
    val ueberstunden: Ueberstunden = Ueberstunden(),
    val todos: Aufgaben = Aufgaben(),
    val notizen: Notizen = Notizen(),
    val checklisten: Checklisten = Checklisten(),
    val kalender: Kalender = Kalender(),
    val farbenEigen: Map<String, String> = emptyMap(),
    val dashboard: Dashboard = Dashboard(),
    val medizin: Medizin = Medizin(),
    val avz: Avz = Avz(),
    val duz: Duz = Duz(),
    val widget: WidgetEinstellungen = WidgetEinstellungen(),
    val feiertagsLand: FeiertagsLand = FeiertagsLand(),
    val nativ: NativEinstellungen = NativEinstellungen(),
    val ben: Benachrichtigungen = Benachrichtigungen(),
    val ferien: Ferien = Ferien(),
    val sicherung: SicherungsStand = SicherungsStand(),
    val update: UpdateEinstellungen = UpdateEinstellungen(),
    val design: Design = Design(),
    val einrichtung: Einrichtung = Einrichtung(),
)
