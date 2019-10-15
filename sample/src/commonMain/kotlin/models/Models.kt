package models

import com.danielrampelt.mapify.Mapper

data class NetworkUser(
    val id: Long = 0,
    val firstName: String = "",
    val lastName: String = "",
    val email: String = "",
    val verificationStatus: String = "unverified"
) {
    fun getName(): String = "testing"
}

data class DomainUser(
    val id: Long? = null,
    val name: String = "",
    val email: String = "",
    val isVerified: Boolean = false
)

@Mapper
fun NetworkUser.mapToDomain() = DomainUser(
    isVerified = verificationStatus == "verified"
)

fun NetworkUser.normalMapToDomain() = DomainUser(
    isVerified = verificationStatus == "verified"
)
