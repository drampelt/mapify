
import models.BasicUser
import models.NetworkUser
import models.mapToDomain
import models.normalMapToDomain
import kotlin.test.Test
import kotlin.test.assertEquals

class Tests {
    private val networkUser = NetworkUser(123, "Bob", "Smith", "bob@smith.com", "unverified")
    private val basicUser: BasicUser = networkUser

    @Test
    fun normalMap() {
        val domainUser = networkUser.normalMapToDomain()
        println("Domain: $domainUser")
        assertEquals(null, domainUser.id)
        assertEquals("", domainUser.name)
        assertEquals("", domainUser.email)
        assertEquals(false, domainUser.isVerified)
    }

    @Test
    fun mapifyMap() {
        val domainUser = networkUser.mapToDomain()
        println("Domain: $domainUser")
        assertEquals(null, domainUser.id)
        assertEquals("testing", domainUser.name)
        assertEquals("bob@smith.com", domainUser.email)
        assertEquals(false, domainUser.isVerified)
    }

    @Test
    fun mapifyMapInterfaceSource() {
        val domainUser = basicUser.mapToDomain()
        println("Domain: $domainUser")
        assertEquals(null, domainUser.id)
        assertEquals("", domainUser.name)
        assertEquals("bob@smith.com", domainUser.email)
        assertEquals(false, domainUser.isVerified)
    }
}
