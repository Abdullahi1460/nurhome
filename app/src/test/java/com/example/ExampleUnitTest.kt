package com.example

import com.example.data.model.ActivityLogItem
import com.example.data.model.ConnectionInfo
import com.example.data.model.Relay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {
  @Test
  fun testRelayFallbacks() {
    val relay = Relay(key = "relay2", defaultName = "Light 2")
    assertEquals("Light 2", relay.displayName)
    assertEquals("/devices/relay2", relay.firebasePath)
    assertEquals("Living Room", relay.room)
  }

  @Test
  fun testActivityLogCreation() {
    val log = ActivityLogItem(
      relayKey = "relay1",
      relayName = "Desk Lamp",
      newState = true,
      source = "App Tap"
    )
    assertNotNull(log.id)
    assertEquals("Desk Lamp", log.relayName)
    assertTrue(log.newState)
    assertEquals("App Tap", log.source)
    assertTrue(log.timestampMs > 0)
  }

  @Test
  fun testConnectionInfoDefaults() {
    val info = ConnectionInfo()
    assertEquals(false, info.isConnected)
    assertEquals(false, info.isAuthenticating)
    assertEquals("Firebase Realtime DB (WebSocket)", info.activeProtocol)
  }
}
