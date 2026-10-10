package pl.kserocki.gravityjar.data

import androidx.compose.runtime.staticCompositionLocalOf

/** The signed-in shopper, provided once near the top of the tree. */
data class AccountData(
    val userId: String,
    val username: String,
    val profilePicture: String?,
)

val LocalAccountData = staticCompositionLocalOf<AccountData?> { null }
