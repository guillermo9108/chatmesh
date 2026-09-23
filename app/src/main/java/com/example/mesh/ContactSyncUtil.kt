package com.example.mesh

import android.content.Context
import android.database.Cursor
import android.provider.ContactsContract
import com.example.data.entity.ContactEntity
import com.example.data.repository.ChatMeshRepository

object ContactSyncUtil {

    suspend fun syncDeviceContacts(
        context: Context,
        repository: ChatMeshRepository,
        myPhoneNumber: String
    ) {
        val contactsList = mutableListOf<ContactEntity>()

        try {
            val cursor: Cursor? = context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                    ContactsContract.CommonDataKinds.Phone.NUMBER,
                    ContactsContract.CommonDataKinds.Phone.PHOTO_THUMBNAIL_URI
                ),
                null,
                null,
                "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} ASC"
            )

            cursor?.use {
                val nameIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val numberIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                val photoIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.PHOTO_THUMBNAIL_URI)

                val seenNumbers = mutableSetOf<String>()

                while (it.moveToNext()) {
                    val name = if (nameIndex >= 0) it.getString(nameIndex) ?: "Contacto" else "Contacto"
                    val numberRaw = if (numberIndex >= 0) it.getString(numberIndex) ?: "" else ""
                    val photoUri = if (photoIndex >= 0) it.getString(photoIndex) else null

                    if (numberRaw.isNotBlank()) {
                        val sanitized = SimDetectionUtil.sanitizePhoneNumber(numberRaw)
                        if (sanitized != myPhoneNumber && !seenNumbers.contains(sanitized)) {
                            seenNumbers.add(sanitized)
                            contactsList.add(
                                ContactEntity(
                                    phoneNumber = sanitized,
                                    displayName = name,
                                    avatarUri = photoUri,
                                    isRegisteredInMesh = false, // starts unregistered until mesh peer announced
                                    isConnected = false,
                                    lastSeen = 0L,
                                    statusText = "Toca invitar para unirse a ChatMesh"
                                )
                            )
                        }
                    }
                }
            }
        } catch (_: SecurityException) {
            // Permission not yet granted, fallback default mock contacts for immediate preview
        }

        // If no contacts were found on device (e.g. freshly created emulator), populate sample contacts
        if (contactsList.isEmpty()) {
            contactsList.addAll(getDefaultContacts(myPhoneNumber))
        }

        for (contact in contactsList) {
            val existing = repository.getContact(contact.phoneNumber)
            if (existing == null) {
                repository.insertContact(contact)
            }
        }
    }

    private fun getDefaultContacts(myPhoneNumber: String): List<ContactEntity> {
        val sample = listOf(
            ContactEntity(
                phoneNumber = "+5352345678",
                displayName = "Alejandro Morales",
                isRegisteredInMesh = true,
                isConnected = true,
                lastSeen = System.currentTimeMillis(),
                lastMessageText = "¡Hola! Conectado a la malla WiFi Direct.",
                lastMessageTime = System.currentTimeMillis() - 1000 * 60 * 5,
                unreadCount = 1,
                statusText = "En línea vía WiFi Aware",
                meshNodeId = "NODE_ALEJANDRO"
            ),
            ContactEntity(
                phoneNumber = "+5353456789",
                displayName = "Claudia Rodríguez",
                isRegisteredInMesh = true,
                isConnected = true,
                lastSeen = System.currentTimeMillis(),
                lastMessageText = "¿Hacemos una llamada de audio P2P?",
                lastMessageTime = System.currentTimeMillis() - 1000 * 60 * 30,
                unreadCount = 0,
                statusText = "Disponible en la malla",
                meshNodeId = "NODE_CLAUDIA"
            ),
            ContactEntity(
                phoneNumber = "+5354567890",
                displayName = "Carlos Pérez",
                isRegisteredInMesh = true,
                isConnected = false,
                lastSeen = System.currentTimeMillis() - 1000 * 60 * 120,
                lastMessageText = "Te dejé los archivos del proyecto.",
                lastMessageTime = System.currentTimeMillis() - 1000 * 60 * 120,
                unreadCount = 0,
                statusText = "Desconectado (store & forward activo)",
                meshNodeId = "NODE_CARLOS"
            ),
            ContactEntity(
                phoneNumber = "+5355678901",
                displayName = "Dra. Elena Gómez",
                isRegisteredInMesh = false,
                isConnected = false,
                lastSeen = 0L,
                statusText = "No registrado en la malla WiFi"
            ),
            ContactEntity(
                phoneNumber = "+5356789012",
                displayName = "Roberto Sánchez",
                isRegisteredInMesh = false,
                isConnected = false,
                lastSeen = 0L,
                statusText = "No registrado en la malla WiFi"
            ),
            ContactEntity(
                phoneNumber = "+5357890123",
                displayName = "Valeria Díaz",
                isRegisteredInMesh = true,
                isConnected = true,
                lastSeen = System.currentTimeMillis(),
                lastMessageText = "¡La red en malla sin Internet funciona perfecto!",
                lastMessageTime = System.currentTimeMillis() - 1000 * 60 * 3,
                unreadCount = 0,
                statusText = "Transmitiendo paquetes mesh...",
                meshNodeId = "NODE_VALERIA"
            )
        )
        return sample.filter { it.phoneNumber != myPhoneNumber }
    }
}
