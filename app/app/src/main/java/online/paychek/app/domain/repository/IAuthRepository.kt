package online.paychek.app.domain.repository

import online.paychek.app.data.remote.dto.LinkedCredentialsResponse
import online.paychek.app.data.remote.dto.LinkCredentialOtpRequest
import online.paychek.app.data.remote.dto.VerifyLinkCredentialRequest
import retrofit2.Response

/**
 * IAuthRepository — Domain interface for authentication and credential operations.
 *
 * Implementation: data/repository/CredentialRepository (concrete class, unchanged)
 *
 * Why interface:
 *  - Decouples UI/ViewModel from EncryptedSharedPreferences and Retrofit details.
 *  - Enables testing credential flows with fakes without touching real storage.
 *  - Future: allows adding biometric-gate or PIN-gate at the domain layer.
 *
 * Note: Methods that return raw Retrofit Response<ResponseBody> are kept as-is
 *       to maintain backward compatibility. Future refactoring can wrap them in Result<T>.
 */
interface IAuthRepository {

    /**
     * Returns all credentials linked to the current user account.
     */
    suspend fun getLinkedCredentials(): Result<LinkedCredentialsResponse>

    /**
     * Initiates OTP delivery for linking a new payment credential.
     * @param request  Contains provider type and phone/email target
     */
    suspend fun sendLinkOtp(request: LinkCredentialOtpRequest): Response<okhttp3.ResponseBody>

    /**
     * Verifies OTP and finalizes linking of a payment credential.
     * @param request  Contains OTP code and credential details
     */
    suspend fun verifyLinkCredential(request: VerifyLinkCredentialRequest): Response<okhttp3.ResponseBody>

    /**
     * Removes a linked credential by ID after PIN verification.
     * @param id   Credential database ID from getLinkedCredentials()
     * @param pin  User's security PIN (never stored, verified server-side)
     */
    suspend fun removeCredential(id: Int, pin: String): Response<okhttp3.ResponseBody>
}
