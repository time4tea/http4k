package org.http4k.security.oauth.server

import org.http4k.core.Uri
import org.http4k.security.ResponseType
import org.http4k.security.ResponseType.Code
import org.http4k.security.openid.RequestJwtContainer
import org.http4k.security.openid.RequestJwts

data class AuthRequest(
    val client: ClientId,
    val scopes: List<String>,
    val redirectUri: Uri,
    val state: String?,
    val responseType: ResponseType = Code,
    val nonce: String? = null,
    val request: RequestJwtContainer? = null) {

    fun isOIDC() = scopes.map { it.toLowerCase() }.contains(OIDC_SCOPE)

    companion object {

        const val OIDC_SCOPE = "openid"

    }

}

