package com.example.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class FamilyRepository(private val familyDao: FamilyDao) {

    val familyMembers: Flow<List<FamilyMember>> = familyDao.getFamilyMembers()
    val activityLogs: Flow<List<ActivityLog>> = familyDao.getActivityLogs()

    suspend fun getFamilyMembersOnce(): List<FamilyMember> = withContext(Dispatchers.IO) {
        familyDao.getFamilyMembersOnce()
    }

    suspend fun updateMember(member: FamilyMember) = withContext(Dispatchers.IO) {
        familyDao.updateFamilyMember(member)
    }

    suspend fun deleteMember(member: FamilyMember) = withContext(Dispatchers.IO) {
        familyDao.deleteFamilyMember(member)
    }

    suspend fun insertFamilyMembers(members: List<FamilyMember>) = withContext(Dispatchers.IO) {
        familyDao.insertFamilyMembers(members)
    }

    suspend fun insertLog(log: ActivityLog) = withContext(Dispatchers.IO) {
        familyDao.insertActivityLog(log)
    }

    suspend fun clearLogs() = withContext(Dispatchers.IO) {
        familyDao.clearActivityLogs()
    }

    suspend fun ensureDefaultDataInserted(homeLat: Double, homeLng: Double) = withContext(Dispatchers.IO) {
        // Query current list of members
        val currentMembers = familyDao.getFamilyMembersOnce()
        
        // Clean up legacy test IDs if any exist
        for (m in currentMembers) {
            val mClean = m.name.lowercase().trim()
            if (m.id in listOf("sarah", "mom", "dad", "alex") && !mClean.contains("eloise")) {
                familyDao.deleteFamilyMember(m)
                familyDao.clearBreadcrumbsForMember(m.id)
            }
        }

        // If the database already has members (even just "me" or custom members), NEVER re-insert deleted demo members!
        if (currentMembers.isNotEmpty()) {
            return@withContext
        }

        // Only on a completely clean, empty initial app launch:
        val initialMembers = listOf(
            FamilyMember(
                id = "isabel",
                name = "Isabel (Older Daughter)",
                avatarColorHex = "#26A69A",
                x = homeLng,
                y = homeLat,
                batteryPercentage = 78,
                isCharging = false,
                speedMph = 0.0,
                statusText = "At Home",
                isComingHome = false,
                etaMinutes = 0,
                avatarEmoji = "👩‍🎓",
                phoneNumber = "+447760477416",
                photoPath = ""
            ),
            FamilyMember(
                id = "annette",
                name = "Annette (Mama)",
                avatarColorHex = "#EC407A",
                x = homeLng,
                y = homeLat,
                batteryPercentage = 84,
                isCharging = true,
                speedMph = 0.0,
                statusText = "At Home",
                isComingHome = false,
                etaMinutes = 0,
                avatarEmoji = "👩",
                phoneNumber = "+447803171262",
                photoPath = ""
            )
        )
        familyDao.insertFamilyMembers(initialMembers)

        // Insert initial activity logs
        familyDao.insertActivityLog(ActivityLog(memberId = "system", memberName = "System", actionText = "Family Radar active", iconName = "check_in"))
    }


    val groupPinMappings: Flow<List<GroupPinMapping>> = familyDao.getAllGroupPinMappings()

    suspend fun insertGroupPinMapping(mapping: GroupPinMapping) = withContext(Dispatchers.IO) {
        familyDao.insertGroupPinMapping(mapping)
    }

    suspend fun deactivateAllGroups() = withContext(Dispatchers.IO) {
        familyDao.deactivateAllGroups()
    }

    suspend fun activateGroup(pin: String) = withContext(Dispatchers.IO) {
        familyDao.deactivateAllGroups()
        familyDao.activateGroup(pin)
    }

    suspend fun getActiveGroupPinMappingOnce(): GroupPinMapping? = withContext(Dispatchers.IO) {
        familyDao.getActiveGroupPinMappingOnce()
    }

    suspend fun getGroupPinMappingByPin(pin: String): GroupPinMapping? = withContext(Dispatchers.IO) {
        familyDao.getGroupPinMappingByPin(pin)
    }

    suspend fun deleteGroupPinMapping(mapping: GroupPinMapping) = withContext(Dispatchers.IO) {
        familyDao.deleteGroupPinMapping(mapping)
    }

    val safeZones: Flow<List<SafeZone>> = familyDao.getAllSafeZones()

    suspend fun insertSafeZone(zone: SafeZone) = withContext(Dispatchers.IO) {
        familyDao.insertSafeZone(zone)
    }

    suspend fun deleteSafeZone(zone: SafeZone) = withContext(Dispatchers.IO) {
        familyDao.deleteSafeZone(zone)
    }

    suspend fun getAllSafeZonesOnce(): List<SafeZone> = withContext(Dispatchers.IO) {
        familyDao.getAllSafeZonesOnce()
    }

    val shoppingItems: Flow<List<ShoppingItem>> = familyDao.getShoppingItems()

    suspend fun getShoppingItemsOnce(): List<ShoppingItem> = withContext(Dispatchers.IO) {
        familyDao.getShoppingItemsOnce()
    }

    suspend fun insertShoppingItem(item: ShoppingItem): Long = withContext(Dispatchers.IO) {
        familyDao.insertShoppingItem(item)
    }

    suspend fun updateShoppingItem(item: ShoppingItem) = withContext(Dispatchers.IO) {
        familyDao.updateShoppingItem(item)
    }

    suspend fun deleteShoppingItem(item: ShoppingItem) = withContext(Dispatchers.IO) {
        familyDao.deleteShoppingItem(item)
    }

    fun getBreadcrumbsForMemberSince(memberId: String, fromTimestamp: Long): Flow<List<LocationBreadcrumb>> {
        return familyDao.getBreadcrumbsForMemberSince(memberId, fromTimestamp)
    }

    suspend fun getBreadcrumbsForMemberSinceOnce(memberId: String, fromTimestamp: Long): List<LocationBreadcrumb> = withContext(Dispatchers.IO) {
        familyDao.getBreadcrumbsForMemberSinceOnce(memberId, fromTimestamp)
    }

    suspend fun recordBreadcrumbThrottled(
        memberId: String,
        latitude: Double,
        longitude: Double,
        speedMph: Double = 0.0
    ): Boolean = withContext(Dispatchers.IO) {
        if (latitude == 0.0 && longitude == 0.0) return@withContext false
        val now = System.currentTimeMillis()
        val lastPoint = familyDao.getLastBreadcrumbForMember(memberId)
        
        if (lastPoint != null) {
            val latDiff = latitude - lastPoint.latitude
            val lngDiff = longitude - lastPoint.longitude
            val xDistanceKm = lngDiff * 111.0 * Math.cos(Math.toRadians(lastPoint.latitude))
            val yDistanceKm = latDiff * 111.0
            val distanceMeters = Math.hypot(xDistanceKm, yDistanceKm) * 1000.0
            val timeDiffMs = now - lastPoint.timestamp

            // If moved less than 20 meters and less than 5 minutes passed, skip
            if (distanceMeters < 20.0 && timeDiffMs < 300_000L) {
                return@withContext false
            }
            // Suppress stationary jitter (under 8m)
            if (distanceMeters < 8.0) {
                return@withContext false
            }
        }

        familyDao.insertBreadcrumb(
            LocationBreadcrumb(
                memberId = memberId,
                latitude = latitude,
                longitude = longitude,
                speedMph = speedMph,
                timestamp = now
            )
        )
        true
    }

    suspend fun pruneOldBreadcrumbs(days: Int = 30) = withContext(Dispatchers.IO) {
        val cutoff = System.currentTimeMillis() - (days * 24L * 60 * 60 * 1000)
        familyDao.deleteBreadcrumbsOlderThan(cutoff)
    }

    suspend fun clearBreadcrumbsForMember(memberId: String) = withContext(Dispatchers.IO) {
        familyDao.clearBreadcrumbsForMember(memberId)
    }
}
