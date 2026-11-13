package com.example.data.sync

import android.content.SharedPreferences
import com.example.data.CloudMember
import com.example.data.CloudShoppingItem
import com.example.data.FamilyRepository
import com.example.data.ShoppingItem

data class ShoppingSyncResult(
    val finalItems: List<CloudShoppingItem>,
    val mergedDeletions: Map<String, Long>
)

/**
 * Shared pure and persistence helpers used identically by both foreground [CloudSyncManager]
 * and background [com.example.data.BackgroundSyncProcessor] to eliminate sync drift.
 */
object SyncResolutionHelper {
    private const val TOMBSTONE_RETENTION_MS = 30L * 24 * 60 * 60 * 1000L
    private const val ALARM_ACTIVE_WINDOW_MS = 60_000L

    fun isValidSyncToken(token: String?): Boolean {
        return !token.isNullOrBlank() &&
            token != "00000000_" &&
            !token.startsWith("00000000_")
    }

    fun buildMyCloudId(deviceName: String, deviceUuid: String): String {
        return "device_" + deviceName.lowercase().replace("\\s".toRegex(), "") + "_" + deviceUuid
    }

    fun isMyCloudEntry(
        member: CloudMember,
        myCloudId: String,
        myDeviceUuid: String,
        myDeviceName: String
    ): Boolean {
        return member.id == myCloudId ||
            (myDeviceUuid.length >= 4 && member.id.endsWith("_$myDeviceUuid")) ||
            member.name.trim().equals(myDeviceName.trim(), ignoreCase = true)
    }

    fun shouldTriggerLocalAlarm(
        members: Collection<CloudMember>,
        myCloudId: String,
        myDeviceUuid: String,
        myDeviceName: String,
        nowMs: Long = System.currentTimeMillis()
    ): Boolean {
        return members.any { member ->
            val isSameDevice = isMyCloudEntry(member, myCloudId, myDeviceUuid, myDeviceName)
            val isAlarmActive = member.statusText == "🚨 ALARM" ||
                (member.alarmRequestedAt > 0L && (nowMs - member.alarmRequestedAt) in 0L..ALARM_ACTIVE_WINDOW_MS)
            isSameDevice && isAlarmActive
        }
    }

    fun mergeShoppingListsPure(
        localItems: List<ShoppingItem>,
        incomingItems: List<CloudShoppingItem>,
        localDeletions: Map<String, Long>,
        incomingDeletions: Map<String, Long>,
        nowMs: Long = System.currentTimeMillis()
    ): ShoppingSyncResult {
        val cutoff = nowMs - TOMBSTONE_RETENTION_MS
        val mergedDeletions = mutableMapOf<String, Long>()

        for ((k, v) in localDeletions) {
            if (v > cutoff) mergedDeletions[k] = v
        }
        for ((k, v) in incomingDeletions) {
            if (v > cutoff) {
                val existing = mergedDeletions[k] ?: 0L
                if (v > existing) mergedDeletions[k] = v
            }
        }

        fun getDeletionTimestamp(name: String): Long {
            val normKey = name.lowercase().replace("[^a-z0-9]".toRegex(), "").trim()
            val rawKey = name.lowercase().trim()
            return maxOf(mergedDeletions[normKey] ?: 0L, mergedDeletions[rawKey] ?: 0L)
        }

        val mergedMap = LinkedHashMap<String, CloudShoppingItem>()

        for (localItem in localItems) {
            val normKey = localItem.name.lowercase().replace("[^a-z0-9]".toRegex(), "").trim()
            if (normKey.isBlank()) continue
            val delTime = getDeletionTimestamp(localItem.name)
            if (delTime == 0L || localItem.timestamp > delTime) {
                val existing = mergedMap[normKey]
                if (existing == null || localItem.timestamp > existing.timestamp) {
                    mergedMap[normKey] = CloudShoppingItem(
                        name = localItem.name,
                        isChecked = localItem.isChecked,
                        addedByMemberId = localItem.addedByMemberId,
                        addedByMemberName = localItem.addedByMemberName,
                        timestamp = localItem.timestamp
                    )
                }
            }
        }

        for (cloudItem in incomingItems) {
            val normKey = cloudItem.name.lowercase().replace("[^a-z0-9]".toRegex(), "").trim()
            if (normKey.isBlank()) continue
            val delTime = getDeletionTimestamp(cloudItem.name)
            if (delTime > 0L && cloudItem.timestamp <= delTime) {
                mergedMap.remove(normKey)
            } else if (delTime == 0L || cloudItem.timestamp > delTime) {
                val localMatch = mergedMap[normKey]
                if (localMatch == null || cloudItem.timestamp > localMatch.timestamp) {
                    mergedMap[normKey] = cloudItem
                }
            }
        }

        return ShoppingSyncResult(
            finalItems = mergedMap.values.toList(),
            mergedDeletions = mergedDeletions
        )
    }

    suspend fun syncShoppingListWithDatabase(
        repository: FamilyRepository,
        deletionPrefs: SharedPreferences,
        incomingShoppingItems: List<CloudShoppingItem>,
        incomingDeletions: Map<String, Long>,
        nowMs: Long = System.currentTimeMillis()
    ): ShoppingSyncResult {
        val localShoppingItems = repository.getShoppingItemsOnce()
        val localDeletions = mutableMapOf<String, Long>()
        for ((k, v) in deletionPrefs.all) {
            if (v is Long) localDeletions[k] = v
        }

        val syncResult = mergeShoppingListsPure(
            localItems = localShoppingItems,
            incomingItems = incomingShoppingItems,
            localDeletions = localDeletions,
            incomingDeletions = incomingDeletions,
            nowMs = nowMs
        )

        val delEditor = deletionPrefs.edit()
        for ((k, v) in syncResult.mergedDeletions) {
            delEditor.putLong(k, v)
        }
        delEditor.apply()

        val mergedByKey = syncResult.finalItems.associateBy {
            it.name.lowercase().replace("[^a-z0-9]".toRegex(), "").trim()
        }
        val seenKeysInDb = mutableSetOf<String>()

        for (localItem in localShoppingItems) {
            val normKey = localItem.name.lowercase().replace("[^a-z0-9]".toRegex(), "").trim()
            val matchedCloud = mergedByKey[normKey]
            if (matchedCloud == null || seenKeysInDb.contains(normKey)) {
                repository.deleteShoppingItem(localItem)
            } else {
                seenKeysInDb.add(normKey)
                if (localItem.isChecked != matchedCloud.isChecked && matchedCloud.timestamp > localItem.timestamp) {
                    repository.updateShoppingItem(
                        localItem.copy(isChecked = matchedCloud.isChecked, timestamp = matchedCloud.timestamp)
                    )
                }
            }
        }

        for (cloudItem in syncResult.finalItems) {
            val normKey = cloudItem.name.lowercase().replace("[^a-z0-9]".toRegex(), "").trim()
            if (!seenKeysInDb.contains(normKey)) {
                repository.insertShoppingItem(
                    ShoppingItem(
                        name = cloudItem.name,
                        isChecked = cloudItem.isChecked,
                        addedByMemberId = cloudItem.addedByMemberId,
                        addedByMemberName = cloudItem.addedByMemberName,
                        timestamp = cloudItem.timestamp
                    )
                )
                seenKeysInDb.add(normKey)
            }
        }

        return syncResult
    }
}
