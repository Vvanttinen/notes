package dev.vvanttinen.notes.support

import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.JwtException

@TestConfiguration(proxyBeanMethods = false)
class TestSecurityConfiguration {
    @Bean("testJwtDecoder")
    @Primary
    fun testJwtDecoder(): JwtDecoder =
        JwtDecoder {
            throw JwtException("Integration tests use mocked JWT principals instead of decoding live tokens.")
        }
}
