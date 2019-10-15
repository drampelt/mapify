
import models.NetworkUser
import models.mapToDomain
import models.normalMapToDomain
import kotlin.test.Test
import kotlin.test.assertEquals

class Tests {
    private val networkUser = NetworkUser(123, "Bob", "Smith", "bob@smith.com", "unverified")

    @Test
    fun normalMap() {
        val domainUser = networkUser.normalMapToDomain()
        println("Domain: $domainUser")
        assertEquals(domainUser.id, null)
        assertEquals(domainUser.name, "")
        assertEquals(domainUser.email, "")
        assertEquals(domainUser.isVerified, false)
    }

    @Test
    fun mapifyMap() {
        val domainUser = networkUser.mapToDomain()
        println("Domain: $domainUser")
        assertEquals(domainUser.id, null)
        assertEquals(domainUser.name, "testing")
        assertEquals(domainUser.email, "bob@smith.com")
        assertEquals(domainUser.isVerified, false)
    }
}