package dev.hostyn.gallery.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DetailsTest {
    @Test
    fun exposure() {
        assertEquals("1/120 s", formatExposure(1 / 120.0))
        assertEquals("1/4000 s", formatExposure(0.00025))
        assertEquals("1/4 s", formatExposure(0.25))
        assertEquals("0.5 s", formatExposure(0.5))
        assertEquals("2 s", formatExposure(2.0))
    }

    @Test
    fun aperture() {
        assertEquals("ƒ/1.8", formatAperture(1.8))
        assertEquals("ƒ/2", formatAperture(2.0))
    }

    @Test
    fun videoLocation() {
        assertEquals(40.4168 to -3.7038, parseIso6709("+40.4168-003.7038/"))
        assertEquals(-33.8688 to 151.2093, parseIso6709("-33.8688+151.2093+012.5/"))
        assertNull(parseIso6709("+00.0000+000.0000/"))
        assertNull(parseIso6709(""))
        assertNull(parseIso6709(null))
        assertNull(parseIso6709("+95.0+010.0/"))
    }
}
