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
        // If they contain any of the old IDs, clear database to initialize correctly
        val hasOldData = currentMembers.any { it.id in listOf("sarah", "mom", "dad", "alex") }
        if (hasOldData) {
            for (m in currentMembers) {
                familyDao.deleteFamilyMember(m)
            }
        }

        val currentRefreshed = familyDao.getFamilyMembersOnce()
        for (m in currentRefreshed) {
            if (m.id == "louis") {
                familyDao.deleteFamilyMember(m)
            }
        }

        val updatedMembers = familyDao.getFamilyMembersOnce()
        val hasIsabel = updatedMembers.any { it.id == "isabel" || it.name.contains("Isabel", ignoreCase = true) }
        val hasAnnette = updatedMembers.any { it.id == "annette" || it.name.contains("Annette", ignoreCase = true) }
        val hasEloise = updatedMembers.any { it.id == "eloise" || it.name.contains("Eloise", ignoreCase = true) }

        val membersToRestore = mutableListOf<FamilyMember>()
        if (!hasIsabel) {
            membersToRestore.add(
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
                )
            )
        }
        if (!hasAnnette) {
            membersToRestore.add(
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
                )
            )
        }
        if (!hasEloise) {
            membersToRestore.add(
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
        }
        if (membersToRestore.isNotEmpty()) {
            familyDao.insertFamilyMembers(membersToRestore)
        }

            // Insert initial logs
            familyDao.insertActivityLog(ActivityLog(memberId = "system", memberName = "System", actionText = "Family Radar active", iconName = "check_in"))
            familyDao.insertActivityLog(ActivityLog(memberId = "isabel", memberName = "Isabel (Older Daughter)", actionText = "entered High School Zone", iconName = "away"))
            familyDao.insertActivityLog(ActivityLog(memberId = "annette", memberName = "Annette (Mama)", actionText = "arrived at Supermarket", iconName = "away"))
            familyDao.insertActivityLog(ActivityLog(memberId = "eloise", memberName = "Eloise (Younger Daughter)", actionText = "checked in of Dance Studio", iconName = "away"))

        val currentShopping = familyDao.getShoppingItemsOnce()
        if (currentShopping.isEmpty()) {
            familyDao.insertShoppingItem(ShoppingItem(name = "Fresh Milk 🥛", isChecked = false, addedByMemberId = "annette", addedByMemberName = "Annette (Mama)"))
            familyDao.insertShoppingItem(ShoppingItem(name = "Sourdough Bread 🍞", isChecked = false, addedByMemberId = "me", addedByMemberName = "Louis"))
            familyDao.insertShoppingItem(ShoppingItem(name = "Ice Cream 🍦", isChecked = false, addedByMemberId = "eloise", addedByMemberName = "Eloise (Younger Daughter)"))
        }
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
}
