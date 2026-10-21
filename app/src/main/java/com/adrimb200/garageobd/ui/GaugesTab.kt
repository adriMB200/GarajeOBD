package com.adrimb200.garageobd.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Divider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.adrimb200.garageobd.data.ConnectionStatus
import com.adrimb200.garageobd.data.DashboardState
import com.adrimb200.garageobd.data.ObdSensor
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private val MintGauge = Color(0xFF77E0AB)
private val PanelGauge = Color(0xFF17232C)
private val TextGauge = Color(0xFFE9F1F5)
private val MutedGauge = Color(0xFFA4B5BF)
private val OrangeGauge = Color(0xFFFFC77D)

@Composable
fun GaugesTab(
    state: DashboardState,
    onToggle: (ObdSensor) -> Unit,
    onOpenConnection: () -> Unit
) {
    var edit by rememberSaveable { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(Modifier.weight(1f)) {
            Text("RELOJES", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MintGauge, letterSpacing = 2.sp)
            Text("Tu instrumentación", color = TextGauge, fontSize = 23.sp, fontWeight = FontWeight.Bold)
        }
        OutlinedButton(onClick = { edit = !edit }) {
            Text(if (edit) "Terminar" else "Editar")
        }
    }
    when (state.status) {
        ConnectionStatus.DEMO -> MessageCard("MODO DEMO · Los valores son simulados, no proceden del coche.", OrangeGauge)
        ConnectionStatus.CONNECTED -> MessageCard("Conectado a ${state.deviceName}. Se muestran únicamente datos respondidos por la ECU.", MintGauge)
        else -> {
            MessageCard("Para mostrar valores reales conecta el ELM327 o activa el modo DEMO.", MutedGauge)
            Button(onClick = onOpenConnection, modifier = Modifier.fillMaxWidth()) { Text("Ir a conexión") }
        }
    }

    if (edit) {
        Surface(color = PanelGauge, shape = RoundedCornerShape(16.dp)) {
            Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Selecciona entre 1 y ${ObdSensor.MAX_GAUGES} relojes (${state.selectedGauges.size} activos)",
                    color = TextGauge, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Text("El orden sigue el que has ido seleccionando. Se guarda automáticamente en este teléfono.",
                    color = MutedGauge, fontSize = 12.sp)
                ObdSensor.values().forEach { sensor ->
                    val selected = sensor in state.selectedGauges
                    val allowed = selected && state.selectedGauges.size > 1 ||
                        !selected && state.selectedGauges.size < ObdSensor.MAX_GAUGES
                    val unsupported = state.status == ConnectionStatus.CONNECTED &&
                        state.supportedPids?.contains(sensor.pid) == false
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Checkbox(checked = selected, enabled = allowed, onCheckedChange = { onToggle(sensor) })
                        Column(Modifier.weight(1f)) {
                            Text(sensor.label, color = if (allowed || selected) TextGauge else MutedGauge,
                                fontSize = 13.sp, fontWeight = FontWeight.Medium)
                            Text("PID ${sensor.command} · ${sensor.unit}" +
                                if (unsupported) " · No admitido por la ECU" else "",
                                fontSize = 11.sp, color = if (unsupported) OrangeGauge else MutedGauge)
                        }
                    }
                    Divider(color = Color(0xFF30414A))
                }
            }
        }
    }

    state.selectedGauges.chunked(2).forEach { pair ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            pair.forEach { sensor ->
                GaugeCard(sensor, state, Modifier.weight(1f))
            }
            if (pair.size == 1) Box(Modifier.weight(1f))
        }
    }
    Text("Los indicadores no sustituyen al cuadro original del vehículo. Configura los relojes con el coche estacionado.",
        fontSize = 12.sp, color = MutedGauge)
}

@Composable
private fun MessageCard(message: String, accent: Color) {
    Surface(color = PanelGauge, shape = RoundedCornerShape(13.dp)) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            Box(Modifier.size(7.dp).background(accent, RoundedCornerShape(4.dp)))
            Text(message, color = accent, fontSize = 12.sp)
        }
    }
}

@Composable
private fun GaugeCard(sensor: ObdSensor, state: DashboardState, modifier: Modifier) {
    val supported = state.status == ConnectionStatus.DEMO ||
        state.supportedPids?.contains(sensor.pid) != false
    val value = if (state.status == ConnectionStatus.DEMO || state.status == ConnectionStatus.CONNECTED)
        state.sensorValues[sensor] else null
    val display = if (supported) sensor.format(value) else "N/D"
    val accent = when (sensor) {
        ObdSensor.COOLANT, ObdSensor.IAT, ObdSensor.ECU_VOLTAGE -> OrangeGauge
        ObdSensor.SPEED -> Color(0xFF87C8F6)
        ObdSensor.MAP, ObdSensor.MAF -> Color(0xFFBCA5FD)
        else -> MintGauge
    }
    val ratio = if (value == null || !supported) 0f else
        ((value.toFloat() - sensor.low) / (sensor.high - sensor.low)).coerceIn(0f, 1f)
    Card(modifier = modifier, shape = RoundedCornerShape(19.dp),
        colors = CardDefaults.cardColors(containerColor = PanelGauge)) {
        Column(Modifier.fillMaxWidth().padding(11.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(sensor.label, fontWeight = FontWeight.SemiBold, color = TextGauge, fontSize = 12.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Box(modifier = Modifier.fillMaxWidth().aspectRatio(1.12f), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                    val stroke = 10.dp.toPx()
                    val padding = stroke * 2
                    val d = size.minDimension - padding * 2
                    val topLeft = Offset((size.width - d) / 2, (size.height - d) / 2)
                    val arcSize = Size(d, d)
                    drawArc(Color(0xFF354852), startAngle = 150f, sweepAngle = 240f,
                        useCenter = false, topLeft = topLeft, size = arcSize,
                        style = Stroke(stroke, cap = StrokeCap.Round))
                    if (ratio > 0f) drawArc(accent, startAngle = 150f, sweepAngle = ratio * 240f,
                        useCenter = false, topLeft = topLeft, size = arcSize,
                        style = Stroke(stroke, cap = StrokeCap.Round))
                    // Marcas de escala sin depender de Canvas nativo ni bibliotecas externas.
                    val cx = size.width / 2f
                    val cy = size.height / 2f
                    val radius = d / 2f
                    for (i in 0..8) {
                        val radians = (150.0 + i * 30.0) * PI / 180.0
                        val c = cos(radians).toFloat()
                        val s = sin(radians).toFloat()
                        drawLine(Color(0xFF90A4AF),
                            Offset(cx + c * (radius - stroke * 2.15f), cy + s * (radius - stroke * 2.15f)),
                            Offset(cx + c * (radius - stroke * 2.7f), cy + s * (radius - stroke * 2.7f)),
                            strokeWidth = 1.dp.toPx(), cap = StrokeCap.Round)
                    }
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(display, fontSize = 29.sp, fontWeight = FontWeight.ExtraBold, color = TextGauge,
                        maxLines = 1, letterSpacing = (-1).sp)
                    Text(sensor.unit, fontSize = 11.sp, color = accent)
                }
            }
            val detail = when {
                !supported -> "No soportado por ECU"
                state.status == ConnectionStatus.DEMO -> "SIMULACIÓN"
                state.status == ConnectionStatus.CONNECTING -> "Conectando"
                value == null && state.status == ConnectionStatus.CONNECTED -> "Esperando lectura"
                value == null -> "Sin conexión"
                else -> "OBD-II · ${sensor.command}"
            }
            Text(detail, fontSize = 10.sp, color = if (!supported) OrangeGauge else MutedGauge,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
