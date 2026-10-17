package com.music.bitchord.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.playback.*
import kotlin.math.roundToInt

/** Contextual controls use theme colors in light, dark and AMOLED. No continuous animation. */
@Composable
fun SpatialSceneControls() {
    val settings by AppSettings.spatialScene.collectAsStateWithLifecycle()
    val enabled by AppSettings.spatialAudio.collectAsStateWithLifecycle()
    val p = settings.selected
    val names = listOf("Espacial original", "Audio 8D", "Espacial avanzado")
    val descriptions = listOf("Conserva el sonido espacial actual.", "Música en movimiento alrededor de ti.", "Profundidad y posicionamiento binaural paramétrico.")
    val icons = listOf(Icons.Rounded.SurroundSound, Icons.Rounded.Waves, Icons.Rounded.Headphones)
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Audio espacial", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Switch(checked = enabled, onCheckedChange = AppSettings::setSpatialAudio)
        }
        Text("Personaliza cómo se mueve y se siente tu música.", style = MaterialTheme.typography.bodySmall)
        SpatialMode.entries.forEachIndexed { index, mode ->
            val selected = settings.mode == mode
            Surface(shape = RoundedCornerShape(20.dp),
                color = if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = .65f) else MaterialTheme.colorScheme.surfaceContainer.copy(alpha = .65f),
                border = BorderStroke(1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = .45f))) {
                Row(Modifier.fillMaxWidth().selectable(selected, role = Role.RadioButton,
                    onClick = { AppSettings.setSpatialScene(settings.copy(mode = mode)) }).padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(icons[index], contentDescription = null)
                    Column(Modifier.weight(1f)) {
                        Text(names[index], style = MaterialTheme.typography.labelLarge)
                        Text(descriptions[index], style = MaterialTheme.typography.bodySmall)
                    }
                    if (selected) Icon(Icons.Rounded.Check, contentDescription = "Seleccionado")
                }
            }
        }
        Text(if (enabled) "Recomendado con audífonos. No separa instrumentos ni sigue la cabeza."
            else "Activa el interruptor de audio espacial para escuchar el efecto.", style = MaterialTheme.typography.bodySmall)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Mostrar escenario", modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
            Switch(checked = settings.visualization, onCheckedChange = {
                AppSettings.setSpatialScene(settings.copy(visualization = it))
            })
        }
        if (settings.visualization) SpatialScenario(settings)
        if (settings.mode != SpatialMode.ORIGINAL) {
            val profiles = if (settings.mode == SpatialMode.EIGHT_D) listOf("Suave", "Equilibrado", "Inmersivo") else listOf("Natural", "Envolvente", "Cinemático")
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                profiles.forEachIndexed { i, label ->
                    FilterChip(selected = p.profile == i, onClick = {
                        AppSettings.setSpatialScene(settings.withSelected(SpatialParameters.preset(settings.mode, i)))
                    }, label = { Text(label) })
                }
            }
            fun update(value: SpatialParameters) = AppSettings.setSpatialScene(settings.withSelected(value.copy(profile = -1)))
            SpatialSlider("Intensidad", p.intensity, enabled) { update(p.copy(intensity = it)) }
            if (settings.mode == SpatialMode.EIGHT_D) {
                SpatialSlider("Velocidad · %.1f s por vuelta".format(p.periodSeconds), p.speed, enabled) { update(p.copy(speed = it)) }
            } else {
                SpatialSlider("Profundidad", p.depth, enabled) { update(p.copy(depth = it)) }
                SpatialSlider("Distancia virtual", p.distance, enabled) { update(p.copy(distance = it)) }
            }
            SpatialSlider("Amplitud", p.width, enabled) { update(p.copy(width = it)) }
            Text("Reverberación", style = MaterialTheme.typography.labelLarge)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf("Sin efecto", "Suave", "Moderada").forEachIndexed { i, label ->
                    FilterChip(selected = p.reverb == i, enabled = enabled, onClick = { update(p.copy(reverb = i)) }, label = { Text(label) })
                }
            }
            TextButton(onClick = { AppSettings.setSpatialScene(settings.withSelected(SpatialParameters.preset(settings.mode, 1))) }) {
                Text("Restablecer este modo")
            }
        }
    }
}

@Composable
private fun SpatialSlider(label: String, value: Float, enabled: Boolean, onChange: (Float) -> Unit) {
    Column {
        Text("$label · ${(value * 100).roundToInt()} %", style = MaterialTheme.typography.labelLarge)
        Slider(value = value, onValueChange = onChange, enabled = enabled, valueRange = 0f..1f)
    }
}

/** Static model diagram: extent/virtual speakers, never an invented live audio analysis. */
@Composable
private fun SpatialScenario(settings: SpatialSettings) {
    val color = MaterialTheme.colorScheme.primary
    val outline = MaterialTheme.colorScheme.outlineVariant
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().height(130.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize().semantics {
                contentDescription = "Esquema estático del escenario espacial seleccionado"
            }) {
                val width = if (settings.mode == SpatialMode.ORIGINAL) 1f else settings.selected.width
                val radius = size.height * (.22f + .2f * width)
                for (scale in listOf(.5f, .75f, 1f)) {
                    drawCircle(outline.copy(alpha = .6f), radius * scale, style = Stroke(1.dp.toPx()))
                }
                if (settings.mode == SpatialMode.ADVANCED) {
                    drawCircle(color, 5.dp.toPx(), Offset(center.x - radius, center.y))
                    drawCircle(color, 5.dp.toPx(), Offset(center.x + radius, center.y))
                }
            }
            Icon(Icons.Rounded.Headphones, contentDescription = null, tint = color, modifier = Modifier.size(28.dp))
        }
        Text(when (settings.mode) {
            SpatialMode.ORIGINAL -> "Estéreo con ensanchamiento y crossfeed"
            SpatialMode.EIGHT_D -> "Alcance de la órbita · esquema estático"
            SpatialMode.ADVANCED -> "Dos fuentes virtuales · esquema estático"
        }, style = MaterialTheme.typography.bodySmall)
    }
}
