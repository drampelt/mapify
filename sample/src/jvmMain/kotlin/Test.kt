import models.NetworkUser
import models.mapToDomain
import models.normalMapToDomain

fun main() {
    val network = NetworkUser(123, "Bob", "Smith", "bob@example.com", "pending")
    println("Network model $network")
    val domainNormal = network.normalMapToDomain()
    val domain = network.mapToDomain()
    println("Domain model (normal) $domainNormal")
    println("Domain model (mapper) $domain")
}