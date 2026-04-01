package com.vaultzip.archive.impl

import com.vaultzip.archive.model.ArchiveFormat
import com.vaultzip.archive.model.ArchivePartSource
import com.vaultzip.archive.model.VolumePart
import com.vaultzip.archive.model.VolumeSet
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VolumeDetector @Inject constructor() {

    fun detect(
        selectedName: String,
        siblings: List<ArchivePartSource>
    ): VolumeSet? {
        val lower = selectedName.lowercase()

        return when {
            isSevenZipVolume(lower) -> detectSevenZipSet(lower, siblings)
            isRarVolume(lower) -> detectRarSet(selectedName, siblings)
            isZipVolume(lower) -> detectZipSet(lower, siblings)
            else -> null
        }
    }

    private fun isSevenZipVolume(name: String): Boolean {
        return name.matches(Regex(""".*\.7z\.\d{3}$"""))
    }

    private fun isRarVolume(name: String): Boolean {
        return name.matches(Regex(""".*\.part\d+\.rar$""")) ||
            name.matches(Regex(""".*\.r\d{2}$""")) ||
            name.endsWith(".rar")
    }

    private fun isZipVolume(name: String): Boolean {
        return name.matches(Regex(""".*\.z\d{2}$""")) ||
            name.matches(Regex(""".*\.zip\.\d{3}$""")) ||
            name.endsWith(".zip")
    }

    private fun detectSevenZipSet(selectedLower: String, parts: List<ArchivePartSource>): VolumeSet? {
        val rootName = selectedLower.substringBefore(".7z.")
        val matched = parts.filter {
            val name = it.name.lowercase()
            name.matches(Regex(""".*\.7z\.\d{3}$""")) && name.substringBefore(".7z.") == rootName
        }.sortedBy { parseLastNumber(it.name) }
        if (matched.isEmpty()) return null

        val indexes = matched.map { parseLastNumber(it.name) }.sorted()
        val missing = findMissing(indexes).map { idx ->
            "${matched.first().name.substringBefore(".7z.")}.7z.${idx.toString().padStart(3, '0')}"
        }
        return VolumeSet(
            rootName = matched.first().name.substringBefore(".7z."),
            format = ArchiveFormat.SEVEN_Z,
            parts = matched.mapIndexed { index, item ->
                VolumePart(index = index + 1, fileName = item.name, source = item)
            },
            isComplete = missing.isEmpty(),
            missingParts = missing
        )
    }

    private fun detectRarSet(selectedName: String, parts: List<ArchivePartSource>): VolumeSet? {
        val selectedLower = selectedName.lowercase()
        return when {
            selectedLower.matches(Regex(""".*\.part\d+\.rar$""")) -> detectPartRarSet(selectedLower, parts)
            selectedLower.endsWith(".rar") || selectedLower.matches(Regex(""".*\.r\d{2}$""")) -> detectLegacyRarSet(selectedLower, parts)
            else -> null
        }
    }

    private fun detectPartRarSet(selectedLower: String, parts: List<ArchivePartSource>): VolumeSet? {
        val rootName = selectedLower.substringBefore(".part")
        val matched = parts.filter {
            val name = it.name.lowercase()
            name.matches(Regex(""".*\.part\d+\.rar$""")) && name.substringBefore(".part") == rootName
        }.sortedBy { parseRarPartOrder(it.name) }
        if (matched.isEmpty()) return null

        val indexes = matched.map { parseRarPartOrder(it.name) }.sorted()
        val missing = findMissing(indexes).map { idx ->
            "${matched.first().name.substringBefore(".part")}.part${idx}.rar"
        }

        return VolumeSet(
            rootName = matched.first().name.substringBefore(".part"),
            format = ArchiveFormat.RAR,
            parts = matched.mapIndexed { index, item ->
                VolumePart(index = index + 1, fileName = item.name, source = item)
            },
            isComplete = missing.isEmpty(),
            missingParts = missing
        )
    }

    private fun detectLegacyRarSet(selectedLower: String, parts: List<ArchivePartSource>): VolumeSet? {
        val rootName = selectedLower.substringBeforeLast(".")
        val matched = parts.filter {
            val name = it.name.lowercase()
            when {
                name.endsWith(".rar") -> name.substringBeforeLast(".") == rootName
                name.matches(Regex(""".*\.r\d{2}$""")) -> name.substringBeforeLast(".") == rootName
                else -> false
            }
        }.sortedBy { parseLegacyRarOrder(it.name) }
        if (matched.isEmpty()) return null

        val indexes = matched.map { parseLegacyRarOrder(it.name) }.sorted()
        val missing = findMissing(indexes).map { idx ->
            if (idx == 1) "${matched.first().name.substringBeforeLast(".")}.rar"
            else "${matched.first().name.substringBeforeLast(".")}.r${(idx - 2).toString().padStart(2, '0')}"
        }

        return VolumeSet(
            rootName = matched.first().name.substringBeforeLast("."),
            format = ArchiveFormat.RAR,
            parts = matched.mapIndexed { index, item ->
                VolumePart(index = index + 1, fileName = item.name, source = item)
            },
            isComplete = missing.isEmpty(),
            missingParts = missing
        )
    }

    private fun detectZipSet(selectedLower: String, parts: List<ArchivePartSource>): VolumeSet? {
        return when {
            selectedLower.matches(Regex(""".*\.zip\.\d{3}$""")) -> detectSplitZipSet(selectedLower, parts)
            selectedLower.matches(Regex(""".*\.z\d{2}$""")) || selectedLower.endsWith(".zip") -> detectLegacyZipSet(selectedLower, parts)
            else -> null
        }
    }

    private fun detectSplitZipSet(selectedLower: String, parts: List<ArchivePartSource>): VolumeSet? {
        val rootName = selectedLower.substringBefore(".zip.")
        val matched = parts.filter {
            val name = it.name.lowercase()
            name.matches(Regex(""".*\.zip\.\d{3}$""")) && name.substringBefore(".zip.") == rootName
        }.sortedBy { parseSplitZipOrder(it.name) }
        if (matched.isEmpty()) return null

        val indexes = matched.map { parseSplitZipOrder(it.name) }.sorted()
        val missing = findMissing(indexes).map { idx ->
            "${matched.first().name.substringBefore(".zip.")}.zip.${idx.toString().padStart(3, '0')}"
        }
        return VolumeSet(
            rootName = matched.first().name.substringBefore(".zip."),
            format = ArchiveFormat.ZIP,
            parts = matched.mapIndexed { index, item ->
                VolumePart(index = index + 1, fileName = item.name, source = item)
            },
            isComplete = missing.isEmpty(),
            missingParts = missing
        )
    }

    private fun detectLegacyZipSet(selectedLower: String, parts: List<ArchivePartSource>): VolumeSet? {
        val rootName = selectedLower.substringBeforeLast(".")
        val matched = parts.filter {
            val name = it.name.lowercase()
            when {
                name.endsWith(".zip") -> name.substringBeforeLast(".") == rootName
                name.matches(Regex(""".*\.z\d{2}$""")) -> name.substringBeforeLast(".") == rootName
                else -> false
            }
        }.sortedBy { parseLegacyZipOrder(it.name) }
        if (matched.isEmpty()) return null

        val indexes = matched.map { parseLegacyZipOrder(it.name) }.sorted()
        val missing = findMissing(indexes).map { idx ->
            if (idx == 1) "${matched.first().name.substringBeforeLast(".")}.zip"
            else "${matched.first().name.substringBeforeLast(".")}.z${(idx - 1).toString().padStart(2, '0')}"
        }
        return VolumeSet(
            rootName = matched.first().name.substringBeforeLast("."),
            format = ArchiveFormat.ZIP,
            parts = matched.mapIndexed { index, item ->
                VolumePart(index = index + 1, fileName = item.name, source = item)
            },
            isComplete = missing.isEmpty(),
            missingParts = missing
        )
    }

    private fun parseLastNumber(name: String): Int {
        return Regex("""(\d+)(?!.*\d)""").find(name)?.groupValues?.get(1)?.toIntOrNull() ?: Int.MAX_VALUE
    }

    private fun parseRarPartOrder(name: String): Int {
        return Regex(""".*\.part(\d+)\.rar$""")
            .find(name.lowercase())?.groupValues?.get(1)?.toIntOrNull() ?: Int.MAX_VALUE
    }

    private fun parseLegacyRarOrder(name: String): Int {
        val lower = name.lowercase()
        if (lower.endsWith(".rar")) return 1
        Regex(""".*\.r(\d{2})$""").find(lower)?.groupValues?.get(1)?.toIntOrNull()?.let { return it + 2 }
        return Int.MAX_VALUE
    }

    private fun parseSplitZipOrder(name: String): Int {
        return Regex(""".*\.zip\.(\d{3})$""")
            .find(name.lowercase())?.groupValues?.get(1)?.toIntOrNull() ?: Int.MAX_VALUE
    }

    private fun parseLegacyZipOrder(name: String): Int {
        val lower = name.lowercase()
        if (lower.endsWith(".zip")) return 1
        Regex(""".*\.z(\d{2})$""").find(lower)?.groupValues?.get(1)?.toIntOrNull()?.let { return it + 1 }
        return Int.MAX_VALUE
    }

    private fun findMissing(indexes: List<Int>): List<Int> {
        if (indexes.isEmpty()) return emptyList()
        val missing = mutableListOf<Int>()
        for (i in indexes.first()..indexes.last()) {
            if (i !in indexes) missing += i
        }
        return missing
    }
}
