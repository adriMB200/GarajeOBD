package com.adrimb200.garageobd.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.adrimb200.garageobd.data.ConnectionStatus
import com.adrimb200.garageobd.data.DashboardState
import com.adrimb200.garageobd.data.GarageViewModel
import com.adrimb200.garageobd.data.PairedDevice

private val Base = Color(0xFF0B1319)
private val Panel = Color(0xFF17232C)
private val PanelAlt = Color(0xFF22333D)
private val Mint = Color(0xFF77E0AB)
private val Light = Color(0xFFE9F1F5)
private val Subtle = Color(0xFFA4B5BF)
private val Warning = Color(0xFFFFC77D)

private val garageColors = darkColorScheme(
    primary = Mint,
    onPrimary = Color(0xFF092417),
    background = Base,
    onBackground = Light,
    surface = Panel,
    onSurface = Light,
    secondary = Subtle,
    outline = Color(0xFF4B626F)
)

@Composable
fun GarageScreen(
    vm: GarageViewModel,
    permissionGranted: Boolean,
    onPermissionRequest: () -> Unit,
    onBluetoothSettings: () -> Unit
) {
    val state by vm.state.collectAsState()
    var selected by rememberSaveable { mutableStateOf(0) }
    MaterialTheme(colorScheme = garageColors) {
        Scaffold(
            containerColor = Base,
            bottomBar = {
                NavigationBar(containerColor = Panel) {
                    listOf("Panel", "Conexión", "Averías").forEachIndexed { index, label ->
                        NavigationBarItem(
                            selected = selected == index,
                            onClick = { selected = index },
                            icon = {
                                Text(
                                    when (index) { 0 -> "◉"; 1 -> "⌁"; else -> "⚙" },
                                    fontSize = 23.sp
                                )
                            },
                            label = { Text(label) }
                        )
                    }
                }
            }
        ) { insets ->
            Box(modifier = Modifier.fillMaxSize().padding(insets), contentAlignment = Alignment.TopCenter) {
                Column(
                    modifier = Modifier.fillMaxSize().widthIn(max = 850.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 18.dp, vertical = 23.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    Header(state)
                    if (state.error != null) {
                        Surface(color = Color(0xFF472F26), shape = RoundedCornerShape(14.dp)) {
                            Text(state.error ?: "", color = Color(0xFFFFD2B6),
                                modifier = Modifier.fillMaxWidth().padding(14.dp), fontSize = 13.sp)
                        }
                    }
                    when (selected) {
                        0 -> DashboardTab(state, onDemo = vm::startDemo,
                            onConnectPage = { selected = 1 }, onDisconnect = vm::disconnect)
                        1 -> ConnectionTab(
                            state = state,
                            permissionGranted = permissionGranted,
                            bluetoothAvailable = vm.bluetoothPresent(),
                            bluetoothEnabled = vm.bluetoothEnabled(),
                            onPermissionRequest = onPermissionRequest,
                            onBluetoothSettings = onBluetoothSettings,
                            onRefresh = vm::refreshDevices,
                            onConnect = vm::connectTo,
                            onDisconnect = vm::disconnect,
                            onDemo = vm::startDemo
                        )
                        else -> FaultTab(state, onRead = vm::readFaultCodes,
                            onConnectPage = { selected = 1 })
                    }
                    Spacer(Modifier.height(4.dp))
                    Text("GarageOBD · v0.1  •  Lectura OBD-II genérica · Sin servidor",
                        fontSize = 11.sp, color = Subtle)
                }
            }
        }
    }
}

@Composable
private fun Header(state: DashboardState) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(modifier = Modifier.size(43.dp).background(PanelAlt, RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center) {
                Text("◉", fontSize = 27.sp, color = Mint)
            }
            Column(Modifier.weight(1f)) {
                Text("GARAGE", fontSize = 21.sp, color = Light, fontWeight = FontWeight.Black,
                    letterSpacing = 2.sp)
                Text("OBD", fontSize = 12.sp, color = Mint, fontWeight = FontWeight.Bold,
                    letterSpacing = 5.sp)
            }
            StatusPill(state.status)
        }
        Text("DIAGNOSIS A BORDO", color = Subtle, fontSize = 11.sp, letterSpacing = 2.sp)
        Text("Tu motor. Los datos.", color = Light, fontSize = 27.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun StatusPill(status: ConnectionStatus) {
    val label = when (status) {
        ConnectionStatus.DISCONNECTED -> "Sin conexión"
        ConnectionStatus.CONNECTING -> "Conectando…"
        ConnectionStatus.CONNECTED -> "Conectado"
        ConnectionStatus.DEMO -> "DEMO"
    }
    val color = when(status) {
        ConnectionStatus.CONNECTED -> Mint
        ConnectionStatus.DEMO -> Warning
        else -> Subtle
    }
    Surface(shape = RoundedCornerShape(40.dp), color = PanelAlt) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(7.dp).background(color, CircleShape))
            Text(label, fontSize = 11.sp, color = color, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun DashboardTab(
    state: DashboardState,
    onDemo: () -> Unit,
    onConnectPage: () -> Unit,
    onDisconnect: () -> Unit
) {
    if (state.status == ConnectionStatus.DISCONNECTED) {
        CardPanel {
            Label("LISTO PARA EMPEZAR")
            Text("Conecta tu ELM327 por Bluetooth clásico y consulta el motor en tiempo real.",
                fontSize = 16.sp, color = Light)
            MainButton("Conectar ELM327", onConnectPage)
            OutlinedButton(onClick = onDemo, modifier = Modifier.fillMaxWidth()) {
                Text("Probar modo demostración")
            }
            Text("El modo demostración utiliza datos simulados; no está conectado a ningún vehículo.",
                color = Subtle, fontSize = 12.sp)
        }
    } else {
        CardPanel {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Label("VEHÍCULO / INTERFAZ")
                    Text(state.deviceName.ifBlank { "ELM327" }, color = Light,
                        fontWeight = FontWeight.Bold, fontSize = 18.sp, maxLines = 2,
                        overflow = TextOverflow.Ellipsis)
                }
                OutlinedButton(onClick = onDisconnect) { Text("Salir") }
            }
            if (state.status == ConnectionStatus.DEMO) {
                Text("SIMULACIÓN ACTIVA · Los valores no proceden de una ECU.",
                    color = Warning, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
            if (state.status == ConnectionStatus.CONNECTING) {
                Text("Estableciendo conexión y negociando protocolo OBD-II…",
                    color = Subtle, fontSize = 13.sp)
            }
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        MetricCard("REVOLUCIONES", state.rpm?.toString() ?: "—", "RPM",
            Modifier.weight(1f), Mint)
        MetricCard("REFRIGERANTE", state.coolant?.toString() ?: "—", "°C",
            Modifier.weight(1f), Warning)
    }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        MetricCard("VELOCIDAD ECU", state.speed?.toString() ?: "—", "km/h",
            Modifier.weight(1f), Color(0xFF87C8F6))
        MetricCard("CÓDIGOS DTC", if (state.dtcsRead) state.dtcs.size.toString() else "—", "almacenados",
            Modifier.weight(1f), Color(0xFFBCA5FD))
    }
    CardPanel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Label("TELEMETRÍA")
                Text("RPM · Sesión actual", color = Light, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
            Text("48 muestras", color = Subtle, fontSize = 11.sp)
        }
        RpmGraph(state.rpmHistory)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("0 rpm", fontSize = 11.sp, color = Subtle)
            Text("6.000 rpm", fontSize = 11.sp, color = Subtle)
        }
    }
    Text("Los valores pueden no estar disponibles en todas las centralitas. No manipules la app conduciendo.",
        color = Subtle, fontSize = 12.sp)
}

@Composable
private fun MetricCard(label: String, number: String, unit: String, modifier: Modifier, accent: Color) {
    Card(modifier = modifier, colors = CardDefaults.cardColors(containerColor = Panel),
        shape = RoundedCornerShape(17.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(label, fontSize = 10.sp, color = Subtle, letterSpacing = 0.7.sp)
            Text(number, fontSize = 29.sp, color = Light, fontWeight = FontWeight.Bold, maxLines = 1)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.size(6.dp).background(accent, CircleShape))
                Text(unit, fontSize = 11.sp, color = accent)
            }
        }
    }
}

@Composable
private fun RpmGraph(values: List<Int>) {
    Canvas(Modifier.fillMaxWidth().height(145.dp)) {
        val width = size.width
        val height = size.height
        for (i in 1..3) {
            val y = height * i / 4f
            drawLine(color = Color(0xFF32434D), start = Offset(0f, y), end = Offset(width, y),
                strokeWidth = 1.dp.toPx())
        }
        if (values.size > 1) {
            val path = Path()
            values.forEachIndexed { index, rpm ->
                val x = width * index / 47f
                val y = height * (1f - (rpm.coerceIn(0, 6000) / 6000f))
                if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(path, color = Mint, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round))
        }
    }
}

@Composable
private fun ConnectionTab(
    state: DashboardState,
    permissionGranted: Boolean,
    bluetoothAvailable: Boolean,
    bluetoothEnabled: Boolean,
    onPermissionRequest: () -> Unit,
    onBluetoothSettings: () -> Unit,
    onRefresh: () -> Unit,
    onConnect: (PairedDevice) -> Unit,
    onDisconnect: () -> Unit,
    onDemo: () -> Unit
) {
    CardPanel {
        Label("BLUETOOTH CLÁSICO · SPP")
        Text("Conecta tu ELM327", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text("Primero empareja el adaptador desde los ajustes Bluetooth de Android. Aquí se muestran solo dispositivos ya vinculados.",
            color = Subtle, fontSize = 13.sp)
        when {
            !bluetoothAvailable -> Text("Este dispositivo no dispone de Bluetooth.", color = Warning)
            !permissionGranted -> MainButton("Autorizar dispositivos cercanos", onPermissionRequest)
            !bluetoothEnabled -> MainButton("Abrir ajustes Bluetooth", onBluetoothSettings)
            else -> {
                OutlinedButton(onClick = onRefresh, modifier = Modifier.fillMaxWidth()) {
                    Text("Actualizar dispositivos emparejados")
                }
                if (state.devices.isEmpty()) {
                    Text("No hay dispositivos emparejados. Vincula el ELM327 en Ajustes → Bluetooth y vuelve aquí.",
                        color = Warning, fontSize = 13.sp)
                    OutlinedButton(onClick = onBluetoothSettings, modifier = Modifier.fillMaxWidth()) {
                        Text("Abrir ajustes de Bluetooth")
                    }
                } else {
                    state.devices.forEach { device ->
                        DeviceRow(device,
                            enabled = state.status == ConnectionStatus.DISCONNECTED || state.status == ConnectionStatus.DEMO,
                            onConnect = { onConnect(device) })
                    }
                }
            }
        }
        if (state.status != ConnectionStatus.DISCONNECTED) {
            OutlinedButton(onClick = onDisconnect, modifier = Modifier.fillMaxWidth()) {
                Text("Desconectar / detener")
            }
        }
    }
    CardPanel {
        Label("SIN CONECTAR UN COCHE")
        Text("Modo de demostración", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Text("Visualiza datos ficticios de RPM, refrigerante y velocidad. Funciona incluso en el emulador.",
            fontSize = 13.sp, color = Subtle)
        MainButton("Iniciar demostración", onDemo)
    }
    CardPanel {
        Label("AYUDA PARA CONECTAR")
        Text("1. Enchufa el ELM327 con el coche estacionado.", color = Light, fontSize = 13.sp)
        Text("2. Empareja el adaptador desde Ajustes > Bluetooth.", color = Light, fontSize = 13.sp)
        Text("3. Activa contacto / motor y selecciona el dispositivo aquí.", color = Light, fontSize = 13.sp)
        Text("El PIN suele ser 1234 o 0000; depende del adaptador. No todos los ELM327 baratos implementan correctamente SPP.",
            color = Subtle, fontSize = 12.sp)
    }
}

@Composable
private fun DeviceRow(device: PairedDevice, enabled: Boolean, onConnect: () -> Unit) {
    Surface(color = PanelAlt, shape = RoundedCornerShape(13.dp)) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(device.name, color = Light, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text(device.address, color = Subtle, fontSize = 11.sp)
            }
            Spacer(Modifier.width(8.dp))
            Button(onClick = onConnect, enabled = enabled, contentPadding = ButtonDefaults.ContentPadding) {
                Text("Conectar", fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun FaultTab(state: DashboardState, onRead: () -> Unit, onConnectPage: () -> Unit) {
    CardPanel {
        Label("DIAGNOSIS OBD-II")
        Text("Códigos de avería", fontSize = 22.sp, color = Light, fontWeight = FontWeight.Bold)
        Text("Consulta los DTC genéricos almacenados en la ECU (modo 03). No se borra ningún código.",
            color = Subtle, fontSize = 13.sp)
        if (state.status == ConnectionStatus.DISCONNECTED || state.status == ConnectionStatus.CONNECTING) {
            MainButton("Ir a conexión", onConnectPage)
        } else {
            MainButton(if (state.readingDtcs) "Consultando ECU…" else "Leer códigos almacenados", onRead,
                enabled = !state.readingDtcs)
        }
        Divider(color = Color(0xFF354751))
        if (!state.dtcsRead) {
            Text("Todavía no has consultado las averías de esta sesión.", color = Subtle, fontSize = 13.sp)
        } else if (state.dtcs.isEmpty()) {
            Text("No se detectaron códigos genéricos en la respuesta recibida.", color = Mint, fontSize = 13.sp)
        } else {
            if (state.status == ConnectionStatus.DEMO) {
                Text("EJEMPLO SIMULADO", color = Warning, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
            state.dtcs.forEach { code ->
                Surface(color = PanelAlt, shape = RoundedCornerShape(12.dp)) {
                    Row(Modifier.fillMaxWidth().padding(15.dp), horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically) {
                        Text(code, fontWeight = FontWeight.Bold, color = Light, fontSize = 20.sp)
                        Text("DTC almacenado", color = Subtle, fontSize = 11.sp)
                    }
                }
            }
        }
    }
    CardPanel {
        Label("IMPORTANTE")
        Text("Los códigos P0xxx genéricos no sustituyen una diagnosis específica del fabricante. El parser DTC de esta primera versión cubre respuestas simples; aún no decodifica todas las variantes multitrama ISO-TP.",
            color = Subtle, fontSize = 13.sp)
        Text("No se ejecuta el comando 04 de borrado de averías.", color = Mint, fontSize = 13.sp)
    }
}

@Composable
private fun CardPanel(content: @Composable ColumnScope.() -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp), content = content)
    }
}

@Composable
private fun Label(text: String) {
    Text(text, color = Mint, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.3.sp)
}

@Composable
private fun MainButton(label: String, onClick: () -> Unit, enabled: Boolean = true) {
    Button(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(11.dp)) { Text(label, fontWeight = FontWeight.Bold) }
}
