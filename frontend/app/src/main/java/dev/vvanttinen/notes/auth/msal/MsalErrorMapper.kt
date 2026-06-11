package dev.vvanttinen.notes.auth.msal

import com.microsoft.identity.client.exception.MsalClientException
import com.microsoft.identity.client.exception.MsalException
import com.microsoft.identity.client.exception.MsalServiceException
import com.microsoft.identity.client.exception.MsalUiRequiredException
import dev.vvanttinen.notes.auth.AuthErrorCategory

internal fun MsalException.toAuthErrorCategory(): AuthErrorCategory = when (this) {
    is MsalUiRequiredException -> AuthErrorCategory.InteractionRequired
    is MsalServiceException -> toAuthErrorCategory()
    is MsalClientException -> toAuthErrorCategory()
    else -> AuthErrorCategory.Unknown
}

private fun MsalServiceException.toAuthErrorCategory(): AuthErrorCategory = when (errorCode) {
    MsalServiceException.ACCESS_DENIED -> AuthErrorCategory.AccessDenied
    MsalServiceException.INVALID_REQUEST,
    MsalServiceException.INVALID_SCOPE,
    MsalServiceException.UNAUTHORIZED_CLIENT,
    MsalServiceException.INVALID_INSTANCE -> AuthErrorCategory.Configuration
    MsalServiceException.REQUEST_TIMEOUT,
    MsalServiceException.SERVICE_NOT_AVAILABLE -> AuthErrorCategory.Network
    else -> AuthErrorCategory.Service
}

private fun MsalClientException.toAuthErrorCategory(): AuthErrorCategory = when (errorCode) {
    MsalClientException.DEVICE_NETWORK_NOT_AVAILABLE -> AuthErrorCategory.Network
    MsalClientException.APP_MANIFEST_VALIDATION_ERROR,
    MsalClientException.AUTHORITY_VALIDATION_NOT_SUPPORTED,
    MsalClientException.JSON_PARSE_FAILURE,
    MsalClientException.MALFORMED_URL,
    MsalClientException.MULTIPLE_APPS_LISTENING_CUSTOM_URL_SCHEME,
    MsalClientException.REDIRECT_URI_VALIDATION_ERROR,
    MsalClientException.SCOPE_EMPTY_OR_NULL,
    MsalClientException.STATE_MISMATCH,
    MsalClientException.UNKNOWN_AUTHORITY -> AuthErrorCategory.Configuration
    else -> AuthErrorCategory.Client
}
