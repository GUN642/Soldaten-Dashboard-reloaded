package de.gun.dashboard.reloaded

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import de.gun.dashboard.reloaded.daten.Anhaenge
import de.gun.dashboard.reloaded.daten.Speicher
import de.gun.dashboard.reloaded.ui.Oberflaeche

class MainActivity : ComponentActivity() {

    /** Ziel aus Widget oder Benachrichtigung ("todo", "kalender", …). */
    var ziel by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Speicher.init(this)
        ziel = intent?.getStringExtra("ziel")
        setContent { Oberflaeche(this) }
        // Einmal je Start: Dateien ohne Verweis entfernen
        window.decorView.postDelayed({
            try { Anhaenge.verwaisteAufraeumen(this, Speicher.aktuell) } catch (e: Exception) { }
        }, 4000)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.getStringExtra("ziel")?.let { ziel = it }
    }

    override fun onResume() {
        super.onResume()
        // Änderungen aus Outlook oder der Kalender-App bekommt die App nicht mitgeteilt
        Aktualisierung.geraetLaden(this)
    }

    override fun onPause() {
        super.onPause()
        Speicher.sofortSchreiben()
    }
}
