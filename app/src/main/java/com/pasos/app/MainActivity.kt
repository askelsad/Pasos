package com.pasos.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import java.time.LocalDate

class MainActivity : ComponentActivity() {
    private val perms = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { startSvc() }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val need = mutableListOf(Manifest.permission.ACTIVITY_RECOGNITION)
        if (Build.VERSION.SDK_INT >= 33) need.add(Manifest.permission.POST_NOTIFICATIONS)
        perms.launch(need.toTypedArray())
        setContent { App() }
    }

    private fun startSvc() {
        if (checkSelfPermission(Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED)
            ContextCompat.startForegroundService(this, Intent(this, StepService::class.java))
    }
}

@Composable
fun App() {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
        Surface(Modifier.fillMaxSize()) {
            var tab by remember { mutableIntStateOf(0) }
            Scaffold(bottomBar = {
                NavigationBar {
                    listOf("Hoy", "Historial", "Perfil").forEachIndexed { i, t ->
                        NavigationBarItem(selected = tab == i, onClick = { tab = i }, icon = {}, label = { Text(t) })
                    }
                }
            }) { pad ->
                Box(Modifier.padding(pad).padding(16.dp)) {
                    when (tab) { 0 -> Today(); 1 -> History(); else -> ProfileTab() }
                }
            }
        }
    }
}

@Composable
fun Stat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Text(label, fontSize = 12.sp)
    }
}

@Composable
fun Today() {
    val c = LocalContext.current
    var steps by remember { mutableIntStateOf(0) }
    var veh by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        while (true) {
            steps = Store.steps(c)
            veh = Store.p(c).getBoolean("veh", false)
            delay(1000)
        }
    }
    val pr = Store.profile(c)
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Spacer(Modifier.height(12.dp))
        Box(contentAlignment = Alignment.Center) {
            CircularProgressIndicator(
                progress = { (steps / pr.goal.toFloat()).coerceIn(0f, 1f) },
                modifier = Modifier.size(230.dp), strokeWidth = 14.dp
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("$steps", fontSize = 46.sp, fontWeight = FontWeight.Bold)
                Text("de ${pr.goal} pasos")
            }
        }
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceEvenly) {
            Stat("Calorías", "${Calc.kcal(steps, pr)} kcal")
            Stat("Distancia", "%.2f km".format(Calc.km(steps, pr)))
            Stat("Activos", "${Calc.activeMin(steps)} min")
        }
        Text("Nivel de actividad: ${Calc.level(steps)}", fontSize = 18.sp, fontWeight = FontWeight.Medium)
        Text(if (veh) "🚗 En vehículo: no se cuentan pasos" else "🚶 Contando pasos")
    }
}

@Composable
fun History() {
    val c = LocalContext.current
    var days by remember { mutableIntStateOf(7) }
    val data = (days - 1 downTo 0).map { Store.steps(c, LocalDate.now().minusDays(it.toLong())) }
    val goal = Store.profile(c).goal
    val bar = MaterialTheme.colorScheme.primary
    val line = MaterialTheme.colorScheme.error
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(days == 7, { days = 7 }, { Text("Semana") })
            FilterChip(days == 30, { days = 30 }, { Text("Mes") })
        }
        Canvas(Modifier.fillMaxWidth().height(220.dp)) {
            val mx = maxOf(data.max(), goal).toFloat()
            val w = size.width / data.size
            data.forEachIndexed { i, v ->
                val h = size.height * v / mx
                drawRect(bar, Offset(i * w + w * 0.1f, size.height - h), Size(w * 0.8f, h))
            }
            val gy = size.height - size.height * goal / mx
            drawLine(line, Offset(0f, gy), Offset(size.width, gy), 3f)
        }
        Text("Línea roja = tu meta ($goal)", fontSize = 12.sp)
        Text("Promedio: ${data.average().toInt()} pasos/día")
        Text("Total: ${data.sum()} pasos")
        Text("Mejor día: ${data.max()} pasos")
    }
}

@Composable
fun ProfileTab() {
    val c = LocalContext.current
    val p = Store.profile(c)
    var kg by remember { mutableStateOf(p.kg.toString()) }
    var cm by remember { mutableStateOf(p.cm.toString()) }
    var goal by remember { mutableStateOf(p.goal.toString()) }
    var male by remember { mutableStateOf(p.male) }
    var rem by remember { mutableStateOf(Store.p(c).getBoolean("remind", true)) }
    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(kg, { kg = it }, label = { Text("Peso (kg)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
        OutlinedTextField(cm, { cm = it }, label = { Text("Altura (cm)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
        OutlinedTextField(goal, { goal = it }, label = { Text("Meta diaria (pasos)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Sexo:")
            FilterChip(male, { male = true }, { Text("Hombre") })
            FilterChip(!male, { male = false }, { Text("Mujer") })
        }
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
            Text("Avisarme si estoy quieto 1 hora")
            Switch(rem, { rem = it })
        }
        Button(onClick = {
            Store.save(
                c,
                Profile(
                    kg.replace(',', '.').toFloatOrNull() ?: 70f,
                    cm.toIntOrNull() ?: 170, male,
                    (goal.toIntOrNull() ?: 8000).coerceAtLeast(1)
                ), rem
            )
            Toast.makeText(c, "Guardado", Toast.LENGTH_SHORT).show()
        }) { Text("Guardar") }
    }
}
