package dev.vvanttinen.notes.web

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api")
class MeController(
    private val meService: MeService,
) {
    @GetMapping("/me")
    fun me(): MeResponse =
        meService.me()
}
