package de.gun.dashboard.reloaded.geraet

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/** Geburtstag, Jahrestag oder eigener Anlass aus den Kontakten. */
data class KontaktAnlass(
    val kontaktId: String,
    val name: String,
    val bezeichnung: String,
    val jahr: Int?,
    val monat: Int,
    val tag: Int,
)

object Kontakte {
    private val _anlaesse = MutableStateFlow<List<KontaktAnlass>>(emptyList())
    val anlaesse: StateFlow<List<KontaktAnlass>> = _anlaesse.asStateFlow()

    fun darfLesen(ctx: Context) =
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED

    /** "1985-03-14", "--03-14", "14.03.1985", "14.03." */
    fun datumZerlegen(roh: String): Triple<Int?, Int, Int>? {
        val t = roh.trim()
        Regex("""^(\d{4})-(\d{1,2})-(\d{1,2})""").find(t)?.let {
            return Triple(it.groupValues[1].toInt(), it.groupValues[2].toInt(), it.groupValues[3].toInt())
        }
        Regex("""^--(\d{1,2})-(\d{1,2})""").find(t)?.let {
            return Triple(null, it.groupValues[1].toInt(), it.groupValues[2].toInt())
        }
        Regex("""^(\d{1,2})\.(\d{1,2})\.(\d{4})""").find(t)?.let {
            return Triple(it.groupValues[3].toInt(), it.groupValues[2].toInt(), it.groupValues[1].toInt())
        }
        Regex("""^(\d{1,2})\.(\d{1,2})\.?$""").find(t)?.let {
            return Triple(null, it.groupValues[2].toInt(), it.groupValues[1].toInt())
        }
        return null
    }

    suspend fun einlesen(ctx: Context) = withContext(Dispatchers.IO) {
        if (!darfLesen(ctx)) { _anlaesse.value = emptyList(); return@withContext }
        val liste = mutableListOf<KontaktAnlass>()
        val gesehen = HashSet<String>()
        try {
            ctx.contentResolver.query(
                ContactsContract.Data.CONTENT_URI,
                arrayOf(
                    ContactsContract.Data.CONTACT_ID,
                    ContactsContract.Data.DISPLAY_NAME,
                    ContactsContract.CommonDataKinds.Event.START_DATE,
                    ContactsContract.CommonDataKinds.Event.TYPE,
                    ContactsContract.CommonDataKinds.Event.LABEL,
                ),
                ContactsContract.Data.MIMETYPE + " = ?",
                arrayOf(ContactsContract.CommonDataKinds.Event.CONTENT_ITEM_TYPE),
                null
            )?.use { c ->
                val iId = c.getColumnIndex(ContactsContract.Data.CONTACT_ID)
                val iName = c.getColumnIndex(ContactsContract.Data.DISPLAY_NAME)
                val iDatum = c.getColumnIndex(ContactsContract.CommonDataKinds.Event.START_DATE)
                val iTyp = c.getColumnIndex(ContactsContract.CommonDataKinds.Event.TYPE)
                val iLabel = c.getColumnIndex(ContactsContract.CommonDataKinds.Event.LABEL)
                while (c.moveToNext()) {
                    val datum = if (iDatum >= 0) c.getString(iDatum) else null
                    if (datum.isNullOrBlank()) continue
                    val d = datumZerlegen(datum) ?: continue
                    val typ = if (iTyp >= 0 && !c.isNull(iTyp)) c.getInt(iTyp) else 0
                    val bez = when (typ) {
                        ContactsContract.CommonDataKinds.Event.TYPE_BIRTHDAY -> "Geburtstag"
                        ContactsContract.CommonDataKinds.Event.TYPE_ANNIVERSARY -> "Jahrestag"
                        ContactsContract.CommonDataKinds.Event.TYPE_OTHER -> "Anlass"
                        else -> (if (iLabel >= 0) c.getString(iLabel) else null)?.takeIf { it.isNotBlank() } ?: "Anlass"
                    }
                    val name = (if (iName >= 0) c.getString(iName) else null) ?: "Unbekannt"
                    // Derselbe Kontakt kann über mehrere Konten mehrfach vorliegen
                    val schluessel = name.trim().lowercase() + "|" + (d.first ?: 0) + "-" + d.second + "-" + d.third + "|" + bez
                    if (!gesehen.add(schluessel)) continue
                    liste.add(KontaktAnlass((if (iId >= 0) c.getString(iId) else null) ?: "", name, bez, d.first, d.second, d.third))
                }
            }
        } catch (e: Exception) { /* ohne Kontakte weiter */ }
        _anlaesse.value = liste
    }
}
