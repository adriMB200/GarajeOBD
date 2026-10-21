# GarageOBD — MVP Android / ELM327 Bluetooth clásico

Aplicación **Android nativa**, con Kotlin + Jetpack Compose, para conectar un adaptador ELM327 Bluetooth clásico (SPP/RFCOMM) emparejado y consultar datos OBD-II genéricos.

## Qué incluye la versión 0.1.1

- Modo demostración, incluso en el emulador, **claramente identificado** como datos ficticios.
- Listado de dispositivos Bluetooth **ya emparejados** (no busca dispositivos nuevos).
- Permiso `BLUETOOTH_CONNECT` en Android 12+; `BLUETOOTH` heredado en Android 11 o inferior.
- Conexión Bluetooth Classic con el UUID SPP estándar, inicialización ELM327 (`ATZ`, `ATE0`, `ATL0`, `ATS0`, `ATH0`, `ATSP0`).
- Sondeo secuencial: revoluciones (010C), temperatura del refrigerante (0105), velocidad (010D).
- Lectura **manual** de códigos de avería OBD-II almacenados (servicio 03, básico). No se borran DTC ni se escriben datos en la ECU.
- Gráfica de RPM de la sesión actual; no se guardan datos todavía.
- Tratamiento de respuestas `NO DATA`, errores de conexión y tiempo de espera.

## Requisitos

- Android Studio reciente compatible con Android Gradle Plugin 8.11.1.
- **JDK 17** (JDK 21 normalmente también sirve si tu versión de Gradle es compatible).
- SDK Platform **Android 13 / API 33** para `compileSdk=33` y Android SDK Build Tools.
- Gradle wrapper 8.0.2 incluido; se descargará al ejecutar el proyecto por primera vez.
- Android 8.0 (API 26) o posterior.
- Adaptador **Bluetooth Classic / SPP**. No funciona con adaptadores Wi-Fi o BLE (serán transportes diferentes).

## Abrir en Android Studio

1. `File > Open` → selecciona la carpeta `GarageOBD` que contiene `settings.gradle.kts`.
2. Espera a `Gradle Sync` y acepta instalar SDK Platform 36 si se solicita.
3. Selecciona tu teléfono Android y pulsa `Run ▶`. Puedes abrir el **modo demo** en el emulador.

## Conectar al coche

1. Aparca el coche en un lugar seguro, pon el contacto; para RPM reales, arranca el motor.
2. Conecta el ELM327 en el conector OBD-II. **No lo enchufes ni manipules conduciendo**.
3. Empareja el teléfono con el adaptador desde **Ajustes del sistema > Bluetooth** (PIN habitual 1234 o 0000 si el dispositivo lo requiere).
4. Abre GarageOBD y autoriza “Dispositivos cercanos” cuando te lo solicite (Android 12+).
5. Pulsa **Actualizar lista**, elige tu dispositivo en “Emparejados” y pulsa **Conectar**.
6. Con el contacto dado y el motor encendido, deberían mostrarse RPM y temperatura si la ECU los ofrece.
7. Si aparece “Sin respuesta de la ECU”, comprueba el contacto, la compatibilidad ELM327 y el protocolo del coche.

Al salir de la pantalla, GarageOBD desconecta el adaptador; no tiene un servicio en segundo plano ni transmite datos a servidores.

## Limitaciones conscientes

- ELM327 clónicos muy baratos pueden fallar al establecer SPP o responder irregularmente; no hay garantía universal de compatibilidad.
- Los PIDs genéricos **no cubren** diagnóstico específico BMW, codificación de centralitas, airbags, ABS, etc.
- `03` solo interpreta tramas de DTC genéricas sencillas; respuestas CAN multitrama complejas o de fabricante requieren un parser ISO-TP más completo.
- No se detectan dispositivos no emparejados desde la app; se emparejan en ajustes de Android.
- No hay grabación permanente, gráficos históricos persistentes ni exportación CSV en esta versión.
- Sin una ECU real no es posible certificar conectividad de hardware.

## Seguridad

Solo se emiten comandos de lectura (`01xx`, `03`) y de configuración del ELM327 (`AT...`), nunca comandos de borrado (`04`) ni escritura en las ECU. No uses el teléfono mientras conduces.

## Estructura

```
app/src/main/java/com/adrimb200/garageobd/
  MainActivity.kt       Arranque, permisos, ciclo de vida
  data/BluetoothElm.kt  Transporte RFCOMM y comandos AT
  data/ObdParser.kt     Interpretación de los PIDs/DTC
  data/GarageViewModel.kt  Estado, modo demo y sondeo
  ui/GarageScreen.kt    Pantallas Compose
```

## Comprobación

```powershell
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:testDebugUnitTest
```

APK: `app\build\outputs\apk\debug\app-debug.apk`.

## Compatibilidad con Android Studio antiguo

Esta variante utiliza **AGP 8.0.2, Gradle 8.0.2, Kotlin 1.8.20, Compose Compiler 1.4.6, Compose BOM 2023.06.01** y **Java 17**. Necesitas **Android SDK Platform 33**. En Android Studio: Settings > Build, Execution, Deployment > Build Tools > Gradle > **Gradle JDK = jbr-17 / JDK 17**.

No abras simultáneamente el proyecto original con AGP 8.11.1 y esta versión sin identificar cuál estás ejecutando. Abre la carpeta que contiene `settings.gradle.kts`.

**Comprobación de esta entrega:** versión de Gradle y APIs revisadas estáticamente y parser OBD probado con `kotlinc`, pero sin Android SDK descargado en el entorno no se ha ejecutado un `assembleDebug` Android completo. Compila desde tu Android Studio y proporciona el primer error de Build Output si aparece uno.
