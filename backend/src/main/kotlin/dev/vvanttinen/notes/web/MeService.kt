package dev.vvanttinen.notes.web

import dev.vvanttinen.notes.user.CurrentUser
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class MeService(
    private val currentUser: CurrentUser,
) {
    fun me(): MeResponse =
        MeResponse(userId = currentUser.currentUserId())
}

data class MeResponse(
    val userId: UUID,
)
