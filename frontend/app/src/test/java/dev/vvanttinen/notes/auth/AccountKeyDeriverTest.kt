package dev.vvanttinen.notes.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountKeyDeriverTest {
    @Test
    fun repeatedDerivationWithSameAuthorityAndAccountIdIsIdentical() {
        val first = AccountKeyDeriver.derive(
            authority = "https://Login.MicrosoftOnline.com/11111111-1111-1111-1111-111111111111/",
            accountId = "account-1"
        )
        val second = AccountKeyDeriver.derive(
            authority = "https://login.microsoftonline.com/11111111-1111-1111-1111-111111111111",
            accountId = "account-1"
        )

        assertEquals(first, second)
        assertTrue(first.startsWith("v1:"))
        assertEquals(67, first.length)
    }

    @Test
    fun differentAuthorityOrAccountIdProduceDifferentKeys() {
        val baseline = AccountKeyDeriver.derive(
            authority = "https://login.microsoftonline.com/11111111-1111-1111-1111-111111111111",
            accountId = "account-1"
        )
        val differentAuthority = AccountKeyDeriver.derive(
            authority = "https://login.microsoftonline.com/22222222-2222-2222-2222-222222222222",
            accountId = "account-1"
        )
        val differentAccount = AccountKeyDeriver.derive(
            authority = "https://login.microsoftonline.com/11111111-1111-1111-1111-111111111111",
            accountId = "account-2"
        )

        assertNotEquals(baseline, differentAuthority)
        assertNotEquals(baseline, differentAccount)
    }
}
