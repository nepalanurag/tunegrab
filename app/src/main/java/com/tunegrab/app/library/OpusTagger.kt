package com.tunegrab.app.library

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Minimal Ogg/Opus tag reader/writer for TITLE/ARTIST/GENRE.
 *
 * jaudiotagger cannot read Opus at all
 * ("No Reader associated with this extension:opus"), so without this
 * every tag write on an Opus download fails — "Fix song genres" reported
 * "53 failed" with zero successes. OpusTags is just a length-prefixed
 * vendor string plus "KEY=value" comments, so a small focused writer is
 * safe:
 *
 * - Only the single page holding the OpusTags packet is rebuilt (fresh
 *   segment table + recomputed CRC32). Every other page — all audio —
 *   is copied byte-identical.
 * - Anything unexpected (tag packet spanning pages, missing tags packet,
 *   malformed Ogg) aborts and returns false WITHOUT touching the file.
 * - Writes go to a temp file + rename, so a crash can't corrupt the
 *   library.
 *
 * Pure JVM, no Android dependencies: covered by unit tests.
 */
object OpusTagger {

    data class Tags(val title: String?, val artist: String?, val genre: String?)

    /** Current title/artist/genre from the OpusTags packet, or null. */
    fun readTags(file: File): Tags? = runCatching {
        val data = file.readBytes()
        val pages = parsePages(data) ?: return null
        val tagPacket = findTagPacket(pages, data) ?: return null
        parseComments(tagPacket)
    }.getOrNull()

    /**
     * Writes the non-null fields into the OpusTags packet, preserving the
     * vendor string and every other existing comment. True on success.
     */
    fun writeTags(file: File, title: String?, artist: String?, genre: String?): Boolean {
        return try {
            val data = file.readBytes()
            val pages = parsePages(data) ?: return false
            val newData = rebuildWithTags(pages, data, title, artist, genre) ?: return false
            val tmp = File(file.parentFile, "${file.name}.tagtmp")
            tmp.writeBytes(newData)
            if (!tmp.renameTo(file)) {
                tmp.delete()
                return false
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    // ---------------- Ogg parsing ----------------

    private class Page(
        val headerType: Int,
        val granule: Long,
        val serial: Int,
        val sequence: Int,
        val segmentTable: ByteArray,
        val pageStart: Int,
        val dataStart: Int,
    ) {
        val dataLen: Int get() = segmentTable.sumOf { it.toInt() and 0xFF }
        val pageLen: Int get() = (dataStart - pageStart) + dataLen
    }

    /** Sequential walk of Ogg pages; null when the stream is malformed. */
    private fun parsePages(data: ByteArray): List<Page>? {
        val pages = mutableListOf<Page>()
        var off = 0
        while (off < data.size) {
            if (off + 27 > data.size) return null
            if (!(data[off] == 'O'.code.toByte() && data[off + 1] == 'g'.code.toByte() &&
                        data[off + 2] == 'g'.code.toByte() && data[off + 3] == 'S'.code.toByte())
            ) return null
            if (data[off + 4] != 0.toByte()) return null // stream version
            val headerType = data[off + 5].toInt() and 0xFF
            val granule = ByteBuffer.wrap(data, off + 6, 8).order(ByteOrder.LITTLE_ENDIAN).long
            val serial = ByteBuffer.wrap(data, off + 14, 4).order(ByteOrder.LITTLE_ENDIAN).int
            val sequence = ByteBuffer.wrap(data, off + 18, 4).order(ByteOrder.LITTLE_ENDIAN).int
            val segCount = data[off + 26].toInt() and 0xFF
            if (off + 27 + segCount > data.size) return null
            val table = data.copyOfRange(off + 27, off + 27 + segCount)
            val dataStart = off + 27 + segCount
            val page = Page(headerType, granule, serial, sequence, table, off, dataStart)
            if (dataStart + page.dataLen > data.size) return null
            pages.add(page)
            off += page.pageLen
            // Only the EOS flag ends the stream: a page with few segments
            // is normal (the header pages have just one segment each).
            if (headerType and 0x04 != 0) break
        }
        return pages.takeIf { it.isNotEmpty() }
    }

    /** Splits pages into packets; each entry notes which pages it spanned. */
    private fun depacketize(pages: List<Page>, data: ByteArray): List<Packet> {
        val packets = mutableListOf<Packet>()
        var cur = mutableListOf<Byte>()
        var curPages = mutableListOf<Int>()
        var dataOff = 0
        for ((pi, page) in pages.withIndex()) {
            dataOff = page.dataStart
            for (seg in page.segmentTable) {
                val len = seg.toInt() and 0xFF
                for (i in 0 until len) cur.add(data[dataOff + i])
                dataOff += len
                curPages.add(pi)
                if (len < 255) {
                    packets.add(Packet(cur.toByteArray(), curPages.distinct()))
                    cur = mutableListOf()
                    curPages = mutableListOf()
                }
            }
        }
        return packets
    }

    private class Packet(val bytes: ByteArray, val pageIndexes: List<Int>)

    /** The OpusTags packet: must be packet 1 right after OpusHead. */
    private fun findTagPacket(pages: List<Page>, data: ByteArray): ByteArray? {
        val packets = depacketize(pages, data)
        if (packets.size < 2) return null
        if (!packets[0].bytes.startsWithAscii("OpusHead")) return null
        val tag = packets[1].bytes
        if (!tag.startsWithAscii("OpusTags")) return null
        return tag
    }

    private fun ByteArray.startsWithAscii(s: String): Boolean {
        if (size < s.length) return false
        for (i in s.indices) if (this[i] != s[i].code.toByte()) return false
        return true
    }

    // ---------------- Vorbis-comment style tags ----------------

    private fun parseComments(packet: ByteArray): Tags {
        val buf = ByteBuffer.wrap(packet).order(ByteOrder.LITTLE_ENDIAN)
        buf.position(8) // "OpusTags"
        val vendorLen = buf.int
        buf.position(buf.position() + vendorLen)
        val count = buf.int
        var title: String? = null
        var artist: String? = null
        var genre: String? = null
        repeat(count) {
            val len = buf.int
            val bytes = ByteArray(len)
            buf.get(bytes)
            val s = bytes.toString(Charsets.UTF_8)
            val eq = s.indexOf('=')
            if (eq > 0) {
                val value = s.substring(eq + 1).takeIf { it.isNotBlank() }
                when (s.substring(0, eq).uppercase()) {
                    "TITLE" -> title = value
                    "ARTIST" -> artist = value
                    "GENRE" -> genre = value
                }
            }
        }
        return Tags(title, artist, genre)
    }

    private fun buildTagPacket(
        oldPacket: ByteArray,
        title: String?,
        artist: String?,
        genre: String?,
    ): ByteArray {
        val buf = ByteBuffer.wrap(oldPacket).order(ByteOrder.LITTLE_ENDIAN)
        buf.position(8)
        val vendorLen = buf.int
        val vendor = ByteArray(vendorLen).also { buf.get(it) }
        val count = buf.int
        val kept = mutableListOf<String>()
        val replaced = mutableSetOf<String>()
        if (title != null) replaced.add("TITLE")
        if (artist != null) replaced.add("ARTIST")
        if (genre != null) replaced.add("GENRE")
        repeat(count) {
            val len = buf.int
            val bytes = ByteArray(len).also { buf.get(it) }
            val s = bytes.toString(Charsets.UTF_8)
            val key = s.substringBefore('=').uppercase()
            if (key !in replaced) kept.add(s)
        }
        if (title != null) kept.add("TITLE=$title")
        if (artist != null) kept.add("ARTIST=$artist")
        if (genre != null) kept.add("GENRE=$genre")
        val body = kept.map { it.toByteArray(Charsets.UTF_8) }
        val total = 8 + 4 + vendor.size + 4 + body.sumOf { 4 + it.size }
        val out = ByteBuffer.allocate(total).order(ByteOrder.LITTLE_ENDIAN)
        out.put("OpusTags".toByteArray(Charsets.US_ASCII))
        out.putInt(vendor.size)
        out.put(vendor)
        out.putInt(body.size)
        for (b in body) {
            out.putInt(b.size)
            out.put(b)
        }
        return out.array()
    }

    // ---------------- rebuild ----------------

    /**
     * Returns the full new file bytes, or null when the layout is anything
     * unexpected — the caller then leaves the file untouched.
     */
    private fun rebuildWithTags(
        pages: List<Page>,
        data: ByteArray,
        title: String?,
        artist: String?,
        genre: String?,
    ): ByteArray? {
        val packets = depacketize(pages, data)
        if (packets.size < 2 || !packets[1].bytes.startsWithAscii("OpusTags")) return null
        val tagPageIndexes = packets[1].pageIndexes
        // The tag packet must sit wholly inside exactly one page; anything
        // else (continued packets) is too exotic to rewrite safely.
        if (tagPageIndexes.size != 1) return null
        val tagPageIdx = tagPageIndexes[0]
        val tagPage = pages[tagPageIdx]
        if (tagPage.headerType and 0x01 != 0) return null // previous packet continues in
        val next = pages.getOrNull(tagPageIdx + 1)
        if (next != null && next.headerType and 0x01 != 0) return null // packet continues out

        // Packets fully contained in the tag page, with the new tag packet
        // substituted for the old one.
        val newTagPacket = buildTagPacket(packets[1].bytes, title, artist, genre)
        val pagePackets = packets.filter { it.pageIndexes == listOf(tagPageIdx) }
        if (pagePackets.isEmpty()) return null
        val newPacketBytes = pagePackets.map {
            if (it === packets[1]) newTagPacket else it.bytes
        }

        val newPage = buildPage(tagPage, newPacketBytes) ?: return null
        val out = ByteArray(tagPage.pageStart + newPage.size + (data.size - tagPage.pageStart - tagPage.pageLen))
        System.arraycopy(data, 0, out, 0, tagPage.pageStart)
        System.arraycopy(newPage, 0, out, tagPage.pageStart, newPage.size)
        System.arraycopy(
            data, tagPage.pageStart + tagPage.pageLen,
            out, tagPage.pageStart + newPage.size,
            data.size - tagPage.pageStart - tagPage.pageLen
        )
        return out
    }

    /**
     * Rebuilds one page around new packet bytes, keeping the original
     * header fields (type, granule, serial, sequence). Null when the
     * packets can't be segmented into a single page.
     */
    private fun buildPage(page: Page, packetBytes: List<ByteArray>): ByteArray? {
        val table = mutableListOf<Byte>()
        for (p in packetBytes) {
            var remaining = p.size
            // A packet size that is an exact multiple of 255 needs a
            // zero-length terminator segment.
            do {
                val seg = minOf(remaining, 255)
                table.add(seg.toByte())
                remaining -= seg
            } while (remaining > 0)
            if (p.size % 255 == 0) table.add(0)
        }
        if (table.size > 255) return null
        val dataLen = table.sumOf { it.toInt() and 0xFF }
        val out = ByteBuffer.allocate(27 + table.size + dataLen).order(ByteOrder.LITTLE_ENDIAN)
        out.put("OggS".toByteArray(Charsets.US_ASCII))
        out.put(0) // version
        out.put(page.headerType.toByte())
        out.putLong(page.granule)
        out.putInt(page.serial)
        out.putInt(page.sequence)
        out.putInt(0) // CRC placeholder
        out.put(table.size.toByte())
        for (b in table) out.put(b)
        for (p in packetBytes) out.put(p)
        val bytes = out.array()
        // Ogg CRC32: MSB-first, poly 0x04C11DB7, init 0, no final XOR —
        // NOT the same as java.util.zip.CRC32 (which inits/XORs with
        // 0xFFFFFFFF). Computed over the page with the CRC field zeroed.
        ByteBuffer.wrap(bytes, 22, 4).order(ByteOrder.LITTLE_ENDIAN).putInt(oggCrc(bytes))
        return bytes
    }

    private val OGG_CRC_TABLE: IntArray = IntArray(256) { i ->
        var r = i shl 24
        repeat(8) {
            r = if (r and Int.MIN_VALUE != 0) (r shl 1) xor 0x04C11DB7 else r shl 1
        }
        r
    }

    private fun oggCrc(data: ByteArray): Int {
        var crc = 0
        for (b in data) {
            crc = (crc shl 8) xor OGG_CRC_TABLE[(crc ushr 24) xor (b.toInt() and 0xFF)]
        }
        return crc
    }
}
