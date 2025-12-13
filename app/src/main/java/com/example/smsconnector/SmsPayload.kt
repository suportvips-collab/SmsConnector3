package com.example.smsconnector

import com.google.gson.annotations.SerializedName

/**
 * Data Transfer Object (DTO) para enviar os dados do SMS para a API.
 * As anotações @SerializedName garantem que os nomes no JSON (snake_case)
 * correspondam corretamente às propriedades do Kotlin (camelCase).
 */
data class SmsPayload(
    @SerializedName("license_key") val licenseKey: String,
    @SerializedName("target_email") val targetEmail: String,
    @SerializedName("sms_sender") val smsSender: String,
    @SerializedName("sms_body") val smsBody: String,
    @SerializedName("timestamp") val timestamp: String
)
