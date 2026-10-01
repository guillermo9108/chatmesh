    /** Guarda el número ingresado manualmente por el usuario. */
    fun saveManualPhoneNumber(context: Context, phoneNumber: String) {
        val clean = sanitizePhoneNumber(phoneNumber)
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_USER_PHONE, clean)
            .putString(KEY_PHONE_SOURCE, "MANUAL")
            .putBoolean(KEY_IS_CONFIRMED, true)
            .apply()
    }

    /** ¿El usuario ya tiene un número guardado (de SIM, manual o API)? */
    fun hasAnyPhoneNumber(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val phone = prefs.getString(KEY_USER_PHONE, null)
        return !phone.isNullOrBlank()
    }