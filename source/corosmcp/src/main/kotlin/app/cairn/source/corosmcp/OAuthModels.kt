package app.cairn.source.corosmcp

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ServerMetadata(
    val issuer: String,
    @SerialName("authorization_endpoint") val authorizationEndpoint: String,
    @SerialName("token_endpoint") val tokenEndpoint: String,
    @SerialName("registration_endpoint") val registrationEndpoint: String? = null,
    @SerialName("revocation_endpoint") val revocationEndpoint: String? = null,
    @SerialName("device_authorization_endpoint") val deviceAuthorizationEndpoint: String? = null,
    @SerialName("grant_types_supported") val grantTypesSupported: List<String> = emptyList(),
    @SerialName("code_challenge_methods_supported") val codeChallengeMethods: List<String> = emptyList(),
    @SerialName("scopes_supported") val scopesSupported: List<String> = emptyList(),
) {
    val supportsDeviceCode: Boolean
        get() = deviceAuthorizationEndpoint != null && DEVICE_GRANT in grantTypesSupported

    companion object { const val DEVICE_GRANT = "urn:ietf:params:oauth:grant-type:device_code" }
}

@Serializable
data class RegistrationRequest(
    @SerialName("client_name") val clientName: String,
    @SerialName("redirect_uris") val redirectUris: List<String>,
    @SerialName("grant_types") val grantTypes: List<String>,
    @SerialName("response_types") val responseTypes: List<String>,
    @SerialName("token_endpoint_auth_method") val tokenEndpointAuthMethod: String,
    val scope: String,
)

@Serializable
data class RegistrationResponse(@SerialName("client_id") val clientId: String)

@Serializable
data class TokenResponse(
    @SerialName("access_token") val accessToken: String,
    @SerialName("refresh_token") val refreshToken: String? = null,
    @SerialName("expires_in") val expiresIn: Long? = null,
) {
    override fun toString() = "TokenResponse(redacted)"
}

@Serializable
data class DeviceAuthResponse(
    @SerialName("device_code") val deviceCode: String,
    @SerialName("user_code") val userCode: String,
    @SerialName("verification_uri") val verificationUri: String,
    @SerialName("verification_uri_complete") val verificationUriComplete: String? = null,
    @SerialName("expires_in") val expiresIn: Long = 600,
    val interval: Long = 5,
)

@Serializable
internal data class OAuthError(val error: String = "", @SerialName("error_description") val description: String? = null)

/** State of an in-progress authorization-code flow. */
class PendingAuthorization(
    val authorizeUrl: String,
    val clientId: String,
    val redirectUri: String,
    val state: String,
    val verifier: String,
    val metadata: ServerMetadata,
)
