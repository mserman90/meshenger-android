/*
 * Copyright (C) 2026 Meshenger Contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package d.d.meshenger.disaster

import org.json.JSONObject
import java.io.Serializable

data class DisasterSignal(
    val senderName: String,
    val status: StatusType,
    val latitude: Double?,
    val longitude: Double?,
    val medicalNotes: String,
    val ipAddress: String,
    val timestamp: Long = System.currentTimeMillis()
) : Serializable {

    enum class StatusType {
        SAFE,      // Güvendeyim
        HELP,      // Yardım İstiyorum / Enkaz Altındayım
        MEDICAL    // Tıbbi Destek Lazım
    }

    fun toJSON(): JSONObject {
        val json = JSONObject()
        json.put("name", senderName)
        json.put("status", status.name)
        json.put("lat", latitude ?: 0.0)
        json.put("lng", longitude ?: 0.0)
        json.put("notes", medicalNotes)
        json.put("ip", ipAddress)
        json.put("ts", timestamp)
        return json
    }

    companion object {
        fun fromJSON(jsonStr: String): DisasterSignal? {
            return try {
                val json = JSONObject(jsonStr)
                DisasterSignal(
                    senderName = json.optString("name", "Bilinmeyen Afetzede"),
                    status = try { StatusType.valueOf(json.optString("status", "SAFE")) } catch (_: Exception) { StatusType.SAFE },
                    latitude = if (json.has("lat") && json.getDouble("lat") != 0.0) json.getDouble("lat") else null,
                    longitude = if (json.has("lng") && json.getDouble("lng") != 0.0) json.getDouble("lng") else null,
                    medicalNotes = json.optString("notes", ""),
                    ipAddress = json.optString("ip", ""),
                    timestamp = json.optLong("ts", System.currentTimeMillis())
                )
            } catch (_: Exception) {
                null
            }
        }
    }
}
