package io.cosgriff.waitlight.domain

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream

/**
 * A drive-time table of one cell, 10 degrees on a side, centred on [at], with [minutes] to each station (255 for
 * none; stations left out have none). In the layout `scripts/drive_times.py` writes.
 */
internal fun driveTableAround(at: GeoPoint, minutes: Map<Int, Int>): DriveTable {
    val bytes = ByteArrayOutputStream()
    DataOutputStream(bytes).apply {
        write("WLDT".toByteArray())
        writeByte(2)
        writeInt((at.latitude * 1e6).toInt())
        writeInt((at.longitude * 1e6).toInt())
        writeInt(10_000_000)
        writeInt(10_000_000)
        writeInt(1)
        writeInt(1)
        writeInt(Stations.all.size)
        Stations.all.forEach { writeInt(it.id) }
        writeInt(0)
        writeInt(1)
        Stations.all.forEach { writeByte(minutes[it.id] ?: 255) }
    }
    return DriveTable.parse(bytes.toByteArray())
}
