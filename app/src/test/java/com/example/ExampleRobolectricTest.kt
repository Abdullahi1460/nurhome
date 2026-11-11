package com.example

import com.example.data.model.QuickPreset
import com.example.data.model.Relay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleRobolectricTest {

  @Test
  fun `verify relay data model properties and paths`() {
    val relay = Relay(
      key = "relay1",
      defaultName = "Light 1",
      customName = "Chandelier",
      room = "Living Room",
      isOn = false
    )
    assertEquals("Chandelier", relay.displayName)
    assertEquals("/devices/relay1", relay.firebasePath)
    assertFalse(relay.isOn)

    val updated = relay.copy(isOn = true)
    assertTrue(updated.isOn)
  }

  @Test
  fun `verify quick presets definition`() {
    val allOn = QuickPreset.ALL_ON
    val allOff = QuickPreset.ALL_OFF
    assertEquals("All On", allOn.title)
    assertEquals("All Off", allOff.title)
  }
}
