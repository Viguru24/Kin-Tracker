package com.example

import com.example.data.CloudGroupPayload
import com.example.data.CloudMember
import com.example.data.RoomAudioStreamManager
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AudioStreamVpsLinkTest {

    @Before
    fun setup() {
        RoomAudioStreamManager.registerMemberAudioState("device_test_1", "", false, "Test")
    }

    @Test
    fun testAudioTransmitterStateRegistrationFromVps() {
        // Simulate incoming member data from VPS indicating Eloise is transmitting
        RoomAudioStreamManager.registerMemberAudioState(
            memberId = "device_eloise_a9e3d7",
            ip = "192.168.1.155",
            isTransmitting = true,
            memberName = "Eloise (Daughter)"
        )

        // Verify IP registered
        assertEquals("192.168.1.155", RoomAudioStreamManager.getMemberIp("device_eloise_a9e3d7", "Eloise"))
        assertEquals("192.168.1.155", RoomAudioStreamManager.getMemberIp("eloise", "Eloise"))

        // Verify transmitter active status registered
        assertTrue(RoomAudioStreamManager.isMemberTransmitting("device_eloise_a9e3d7", "Eloise"))
        assertTrue(RoomAudioStreamManager.isMemberTransmitting("device_eloise_a9e3d7", "Eloise (Daughter)"))

        // Simulate transmitter stopped on VPS
        RoomAudioStreamManager.registerMemberAudioState(
            memberId = "device_eloise_a9e3d7",
            ip = "192.168.1.155",
            isTransmitting = false,
            memberName = "Eloise (Daughter)"
        )

        assertFalse(RoomAudioStreamManager.isMemberTransmitting("device_eloise_a9e3d7", "Eloise"))
    }

    @Test
    fun testOnTransmitterToggledCallback() {
        var callbackFired = false
        var lastState = false

        RoomAudioStreamManager.onTransmitterToggled = { active ->
            callbackFired = true
            lastState = active
        }

        assertNotNull(RoomAudioStreamManager.onTransmitterToggled)
        RoomAudioStreamManager.onTransmitterToggled?.invoke(true)
        assertTrue(callbackFired)
        assertTrue(lastState)

        RoomAudioStreamManager.onTransmitterToggled?.invoke(false)
        assertFalse(lastState)
    }

    @Test
    fun testCloudMemberVpsSerializationIncludesAudioFields() {
        val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
        val adapter = moshi.adapter(CloudGroupPayload::class.java)

        val member = CloudMember(
            id = "device_dad_336a12",
            name = "Dad",
            avatarColorHex = "#AA22FF",
            localIp = "192.168.1.42",
            isAudioTransmitter = true
        )

        val payload = CloudGroupPayload(
            homeLat = 51.3294,
            homeLng = -0.1190,
            isHomeCalibrated = true,
            lastUpdated = System.currentTimeMillis(),
            members = mapOf(member.id to member)
        )

        val json = adapter.toJson(payload)
        assertTrue("JSON payload to VPS must contain localIp", json.contains("\"localIp\":\"192.168.1.42\""))
        assertTrue("JSON payload to VPS must contain isAudioTransmitter", json.contains("\"isAudioTransmitter\":true"))

        val deserialized = adapter.fromJson(json)
        assertNotNull(deserialized)
        val deserializedMember = deserialized!!.members[member.id]
        assertNotNull(deserializedMember)
        assertEquals("192.168.1.42", deserializedMember!!.localIp)
        assertTrue(deserializedMember.isAudioTransmitter)
    }
}
