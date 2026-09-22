package com.example.ui

import com.example.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.hypot
import kotlin.random.Random

class SimulationEngine(
    private val repository: FamilyRepository,
    private val scope: CoroutineScope,
    private val uiEvents: MutableSharedFlow<String>,
    private val isSimulationPaused: StateFlow<Boolean>,
    private val isSimulationModeEnabled: StateFlow<Boolean>,
    val isWifeCloudSimulationEnabled: MutableStateFlow<Boolean>,
    private val groupSyncToken: StateFlow<String>,
    private val cloudSyncManager: CloudSyncManager,
    private val familyMembers: StateFlow<List<FamilyMember>>,
    private val getHomeLat: () -> Double,
    private val getHomeLng: () -> Double,
    private val savePreferences: () -> Unit
) {
    private var simulationJob: Job? = null
    private var simulatedWifeJob: Job? = null
    private var simulatedWifeAngle = 0.0
    private val simulatedHeadings = java.util.concurrent.ConcurrentHashMap<String, Double>()
    private val triggeredApproachingHomeAlerts = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

    fun start() {
        simulationJob?.cancel()
        simulationJob = scope.launch {
            while (isActive) {
                delay(1000)
                if (isSimulationPaused.value || !isSimulationModeEnabled.value) continue

                val members = familyMembers.value
                if (members.isEmpty()) continue

                val homeLat = getHomeLat()
                val homeLng = getHomeLng()

                for (member in members) {
                    if (member.id == "me") continue
                    if (member.id.startsWith("device_")) continue
                    
                    var updated = false
                    var newX = member.x
                    var newY = member.y
                    var newBattery = member.batteryPercentage
                    var newCharging = member.isCharging
                    var newSpeed = member.speedMph
                    var newStatus = member.statusText
                    var newIsComingHome = member.isComingHome
                    var newEta = member.etaMinutes

                    if (newIsComingHome) {
                        val latDiff = newY - homeLat
                        val lngDiff = newX - homeLng
                        val distanceInDegrees = hypot(lngDiff, latDiff)
                        
                        if (distanceInDegrees < 0.0005) {
                            newX = homeLng
                            newY = homeLat
                            newSpeed = 0.0
                            newIsComingHome = false
                            newEta = 0
                            newStatus = "At Home (Live GPS)"
                            updated = true

                            repository.insertLog(ActivityLog(memberId = member.id, memberName = member.name, actionText = "arrived Home safely", iconName = "home"))
                            uiEvents.emit("${member.name} has arrived Home!")
                            triggeredApproachingHomeAlerts.remove(member.id)
                        } else {
                            val stepRatio = 0.0001
                            val dx = -lngDiff / distanceInDegrees
                            val dy = -latDiff / distanceInDegrees
                            newX += dx * stepRatio
                            newY += dy * stepRatio
                            
                            newSpeed = when (member.id) {
                                "eloise" -> Random.nextDouble(2.2, 3.8)
                                "isabel" -> Random.nextDouble(10.5, 14.5)
                                "louis" -> Random.nextDouble(62.0, 78.0)
                                else -> Random.nextDouble(24.0, 42.0)
                            }
                            val distanceKm = distanceInDegrees * 111.0
                            
                            val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
                            val isRushHour = hour in 7..9 || hour in 16..18
                            val trafficMultiplier = if (isRushHour) 2.2 else 1.5
                            newEta = (distanceKm * trafficMultiplier).toInt().coerceAtLeast(1)
                            
                            val baseStatus = when (member.id) {
                                "eloise" -> "Walking from School"
                                "isabel" -> "Biking from High School"
                                "louis" -> "Commuting via Train"
                                "annette" -> "Driving from Store"
                                else -> "Driving Home"
                            }
                            newStatus = if (isRushHour && member.id != "eloise") {
                                "$baseStatus (⚠️ AI Traffic Commute Delay)"
                            } else {
                                baseStatus
                            }
                            if (newBattery <= 15 && !newCharging) {
                                newStatus = "🚨 low battery - " + newStatus
                            }
                            updated = true

                            if (distanceKm <= 0.05 && !triggeredApproachingHomeAlerts.contains(member.id)) {
                                triggeredApproachingHomeAlerts.add(member.id)
                            }
                        }
                    } else {
                        triggeredApproachingHomeAlerts.remove(member.id)
                        val latDiff = newY - homeLat
                        val lngDiff = newX - homeLng
                        val distanceInDegrees = hypot(lngDiff, latDiff)
                        val distanceKm = distanceInDegrees * 111.0
                        
                        if (distanceKm > 0.05) {
                            val curAngle = simulatedHeadings.getOrPut(member.id) { Random.nextDouble(0.0, 2.0 * Math.PI) }
                            // Gentle natural road curvature (steering ±2 degrees)
                            var nextAngle = curAngle + Random.nextDouble(-0.04, 0.04)
                            // If approaching bounding boundary, gently steer back toward center
                            if (newX < homeLng - 0.06 || newX > homeLng + 0.06 || newY < homeLat - 0.06 || newY > homeLat + 0.06) {
                                nextAngle = kotlin.math.atan2(homeLat - newY, (homeLng - newX) * kotlin.math.cos(Math.toRadians(homeLat)))
                            }
                            simulatedHeadings[member.id] = nextAngle
                            val stepDeg = 0.00008 // ~8 meters per second (~18 mph)
                            val deltaLng = stepDeg * kotlin.math.cos(nextAngle) / kotlin.math.cos(Math.toRadians(homeLat))
                            val deltaLat = stepDeg * kotlin.math.sin(nextAngle)
                            newX = (newX + deltaLng).coerceIn(homeLng - 0.08, homeLng + 0.08)
                            newY = (newY + deltaLat).coerceIn(homeLat - 0.08, homeLat + 0.08)
                            newSpeed = (member.speedMph + Random.nextDouble(-0.4, 0.4)).coerceIn(12.0, 32.0)
                            updated = true
                        } else {
                            if (newSpeed > 0.0) {
                                newSpeed = 0.0
                                updated = true
                            }
                        }
                    }

                    if (newCharging) {
                        newBattery += 2
                        if (newBattery >= 100) {
                            newBattery = 100
                            newCharging = false
                            repository.insertLog(ActivityLog(memberId = member.id, memberName = member.name, actionText = "fully charged (100%)", iconName = "battery"))
                        }
                        updated = true
                    } else {
                        if (Random.nextDouble() < 0.125) { 
                            newBattery -= 1
                            if (newBattery <= 15 && member.batteryPercentage > 15) {
                                repository.insertLog(ActivityLog(memberId = member.id, memberName = member.name, actionText = "battery low alert (${newBattery}%)", iconName = "critical"))
                                uiEvents.emit("Low battery warning for ${member.name}!")
                            }
                            if (newBattery <= 5) {
                                newBattery = 5
                                newCharging = true
                            }
                            updated = true
                        }
                    }

                    if (updated) {
                        repository.updateMember(
                            member.copy(
                                x = newX,
                                y = newY,
                                batteryPercentage = newBattery,
                                isCharging = newCharging,
                                speedMph = Math.round(newSpeed * 10.0) / 10.0,
                                statusText = newStatus,
                                isComingHome = newIsComingHome,
                                etaMinutes = newEta
                            )
                        )
                    }
                }
            }
        }
        startWifeCloudSimulationLoop()
    }

    fun toggleWifeCloudSimulation(enabled: Boolean) {
        scope.launch {
            isWifeCloudSimulationEnabled.value = enabled
            savePreferences()
            if (enabled) {
                uiEvents.emit("Partner simulation active!")
                startWifeCloudSimulationLoop()
            } else {
                simulatedWifeJob?.cancel()
                uiEvents.emit("Partner simulation deactivated.")
                val token = groupSyncToken.value
                if (token.isNotBlank()) {
                    cloudSyncManager.getGroupData(token)?.let { p ->
                        val updated = p.members.toMutableMap().apply { remove("device_annette_mock_simulated") }
                        cloudSyncManager.updateGroupData(token, p.copy(lastUpdated = System.currentTimeMillis(), members = updated))
                    }
                }
            }
        }
    }

    fun startWifeCloudSimulationLoop() {
        simulatedWifeJob?.cancel()
        if (!isWifeCloudSimulationEnabled.value || !isSimulationModeEnabled.value) return
        simulatedWifeJob = scope.launch {
            while (isActive) {
                val token = groupSyncToken.value
                if (token.isNotBlank()) {
                    simulatedWifeAngle += 0.05
                    val scale = 111000.0; val cosLat = Math.cos(Math.toRadians(getHomeLat()))
                    val sLat = getHomeLat() + (1200.0 * Math.sin(simulatedWifeAngle) / scale)
                    val sLng = getHomeLng() + (1200.0 * Math.cos(simulatedWifeAngle) / (scale * cosLat))
                    val p = cloudSyncManager.getGroupData(token) ?: CloudGroupPayload(getHomeLat(), getHomeLng(), true, System.currentTimeMillis())
                    val batt = (85 - (simulatedWifeAngle * 2).toInt() % 30).coerceIn(10, 100)
                    val wife = CloudMember("device_annette_mock_simulated", "Partner (Simulated Phone)", "#EC407A", sLng, sLat, batt, batt < 70 && (System.currentTimeMillis() / 60000) % 2 == 0L, 24.5, "Driving back home", true, 7, System.currentTimeMillis(), "👩")
                    val updated = p.members.toMutableMap().apply { put("device_annette_mock_simulated", wife) }
                    cloudSyncManager.updateGroupData(token, p.copy(lastUpdated = System.currentTimeMillis(), members = updated))
                }
                delay(8000)
            }
        }
    }

    fun stop() {
        simulationJob?.cancel()
        simulatedWifeJob?.cancel()
    }
}
