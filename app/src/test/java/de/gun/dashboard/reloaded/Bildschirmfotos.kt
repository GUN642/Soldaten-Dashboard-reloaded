package de.gun.dashboard.reloaded

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import de.gun.dashboard.reloaded.daten.*
import de.gun.dashboard.reloaded.logik.alsDE
import de.gun.dashboard.reloaded.ui.*
import de.gun.dashboard.reloaded.ui.seiten.*
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

/** Erzeugt Bildschirmfotos aller Reiter (zur Sichtprüfung des Designs). */
class Bildschirmfotos {
    @get:Rule
    val paparazzi = Paparazzi(deviceConfig = DeviceConfig.PIXEL_5, maxPercentDifference = 100.0)

    private fun t(tage: Long) = LocalDate.now().plusDays(tage).alsDE()

    private fun beispiel(thema: String) = AppDaten(
        design = Design(thema = thema),
        einrichtung = Einrichtung(true, t(0)),
        urlaub = Urlaub(24.0, listOf(UrlaubZugang("z", "01.01.2026", 30.0, "Jahreswechsel")),
            listOf(UrlaubZeitraum("u1", t(20), t(24), 5.0, "Sommerurlaub", "geplant"), UrlaubZeitraum("u2", t(-40), t(-36), 5.0, "Ostern")), letztesJahr = 2026),
        ueberstunden = Ueberstunden(listOf(UeberstundenEintrag("o1", t(-3), 4.5, "Übung"), UeberstundenEintrag("o2", t(-1), -2.0, "FvD"))),
        todos = Aufgaben(listOf(
            Aufgabe("a1", "Lehrgangsantrag einreichen", t(0), "09:30", "hoch", "Beim S1 abgeben"),
            Aufgabe("a2", "Reisekosten abrechnen", t(-2), "", "mittel"),
            Aufgabe("a3", "Stiefel zur Kleiderkammer", "", "", "niedrig"),
            Aufgabe("a4", "Impfpass kopieren", t(-5), "", "mittel", erledigt = true, erledigtAm = t(-1)),
        )),
        notizen = Notizen(listOf(Notiz("n1", "Telefonliste Zug", "Dienst", "Zugführer 1234\nS1 5678", t(-2), t(-1)))),
        checklisten = Checklisten(listOf(Checkliste("c1", "Marschgepäck", t(-10), listOf(
            ChecklistenPunkt("p1", "Schlafsack", true), ChecklistenPunkt("p2", "Isomatte", true), ChecklistenPunkt("p3", "Regenschutz", false))))),
        ablaufregister = listOf(
            AblaufEintrag("l1", "Erste Hilfe", t(-700), "2", "J", t(30)),
            AblaufEintrag("l2", "Kraftfahrer Klasse C", "", "", "J", t(900)),
            AblaufEintrag("l3", "Schießausbildung", "", "", "J", t(-10)),
        ),
        dokumente = listOf(Dokument("d1", "Reisepass", "Ich", "C01X00T47", "", "10", "J", t(1200)), Dokument("d2", "Personalausweis", "Ehefrau", "", "", "", "J", t(90))),
        medizin = Medizin(
            person = Person("Mustermann, Max", "Hauptfeldwebel", "HSG 64", "14.03.1985", 41, "m"),
            igf = listOf(MedEintrag("i1", "BFT", t(-100), t(265), "12M", "bestanden · 285 P"), MedEintrag("i2", "Schießausbildung", t(-400), t(-35), "12M")),
            impfungen = listOf(MedEintrag("m1", "Tetanus", "01.01.2020", "01.01.2030", jahre = 10)),
        ),
        kalender = Kalender(eigene = listOf(
            EigenerTermin("e1", "u1", 0, t(0), "Besprechung Staffel", t(0), t(0), false, "10:00", "11:30", "Stabsgebäude"),
            EigenerTermin("e2", "u2", 0, t(0), "Truppenübungsplatz", t(3), t(6), true),
            EigenerTermin("e3", "u3", 0, t(0), "Sport", t(1), t(1), false, "07:30", "08:30", wiederholung = "weekly"),
        )),
        avz = Avz(listOf(AvzZeitraum("v1", "01.02.2026", "30.04.2026", 89, "Litauen", "3", 95.0))),
        duz = Duz(listOf(DuzDienst("w1", t(-4), "18:00", "06:00", "Wache"))),
    )

    @Composable
    private fun Rahmen(thema: String, reiter: Reiter, inhalt: @Composable () -> Unit) {
        Speicher.vorschau(beispiel(thema))
        ReloadedTheme(Design(thema = thema)) {
            val scope = rememberCoroutineScope()
            val st = remember { Steuerung(scope, SnackbarHostState()).also { it.reiter = reiter } }
            val registerOwner = remember {
                object : androidx.activity.result.ActivityResultRegistryOwner {
                    override val activityResultRegistry = object : androidx.activity.result.ActivityResultRegistry() {
                        override fun <I, O> onLaunch(
                            requestCode: Int,
                            contract: androidx.activity.result.contract.ActivityResultContract<I, O>,
                            input: I,
                            options: androidx.core.app.ActivityOptionsCompat?,
                        ) {}
                    }
                }
            }
            CompositionLocalProvider(
                LocalSteuerung provides st,
                androidx.activity.compose.LocalActivityResultRegistryOwner provides registerOwner,
            ) {
                Column(Modifier.fillMaxSize().background(LocalPalette.current.bg)) {
                    Kopf(st)
                    ReiterLeiste(st)
                    Box(Modifier.weight(1f)) { inhalt() }
                }
            }
        }
    }

    private fun foto(name: String, thema: String, reiter: Reiter, inhalt: @Composable () -> Unit) {
        paparazzi.snapshot(name = "${thema}_$name") { Rahmen(thema, reiter, inhalt) }
    }

    @Test fun heute() { foto("heute", "nothing", Reiter.HEUTE) { HeuteSeite() }; foto("heute", "nothing-light", Reiter.HEUTE) { HeuteSeite() } }
    @Test fun heuteGraphit() { foto("heute", "graphit", Reiter.HEUTE) { HeuteSeite() }; foto("heute", "aulumu", Reiter.HEUTE) { HeuteSeite() } }
    @Test fun kalender() { foto("kalender", "nothing", Reiter.KALENDER) { KalenderSeite() } }
    @Test fun kalenderTag() {
        foto("kalendertag", "nothing", Reiter.KALENDER) {
            val st = LocalSteuerung.current
            remember { st.kalenderTag = LocalDate.now(); true }
            KalenderSeite()
        }
    }
    @Test fun wetter() {
        val heute = LocalDate.now()
        val codes = listOf(0, 1, 2, 3, 45, 61, 80, 95, 71, 51)
        val stunden = (0 until 7 * 24).map { h ->
            val z = heute.atStartOfDay().plusHours(h.toLong())
            de.gun.dashboard.reloaded.netz.WetterStunde(z.toString(), 8.0 + (h % 24) / 2.0, codes[(h / 5) % codes.size], (h * 7) % 60, 0.0)
        }
        val tage = (0 until 7).map { d ->
            de.gun.dashboard.reloaded.netz.WetterTag(heute.plusDays(d.toLong()).toString(), codes[d % codes.size], 20.0 + d, 5.0 + d, (d * 15) % 80, 0.0,
                heute.plusDays(d.toLong()).atTime(7, 10).toString(), heute.plusDays(d.toLong()).atTime(19, 5).toString())
        }
        val w = de.gun.dashboard.reloaded.netz.Wetter("Illerrieden", 12.0, 10.0, 45, 5.0, 80.0, stunden, tage, 0L)
        foto("wetter", "nothing", Reiter.HEUTE) {
            androidx.compose.foundation.layout.Column(Modifier.padding(14.dp)) { Karte("Wetter", "03") { WetterAnzeige(w) } }
        }
    }
    @Test fun unwetter() {
        val jetzt = java.time.OffsetDateTime.now()
        de.gun.dashboard.reloaded.netz.DwdWarnDienst.aktuell.value = listOf(
            de.gun.dashboard.reloaded.netz.DwdWarnung("a", "Unwetter", 3, listOf("THUNDERSTORM", "HAIL"),
                "Amtliche UNWETTERWARNUNG vor SCHWEREM GEWITTER mit HAGEL", "Es tritt ein schweres Gewitter auf.", "Schließen Sie Fenster und Türen!",
                jetzt, jetzt.plusHours(4), "Gemeinde Illerrieden"),
            de.gun.dashboard.reloaded.netz.DwdWarnung("b", "Sturmböen", 1, listOf("WIND"),
                "Amtliche WARNUNG vor STURMBÖEN", "", "", jetzt, jetzt.plusHours(8), "Gemeinde Illerrieden"),
        )
        foto("unwetter", "nothing", Reiter.HEUTE) {
            androidx.compose.foundation.layout.Column(Modifier.padding(14.dp)) { UnwetterKarte() }
        }
    }
    @Test fun todo() { foto("todo", "nothing", Reiter.TODO) { AufgabenSeite() } }
    @Test fun notizen() { foto("notizen", "nothing", Reiter.NOTIZEN) { NotizenSeite() } }
    @Test fun urlaub() { foto("urlaub", "nothing", Reiter.URLAUB) { UrlaubSeite() } }
    @Test fun lehrgaenge() { foto("lehrgaenge", "nothing", Reiter.LEHRGAENGE) { LehrgaengeSeite() } }
    @Test fun dokumente() { foto("dokumente", "nothing-light", Reiter.DOKUMENTE) { DokumenteSeite() } }
    @Test fun akte() { foto("akte", "nothing", Reiter.AKTE) { AkteSeite() } }
    @Test fun tools() { foto("tools", "nothing", Reiter.TOOLS) { ToolsSeite() } }
    @Test fun menue() { foto("menue", "nothing", Reiter.HEUTE) { MenueEbene() } }
    @Test fun maske() { foto("maske", "nothing", Reiter.KALENDER) { TerminMaskeEbene(MaskeStart(LocalDate.now())) } }
}
