package models

import com.danielrampelt.mapify.Mapper

interface BasicUser {
    val id: Long
    val email: String
}

data class NetworkUser(
    override val id: Long = 0,
    val firstName: String = "",
    val lastName: String = "",
    override val email: String = "",
    val verificationStatus: String = "unverified"
): BasicUser {
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

@Mapper
fun BasicUser.mapToDomain() = DomainUser()

fun NetworkUser.normalMapToDomain() = DomainUser(
    isVerified = verificationStatus == "verified"
)
