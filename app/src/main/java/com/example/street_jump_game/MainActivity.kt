package com.example.street_jump_game

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.speech.tts.TextToSpeech
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.foundation.shape.RoundedCornerShape

import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.street_jump_game.ui.theme.StreetjumpgameTheme
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.sqrt

data class Routine(val name: String, val pattern: List<String>, val highlightedSquares: List<Int>, val description: String)

val ROUTINES = listOf(
    Routine("The Classic Hopscotch", listOf("Right foot", "Both feet", "Left foot", "Both feet"),
        (0..29).flatMap { r ->
            when (r % 4) {
                0 -> listOf(r * 2 + 1)
                1 -> listOf(r * 2, r * 2 + 1)
                2 -> listOf(r * 2)
                else -> listOf(r * 2, r * 2 + 1)
            }
        },
        "A warm-up pattern. Focuses on light, rhythmic bounding and ankle stiffness. Great for agility."
    ),
    Routine("The Centurion's March", listOf("Jump forward two squares", "Hold", "Jump forward two squares"),
        (0..29).flatMap { r -> if (r % 2 != 0) listOf(r * 2, r * 2 + 1) else emptyList() },
        "Simulates covering ground quickly with heavy armor. Requires explosive lower-body power."
    ),
    Routine("The Shield-Bearer's Lunge", listOf("Deep lunge right", "Deep lunge left"),
        (0..29).map { r -> if (r % 2 == 0) r * 2 + 1 else r * 2 },
        "A grueling, slow-burn leg destroyer over 60 feet. Step deep into each staggered square."
    ),
    Routine("The Skater's Evasion", listOf("Bound to right column", "Bound to left column"),
        (0..29).map { r -> if (r % 2 == 0) r * 2 + 1 else r * 2 },
        "Dodge-and-weave lateral movement, building hip stability and side-to-side explosiveness."
    ),
    Routine("The Phalanx Shuffle", listOf("Two squares forward", "One square back"),
        (0..59).toList(),
        "High cognitive load and constant change of direction, simulating moving in a tight formation."
    ),
    Routine("The One-Legged Scout", listOf("Hop right foot", "Keep balancing"),
        (0..29).map { r -> if (r < 15) r * 2 + 1 else r * 2 },
        "Tests unilateral strength and balance. 30 feet entirely on one foot, then 30 feet on the other."
    ),
    Routine("Hot Coals", listOf("Rapid right", "Rapid left"),
        (0..59).toList(),
        "An intense sprint. Feet should barely spend any time on the ground. Hit every single square rapidly."
    ),
    Routine("The Tactical Retreat", listOf("Hop backwards", "Hop backwards"),
        (0..59).toList(),
        "Forces reliance on spatial awareness by moving backwards blindly through the entire grid."
    ),
    Routine("The Siege Tower", listOf("High vertical jump", "Long broad jump"),
        (0..29).flatMap { r -> if (r % 2 == 0) listOf(r * 2, r * 2 + 1) else emptyList() },
        "Extreme cardiovascular demand alternating high knees/vertical jumps with forward broad jumps."
    ),
    Routine("The Gladiator's Gauntlet", listOf("Jump forward", "Drop for burpee"),
        (0..59).toList(),
        "The ultimate finisher. Doing burpees in every square over 60 feet tests massive physical endurance."
    )
)

class MainActivity : ComponentActivity(), TextToSpeech.OnInitListener, SensorEventListener {

    private lateinit var tts: TextToSpeech
    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null

    // App State (Hosted in Activity so it survives tab switching)
    private var isWorkoutActive by mutableStateOf(false)
    private var currentInstruction by mutableStateOf("Ready to start")
    private var jumpCount by mutableStateOf(0)
    private var selectedRoutine by mutableStateOf(ROUTINES[0])
    
    // Theme State
    private var isDarkTheme by mutableStateOf(false)
    
    // Timer state for Tab 3
    private var workoutTimeSeconds by mutableStateOf(0)

    // Database
    private lateinit var dbHelper: DatabaseHelper
    private var savedWorkouts by mutableStateOf<List<WorkoutSession>>(emptyList())

    // Physics
    private var currentAcceleration = SensorManager.GRAVITY_EARTH
    private var lastAcceleration = SensorManager.GRAVITY_EARTH
    private var jumpThreshold by mutableFloatStateOf(14f)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        tts = TextToSpeech(this, this)
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        
        dbHelper = DatabaseHelper(this)
        savedWorkouts = dbHelper.getAllWorkouts()

        setContent {
            StreetjumpgameTheme(darkTheme = isDarkTheme) {
                MainScreen(
                    isDarkTheme = isDarkTheme,
                    onThemeToggle = { isDarkTheme = !isDarkTheme }
                )
            }
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts.language = Locale.US
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event != null && isWorkoutActive) {
            val x = event.values[0]
            val y = event.values[1]
            val z = event.values[2]

            lastAcceleration = currentAcceleration
            currentAcceleration = sqrt((x * x + y * y + z * z).toDouble()).toFloat()
            val delta = currentAcceleration - lastAcceleration

            if (delta > jumpThreshold) {
                jumpCount++
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun speakOut(text: String) {
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "")
    }

    override fun onDestroy() {
        if (::tts.isInitialized) {
            tts.stop()
            tts.shutdown()
        }
        sensorManager.unregisterListener(this)
        super.onDestroy()
    }

    @Composable
    fun MainScreen(isDarkTheme: Boolean, onThemeToggle: () -> Unit) {
        var selectedTabIndex by remember { mutableIntStateOf(1) } // Default to Tab 2 (Workout)

        // Workout Loop & Timer Engine
        LaunchedEffect(isWorkoutActive, selectedRoutine) {
            if (isWorkoutActive) {
                accelerometer?.let {
                    sensorManager.registerListener(this@MainActivity, it, SensorManager.SENSOR_DELAY_GAME)
                }
                
                var stepIndex = 0
                speakOut("Starting ${selectedRoutine.name}.")
                delay(3000)

                while(isWorkoutActive) {
                    val instruction = selectedRoutine.pattern[stepIndex % selectedRoutine.pattern.size]
                    currentInstruction = instruction
                    speakOut(instruction)
                    
                    delay(4000) // 4 second pacing
                    stepIndex++
                    workoutTimeSeconds += 4
                }
            } else {
                sensorManager.unregisterListener(this@MainActivity)
            }
        }

        Scaffold(
            bottomBar = {
                NavigationBar {
                    NavigationBarItem(
                        icon = { Text("🌍") },
                        label = { Text("Grid Map") },
                        selected = selectedTabIndex == 0,
                        onClick = { selectedTabIndex = 0 }
                    )
                    NavigationBarItem(
                        icon = { Text("🏃") },
                        label = { Text("Workout") },
                        selected = selectedTabIndex == 1,
                        onClick = { selectedTabIndex = 1 }
                    )
                    NavigationBarItem(
                        icon = { Text("💾") },
                        label = { Text("Data & DB") },
                        selected = selectedTabIndex == 2,
                        onClick = { selectedTabIndex = 2 }
                    )
                    NavigationBarItem(
                        icon = { 
                            Icon(
                                if (isDarkTheme) Icons.Default.LightMode else Icons.Default.DarkMode, 
                                contentDescription = "Toggle Theme"
                            ) 
                        },
                        label = { Text("Theme") },
                        selected = false,
                        onClick = onThemeToggle
                    )
                }
            }
        ) { innerPadding ->
            Box(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
                when (selectedTabIndex) {
                    0 -> MapTab(selectedRoutine)
                    1 -> WorkoutTab()
                    2 -> DataTab()
                }
            }
        }
    }

    // --- TAB 1: MAPS / GRID ---
    @Composable
    fun MapTab(selectedRoutine: Routine) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            Text("The 60-Foot Street Grid", fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))
            Text("Pattern: ${selectedRoutine.name}", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 4.dp))
            
            // The Summary/Description Box
            Card(
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
            ) {
                Text(
                    text = selectedRoutine.description, 
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
            
            // Visual representation of the dual-column grid
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(60) { index -> // 30 rows of 2 columns = 60 squares total
                    val isHighlighted = selectedRoutine.highlightedSquares.contains(index)
                    val bgColor = if (isHighlighted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                    val textColor = if (isHighlighted) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant

                    Box(
                        modifier = Modifier
                            .aspectRatio(1f)
                            .background(bgColor, RoundedCornerShape(8.dp))
                            .border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Sq ${index + 1}", fontWeight = FontWeight.Bold, color = textColor)
                    }
                }
            }
        }
    }

    // --- TAB 2: WORKOUT (VOICE & SENSORS) ---
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun WorkoutTab() {
        var expanded by remember { mutableStateOf(false) }

        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Voice & Sensor Workout", fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(24.dp))

            // Dropdown for routines
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = !expanded }
            ) {
                OutlinedTextField(
                    value = selectedRoutine.name,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Select Routine") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth()
                )
                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    ROUTINES.forEach { routine ->
                        DropdownMenuItem(
                            text = { Text(routine.name) },
                            onClick = {
                                selectedRoutine = routine
                                expanded = false
                                if (!isWorkoutActive) currentInstruction = "Ready to start"
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(48.dp))

            // Current instruction display
            Text(
                text = currentInstruction,
                fontSize = 28.sp,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
                modifier = Modifier.height(100.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Sensor Data display
            Card(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
            ) {
                Column(modifier = Modifier.padding(16.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Hardware Accelerometer", fontWeight = FontWeight.Bold)
                    Text("Jumps Detected: $jumpCount", fontSize = 32.sp, fontWeight = FontWeight.ExtraBold)
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            Button(
                onClick = { 
                    if (isWorkoutActive) {
                        // Stopping the workout - save it!
                        isWorkoutActive = false
                        currentInstruction = "Workout Finished"
                        speakOut("Workout Finished. Great job.")
                        
                        if (jumpCount > 0 || workoutTimeSeconds > 0) {
                            val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.US)
                            val currentDateStr = dateFormat.format(Date())
                            
                            val session = WorkoutSession(
                                dateString = currentDateStr,
                                routineName = selectedRoutine.name,
                                jumpCount = jumpCount,
                                durationSeconds = workoutTimeSeconds
                            )
                            dbHelper.insertWorkout(session)
                            savedWorkouts = dbHelper.getAllWorkouts() // Refresh list
                        }
                    } else {
                        // Starting fresh
                        isWorkoutActive = true
                        jumpCount = 0 
                        workoutTimeSeconds = 0
                    }
                },
                modifier = Modifier.fillMaxWidth().height(72.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isWorkoutActive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                )
            ) {
                Text(
                    text = if (isWorkoutActive) "STOP SESSION" else "START WORKOUT",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }

    // --- TAB 3: DATA & SETTINGS ---
    @Composable
    fun DataTab() {
        val minutes = workoutTimeSeconds / 60
        val seconds = workoutTimeSeconds % 60
        val timeString = String.format(Locale.US, "%02d:%02d", minutes, seconds)

        Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
            Text("Data Storage & Clocks", fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(16.dp))

            // Timers & Clocks
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Session Timer", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary)
                    Text(timeString, fontSize = 48.sp, fontWeight = FontWeight.ExtraBold)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Accelerometer Settings
            Text("Hardware Settings", fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Jump Force Threshold: ${String.format(Locale.US, "%.1f", jumpThreshold)}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary)
                    Text(
                        "Lower value = more sensitive (light steps count). Higher value = requires heavy stomps.", 
                        style = MaterialTheme.typography.bodySmall, 
                        modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
                    )
                    Slider(
                        value = jumpThreshold,
                        onValueChange = { jumpThreshold = it },
                        valueRange = 10f..30f
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))



            // Mock Data List
            Text("Saved Workout History:", fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 16.dp, bottom = 8.dp))
            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                items(savedWorkouts.size) { index ->
                    val session = savedWorkouts[index]
                    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Row(modifier = Modifier.padding(16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(session.dateString, fontWeight = FontWeight.Bold)
                                Text(session.routineName, color = MaterialTheme.colorScheme.secondary)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("${session.jumpCount} Jumps", fontWeight = FontWeight.Bold)
                                val m = session.durationSeconds / 60
                                val s = session.durationSeconds % 60
                                Text(String.format(Locale.US, "%02d:%02d", m, s))
                            }
                        }
                    }
                }
            }
        }
    }
}