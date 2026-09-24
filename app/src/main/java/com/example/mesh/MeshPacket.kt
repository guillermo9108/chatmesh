package com.example.mesh

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class MeshPacket(
    val packetType: String, // "HANDSHAKE", "CHAT_MESSAGE", "ACK", "ROUTING_ANNOUNCE", "STATUS_UPDATE", "CALL_SIGNAL"
    val packetUuid: String = UUID.randomUUID().toString(),
    val sourceNodeId: String,
    val sourcePhone: String,
    val sourceName: String,
    val sourceSsid: String,
    val destinationPhone: String, // or "BROADCAST"
    val content: String = "",
    val mediaType: String = "TEXT", // "TEXT", "IMAGE", "AUDIO", "FILE"
    val mediaData: String? = null,
    val audioDuration: Int = 0,
    val hopCount: Int = 0,
    val maxHops: Int = 8,
    val visitedNodeIds: List<String> = emptyList(),
    val timestamp: Long = System.currentTimeMillis(),
    val statusType: String? = null, // "TYPING", "RECORDING", "ONLINE", "IDLE"
    val callSignalType: String? = null, // "OFFER", "ANSWER", "HANGUP", "REJECT"
    val callIsVideo: Boolean = false,
    val chunkIndex: Int = 0,
    val totalChunks: Int = 1,
    val nodeLoad: Int = 0,
    val signalDbm: Int = 0,
    val priority: Int = 1 // 0 = URGENT (ACK/CALL), 1 = NORMAL (CHAT/STATUS), 2 = BULK (IMAGE_CHUNK)
) {
    fun toJson(): String {
        val obj = JSONObject()
        obj.put("packetType", packetType)
        obj.put("packetUuid", packetUuid)
        obj.put("sourceNodeId", sourceNodeId)
        obj.put("sourcePhone", sourcePhone)
        obj.put("sourceName", sourceName)
        obj.put("sourceSsid", sourceSsid)
        obj.put("destinationPhone", destinationPhone)
        obj.put("content", content)
        obj.put("mediaType", mediaType)
        if (mediaData != null) obj.put("mediaData", mediaData)
        obj.put("audioDuration", audioDuration)
        obj.put("hopCount", hopCount)
        obj.put("maxHops", maxHops)
        val arr = JSONArray()
        visitedNodeIds.forEach { arr.put(it) }
        obj.put("visitedNodeIds", arr)
        obj.put("timestamp", timestamp)
        if (statusType != null) obj.put("statusType", statusType)
        if (callSignalType != null) obj.put("callSignalType", callSignalType)
        obj.put("callIsVideo", callIsVideo)
        obj.put("chunkIndex", chunkIndex)
        obj.put("totalChunks", totalChunks)
        obj.put("nodeLoad", nodeLoad)
        obj.put("signalDbm", signalDbm)
        obj.put("priority", priority)
        return obj.toString()
    }

    companion object {
        fun fromJson(jsonStr: String): MeshPacket? {
            return try {
                val obj = JSONObject(jsonStr)
                val visited = mutableListOf<String>()
                val arr = obj.optJSONArray("visitedNodeIds")
                if (arr != null) {
                    for (i in 0 until arr.length()) {
                        visited.add(arr.getString(i))
                    }
                }
                MeshPacket(
                    packetType = obj.getString("packetType"),
                    packetUuid = obj.optString("packetUuid", UUID.randomUUID().toString()),
                    sourceNodeId = obj.getString("sourceNodeId"),
                    sourcePhone = obj.getString("sourcePhone"),
                    sourceName = obj.optString("sourceName", "Nodo"),
                    sourceSsid = obj.optString("sourceSsid", ""),
                    destinationPhone = obj.getString("destinationPhone"),
                    content = obj.optString("content", ""),
                    mediaType = obj.optString("mediaType", "TEXT"),
                    mediaData = if (obj.has("mediaData")) obj.getString("mediaData") else null,
                    audioDuration = obj.optInt("audioDuration", 0),
                    hopCount = obj.optInt("hopCount", 0),
                    maxHops = obj.optInt("maxHops", 8),
                    visitedNodeIds = visited,
                    timestamp = obj.optLong("timestamp", System.currentTimeMillis()),
                    statusType = if (obj.has("statusType")) obj.getString("statusType") else null,
                    callSignalType = if (obj.has("callSignalType")) obj.getString("callSignalType") else null,
                    callIsVideo = obj.optBoolean("callIsVideo", false),
                    chunkIndex = obj.optInt("chunkIndex", 0),
                    totalChunks = obj.optInt("totalChunks", 1),
                    nodeLoad = obj.optInt("nodeLoad", 0),
                    signalDbm = obj.optInt("signalDbm", 0),
                    priority = obj.optInt("priority", 1)
                )
            } catch (_: Exception) {
                null
            }
        }
    }
}
