package com.example.mesh

data class SimCardInfo(
    val slotIndex: Int = 0,
    val carrierName: String = "",
    val countryIso: String = "",
    val phoneNumber: String? = null,
    val isNumberReadFromSim: Boolean = false,
    val isSimPresent: Boolean = false,
    val simStateDescription: String = "Desconocido"
)
