package de.gun.dashboard.reloaded.daten

import android.content.Context
import de.gun.dashboard.reloaded.BuildConfig
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import java.time.Instant

/**
 * Sicherungsdatei im Format des alten Soldaten Dashboards. Eine hier
 * erstellte Datei lässt sich dort einlesen und umgekehrt. Zusätzlich
 * enthalten: DUZ-Dienste und eigene Kalenderfarben (unter kalender.farben).
 */
object Sicherung {

    private fun <T> kodiere(s: KSerializer<T>, wert: T): JsonElement = JsonFormat.encodeToJsonElement(s, wert)

    fun exportieren(ctx: Context, d: AppDaten): String {
        val dokumente = d.dokumente.map { it.copy(dateien = Anhaenge.fuerExport(ctx, it.dateien)) }
        val todos = d.todos.copy(eintraege = d.todos.eintraege.map { it.copy(anhaenge = Anhaenge.fuerExport(ctx, it.anhaenge)) })
        val kalender = d.kalender.copy(
            eigene = d.kalender.eigene.map { it.copy(anhaenge = Anhaenge.fuerExport(ctx, it.anhaenge)) },
            farben = d.farbenEigen,
        )
        val obj = buildJsonObject {
            put("app", "Soldaten Dashboard")
            put("variante", "Reloaded")
            put("version", BuildConfig.VERSION_NAME)
            put("exportedAt", Instant.now().toString())
            put("ablaufregister", kodiere(ListSerializer(AblaufEintrag.serializer()), d.ablaufregister))
            put("dokumente", kodiere(ListSerializer(Dokument.serializer()), dokumente))
            put("urlaub", kodiere(Urlaub.serializer(), d.urlaub))
            put("ueberstunden", kodiere(Ueberstunden.serializer(), d.ueberstunden))
            put("todos", kodiere(Aufgaben.serializer(), todos))
            put("notizen", kodiere(Notizen.serializer(), d.notizen))
            put("kalender", kodiere(Kalender.serializer(), kalender))
            put("dashboard", kodiere(Dashboard.serializer(), d.dashboard))
            put("checklisten", kodiere(Checklisten.serializer(), d.checklisten))
            put("widget", kodiere(WidgetEinstellungen.serializer(), d.widget))
            put("avz", kodiere(Avz.serializer(), d.avz))
            put("duz", kodiere(Duz.serializer(), d.duz))
            put("medizin", kodiere(Medizin.serializer(), d.medizin))
            put("feiertagsLand", kodiere(FeiertagsLand.serializer(), d.feiertagsLand))
        }
        return JsonFormat.encodeToString(JsonObject.serializer(), obj)
    }

    class ImportErgebnis(val daten: AppDaten, val bereiche: List<String>, val probleme: List<String>)

    /**
     * Liest eine Sicherung. Jeder Bereich wird einzeln übernommen; ein
     * fehlerhafter Bereich reißt die anderen nicht mit. Nicht enthaltene
     * Bereiche bleiben unverändert.
     */
    fun importieren(ctx: Context, text: String, basis: AppDaten): ImportErgebnis {
        val obj = try {
            JsonFormat.parseToJsonElement(text.trimStart('﻿')).jsonObject
        } catch (e: Exception) {
            throw IllegalArgumentException("Die Datei ist keine gültige Sicherung (kein JSON).")
        }
        var d = basis
        val bereiche = mutableListOf<String>()
        val probleme = mutableListOf<String>()

        fun <T> bereich(schluessel: String, name: String, s: KSerializer<T>, uebernehmen: (T) -> Unit) {
            val e = obj[schluessel] ?: return
            if (e is JsonPrimitive) return
            try {
                uebernehmen(JsonFormat.decodeFromJsonElement(s, e))
                bereiche += name
            } catch (ex: Exception) {
                probleme += name + ": " + (ex.message ?: ex.toString()).take(160)
            }
        }

        bereich("ablaufregister", "Lehrgänge", ListSerializer(AblaufEintrag.serializer())) { d = d.copy(ablaufregister = it) }
        bereich("dokumente", "Dokumente", ListSerializer(Dokument.serializer())) { l ->
            d = d.copy(dokumente = l.map { it.copy(dateien = Anhaenge.ausImport(ctx, it.dateien)) })
        }
        bereich("urlaub", "Urlaub", Urlaub.serializer()) { d = d.copy(urlaub = it) }
        bereich("ueberstunden", "Mehrarbeit", Ueberstunden.serializer()) { d = d.copy(ueberstunden = it) }
        bereich("todos", "Aufgaben", Aufgaben.serializer()) { a ->
            d = d.copy(todos = a.copy(eintraege = a.eintraege.map { it.copy(anhaenge = Anhaenge.ausImport(ctx, it.anhaenge)) }))
        }
        bereich("notizen", "Notizen", Notizen.serializer()) { d = d.copy(notizen = it) }
        bereich("checklisten", "Checklisten", Checklisten.serializer()) { d = d.copy(checklisten = it) }
        bereich("kalender", "Kalender", Kalender.serializer()) { k ->
            d = d.copy(
                kalender = k.copy(eigene = k.eigene.map { it.copy(anhaenge = Anhaenge.ausImport(ctx, it.anhaenge)) }),
                farbenEigen = d.farbenEigen + k.farben,
            )
        }
        bereich("dashboard", "Wetter", Dashboard.serializer()) { d = d.copy(dashboard = it) }
        bereich("widget", "Widget", WidgetEinstellungen.serializer()) { d = d.copy(widget = it) }
        bereich("avz", "AVZ", Avz.serializer()) { d = d.copy(avz = it) }
        bereich("duz", "DUZ", Duz.serializer()) { d = d.copy(duz = it) }
        bereich("medizin", "Akte", Medizin.serializer()) { d = d.copy(medizin = it) }
        bereich("feiertagsLand", "Bundesland", FeiertagsLand.serializer()) { d = d.copy(feiertagsLand = it) }

        if (bereiche.isEmpty()) {
            throw IllegalArgumentException(
                if (probleme.isEmpty()) "Die Datei scheint keine Sicherung des Soldaten Dashboards zu sein."
                else "Keiner der Bereiche ließ sich lesen:\n" + probleme.joinToString("\n")
            )
        }
        return ImportErgebnis(d, bereiche, probleme)
    }
}
