package de.gun.dashboard.reloaded.auto

import android.content.Intent
import androidx.car.app.CarAppService
import androidx.car.app.CarContext
import androidx.car.app.CarToast
import androidx.car.app.Screen
import androidx.car.app.Session
import androidx.car.app.constraints.ConstraintManager
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.car.app.validation.HostValidator
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import de.gun.dashboard.reloaded.Aktualisierung
import de.gun.dashboard.reloaded.daten.Aufgabe
import de.gun.dashboard.reloaded.daten.Speicher
import de.gun.dashboard.reloaded.geraet.GeraeteKalender
import de.gun.dashboard.reloaded.logik.Termin
import de.gun.dashboard.reloaded.logik.hhmm
import de.gun.dashboard.reloaded.logik.parseDE
import de.gun.dashboard.reloaded.logik.termineAm
import de.gun.dashboard.reloaded.netz.Wetter
import de.gun.dashboard.reloaded.netz.WetterDienst
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.time.LocalDate
import kotlin.math.roundToInt

/**
 * Android Auto: Termine heute & morgen, Aufgaben heute und Wetter.
 * Nur Vorlagen der Car App Library (fahrtaugliche Listen); die Handy-App bleibt unberührt.
 */
class AutoDienst : CarAppService() {
    // Die App ist nicht über den Play Store freigegeben – daher jeden Host zulassen.
    override fun createHostValidator(): HostValidator = HostValidator.ALLOW_ALL_HOSTS_VALIDATOR
    override fun onCreateSession(): Session = AutoSitzung()
}

class AutoSitzung : Session() {
    override fun onCreateScreen(intent: Intent): Screen = UebersichtScreen(carContext)
}

/** Gemeinsamer Datenstand aller Auto-Bildschirme. */
private object AutoDaten {
    var wetter: Wetter? = null
    var wetterFehler: String? = null

    fun termine(tag: LocalDate): List<Termin> =
        termineAm(Aktualisierung.bestand(), tag, false).filter { !it.istTodo }

    fun aufgabenHeute(): List<Aufgabe> {
        val heute = LocalDate.now()
        return Speicher.aktuell.todos.eintraege
            .filter { !it.erledigt && (parseDE(it.faellig)?.let { f -> !f.isAfter(heute) } ?: false) }
            .sortedWith(compareBy({ parseDE(it.faellig) }, { it.uhrzeit.ifBlank { "99" } }))
    }
}

private fun zeitText(t: Termin, tag: LocalDate): String {
    if (t.ganztags) return "ganztägig"
    val beginn = if (t.start.toLocalDate().isBefore(tag)) "…" else t.start.hhmm()
    val e = t.ende
    val ende = when {
        e == null -> ""
        e.toLocalDate().isAfter(tag) -> "–…"
        else -> "–" + e.hhmm()
    }
    return beginn + ende
}

private fun terminZeile(t: Termin, tag: LocalDate): Row =
    Row.Builder()
        .setTitle(t.titel.ifBlank { "(ohne Titel)" })
        .addText(listOf(zeitText(t, tag), t.ort).filter { it.isNotBlank() }.joinToString(" · "))
        .apply { if (t.quelleName.isNotBlank()) addText(t.quelleName) }
        .build()

private fun aufgabeZeile(a: Aufgabe): Row {
    val heute = LocalDate.now()
    val f = parseDE(a.faellig)
    val info = listOfNotNull(
        if (f != null && f.isBefore(heute)) "überfällig seit ${a.faellig}" else "heute fällig",
        a.uhrzeit.takeIf { it.isNotBlank() }?.let { "$it Uhr" },
        "hohe Priorität".takeIf { a.prio == "hoch" },
    ).joinToString(" · ")
    return Row.Builder().setTitle(a.text.ifBlank { "(ohne Text)" }).addText(info).build()
}

private fun leerZeile(text: String): Row = Row.Builder().setTitle(text).build()

private fun listenGrenze(ctx: CarContext): Int = try {
    ctx.getCarService(ConstraintManager::class.java).getContentLimit(ConstraintManager.CONTENT_LIMIT_TYPE_LIST)
} catch (e: Exception) { 6 }

/** Startbildschirm: Wetter plus Einstiege zu Heute, Morgen und Aufgaben mit Vorschau. */
class UebersichtScreen(ctx: CarContext) : Screen(ctx) {
    private var ladeJob: Job? = null

    init {
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onCreate(owner: LifecycleOwner) {
                // Änderungen an Daten oder Gerätekalender sofort übernehmen
                lifecycleScope.launch {
                    combine(Speicher.daten, GeraeteKalender.termine) { _, _ -> }.collect { invalidate() }
                }
            }
            override fun onStart(owner: LifecycleOwner) { laden(false) }
        })
    }

    private fun laden(neu: Boolean) {
        if (ladeJob?.isActive == true) return
        ladeJob = lifecycleScope.launch {
            launch { try { Aktualisierung.geraetLadenJetzt(carContext) } catch (e: Exception) { } }
            val dash = Speicher.aktuell.dashboard
            if (dash.quelle == "openmeteo") {
                try {
                    AutoDaten.wetter = WetterDienst.laden(dash.ort, neu); AutoDaten.wetterFehler = null
                } catch (e: Exception) {
                    AutoDaten.wetterFehler = "Wetter nicht erreichbar"
                }
            } else AutoDaten.wetterFehler = "Wetterquelle in der App auf Open-Meteo stellen"
            invalidate()
        }
    }

    private fun wetterZeile(): Row {
        val w = AutoDaten.wetter
        if (w == null) return Row.Builder().setTitle("Wetter").addText(AutoDaten.wetterFehler ?: "wird geladen …").build()
        val tag = w.tage.firstOrNull()
        val grad = w.grad?.let { "${it.roundToInt()}°" } ?: "–"
        val titel = "${WetterDienst.zeichen(w.code)} $grad · ${WetterDienst.text(w.code).replaceFirstChar { it.uppercase() }}"
        val details = listOfNotNull(
            tag?.max?.let { "max ${it.roundToInt()}°" },
            tag?.min?.let { "min ${it.roundToInt()}°" },
            tag?.regenWkt?.let { "Regen $it %" },
        ).joinToString(" · ")
        return Row.Builder().setTitle(titel)
            .apply { if (details.isNotBlank()) addText(details) }
            .addText(w.ort)
            .build()
    }

    private fun einstieg(titel: String, anzahl: String, vorschau: List<String>, ziel: () -> Screen): Row =
        Row.Builder()
            .setTitle("$titel · $anzahl")
            .addText(vorschau.take(2).joinToString(" · ").ifBlank { "nichts geplant" })
            .setBrowsable(true)
            .setOnClickListener { screenManager.push(ziel()) }
            .build()

    override fun onGetTemplate(): Template {
        val heute = LocalDate.now()
        val morgen = heute.plusDays(1)
        val tHeute = AutoDaten.termine(heute)
        val tMorgen = AutoDaten.termine(morgen)
        val aufgaben = AutoDaten.aufgabenHeute()
        fun kurz(t: Termin, tag: LocalDate) = (if (t.ganztags) "" else zeitText(t, tag).substringBefore("–") + " ") + t.titel
        fun zahl(n: Int, eins: String, viele: String) = if (n == 1) "1 $eins" else "$n $viele"

        val liste = ItemList.Builder()
            .addItem(wetterZeile())
            .addItem(einstieg("Heute", zahl(tHeute.size, "Termin", "Termine"), tHeute.map { kurz(it, heute) }) {
                TagScreen(carContext, heute, "Heute")
            })
            .addItem(einstieg("Morgen", zahl(tMorgen.size, "Termin", "Termine"), tMorgen.map { kurz(it, morgen) }) {
                TagScreen(carContext, morgen, "Morgen")
            })
            .addItem(einstieg("Aufgaben heute", zahl(aufgaben.size, "offen", "offen"), aufgaben.map { it.text }) {
                AufgabenScreen(carContext)
            })
            .build()

        return ListTemplate.Builder()
            .setTitle("Soldaten Dashboard")
            .setHeaderAction(Action.APP_ICON)
            .setSingleList(liste)
            .setActionStrip(
                ActionStrip.Builder().addAction(
                    Action.Builder().setTitle("Aktualisieren").setOnClickListener {
                        laden(true)
                        CarToast.makeText(carContext, "Wird aktualisiert …", CarToast.LENGTH_SHORT).show()
                    }.build()
                ).build()
            )
            .build()
    }
}

/** Alle Termine eines Tages. */
class TagScreen(ctx: CarContext, private val tag: LocalDate, private val titel: String) : Screen(ctx) {
    init {
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onCreate(owner: LifecycleOwner) {
                lifecycleScope.launch { combine(Speicher.daten, GeraeteKalender.termine) { _, _ -> }.collect { invalidate() } }
            }
        })
    }

    override fun onGetTemplate(): Template {
        val grenze = listenGrenze(carContext)
        val termine = AutoDaten.termine(tag)
        val liste = ItemList.Builder()
        if (termine.isEmpty()) liste.addItem(leerZeile("Keine Termine"))
        val zeigen = if (termine.size > grenze) termine.take(grenze - 1) else termine
        zeigen.forEach { liste.addItem(terminZeile(it, tag)) }
        if (termine.size > zeigen.size) liste.addItem(leerZeile("+ ${termine.size - zeigen.size} weitere in der App"))
        val datum = "%02d.%02d.".format(tag.dayOfMonth, tag.monthValue)
        return ListTemplate.Builder()
            .setTitle("$titel, $datum")
            .setHeaderAction(Action.BACK)
            .setSingleList(liste.build())
            .build()
    }
}

/** Heute fällige und überfällige Aufgaben. */
class AufgabenScreen(ctx: CarContext) : Screen(ctx) {
    init {
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onCreate(owner: LifecycleOwner) {
                lifecycleScope.launch { Speicher.daten.collect { invalidate() } }
            }
        })
    }

    override fun onGetTemplate(): Template {
        val grenze = listenGrenze(carContext)
        val aufgaben = AutoDaten.aufgabenHeute()
        val liste = ItemList.Builder()
        if (aufgaben.isEmpty()) liste.addItem(leerZeile("Keine Aufgaben für heute"))
        val zeigen = if (aufgaben.size > grenze) aufgaben.take(grenze - 1) else aufgaben
        zeigen.forEach { liste.addItem(aufgabeZeile(it)) }
        if (aufgaben.size > zeigen.size) liste.addItem(leerZeile("+ ${aufgaben.size - zeigen.size} weitere in der App"))
        return ListTemplate.Builder()
            .setTitle("Aufgaben heute")
            .setHeaderAction(Action.BACK)
            .setSingleList(liste.build())
            .build()
    }
}
