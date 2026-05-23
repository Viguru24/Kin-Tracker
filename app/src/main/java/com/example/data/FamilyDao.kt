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
}
