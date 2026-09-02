package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface FamilyDao {
    @Query("SELECT * FROM family_members ORDER BY CASE WHEN id = 'me' THEN 0 ELSE 1 END, name ASC")
    fun getFamilyMembers(): Flow<List<FamilyMember>>

    @Query("SELECT * FROM family_members")
    suspend fun getFamilyMembersOnce(): List<FamilyMember>

    @Query("SELECT * FROM family_members WHERE id = :id")
    fun getFamilyMemberById(id: String): Flow<FamilyMember?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFamilyMembers(members: List<FamilyMember>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun updateFamilyMember(member: FamilyMember)

    @Delete
    suspend fun deleteFamilyMember(member: FamilyMember)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertActivityLog(log: ActivityLog)

    @Query("SELECT * FROM activity_logs ORDER BY timestamp DESC LIMIT 50")
    fun getActivityLogs(): Flow<List<ActivityLog>>

    @Query("DELETE FROM activity_logs")
    suspend fun clearActivityLogs()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGroupPinMapping(mapping: GroupPinMapping)

    @Query("SELECT * FROM group_pin_mappings ORDER BY createdTimestamp DESC")
    fun getAllGroupPinMappings(): Flow<List<GroupPinMapping>>

    @Query("SELECT * FROM group_pin_mappings WHERE pinCode = :pinCode")
    suspend fun getGroupPinMappingByPin(pinCode: String): GroupPinMapping?

    @Delete
    suspend fun deleteGroupPinMapping(mapping: GroupPinMapping)

    @Query("UPDATE group_pin_mappings SET isActive = 0")
    suspend fun deactivateAllGroups()

    @Query("UPDATE group_pin_mappings SET isActive = 1 WHERE pinCode = :pinCode")
    suspend fun activateGroup(pinCode: String)

    @Query("SELECT * FROM group_pin_mappings WHERE isActive = 1 LIMIT 1")
    suspend fun getActiveGroupPinMappingOnce(): GroupPinMapping?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSafeZone(zone: SafeZone)

    @Delete
    suspend fun deleteSafeZone(zone: SafeZone)

    @Query("SELECT * FROM safe_zones ORDER BY name ASC")
    fun getAllSafeZones(): Flow<List<SafeZone>>

    @Query("SELECT * FROM safe_zones")
    suspend fun getAllSafeZonesOnce(): List<SafeZone>

    @Query("SELECT * FROM shopping_items ORDER BY isChecked ASC, timestamp DESC")
    fun getShoppingItems(): Flow<List<ShoppingItem>>

    @Query("SELECT * FROM shopping_items")
    suspend fun getShoppingItemsOnce(): List<ShoppingItem>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertShoppingItem(item: ShoppingItem): Long

    @Update
    suspend fun updateShoppingItem(item: ShoppingItem)

    @Delete
    suspend fun deleteShoppingItem(item: ShoppingItem)

    // Location Breadcrumbs queries for persistent driving/route history
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBreadcrumb(breadcrumb: LocationBreadcrumb)

    @Query("SELECT * FROM location_breadcrumbs WHERE memberId = :memberId AND timestamp >= :fromTimestamp ORDER BY timestamp ASC")
    fun getBreadcrumbsForMemberSince(memberId: String, fromTimestamp: Long): Flow<List<LocationBreadcrumb>>

    @Query("SELECT * FROM location_breadcrumbs WHERE memberId = :memberId AND timestamp >= :fromTimestamp ORDER BY timestamp ASC")
    suspend fun getBreadcrumbsForMemberSinceOnce(memberId: String, fromTimestamp: Long): List<LocationBreadcrumb>

    @Query("SELECT * FROM location_breadcrumbs WHERE memberId = :memberId ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLastBreadcrumbForMember(memberId: String): LocationBreadcrumb?

    @Query("DELETE FROM location_breadcrumbs WHERE timestamp < :cutoffTimestamp")
    suspend fun deleteBreadcrumbsOlderThan(cutoffTimestamp: Long)

    @Query("DELETE FROM location_breadcrumbs WHERE memberId = :memberId")
    suspend fun clearBreadcrumbsForMember(memberId: String)
}
