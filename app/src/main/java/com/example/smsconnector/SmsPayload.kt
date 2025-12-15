package com.example.smsconnector

import com.google.gson.annotations.SerializedName

/**
 * Data Transfer Object (DTO) para enviar os dados do SMS para a API.
 * As anotações @SerializedName garantem que os nomes no JSON (snake_case)
 * correspondam corretamente às propriedades do Kotlin (camelCase).
 *
 * Estrutura baseada no README.md:
 * {
 *   "license_key": "STRING",
 *   "device_id": "STRING",
 *   "sms_content": "STRING",
 *   "sender_number": "STRING"
 * }
 */
data class SmsPayload(
    @SerializedName("license_key") val licenseKey: String,
    @SerializedName("device_id") val deviceId: String,
    @SerializedName("sms_content") val smsContent: String,
    @SerializedName("sender_number") val senderNumber: String
)
