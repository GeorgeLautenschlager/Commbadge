package com.combadge.app.model

import com.google.gson.annotations.SerializedName

data class HailMessage(
    @SerializedName("type") val type: String = "hail",
    @SerializedName("sessionId") val sessionId: String,
    @SerializedName("from") val from: String,
    /** UDP port on the CALLER's side where they receive incoming audio */
    @SerializedName("callerAudioPort") val callerAudioPort: Int = 0,
    /** The raw phrase the caller spoke, e.g. "George to Violet" */
    @SerializedName("phrase") val phrase: String? = null
)

data class AcceptMessage(
    @SerializedName("type") val type: String = "accept",
    @SerializedName("sessionId") val sessionId: String,
    /** UDP port on the RECEIVER's side where they receive incoming audio */
    @SerializedName("audioPort") val audioPort: Int
)

data class CloseMessage(
    @SerializedName("type") val type: String = "close",
    @SerializedName("sessionId") val sessionId: String
)

/** Generic envelope for deserializing any message type */
data class RawMessage(
    @SerializedName("type") val type: String = "",
    @SerializedName("sessionId") val sessionId: String = "",
    @SerializedName("from") val from: String? = null,
    @SerializedName("audioPort") val audioPort: Int = 0,
    @SerializedName("callerAudioPort") val callerAudioPort: Int = 0,
    @SerializedName("phrase") val phrase: String? = null
)
