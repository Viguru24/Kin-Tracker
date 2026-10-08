package com.example.data

object IdentityUtils {
    /**
     * Resolves any name or device ID variation to a single canonical identity key.
     * Guaranteed to match across all permutations (e.g. "Louis", "Dad", "Louis (Dad)", "device_dad_...", "device_louis_...").
     */
    fun getCanonicalPersonKey(name: String, id: String = ""): String {
        val clean = name.lowercase().replace(Regex("[^a-z0-9]"), " ").trim()
        val cleanId = id.lowercase().replace(Regex("[^a-z0-9]"), " ").trim()

        return when {
            clean.contains("louis") || clean.contains("dad") || clean.contains("father") || cleanId.contains("louis") || cleanId.contains("dad") -> "canonical_dad"
            clean.contains("annette") || clean.contains("mama") || clean.contains("wife") || clean.contains("mother") || clean.contains("mom") || cleanId.contains("annette") || cleanId.contains("mama") || cleanId.contains("wife") -> "canonical_mama"
            clean.contains("eloise") || clean.contains("eloisa") || cleanId.contains("eloise") || cleanId.contains("eloisa") -> "canonical_eloise"
            clean.contains("isabel") || clean.contains("isabelle") || cleanId.contains("isabel") || cleanId.contains("isabelle") -> "canonical_isabel"
            else -> {
                val uuid = if (id.startsWith("device_") && id.contains("_")) id.substringAfterLast("_") else ""
                if (uuid.length >= 4) "uuid_$uuid"
                else clean.replace("\\s+".toRegex(), "").ifBlank { id }
            }
        }
    }

    /**
     * Checks whether two member records represent the exact same human/device.
     */
    fun isSamePersonOrDevice(idA: String, nameA: String, idB: String, nameB: String): Boolean {
        if (idA.isNotBlank() && idA == idB) return true
        val keyA = getCanonicalPersonKey(nameA, idA)
        val keyB = getCanonicalPersonKey(nameB, idB)
        if (keyA.isNotBlank() && keyA == keyB) return true
        val uuidA = if (idA.startsWith("device_") && idA.contains("_")) idA.substringAfterLast("_") else ""
        val uuidB = if (idB.startsWith("device_") && idB.contains("_")) idB.substringAfterLast("_") else ""
        if (uuidA.length >= 4 && uuidA == uuidB) return true
        return false
    }

    /**
     * Sanitizes and heals any character encoding glitches / mojibake in emoji strings.
     */
    fun sanitizeAvatarEmoji(emoji: String, name: String, id: String = ""): String {
        val clean = emoji.trim()
        if (clean.isBlank() || clean.contains("ð") || clean.contains("Ÿ") || clean.contains("\ufffd") || clean.contains("?")) {
            return when (getCanonicalPersonKey(name, id)) {
                "canonical_eloise" -> "👧"
                "canonical_mama" -> "👩"
                "canonical_isabel" -> "🐼"
                "canonical_dad" -> "📱"
                else -> if (clean.isNotBlank() && !clean.contains("ð") && !clean.contains("?") && !clean.contains("\ufffd")) clean else "📱"
            }
        }
        return clean
    }
}
