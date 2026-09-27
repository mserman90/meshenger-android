package d.d.meshenger

import org.junit.Assert.*
import org.junit.Test

class AddressUtilsTest {

    @Test
    fun testStripInterface() {
        assertEquals("fe80::1", AddressUtils.stripInterface("fe80::1%wlan0"))
        assertEquals("192.168.1.1", AddressUtils.stripInterface("192.168.1.1"))
    }

    @Test
    fun testStripHost() {
        assertEquals("1.2.3.4", AddressUtils.stripHost("/1.2.3.4"))
        assertEquals("1.2.3.4", AddressUtils.stripHost("google.com/1.2.3.4"))
        assertEquals("google.com", AddressUtils.stripHost("google.com"))
    }

    @Test
    fun testIsMACAddress() {
        assertTrue(AddressUtils.isMACAddress("00:11:22:33:44:55"))
        assertTrue(AddressUtils.isMACAddress("AA:BB:CC:DD:EE:FF"))
        assertFalse(AddressUtils.isMACAddress("invalid-mac"))
        assertFalse(AddressUtils.isMACAddress("00:11:22:33:44"))
    }

    @Test
    fun testFormatMAC() {
        val bytes = byteArrayOf(0x00, 0x11, 0x22, 0x33, 0x44, 0x55)
        assertEquals("00:11:22:33:44:55", AddressUtils.formatMAC(bytes))
    }

    @Test
    fun testIsDomain() {
        assertTrue(AddressUtils.isDomain("example.com"))
        assertTrue(AddressUtils.isDomain("sub.domain.org"))
        assertFalse(AddressUtils.isDomain("example..com"))
    }

    @Test
    fun testIgnoreDeviceByName() {
        assertTrue(AddressUtils.ignoreDeviceByName("dummy0"))
        assertFalse(AddressUtils.ignoreDeviceByName("wlan0"))
        assertFalse(AddressUtils.ignoreDeviceByName("eth0"))
    }
}
