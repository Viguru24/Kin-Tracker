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

    suspend fun ensureDefaultDataInserted() {
        // Query current list of members
        val currentMembers = familyDao.getFamilyMembersOnce()
        if (currentMembers.isEmpty()) {
            val defaultMembers = listOf(
                FamilyMember(
                    id = "sarah",
                    name = "Sarah",
                    avatarColorHex = "#EC407A",
                    x = 0.8,
                    y = -0.5,
                    batteryPercentage = 84,
                    isCharging = false,
                    speedMph = 0.0,
                    statusText = "At School",
                    isComingHome = false,
                    etaMinutes = 25
                ),
                FamilyMember(
                    id = "mom",
                    name = "Mom (Elena)",
                    avatarColorHex = "#26A69A",
                    x = 0.4,
                    y = 0.7,
                    batteryPercentage = 92,
                    isCharging = true,
                    speedMph = 4.0,
                    statusText = "Grocery Store",
                    isComingHome = false,
                    etaMinutes = 15
                ),
                FamilyMember(
                    id = "dad",
                    name = "Dad (David)",
                    avatarColorHex = "#42A5F5",
                    x = -1.1,
                    y = -0.6,
                    batteryPercentage = 42,
                    isCharging = false,
                    speedMph = 35.0,
                    statusText = "Commuting from Work",
                    isComingHome = true,
                    etaMinutes = 12
                ),
                FamilyMember(
                    id = "alex",
                    name = "Alex",
                    avatarColorHex = "#FF9800",
                    x = -0.4,
                    y = 0.9,
                    batteryPercentage = 18,
                    isCharging = false,
                    speedMph = 2.5,
                    statusText = "Soccer Practice",
                    isComingHome = false,
                    etaMinutes = 40
                )
            )
            familyDao.insertFamilyMembers(defaultMembers)

            // Insert initial logs
            insertLog(ActivityLog(memberId = "system", memberName = "System", actionText = "Family Tracking Radar System initialized", iconName = "check_in"))
            insertLog(ActivityLog(memberId = "sarah", memberName = "Sarah", actionText = "entered High School Zone", iconName = "away"))
            insertLog(ActivityLog(memberId = "mom", memberName = "Mom (Elena)", actionText = "arrived at Supermarket", iconName = "away"))
            insertLog(ActivityLog(memberId = "dad", memberName = "Dad (David)", actionText = "commencing route: Commute Home", iconName = "home"))
            insertLog(ActivityLog(memberId = "alex", memberName = "Alex", actionText = "battery critical (18%)", iconName = "battery"))
        }
    }
}
