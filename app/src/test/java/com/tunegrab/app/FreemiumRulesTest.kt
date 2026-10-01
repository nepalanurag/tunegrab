package com.tunegrab.app

import org.junit.Assert.*
import org.junit.Test

/**
 * JVM unit tests for the freemium gating rules. These run without a
 * device — they verify that a free user can never slip a Pro-only choice
 * into a download, and that downloads, Radio, and the sleep timer are
 * strictly Pro-only (no ads in the store model).
 */
class FreemiumRulesTest {

    // ---------------- pro-only features ----------------

    @Test fun `free user cannot download`() {
        assertFalse(FreemiumRules.canDownload(isPro = false))
    }

    @Test fun `pro user can download`() {
        assertTrue(FreemiumRules.canDownload(isPro = true))
    }

    @Test fun `free user cannot use radio`() {
        assertFalse(FreemiumRules.canUseRadio(isPro = false))
    }

    @Test fun `pro user can use radio`() {
        assertTrue(FreemiumRules.canUseRadio(isPro = true))
    }

    @Test fun `free user cannot use sleep timer`() {
        assertFalse(FreemiumRules.canUseSleepTimer(isPro = false))
    }

    @Test fun `pro user can use sleep timer`() {
        assertTrue(FreemiumRules.canUseSleepTimer(isPro = true))
    }

    // ---------------- format clamping ----------------

    @Test fun `free user format is always MP3`() {
        for (f in AudioFormat.values()) {
            assertEquals(AudioFormat.MP3, FreemiumRules.effectiveFormat(false, f))
        }
    }

    @Test fun `pro user keeps chosen format`() {
        for (f in AudioFormat.values()) {
            assertEquals(f, FreemiumRules.effectiveFormat(true, f))
        }
    }

    @Test fun `only MP3 allowed for free users`() {
        assertTrue(FreemiumRules.isFormatAllowed(false, AudioFormat.MP3))
        assertFalse(FreemiumRules.isFormatAllowed(false, AudioFormat.FLAC))
        assertFalse(FreemiumRules.isFormatAllowed(false, AudioFormat.OPUS))
        assertFalse(FreemiumRules.isFormatAllowed(false, AudioFormat.M4A))
    }

    // ---------------- quality clamping ----------------

    @Test fun `free user quality args are always 192K`() {
        for (f in AudioFormat.values()) {
            for (q in AudioQuality.values()) {
                val args = FreemiumRules.effectiveQualityArgs(false, f, q)
                assertEquals(
                    listOf("--audio-quality" to "192K"),
                    args
                )
            }
        }
    }

    @Test fun `pro FLAC has no quality args`() {
        assertTrue(
            FreemiumRules.effectiveQualityArgs(true, AudioFormat.FLAC, AudioQuality.BEST).isEmpty()
        )
    }

    @Test fun `pro MP3 320K passes through`() {
        assertEquals(
            listOf("--audio-quality" to "320K"),
            FreemiumRules.effectiveQualityArgs(true, AudioFormat.MP3, AudioQuality.HIGH)
        )
    }

    @Test fun `only medium quality allowed for free users`() {
        assertTrue(FreemiumRules.isQualityAllowed(false, AudioQuality.MEDIUM))
        assertFalse(FreemiumRules.isQualityAllowed(false, AudioQuality.BEST))
        assertFalse(FreemiumRules.isQualityAllowed(false, AudioQuality.LOW))
    }

    // ---------------- filename clamping ----------------

    @Test fun `free user template is always title-only`() {
        for (s in FilenameStyle.values()) {
            for (collection in listOf(false, true)) {
                assertEquals(
                    "%(title)s.%(ext)s",
                    FreemiumRules.effectiveTemplate(false, s, collection)
                )
            }
        }
    }

    @Test fun `only title-only filenames allowed for free users`() {
        assertTrue(FreemiumRules.isFilenameAllowed(false, FilenameStyle.TITLE_ONLY))
        assertFalse(FreemiumRules.isFilenameAllowed(false, FilenameStyle.DETAILED))
    }

    // ---------------- template correctness ----------------

    @Test fun `every pro template ends with ext placeholder`() {
        for (s in FilenameStyle.values()) {
            for (collection in listOf(false, true)) {
                val t = FreemiumRules.outputTemplate(s, collection)
                assertTrue("template for $s missing %(ext)s: $t", t.endsWith(".%(ext)s"))
            }
        }
    }

    @Test fun `numbered style prefixes index only for collections`() {
        assertEquals(
            "%(playlist_index)02d %(title)s.%(ext)s",
            FreemiumRules.outputTemplate(FilenameStyle.NUMBERED, true)
        )
        assertEquals(
            "%(title)s.%(ext)s",
            FreemiumRules.outputTemplate(FilenameStyle.NUMBERED, false)
        )
    }

    @Test fun `detailed style has fallbacks`() {
        val t = FreemiumRules.outputTemplate(FilenameStyle.DETAILED, false)
        assertTrue(t.contains("%(artist,uploader)s"))
        assertTrue(t.contains("%(album|Single)s"))
    }
}
