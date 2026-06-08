package dev.vvanttinen.notes.auth

import java.net.URI
import java.security.MessageDigest
import java.util.Locale

object AccountKeyDeriver {
    private const val VERSION_PREFIX = "v1:"

    fun derive(authority: String, accountId: String): String {
        val normalizedAuthority = normalizeAuthority(authority)
        val normalizedAccountId = accountId.trim()
        require(normalizedAuthority.isNotBlank()) { "Authority is required for account key derivation." }
        require(normalizedAccountId.isNotBlank()) { "Account ID is required for account key derivation." }

        val canonicalInput = "$normalizedAuthority\n$normalizedAccountId"
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(canonicalInput.toByteArray(Charsets.UTF_8))
            .joinToString(separator = "") { byte -> "%02x".format(byte) }
        return "$VERSION_PREFIX$digest"
    }

    internal fun normalizeAuthority(authority: String): String {
        val trimmed = authority.trim().trimEnd('/')
        return runCatching {
            val uri = URI(trimmed)
            val scheme = uri.scheme?.lowercase(Locale.ROOT) ?: return@runCatching trimmed.lowercase(Locale.ROOT)
            val host = uri.host?.lowercase(Locale.ROOT) ?: return@runCatching trimmed.lowercase(Locale.ROOT)
            val port = if (uri.port == -1) "" else ":${uri.port}"
            val path = (uri.rawPath ?: "").trimEnd('/')
            "$scheme://$host$port$path"
        }.getOrElse {
            trimmed.lowercase(Locale.ROOT)
        }
    }
}
