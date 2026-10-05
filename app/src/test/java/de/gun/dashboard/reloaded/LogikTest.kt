package de.gun.dashboard.reloaded

import de.gun.dashboard.reloaded.logik.*
import de.gun.dashboard.reloaded.daten.*
import kotlinx.serialization.json.*
import java.time.*
import org.junit.Assert.*
import org.junit.Test

class LogikTest {
    init { java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("Europe/Berlin")) }

    @Test fun mgrs() {
        assertEquals("33U UU 89918 19699", Koordinaten.nachMgrs(52.516275, 13.377704))
        val g = Koordinaten.ausMgrs("33U UU 89918 19699")!!
        assertEquals(52.5162, g.first, 0.001); assertEquals(13.3777, g.second, 0.001)
        assertEquals(52.5163, Koordinaten.gradLesen("52 30 58.6 N", true)!!, 0.001)
    }
    @Test fun ostern() {
        assertEquals(LocalDate.of(2026, 4, 5), Feiertage.ostersonntag(2026))
        assertEquals(LocalDate.of(2025, 4, 20), Feiertage.ostersonntag(2025))
        assertEquals(LocalDate.of(2026, 11, 18), Feiertage.liste(2026, "SN").first { it.name.startsWith("Buß") }.datum)
        // 20.-24.04.2026 Mo-Fr, kein Feiertag -> 5; 30.03.-10.04.2026 mit Karfreitag/Ostermontag -> 8
        assertEquals(5, Feiertage.arbeitstage(LocalDate.of(2026,4,20), LocalDate.of(2026,4,24), "BW"))
        assertEquals(8, Feiertage.arbeitstage(LocalDate.of(2026,3,30), LocalDate.of(2026,4,10), "BW"))
    }
    @Test fun dtg() {
        val z = Dtg.lesen("101430ZAUG26")!!
        assertEquals("101430ZAUG26", Dtg.bilden(z, true))
    }
    @Test fun duz() {
        // Samstag 26.09.2026 10:00-22:00: ab 13 Uhr Samstag (7h = 420 min)
        val z = DuzRechner.zerlegen("26.09.2026", "10:00", "22:00", "BW")!!
        assertEquals(540, z["samstag"]); assertEquals(720, z["gesamt"])
        // Nacht Mi 22:00 - Do 06:00 = 480 min Nacht
        val n = DuzRechner.zerlegen("23.09.2026", "22:00", "06:00", "BW")!!
        assertEquals(480, n["nacht"])
    }
    @Test fun wiederholung() {
        val s = LocalDateTime.of(2026, 1, 31, 9, 0)
        val v = expandiere(s, s.plusHours(1), false, "FREQ=MONTHLY", s, s.plusMonths(6))
        assertEquals(listOf(1,3,5,7), v.map { it.start.monthValue })
        val w = expandiere(LocalDateTime.of(2026,9,21,8,0), null, false, "FREQ=WEEKLY;INTERVAL=2;BYDAY=MO,WE",
            LocalDateTime.of(2026,9,1,0,0), LocalDateTime.of(2026,10,10,0,0))
        assertEquals(listOf(21,23,5,7), w.map { it.start.dayOfMonth })
        val y = expandiere(LocalDateTime.of(1990,3,14,0,0), null, true, "FREQ=YEARLY",
            LocalDateTime.of(2026,1,1,0,0), LocalDateTime.of(2027,12,31,0,0))
        assertEquals(listOf(2026, 2027), y.map { it.start.year })
        assertEquals("", regelZuWdh(null)); assertEquals("quarterly", regelZuWdh("FREQ=MONTHLY;INTERVAL=3")); assertNull(regelZuWdh("FREQ=WEEKLY;BYDAY=MO"))
    }
    @Test fun zeiten() {
        assertEquals("13:00", zeitNormieren("1300")); assertEquals("09:30", zeitNormieren("930"))
        assertEquals("08:00", zeitNormieren("8")); assertNull(zeitNormieren("25:00"))
        assertEquals("2,5", zahl(2.5)); assertEquals("30", zahl(30.0))
    }
    @Test fun importAltesFormat() {
        val json = """{"app":"Soldaten Dashboard","version":"10.26-beta",
          "ablaufregister":[{"id":"a","art":"Erste Hilfe","gueltigVon":"01.01.2024","dauerWert":"2","dauerEinheit":"J","gueltigBis":"01.01.2026","notiz":""}],
          "urlaub":{"jahresanspruch":24,"zugaenge":[{"id":"z","datum":"01.01.2026","tage":30,"notiz":"x"}],"zeitraeume":[{"id":"u","von":"20.04.2026","bis":"24.04.2026","tage":5,"notiz":"","status":"geplant"}],"geplant":[],"letztesJahr":2026},
          "ueberstunden":{"eintraege":[{"id":"o","datum":"01.02.2026","stunden":-3.5,"notiz":"FvD","geplant":true}]},
          "todos":{"eintraege":[{"id":"t","text":"A","faellig":"","uhrzeit":"","prio":"hoch","notiz":"","anhaenge":[],"erledigt":false,"erstellt":"01.01.2026"}]},
          "kalender":{"quellen":[{"id":"q","name":"Q","farbe":"#5fb4ff","istUrlaub":false,"url":"","aktiv":true,"events":[{"uid":"1","titel":"T","start":"2026-09-23T22:00:00.000Z","end":"2026-09-24T22:00:00.000Z","allDay":true,"rrule":null,"ort":"","beschreibung":""}],"aktualisiert":"01.01.2026"}],"fvdStunden":8,"schriftgroesse":110,"farben":{"5":"#ff0000"},"eigene":[{"id":"e","uid":"u@d","sequence":0,"titel":"X","von":"01.10.2026","bis":"01.10.2026","ganztags":true,"zeitVon":"","zeitBis":"","ort":"","notiz":"","anhaenge":[],"kalenderId":"3","wiederholung":"","nativId":1234,"nativ":true}],"todosImKalender":true},
          "medizin":{"person":{"name":"N","dienstgrad":"HF","einheit":"","geburt":"","alter":null},"igf":[{"id":"i","art":"BFT","datum":"01.01.2026","gueltigBis":"01.01.2027","intervall":"12M","ergebnis":"","notiz":""}],"avu":[],"impfungen":[{"id":"m","art":"Tetanus","datum":"01.01.2020","jahre":10,"gueltigBis":"01.01.2030","dosis":"","notiz":""}],"bft":{"maxSprint":60,"bestSprint":43,"minKlimm":5,"bestKlimm":70,"maxLauf":"6:30","bestLauf":"3:30"}},
          "avz":{"zeitraeume":[{"id":"v","von":"01.01.2026","bis":"10.01.2026","tage":10,"einsatz":"L","stufe":"3","satz":null,"notiz":""}]},
          "widget":{"modus":"hell","deckkraft":70,"aufgaben":true,"tage":3},
          "feiertagsLand":{"land":"BY"},"checklisten":{"listen":[]},"notizen":{"eintraege":[]},"dashboard":{"wetterUrl":"","quelle":"openmeteo","ort":{"name":"Ulm","breite":48.4,"laenge":9.98}}}"""
        val o = JsonFormat.parseToJsonElement(json).jsonObject
        val u = JsonFormat.decodeFromJsonElement(Urlaub.serializer(), o["urlaub"]!!)
        assertEquals(24.0, u.jahresanspruch, 0.0001)
        val k = JsonFormat.decodeFromJsonElement(Kalender.serializer(), o["kalender"]!!)
        assertEquals("1234", k.eigene[0].nativId); assertEquals(110, k.schriftgroesse)
        val m = JsonFormat.decodeFromJsonElement(Medizin.serializer(), o["medizin"]!!)
        assertEquals(10, m.impfungen[0].jahre); assertEquals(IGF_PFLICHT_STANDARD, m.igfPflicht)
        val a = JsonFormat.decodeFromJsonElement(kotlinx.serialization.builtins.ListSerializer(AblaufEintrag.serializer()), o["ablaufregister"]!!)
        assertEquals("2", a[0].dauerWert)
        val av = JsonFormat.decodeFromJsonElement(Avz.serializer(), o["avz"]!!); assertNull(av.zeitraeume[0].satz)
        val ue = JsonFormat.decodeFromJsonElement(Ueberstunden.serializer(), o["ueberstunden"]!!); assertEquals(-3.5, ue.eintraege[0].stunden, 0.0001)
        // Rundreise
        val alles = AppDaten(urlaub = u, kalender = k, medizin = m)
        val text = JsonFormat.encodeToString(AppDaten.serializer(), alles)
        assertEquals(alles, JsonFormat.decodeFromString(AppDaten.serializer(), text))
        // ICS-Termin (ganztägig, alte UTC-Ablage) liegt am 24.09.
        val b = TerminBestand(AppDaten(kalender = k), emptyList(), emptyList(), emptyList())
        val t = terminFenster(b, LocalDate.of(2026,9,20), LocalDate.of(2026,9,30)).filter { it.quelleId == "q" }
        assertEquals(1, t.size); assertEquals(LocalDate.of(2026,9,24), t[0].ersterTag); assertEquals(LocalDate.of(2026,9,24), t[0].letzterTag)
    }
    @Test fun aufgabenWiederholung() {
        val heute = LocalDate.of(2026, 9, 27)
        // monatlich vom 31.01. -> 28.02.; spät erledigt -> nächste ab heute
        assertEquals(LocalDate.of(2026, 2, 28), naechsteFaelligkeit(LocalDate.of(2026, 1, 31), "monatlich", LocalDate.of(2026, 1, 1)))
        assertEquals(LocalDate.of(2026, 10, 15), naechsteFaelligkeit(LocalDate.of(2026, 6, 15), "monatlich", heute))
        // werktags: Fr 25.09. -> Mo 28.09.
        assertEquals(LocalDate.of(2026, 9, 28), naechsteFaelligkeit(LocalDate.of(2026, 9, 25), "werktags", heute))
        assertNull(naechsteFaelligkeit(heute, "", heute))
        val a = Aufgabe("a", "Reisekosten", "15.09.2026", "", "mittel", wiederholung = "monatlich")
        val l = aufgabeUmschalten(listOf(a), "a", heute) { "b" }
        assertEquals(2, l.size)
        assertTrue(l.first { it.id == "a" }.erledigt)
        assertEquals("15.10.2026", l.first { it.id == "b" }.faellig)
        assertFalse(l.first { it.id == "b" }.erledigt)
        // einmalige Aufgabe: nur abhaken, wieder öffnen
        val e = aufgabeUmschalten(listOf(a.copy(wiederholung = "")), "a", heute)
        assertEquals(1, e.size); assertTrue(e[0].erledigt)
        assertFalse(aufgabeUmschalten(e, "a", heute)[0].erledigt)
    }
    @Test fun tagesueberblick() {
        val e = Tagesueberblick()
        val jetzt = LocalDateTime.of(2026, 9, 28, 22, 0) // Montagabend
        // Wecker Di 05:45 -> 05:46
        assertEquals(LocalDateTime.of(2026, 9, 29, 5, 46), naechsterUeberblick(jetzt, e, LocalDateTime.of(2026, 9, 29, 5, 45), null))
        // kein Wecker -> feste Zeit 07:00 am Dienstag
        assertEquals(LocalDateTime.of(2026, 9, 29, 7, 0), naechsterUeberblick(jetzt, e, null, null))
        // Nachtdienst-Wecker 02:30 zählt nicht -> 07:00
        assertEquals(LocalDateTime.of(2026, 9, 29, 7, 0), naechsterUeberblick(jetzt, e, LocalDateTime.of(2026, 9, 29, 2, 30), null))
        // heute schon gezeigt -> morgen
        val frueh = LocalDateTime.of(2026, 9, 28, 6, 0)
        assertEquals(LocalDateTime.of(2026, 9, 29, 7, 0), naechsterUeberblick(frueh, e, null, LocalDate.of(2026, 9, 28)))
        // nur Mo-Fr: Freitagabend -> Montag
        val werktags = e.copy(tage = listOf(1, 2, 3, 4, 5))
        assertEquals(LocalDateTime.of(2026, 10, 5, 7, 0), naechsterUeberblick(LocalDateTime.of(2026, 10, 2, 20, 0), werktags, null, null))
        assertNull(naechsterUeberblick(jetzt, e.copy(an = false), null, null))
    }
    @Test fun kerosin() {
        val p = EINHEITEN.first { it.titel.startsWith("F-34") }
        assertEquals("567", einheitRechnen(p, true, "1000"))   // 1000 lbs ≈ 567 l
        assertEquals("1763,7", einheitRechnen(p, false, "1000")) // 1000 l ≈ 1763,7 lbs
    }
    @Test fun mehrarbeitGeplant() {
        val d = AppDaten(ueberstunden = Ueberstunden(
            listOf(UeberstundenEintrag("a", "01.09.2026", 10.0), UeberstundenEintrag("b", "05.10.2026", -8.0, geplant = true)), 2.0))
        assertEquals(12.0, mehrarbeitSaldo(d), 0.001)
        assertEquals(4.0, mehrarbeitSaldoGeplant(d), 0.001)
    }
    @Test fun verschwundeneTermine() {
        val jahr = LocalDate.now().year
        val tag = "02.10.$jahr"
        val kal = de.gun.dashboard.reloaded.geraet.GeraetKalender("7", "Dienst intern", "x@outlook.de", "Outlook", 0, true)
        val fvd = EigenerTermin(id = "fvd", titel = "FvD", von = tag, zeitVon = "07:00", zeitBis = "11:00", kalenderId = "7", nativId = "100", nativ = true)
        val da = EigenerTermin(id = "da", titel = "Sport", von = tag, zeitVon = "15:00", zeitBis = "16:00", kalenderId = "7", nativId = "200", nativ = true)
        val neuId = EigenerTermin(id = "neu", titel = "Wache", von = tag, ganztags = true, kalenderId = "7", nativId = "300", nativ = true)
        val d = AppDaten(kalender = Kalender(eigene = listOf(fvd, da, neuId)))
        val geraet = listOf(
            de.gun.dashboard.reloaded.geraet.GeraetTermin(200, "7", "Sport", LocalDate.of(jahr, 10, 2).atTime(15, 0), null, false, "", "", null, 0),
            // nach Neusynchronisation andere ID, gleicher Titel und Tag -> gilt als vorhanden
            de.gun.dashboard.reloaded.geraet.GeraetTermin(999, "7", "Wache", LocalDate.of(jahr, 10, 2).atStartOfDay(), null, true, "", "", null, 0),
        )
        val b = TerminBestand(d, listOf(kal), geraet, emptyList())
        assertEquals(setOf("fvd"), fehlendeEigeneIds(b))
        // Der verschwundene FvD wird aus der App-Kopie angezeigt und markiert
        val t = terminFenster(b, LocalDate.of(jahr, 10, 2), LocalDate.of(jahr, 10, 2), false)
        assertTrue(t.any { it.eigenerId == "fvd" && it.fehltImGeraet })
        // vorhandener Termin kommt nur aus dem Gerätekalender, nicht zusätzlich aus der App-Kopie
        assertEquals(1, t.count { it.eigenerId == "da" || it.titel == "Sport" })
        assertFalse(t.any { it.quelleId == "eigene" && it.titel == "Sport" })
        // Ohne eingelesene Kalender: keine Aussage, nichts als fehlend markiert
        assertTrue(fehlendeEigeneIds(TerminBestand(d, emptyList(), emptyList(), emptyList())).isEmpty())
    }
    @Test fun uebertragung() {
        assertEquals(3_600_000L, dauerMs("P3600S"))
        assertEquals(5_400_000L, dauerMs("PT1H30M"))
        assertEquals(86_400_000L, dauerMs("P1D"))
        assertEquals(1_209_600_000L, dauerMs("P2W"))
        assertNull(dauerMs("quatsch"))
        assertEquals("P2D", dauerText(2 * 86_400_000L, true))
        assertEquals("P3600S", dauerText(3_600_000L, false))
        val ms = LocalDateTime.of(2026, 10, 2, 7, 0).atZone(ZoneId.of("Europe/Berlin")).toInstant().toEpochMilli()
        assertEquals("20261002T050000Z", exdateAnhaengen(null, ms))
        assertEquals("20261001T050000Z,20261002T050000Z", exdateAnhaengen("20261001T050000Z", ms))
        assertEquals("Europe/Berlin;20261001T070000,20261002T070000", exdateAnhaengen("Europe/Berlin;20261001T070000", ms))
        assertEquals(terminSchluessel(" FvD ", 5), terminSchluessel("fvd", 5))
    }
    @Test fun terminLaenge() {
        fun t(von: LocalDateTime, bis: LocalDateTime, ganz: Boolean = false) =
            Termin("q", "Q", 0, "X", "u", start = von, ende = bis, ganztags = ganz)
        // Mo 05.10. – Fr 09.10.2026 ganztägig (Ende exklusiv Sa 00:00): 5 Kalendertage, 5 Arbeitstage
        assertEquals("5 Kalendertage · 5 Arbeitstage", terminDauer(t(LocalDateTime.of(2026, 10, 5, 0, 0), LocalDateTime.of(2026, 10, 10, 0, 0), true), "BW"))
        // Fr 02.10. – Mo 05.10. inkl. Wochenende und 3. Oktober: 4 Kalendertage, 2 Arbeitstage
        assertEquals("4 Kalendertage · 2 Arbeitstage", terminDauer(t(LocalDateTime.of(2026, 10, 2, 0, 0), LocalDateTime.of(2026, 10, 6, 0, 0), true), "BW"))
        assertEquals("1 Kalendertag · 4 Std", terminDauer(t(LocalDateTime.of(2026, 10, 2, 7, 0), LocalDateTime.of(2026, 10, 2, 11, 0)), "BW"))
        assertEquals("1 Kalendertag · 1 Std 30 Min", terminDauer(t(LocalDateTime.of(2026, 10, 2, 7, 0), LocalDateTime.of(2026, 10, 2, 8, 30)), "BW"))
        assertEquals("1 Kalendertag", terminDauer(t(LocalDateTime.of(2026, 10, 2, 0, 0), LocalDateTime.of(2026, 10, 3, 0, 0), true), "BW"))
    }
    @Test fun ics() {
        val t = icsLesen("BEGIN:VCALENDAR\r\nBEGIN:VEVENT\r\nUID:x\r\nSUMMARY:Test\\, eins\r\nDTSTART;VALUE=DATE:20261003\r\nDTEND;VALUE=DATE:20261004\r\nEND:VEVENT\r\nEND:VCALENDAR")
        assertEquals("Test, eins", t[0].titel); assertTrue(t[0].allDay)
    }
}
