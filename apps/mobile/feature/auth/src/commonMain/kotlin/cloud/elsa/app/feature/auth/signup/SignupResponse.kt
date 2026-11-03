package cloud.elsa.app.feature.auth.signup

import kotlinx.serialization.Serializable

@Serializable
data class SignupResponse(
    val success: Boolean,
    val message: String? = null,
)
