package de.gun.dashboard.reloaded.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.gun.dashboard.reloaded.logik.Status
import de.gun.dashboard.reloaded.logik.alsDE
import de.gun.dashboard.reloaded.logik.parseDE
import de.gun.dashboard.reloaded.logik.zeitNormieren
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

val RUND = RoundedCornerShape(20.dp)
val RUND_KLEIN = RoundedCornerShape(12.dp)

// ------------------------------------------------------------------ Texte

@Composable
fun Punkt(text: String, groesse: TextUnit = 22.sp, farbe: Color = LocalPalette.current.text, modifier: Modifier = Modifier, fett: Boolean = true, zeilen: Int = 1) {
    val p = LocalPalette.current
    Text(
        text, modifier = modifier, color = farbe,
        style = TextStyle(
            fontFamily = p.titelSchrift, fontSize = groesse,
            fontWeight = if (fett) FontWeight.ExtraBold else FontWeight.SemiBold,
            letterSpacing = if (p.punktSchrift) 1.sp else 0.sp,
        ),
        maxLines = zeilen, overflow = TextOverflow.Ellipsis,
    )
}

/** Kleine Beschriftung in Versalien, Monoschrift. */
@Composable
fun Etikett(text: String, farbe: Color = LocalPalette.current.textFaint, modifier: Modifier = Modifier, groesse: TextUnit = 10.5.sp) {
    Text(
        text.uppercase(), modifier = modifier, color = farbe,
        style = TextStyle(fontFamily = Schrift.mono, fontSize = groesse, letterSpacing = 1.4.sp, fontWeight = FontWeight.Bold),
        maxLines = 1, overflow = TextOverflow.Ellipsis,
    )
}

@Composable
fun Fliesstext(text: String, farbe: Color = LocalPalette.current.text, groesse: TextUnit = 14.sp, modifier: Modifier = Modifier, fett: Boolean = false, zeilen: Int = Int.MAX_VALUE) {
    Text(
        text, modifier = modifier, color = farbe, maxLines = zeilen, overflow = TextOverflow.Ellipsis,
        style = TextStyle(fontFamily = Schrift.text, fontSize = groesse, fontWeight = if (fett) FontWeight.Bold else FontWeight.Normal, lineHeight = groesse * 1.35f),
    )
}

@Composable
fun Mono(text: String, farbe: Color = LocalPalette.current.textDim, groesse: TextUnit = 12.sp, modifier: Modifier = Modifier, fett: Boolean = false) {
    Text(text, modifier = modifier, color = farbe, style = TextStyle(fontFamily = Schrift.mono, fontSize = groesse, fontWeight = if (fett) FontWeight.Bold else FontWeight.Normal))
}

@Composable
fun Hinweis(text: String, modifier: Modifier = Modifier, farbe: Color = LocalPalette.current.textFaint) {
    Text(text, modifier = modifier.padding(top = 4.dp), color = farbe, style = TextStyle(fontFamily = Schrift.text, fontSize = 12.sp, lineHeight = 16.sp))
}

// ------------------------------------------------------------------ Flächen

/** Punktraster wie auf der Rückseite der Nothing-Geräte. */
@Composable
fun PunktRaster(modifier: Modifier, farbe: Color, abstand: Dp = 9.dp, radius: Dp = 1.dp) {
    Canvas(modifier) {
        val a = abstand.toPx()
        val r = radius.toPx()
        var y = a / 2
        while (y < size.height) {
            var x = a / 2
            while (x < size.width) {
                drawCircle(farbe, r, Offset(x, y))
                x += a
            }
            y += a
        }
    }
}

@Composable
fun Karte(
    titel: String? = null,
    index: String? = null,
    modifier: Modifier = Modifier,
    aktion: (@Composable RowScope.() -> Unit)? = null,
    inhalt: @Composable ColumnScope.() -> Unit,
) {
    val p = LocalPalette.current
    Column(
        modifier
            .fillMaxWidth()
            .clip(RUND)
            .background(p.panel)
            .border(1.dp, p.rand, RUND)
            .padding(16.dp)
    ) {
        if (titel != null) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                if (index != null) {
                    Mono(index, p.akzent, 12.sp, fett = true)
                    Spacer(Modifier.width(8.dp))
                }
                Punkt(titel.uppercase(), 17.sp, modifier = Modifier.weight(1f), zeilen = 2)
                if (aktion != null) Row(verticalAlignment = Alignment.CenterVertically, content = aktion)
            }
        }
        inhalt()
    }
}

/** Listeneintrag mit farbigem Streifen links. */
@Composable
fun Zeile(
    streifen: Color,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    aktionen: (@Composable RowScope.() -> Unit)? = null,
    inhalt: @Composable ColumnScope.() -> Unit,
) {
    val p = LocalPalette.current
    Row(
        modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RUND_KLEIN)
            .background(p.panelAlt)
            .border(1.dp, p.randLeise, RUND_KLEIN)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .height(IntrinsicSize.Min),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(4.dp).fillMaxHeight().background(streifen))
        Column(Modifier.weight(1f).padding(horizontal = 12.dp, vertical = 10.dp), content = inhalt)
        if (aktionen != null) Row(Modifier.padding(end = 4.dp), verticalAlignment = Alignment.CenterVertically, content = aktionen)
    }
}

@Composable
fun Leer(text: String) {
    val p = LocalPalette.current
    Box(
        Modifier.fillMaxWidth().padding(vertical = 6.dp).clip(RUND_KLEIN)
            .border(BorderStroke(1.dp, p.randLeise), RUND_KLEIN).padding(16.dp),
        contentAlignment = Alignment.Center
    ) { Fliesstext(text, p.textFaint, 13.sp) }
}

@Composable
fun Trenner(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().padding(vertical = 10.dp).height(1.dp).background(LocalPalette.current.randLeise))
}

@Composable
fun Abstand(h: Dp = 12.dp) = Spacer(Modifier.height(h))

// ------------------------------------------------------------------ Knöpfe

enum class KnopfArt { NORMAL, PRIMAER, GEFAHR, LEISE, BESTAETIGEN }

@Composable
fun Knopf(
    text: String,
    modifier: Modifier = Modifier,
    art: KnopfArt = KnopfArt.NORMAL,
    klein: Boolean = false,
    aktiv: Boolean = true,
    onClick: () -> Unit,
) {
    val p = LocalPalette.current
    val (hg, vg, rand) = when (art) {
        KnopfArt.PRIMAER -> Triple(p.akzent, if (p.akzent.luminanz() > 0.5f) Color.Black else Color.White, p.akzent)
        KnopfArt.GEFAHR -> Triple(Color.Transparent, p.rot, p.rot.copy(alpha = 0.6f))
        KnopfArt.BESTAETIGEN -> Triple(Color.Transparent, p.gruen, p.gruen.copy(alpha = 0.6f))
        KnopfArt.LEISE -> Triple(Color.Transparent, p.textDim, Color.Transparent)
        KnopfArt.NORMAL -> Triple(Color.Transparent, p.text, p.rand)
    }
    Box(
        modifier
            .heightIn(min = if (klein) 32.dp else 42.dp)
            .clip(RoundedCornerShape(50))
            .background(hg.copy(alpha = if (aktiv) hg.alpha else hg.alpha * 0.4f))
            .border(1.dp, rand, RoundedCornerShape(50))
            .clickable(enabled = aktiv, onClick = onClick)
            .padding(horizontal = if (klein) 12.dp else 18.dp, vertical = if (klein) 6.dp else 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text.uppercase(), color = vg.copy(alpha = if (aktiv) 1f else 0.4f), maxLines = 1,
            style = TextStyle(fontFamily = Schrift.mono, fontWeight = FontWeight.Bold, fontSize = if (klein) 11.sp else 12.5.sp, letterSpacing = 1.sp)
        )
    }
}

/** Runder Symbolknopf. */
@Composable
fun Symbol(text: String, farbe: Color = LocalPalette.current.textDim, onClick: () -> Unit) {
    Box(
        Modifier.size(36.dp).clip(CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Text(text, color = farbe, fontSize = 16.sp, fontFamily = Schrift.mono) }
}

/** Schalter in Pillenform: ● an / ○ aus. */
@Composable
fun Pille(text: String, an: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val p = LocalPalette.current
    Box(
        modifier
            .clip(RoundedCornerShape(50))
            .background(if (an) p.akzentDim else Color.Transparent)
            .border(1.dp, if (an) p.akzent else p.rand, RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(
            (if (an) "● " else "○ ") + text, color = if (an) p.text else p.textDim,
            style = TextStyle(fontFamily = Schrift.mono, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        )
    }
}

/** Segmentauswahl. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun Segmente(optionen: List<Pair<String, String>>, gewaehlt: String, modifier: Modifier = Modifier, onWahl: (String) -> Unit) {
    val p = LocalPalette.current
    FlowRow(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        optionen.forEach { (id, name) ->
            val an = id == gewaehlt
            Box(
                Modifier
                    .clip(RoundedCornerShape(50))
                    .background(if (an) p.text else Color.Transparent)
                    .border(1.dp, if (an) p.text else p.rand, RoundedCornerShape(50))
                    .clickable { onWahl(id) }
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Text(name.uppercase(), color = if (an) p.bg else p.textDim,
                    style = TextStyle(fontFamily = Schrift.mono, fontSize = 11.5.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp))
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun Knopfreihe(modifier: Modifier = Modifier, inhalt: @Composable () -> Unit) {
    FlowRow(modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        inhalt()
    }
}

// ------------------------------------------------------------------ Status

@Composable
fun StatusMarke(s: Status, text: String = s.label) {
    val p = LocalPalette.current
    val (vg, hg) = when (s) {
        Status.GUELTIG -> p.gruen to p.gruenDim
        Status.WARNUNG -> p.warn to p.warnDim
        Status.ABGELAUFEN -> p.rot to p.rotDim
        Status.UNBEKANNT -> p.textDim to p.panelAlt
    }
    Box(Modifier.clip(RoundedCornerShape(50)).background(hg).padding(horizontal = 8.dp, vertical = 3.dp)) {
        Text(text.uppercase(), color = vg, style = TextStyle(fontFamily = Schrift.mono, fontSize = 9.5.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp))
    }
}

fun Palette.statusFarbe(s: Status) = when (s) {
    Status.GUELTIG -> gruen
    Status.WARNUNG -> warn
    Status.ABGELAUFEN -> rot
    Status.UNBEKANNT -> textFaint
}

/** Große Kennzahl in Punktschrift. */
@Composable
fun Kennzahl(wert: String, label: String, farbe: Color = LocalPalette.current.text, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    val p = LocalPalette.current
    Column(
        modifier.clip(RUND_KLEIN).background(p.panelAlt).border(1.dp, p.randLeise, RUND_KLEIN)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).padding(12.dp)
    ) {
        Punkt(wert, if (wert.length > 5) 20.sp else 26.sp, farbe)
        Etikett(label)
    }
}

/** Gestapelter Balken (gültig / Warnung / abgelaufen). */
@Composable
fun StatusBalken(g: Int, w: Int, a: Int) {
    val p = LocalPalette.current
    val summe = maxOf(1, g + w + a)
    Row(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(p.randLeise)) {
        if (g > 0) Box(Modifier.weight(g.toFloat() / summe).fillMaxHeight().background(p.gruen))
        if (w > 0) Box(Modifier.weight(w.toFloat() / summe).fillMaxHeight().background(p.warn))
        if (a > 0) Box(Modifier.weight(a.toFloat() / summe).fillMaxHeight().background(p.rot))
        if (g + w + a == 0) Spacer(Modifier.weight(1f))
    }
}

// ------------------------------------------------------------------ Eingaben

@Composable
fun feldFarben() = LocalPalette.current.let { p ->
    OutlinedTextFieldDefaults.colors(
        focusedBorderColor = p.akzent, unfocusedBorderColor = p.rand,
        focusedTextColor = p.text, unfocusedTextColor = p.text,
        cursorColor = p.akzent, focusedLabelColor = p.akzent, unfocusedLabelColor = p.textDim,
        focusedContainerColor = p.panelAlt, unfocusedContainerColor = p.panelAlt,
        focusedPlaceholderColor = p.textFaint, unfocusedPlaceholderColor = p.textFaint,
    )
}

@Composable
fun Feld(
    wert: String,
    onWert: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    platzhalter: String = "",
    zeilen: Int = 1,
    tastatur: KeyboardType = KeyboardType.Text,
    rechts: (@Composable () -> Unit)? = null,
    beiVerlassen: (() -> Unit)? = null,
) {
    OutlinedTextField(
        value = wert, onValueChange = onWert,
        modifier = modifier.fillMaxWidth().padding(vertical = 4.dp)
            .then(if (beiVerlassen != null) Modifier.onFocusChanged { if (!it.isFocused) beiVerlassen() } else Modifier),
        label = { Text(label, fontFamily = Schrift.text, fontSize = 13.sp) },
        placeholder = { if (platzhalter.isNotEmpty()) Text(platzhalter, fontFamily = Schrift.text, fontSize = 14.sp) },
        singleLine = zeilen == 1, minLines = if (zeilen > 1) zeilen else 1,
        keyboardOptions = KeyboardOptions(keyboardType = tastatur),
        textStyle = TextStyle(fontFamily = Schrift.text, fontSize = 15.sp),
        shape = RUND_KLEIN, colors = feldFarben(), trailingIcon = rechts,
    )
}

/** Uhrzeit, wird beim Verlassen in HH:MM umgeschrieben (0930 -> 09:30). */
@Composable
fun ZeitFeld(wert: String, onWert: (String) -> Unit, label: String, modifier: Modifier = Modifier) {
    Feld(wert, onWert, label, modifier, platzhalter = "z. B. 0930", tastatur = KeyboardType.Number,
        beiVerlassen = { zeitNormieren(wert)?.let { if (it != wert) onWert(it) } })
}

/** Datum TT.MM.JJJJ mit automatischen Punkten und Kalenderauswahl. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatumFeld(wert: String, onWert: (String) -> Unit, label: String, modifier: Modifier = Modifier) {
    var zeigen by remember { mutableStateOf(false) }
    var feld by remember { mutableStateOf(TextFieldValue(wert, TextRange(wert.length))) }
    if (feld.text != wert) feld = TextFieldValue(wert, TextRange(wert.length))
    OutlinedTextField(
        value = feld,
        onValueChange = { neu ->
            val z = neu.text.filter { it.isDigit() }.take(8)
            val t = when {
                z.length > 4 -> z.substring(0, 2) + "." + z.substring(2, 4) + "." + z.substring(4)
                z.length > 2 -> z.substring(0, 2) + "." + z.substring(2)
                else -> z
            }
            val getippt = neu.text.length >= feld.text.length
            val ergebnis = if (getippt) t else neu.text.filter { it.isDigit() || it == '.' }
            feld = TextFieldValue(ergebnis, TextRange(ergebnis.length))
            onWert(ergebnis)
        },
        modifier = modifier.fillMaxWidth().padding(vertical = 4.dp).onFocusChanged {
            if (!it.isFocused) {
                val z = wert.filter { c -> c.isDigit() }
                if (z.length == 6) onWert(z.substring(0, 2) + "." + z.substring(2, 4) + ".20" + z.substring(4))
            }
        },
        label = { Text(label, fontFamily = Schrift.text, fontSize = 13.sp) },
        placeholder = { Text("TT.MM.JJJJ", fontFamily = Schrift.text) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        textStyle = TextStyle(fontFamily = Schrift.text, fontSize = 15.sp),
        shape = RUND_KLEIN, colors = feldFarben(),
        trailingIcon = {
            IconButton(onClick = { zeigen = true }) {
                Icon(Icons.Outlined.CalendarMonth, "Kalender", tint = LocalPalette.current.textDim)
            }
        },
    )
    if (zeigen) {
        val start = parseDE(wert) ?: LocalDate.now()
        val zustand = rememberDatePickerState(initialSelectedDateMillis = start.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
        val p = LocalPalette.current
        DatePickerDialog(
            onDismissRequest = { zeigen = false },
            confirmButton = {
                TextButton(onClick = {
                    zustand.selectedDateMillis?.let {
                        onWert(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().alsDE())
                    }
                    zeigen = false
                }) { Text("ÜBERNEHMEN", fontFamily = Schrift.mono, color = p.akzent) }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { onWert(""); zeigen = false }) { Text("LEEREN", fontFamily = Schrift.mono, color = p.textDim) }
                    TextButton(onClick = { zeigen = false }) { Text("ABBRECHEN", fontFamily = Schrift.mono, color = p.textDim) }
                }
            },
            colors = DatePickerDefaults.colors(containerColor = p.panel),
        ) {
            DatePicker(
                state = zustand,
                colors = DatePickerDefaults.colors(
                    containerColor = p.panel, selectedDayContainerColor = p.akzent, todayDateBorderColor = p.akzent,
                    todayContentColor = p.akzent,
                ),
            )
        }
    }
}

/** Auswahlliste als aufklappbares Feld. */
@Composable
fun Auswahl(label: String, optionen: List<Pair<String, String>>, wert: String, modifier: Modifier = Modifier, onWert: (String) -> Unit) {
    var offen by remember { mutableStateOf(false) }
    val p = LocalPalette.current
    Box(modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        OutlinedTextField(
            value = optionen.firstOrNull { it.first == wert }?.second ?: wert,
            onValueChange = {}, readOnly = true, enabled = false,
            label = { Text(label, fontFamily = Schrift.text, fontSize = 13.sp) },
            modifier = Modifier.fillMaxWidth(),
            textStyle = TextStyle(fontFamily = Schrift.text, fontSize = 15.sp),
            shape = RUND_KLEIN,
            trailingIcon = { Icon(Icons.Outlined.KeyboardArrowDown, null, tint = p.textDim) },
            colors = OutlinedTextFieldDefaults.colors(
                disabledTextColor = p.text, disabledBorderColor = p.rand, disabledLabelColor = p.textDim,
                disabledContainerColor = p.panelAlt, disabledTrailingIconColor = p.textDim,
            ),
        )
        Box(Modifier.matchParentSize().clip(RUND_KLEIN).clickable { offen = true })
        DropdownMenu(expanded = offen, onDismissRequest = { offen = false }) {
            optionen.forEach { (id, name) ->
                DropdownMenuItem(
                    text = { Text(name, fontFamily = Schrift.text, color = if (id == wert) p.akzent else p.text) },
                    onClick = { onWert(id); offen = false }
                )
            }
        }
    }
}

/** Freitextfeld mit Vorschlagsliste. */
@Composable
fun VorschlagFeld(wert: String, onWert: (String) -> Unit, label: String, vorschlaege: List<Pair<String, String>>, onVorschlag: ((String) -> Unit)? = null) {
    var offen by remember { mutableStateOf(false) }
    val p = LocalPalette.current
    Box {
        Feld(wert, onWert, label, rechts = {
            IconButton(onClick = { offen = true }) { Icon(Icons.Outlined.KeyboardArrowDown, "Vorschläge", tint = p.textDim) }
        })
        DropdownMenu(expanded = offen, onDismissRequest = { offen = false }) {
            vorschlaege.forEach { (id, name) ->
                DropdownMenuItem(text = { Text(name, fontFamily = Schrift.text) }, onClick = {
                    onWert(id); onVorschlag?.invoke(id); offen = false
                })
            }
        }
    }
}

@Composable
fun Regler(wert: Float, bereich: ClosedFloatingPointRange<Float>, schritte: Int, onWert: (Float) -> Unit, onFertig: () -> Unit = {}) {
    val p = LocalPalette.current
    Slider(
        value = wert, onValueChange = onWert, valueRange = bereich, steps = schritte, onValueChangeFinished = onFertig,
        colors = SliderDefaults.colors(thumbColor = p.akzent, activeTrackColor = p.akzent, inactiveTrackColor = p.rand,
            activeTickColor = Color.Transparent, inactiveTickColor = Color.Transparent)
    )
}

// ------------------------------------------------------------------ Dialoge

@Composable
fun Frage(titel: String, text: String, ja: String = "Löschen", gefahr: Boolean = true, onJa: () -> Unit, onNein: () -> Unit) {
    val p = LocalPalette.current
    AlertDialog(
        onDismissRequest = onNein,
        title = { Punkt(titel.uppercase(), 18.sp) },
        text = { Fliesstext(text, p.textDim) },
        confirmButton = { TextButton(onClick = onJa) { Text(ja.uppercase(), fontFamily = Schrift.mono, color = if (gefahr) p.rot else p.akzent, fontWeight = FontWeight.Bold) } },
        dismissButton = { TextButton(onClick = onNein) { Text("ABBRECHEN", fontFamily = Schrift.mono, color = p.textDim) } },
        containerColor = p.panel, shape = RUND,
    )
}

@Composable
fun Meldung(titel: String, text: String, onOk: () -> Unit) {
    val p = LocalPalette.current
    AlertDialog(
        onDismissRequest = onOk,
        title = { Punkt(titel.uppercase(), 18.sp) },
        text = { Fliesstext(text, p.textDim) },
        confirmButton = { TextButton(onClick = onOk) { Text("OK", fontFamily = Schrift.mono, color = p.akzent, fontWeight = FontWeight.Bold) } },
        containerColor = p.panel, shape = RUND,
    )
}

/** Auswahl mit mehreren Knöpfen untereinander (z. B. Serientermin löschen). */
@Composable
fun Wahl(titel: String, text: String, knoepfe: List<Triple<String, KnopfArt, () -> Unit>>, onAbbruch: () -> Unit) {
    val p = LocalPalette.current
    AlertDialog(
        onDismissRequest = onAbbruch,
        title = { Punkt(titel.uppercase(), 18.sp) },
        text = {
            Column {
                Fliesstext(text, p.textDim)
                Abstand()
                knoepfe.forEach { (t, art, f) -> Knopf(t, Modifier.fillMaxWidth().padding(vertical = 4.dp), art) { f() } }
            }
        },
        confirmButton = { TextButton(onClick = onAbbruch) { Text("ABBRECHEN", fontFamily = Schrift.mono, color = p.textDim) } },
        containerColor = p.panel, shape = RUND,
    )
}

/** Klappbereich: "+ Eintrag hinzufügen" öffnet ein Formular. */
@Composable
fun Klappbereich(beschriftung: String, offen: Boolean, onUmschalten: (Boolean) -> Unit, inhalt: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Knopf((if (offen) "✕ " else "+ ") + beschriftung, Modifier.fillMaxWidth(), if (offen) KnopfArt.NORMAL else KnopfArt.PRIMAER) {
            onUmschalten(!offen)
        }
        if (offen) {
            Abstand(8.dp)
            Column(content = inhalt)
        }
    }
}

/** Farbpunkte zur Auswahl. */
@Composable
fun FarbReihe(farben: List<String>, gewaehlt: String?, onWahl: (String) -> Unit) {
    val p = LocalPalette.current
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
        farben.forEach { hex ->
            val c = Color(de.gun.dashboard.reloaded.logik.farbeAusHex(hex))
            Box(
                Modifier.size(24.dp).clip(CircleShape).background(c)
                    .border(2.dp, if (hex.equals(gewaehlt, true)) p.text else Color.Transparent, CircleShape)
                    .clickable { onWahl(hex) }
            )
        }
    }
}

val PALETTE_KALENDER = listOf("#5fb4ff", "#35d488", "#ffb020", "#ff5c5c", "#b98cff", "#ff8fc7", "#4dd0c4", "#8b96a5")
