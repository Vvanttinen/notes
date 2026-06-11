package dev.vvanttinen.notes.auth.msal

import java.net.URI

internal fun String.isConfiguredTenantAuthority(): Boolean {
    val uri = runCatching { URI(this) }.getOrNull() ?: return false
    val normalizedPath = uri.path.orEmpty().trim('/')
    return uri.scheme.equals("https", ignoreCase = true) &&
        !uri.host.isNullOrBlank() &&
        normalizedPath.isNotBlank() &&
        !normalizedPath.endsWith("v2.0", ignoreCase = true)
}
