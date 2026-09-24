package de.gun.dashboard.reloaded.ui

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import de.gun.dashboard.reloaded.logik.Termin
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.time.LocalDate

enum class Reiter(val titel: String) {
    HEUTE("Heute"), KALENDER("Kalender"), TODO("To-do"), NOTIZEN("Notizen"), URLAUB("Urlaub/Mehrarbeit"),
    LEHRGAENGE("Lehrgänge"), DOKUMENTE("Dokumente"), AKTE("Akte"), TOOLS("Tools")
}

/** Aufruf der Termin-Eingabemaske. */
data class MaskeStart(val datum: LocalDate? = null, val termin: Termin? = null)

/** Globaler Zustand der Oberfläche: Reiter, Ebenen, Meldungen. */
class Steuerung(private val scope: CoroutineScope, val snackbar: SnackbarHostState) {
    var reiter by mutableStateOf(Reiter.HEUTE)
    var menueOffen by mutableStateOf(false)
    var sucheOffen by mutableStateOf(false)
    var einrichtungOffen by mutableStateOf(false)
    var changelogOffen by mutableStateOf(false)
    var terminDetail by mutableStateOf<Termin?>(null)
    var maske by mutableStateOf<MaskeStart?>(null)
    var meldung by mutableStateOf<Pair<String, String>?>(null)

    /** Vorbelegung für ein neues IGF-Ergebnis (aus der BFT-Bewertung). */
    var igfVorlage by mutableStateOf<de.gun.dashboard.reloaded.daten.MedEintrag?>(null)

    /** Kalender: gewählter Tag und angezeigter Monat. */
    var kalenderTag by mutableStateOf<LocalDate?>(null)
    var kalenderMonat by mutableStateOf(LocalDate.now().withDayOfMonth(1))
    var kalenderEinstellungen by mutableStateOf(false)
    var kalenderGross by mutableStateOf(false)

    private var rueckgaengigJob: Job? = null
    private var ausstehend: (() -> Unit)? = null

    fun melden(titel: String, text: String) { meldung = titel to text }

    fun kurz(text: String) {
        scope.launch { snackbar.showSnackbar(text, duration = SnackbarDuration.Short) }
    }

    /**
     * Löschen mit kurzer Rückgängig-Chance. Der Aufrufer hat bereits gelöscht;
     * [endgueltig] läuft erst, wenn die Frist ohne Rückgängig abläuft (z. B.
     * Anhang-Dateien entfernen).
     */
    fun rueckgaengig(text: String, wiederherstellen: () -> Unit, endgueltig: (() -> Unit)? = null) {
        ausstehend?.invoke()
        ausstehend = endgueltig
        rueckgaengigJob?.cancel()
        snackbar.currentSnackbarData?.dismiss()
        rueckgaengigJob = scope.launch {
            val r = snackbar.showSnackbar(text, actionLabel = "Rückgängig", duration = SnackbarDuration.Long)
            if (r == SnackbarResult.ActionPerformed) {
                ausstehend = null
                wiederherstellen()
            } else {
                ausstehend?.invoke()
                ausstehend = null
            }
        }
    }

    /** Zurück-Taste: oberste Ebene schließen. true = behandelt. */
    fun zurueck(): Boolean {
        when {
            meldung != null -> meldung = null
            maske != null -> maske = null
            terminDetail != null -> terminDetail = null
            changelogOffen -> changelogOffen = false
            sucheOffen -> sucheOffen = false
            einrichtungOffen -> einrichtungOffen = false
            menueOffen -> menueOffen = false
            reiter == Reiter.KALENDER && kalenderEinstellungen -> kalenderEinstellungen = false
            reiter == Reiter.KALENDER && kalenderGross -> kalenderGross = false
            reiter == Reiter.KALENDER && kalenderTag != null -> kalenderTag = null
            reiter != Reiter.HEUTE -> reiter = Reiter.HEUTE
            else -> return false
        }
        return true
    }
}

val LocalSteuerung = staticCompositionLocalOf<Steuerung> { error("Keine Steuerung") }
