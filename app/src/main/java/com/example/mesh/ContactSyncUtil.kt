package com.example.mesh

import android.content.Context
import android.database.Cursor
import android.provider.ContactsContract
import com.example.data.entity.ContactEntity
import com.example.data.repository.ChatMeshRepository

object ContactSyncUtil {

    /**
     * Reads REAL device contacts from the Android ContactsContract.
     * No mock or fake contacts are generated.
     */
    suspend fun syncDeviceContacts(
        context: Context,
        repository: ChatMeshRepository,
        myPhoneNumber: String
    ): Int {
        var importedCount = 0
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
                            val existing = repository.getContact(sanitized)
                            if (existing == null) {
                                repository.insertContact(
                                    ContactEntity(
                                        phoneNumber = sanitized,
                                        displayName = name,
                                        avatarUri = photoUri,
                                        isRegisteredInMesh = false,
                                        isConnected = false,
                                        lastSeen = 0L,
                                        statusText = "Contacto del teléfono"
                                    )
                                )
                                importedCount++
                            }
                        }
                    }
                }
            }
        } catch (_: SecurityException) {
            // Permission not yet granted
        }
        return importedCount
    }
}
