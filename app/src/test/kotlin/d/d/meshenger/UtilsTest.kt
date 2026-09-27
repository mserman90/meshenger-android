package d.d.meshenger

import org.junit.Assert.*
import org.junit.Test

class UtilsTest {

    @Test
    fun testIsValidName() {
        assertTrue(Utils.isValidName("Alice"))
        assertTrue(Utils.isValidName("Bob_123"))
        assertFalse(Utils.isValidName(null))
        assertFalse(Utils.isValidName(""))
        assertFalse(Utils.isValidName("   "))
    }

    @Test
    fun testByteArrayToHexStringAndBack() {
        val bytes = byteArrayOf(0x01, 0x02, 0x0F, 0xFF.toByte())
        val hex = Utils.byteArrayToHexString(bytes)
        assertEquals("01020FFF", hex)

        val restored = Utils.hexStringToByteArray(hex)
        assertArrayEquals(bytes, restored)
    }

    @Test
    fun testHexStringToByteArrayInvalid() {
        assertArrayEquals(ByteArray(0), Utils.hexStringToByteArray(null))
        assertArrayEquals(ByteArray(0), Utils.hexStringToByteArray("123")) // odd length
        assertArrayEquals(ByteArray(0), Utils.hexStringToByteArray("GG")) // invalid chars
    }
}
