package com.example.data.sync

import android.content.SharedPreferences
import com.example.data.CloudMember
import com.example.data.CloudSafeZone
import com.example.data.CloudShoppingItem
import com.example.data.FamilyRepository
import com.example.data.SafeZone
import com.example.data.ShoppingItem

data class ShoppingSyncResult(
    val finalItems: List<CloudShoppingItem>,
    val mergedDeletions: Map<String, Long>
)

data class SafeZoneSyncResult(
    val finalZones: List<CloudSafeZone>,
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

    private const val SAFE_ZONE_TS_PREFIX = "ts_"
    private const val SAFE_ZONE_DEL_PREFIX = "del_"

    private fun isLegacyRogueSafeZone(zoneId: String, name: String): Boolean {
        return zoneId == "zone_oasis_academy_byron" ||
            zoneId == "zone_woodcote_school" ||
            name.contains("Byron", ignoreCase = true) ||
            (name.contains("Woodcote", ignoreCase = true) && name.contains("Primary", ignoreCase = true))
    }

    fun recordSafeZoneUpsert(metaPrefs: SharedPreferences, zoneId: String, timestampMs: Long = System.currentTimeMillis()) {
        if (zoneId.isBlank()) return
        metaPrefs.edit()
            .putLong("$SAFE_ZONE_TS_PREFIX$zoneId", timestampMs)
            .remove("$SAFE_ZONE_DEL_PREFIX$zoneId")
            .apply()
    }

    fun recordSafeZoneDeletion(metaPrefs: SharedPreferences, zoneId: String, timestampMs: Long = System.currentTimeMillis()) {
        if (zoneId.isBlank()) return
        metaPrefs.edit()
            .putLong("$SAFE_ZONE_DEL_PREFIX$zoneId", timestampMs)
            .remove("$SAFE_ZONE_TS_PREFIX$zoneId")
            .apply()
    }

    fun isSafeZoneDeleted(metaPrefs: SharedPreferences, zoneId: String): Boolean {
        return metaPrefs.getLong("$SAFE_ZONE_DEL_PREFIX$zoneId", 0L) > 0L
    }

    /**
     * Merges local Room [SafeZone] rows with incoming cloud [CloudSafeZone] entries using
     * per-zone LWW timestamps and tombstones so any family member can add, edit, or remove places.
     */
    fun mergeSafeZonesPure(
        localZones: List<SafeZone>,
        incomingZones: List<CloudSafeZone>,
        localUpdatedAt: Map<String, Long>,
        localDeletions: Map<String, Long>,
        incomingDeletions: Map<String, Long>,
        nowMs: Long = System.currentTimeMillis(),
        defaultLocalUpdatedAtMs: Long = 1_000L
    ): SafeZoneSyncResult {
        val cutoff = nowMs - TOMBSTONE_RETENTION_MS
        val mergedDeletions = mutableMapOf<String, Long>()

        for ((id, ts) in localDeletions) {
            if (id.isNotBlank() && ts > cutoff) mergedDeletions[id] = ts
        }
        for ((id, ts) in incomingDeletions) {
            if (id.isNotBlank() && ts > cutoff) {
                val prev = mergedDeletions[id] ?: 0L
                if (ts > prev) mergedDeletions[id] = ts
            }
        }

        val mergedMap = LinkedHashMap<String, CloudSafeZone>()

        for (local in localZones) {
            if (local.id.isBlank() || isLegacyRogueSafeZone(local.id, local.name)) continue
            if (local.latitude == 0.0 && local.longitude == 0.0) continue
            val ts = localUpdatedAt[local.id] ?: defaultLocalUpdatedAtMs
            val delTs = mergedDeletions[local.id] ?: 0L
            if (delTs == 0L || ts > delTs) {
                mergedMap[local.id] = CloudSafeZone(
                    id = local.id,
                    name = local.name,
                    latitude = local.latitude,
                    longitude = local.longitude,
                    radiusMeters = local.radiusMeters,
                    iconName = local.iconName,
                    updatedAt = ts
                )
            }
        }

        for (cloud in incomingZones) {
            if (cloud.id.isBlank() || cloud.name.isBlank() || isLegacyRogueSafeZone(cloud.id, cloud.name)) continue
            if (cloud.latitude == 0.0 && cloud.longitude == 0.0) continue
            val cloudTs = if (cloud.updatedAt > 0L) cloud.updatedAt else nowMs
            val delTs = mergedDeletions[cloud.id] ?: 0L
            if (delTs > 0L && cloudTs <= delTs) {
                mergedMap.remove(cloud.id)
                continue
            }
            val normalized = cloud.copy(updatedAt = cloudTs)
            val existing = mergedMap[cloud.id]
            if (existing == null || cloudTs >= existing.updatedAt) {
                mergedMap[cloud.id] = normalized
                if (delTs > 0L && cloudTs > delTs) {
                    mergedDeletions.remove(cloud.id)
                }
            }
        }

        return SafeZoneSyncResult(
            finalZones = mergedMap.values.toList(),
            mergedDeletions = mergedDeletions
        )
    }

    suspend fun syncSafeZonesWithDatabase(
        repository: FamilyRepository,
        metaPrefs: SharedPreferences,
        incomingZones: List<CloudSafeZone>,
        incomingDeletions: Map<String, Long>,
        nowMs: Long = System.currentTimeMillis()
    ): SafeZoneSyncResult {
        val localZones = try { repository.getAllSafeZonesOnce() } catch (_: Exception) { emptyList() }
        val localUpdatedAt = mutableMapOf<String, Long>()
        val localDeletions = mutableMapOf<String, Long>()

        for ((k, v) in metaPrefs.all) {
            if (v !is Long) continue
            when {
                k.startsWith(SAFE_ZONE_TS_PREFIX) -> localUpdatedAt[k.removePrefix(SAFE_ZONE_TS_PREFIX)] = v
                k.startsWith(SAFE_ZONE_DEL_PREFIX) -> localDeletions[k.removePrefix(SAFE_ZONE_DEL_PREFIX)] = v
            }
        }

        val result = mergeSafeZonesPure(
            localZones = localZones,
            incomingZones = incomingZones,
            localUpdatedAt = localUpdatedAt,
            localDeletions = localDeletions,
            incomingDeletions = incomingDeletions,
            nowMs = nowMs
        )

        val editor = metaPrefs.edit()
        localDeletions.keys.forEach { editor.remove("$SAFE_ZONE_DEL_PREFIX$it") }
        result.mergedDeletions.forEach { (id, ts) ->
            editor.putLong("$SAFE_ZONE_DEL_PREFIX$id", ts)
            editor.remove("$SAFE_ZONE_TS_PREFIX$id")
        }
        result.finalZones.forEach { zone ->
            editor.putLong("$SAFE_ZONE_TS_PREFIX${zone.id}", zone.updatedAt)
        }
        editor.apply()

        val finalById = result.finalZones.associateBy { it.id }
        val localById = localZones.associateBy { it.id }

        for (local in localZones) {
            if (!finalById.containsKey(local.id)) {
                repository.deleteSafeZone(local)
            }
        }

        for (zone in result.finalZones) {
            val existing = localById[zone.id]
            val target = SafeZone(
                id = zone.id,
                name = zone.name,
                latitude = zone.latitude,
                longitude = zone.longitude,
                radiusMeters = zone.radiusMeters,
                iconName = zone.iconName
            )
            if (existing == null || existing != target) {
                repository.insertSafeZone(target)
            }
        }

        return result
    }
}
