package com.example.mesh

import android.content.Context
import android.provider.ContactsContract
import com.example.data.entity.ContactEntity
import com.example.data.repository.ChatMeshRepository

object ContactSyncUtil {
    suspend fun syncDeviceContacts(
        context: Context,
        repository: ChatMeshRepository,
        myPhoneNumber: String
    ): Int {
        var count = 0
        try {
            val cursor = context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                    ContactsContract.CommonDataKinds.Phone.NUMBER,
                    ContactsContract.CommonDataKinds.Phone.PHOTO_URI
                ),
                null,
                null,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC"
            )

            cursor?.use {
                val nameIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val numberIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                val photoIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.PHOTO_URI)

                val contactsToInsert = mutableListOf<ContactEntity>()
                val seenNumbers = mutableSetOf<String>()

                while (it.moveToNext()) {
                    val rawNumber = if (numberIndex >= 0) it.getString(numberIndex) else null
                    if (!rawNumber.isNullOrBlank()) {
                        val sanitized = SimDetectionUtil.sanitizePhoneNumber(rawNumber)
                        if (sanitized != myPhoneNumber && seenNumbers.add(sanitized)) {
                            val name = if (nameIndex >= 0) it.getString(nameIndex) ?: sanitized else sanitized
                            val photo = if (photoIndex >= 0) it.getString(photoIndex) else null
                            contactsToInsert.add(
                                ContactEntity(
                                    phoneNumber = sanitized,
                                    displayName = name,
                                    avatarUri = photo,
                                    isRegisteredInMesh = false
                                )
                            )
                        }
                    }
                }
                if (contactsToInsert.isNotEmpty()) {
                    repository.insertAllContacts(contactsToInsert)
                    count = contactsToInsert.size
                }
            }
        } catch (_: Exception) {
        }
        return count
    }
}
