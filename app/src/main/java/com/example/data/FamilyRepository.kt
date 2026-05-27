package com.example.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

class FamilyRepository(private val familyDao: FamilyDao) {

    val familyMembers: Flow<List<FamilyMember>> = familyDao.getFamilyMembers()
    val activityLogs: Flow<List<ActivityLog>> = familyDao.getActivityLogs()

    suspend fun getFamilyMembersOnce(): List<FamilyMember> = familyDao.getFamilyMembersOnce()

    suspend fun updateMember(member: FamilyMember) {
        familyDao.updateFamilyMember(member)
    }

    suspend fun deleteMember(member: FamilyMember) {
        familyDao.deleteFamilyMember(member)
    }

    suspend fun insertFamilyMembers(members: List<FamilyMember>) {
        familyDao.insertFamilyMembers(members)
    }

    suspend fun insertLog(log: ActivityLog) {
        familyDao.insertActivityLog(log)
    }

    suspend fun clearLogs() {
        familyDao.clearActivityLogs()
    }

    suspend fun ensureDefaultDataInserted(homeLat: Double, homeLng: Double) {
        // Query current list of members
        val currentMembers = familyDao.getFamilyMembersOnce()
        // If they contain any of the old IDs, clear database to initialize correctly
        val hasOldData = currentMembers.any { it.id in listOf("sarah", "mom", "dad", "alex") }
        if (hasOldData) {
            for (m in currentMembers) {
                familyDao.deleteFamilyMember(m)
            }
        }

        // Clean up duplicate automated simulate "louis" member.
        // The real owner is "me" presenting as Louis (Dad).
        val currentRefreshed = familyDao.getFamilyMembersOnce()
        val hasMe = currentRefreshed.any { it.id == "me" }
        val hasLouis = currentRefreshed.any { it.id == "louis" }
        if (hasLouis) {
            val louisMember = currentRefreshed.firstOrNull { it.id == "louis" }
            if (louisMember != null) {
                familyDao.deleteFamilyMember(louisMember)
            }
        }

        val updatedMembers = familyDao.getFamilyMembersOnce()
        if (updatedMembers.isEmpty()) {
            val defaultMembers = listOf(
                FamilyMember(
                    id = "isabel",
                    name = "Isabel (Older Daughter)",
                    avatarColorHex = "#26A69A",
                    x = homeLng + 0.004,
                    y = homeLat + 0.003,
                    batteryPercentage = 78,
                    isCharging = false,
                    speedMph = 0.0,
                    statusText = "At School",
                    isComingHome = false,
                    etaMinutes = 20,
                    avatarEmoji = "👩‍🎓",
                    phoneNumber = "+447760477416",
                    photoPath = ""
                ),
                FamilyMember(
                    id = "annette",
                    name = "Annette (Mama)",
                    avatarColorHex = "#EC407A",
                    x = homeLng - 0.003,
                    y = homeLat + 0.005,
                    batteryPercentage = 84,
                    isCharging = true,
                    speedMph = 0.0,
                    statusText = "Grocery Store",
                    isComingHome = false,
                    etaMinutes = 12,
                    avatarEmoji = "👩",
                    phoneNumber = "+447803171262",
                    photoPath = ""
                ),
                FamilyMember(
                    id = "eloise",
                    name = "Eloise (Younger Daughter)",
                    avatarColorHex = "#FF9800",
                    x = homeLng - 0.005,
                    y = homeLat - 0.004,
                    batteryPercentage = 64,
                    isCharging = false,
                    speedMph = 4.5,
                    statusText = "Dance Class",
                    isComingHome = false,
                    etaMinutes = 15,
                    avatarEmoji = "👧",
                    phoneNumber = "",
                    photoPath = ""
                )
            )
            familyDao.insertFamilyMembers(defaultMembers)

            // Insert initial logs
            insertLog(ActivityLog(memberId = "system", memberName = "System", actionText = "Family Radar active", iconName = "check_in"))
            insertLog(ActivityLog(memberId = "isabel", memberName = "Isabel (Older Daughter)", actionText = "entered High School Zone", iconName = "away"))
            insertLog(ActivityLog(memberId = "annette", memberName = "Annette (Mama)", actionText = "arrived at Supermarket", iconName = "away"))
            insertLog(ActivityLog(memberId = "eloise", memberName = "Eloise (Younger Daughter)", actionText = "checked in of Dance Studio", iconName = "away"))
        }
    }
}
