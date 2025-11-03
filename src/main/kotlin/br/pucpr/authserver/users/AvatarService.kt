package br.pucpr.authserver.users

import br.pucpr.authserver.exception.UnsupportedMediaTypeException
import br.pucpr.authserver.files.FileStorage
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.web.client.RestTemplateBuilder
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.client.RestTemplate
import org.springframework.web.multipart.MultipartFile
import java.io.ByteArrayInputStream
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

@Service
class AvatarService(
    @param:Qualifier("fileStorage") val storage: FileStorage
) {

    private val restTemplate: RestTemplate = RestTemplateBuilder().build()

    fun save(user: User, avatar: MultipartFile): String =
        try {
            val contentType = avatar.contentType!!
            val extension = when (contentType) {
                "image/jpeg" -> "jpg"
                "image/png" -> "png"
                else -> throw UnsupportedMediaTypeException("jpg", "png")
            }
            val name = "avatar.${user.id}.$extension"
            val path = "$FOLDER/$name"
            storage.save(user, path, avatar)
            name
        } catch (e: Exception) {
            log.error("Error saving avatar for user ${user.id}. Using default.", e)
            DEFAULT_AVATAR
        }

    fun load(name: String) = storage.load(name)

    fun urlFor(path: String) = storage.urlFor("$FOLDER/$path")

    fun generateFor(user: User): String {
        return try {
            val bytes = fetchGravatar(user.email) ?: fetchUiAvatar(user.name)
            val multipart = bytesToMultipart(bytes!!, "avatar.${user.id}.png")
            save(user, multipart)
        } catch (e: Exception) {
            log.error("Error generating avatar for user ${user.id}: ${e.message}")
            DEFAULT_AVATAR
        }
    }

    private fun fetchGravatar(email: String): ByteArray? {
        return try {
            val hash = email.trim().lowercase().toSHA256()
            val url = "https://gravatar.com/avatar/$hash?d=404"

            val response = restTemplate.exchange(url, HttpMethod.GET, null, ByteArray::class.java)
            if (response.statusCode == HttpStatus.OK) {
                response.body
            } else {
                null
            }
        } catch (e: Exception) {
            log.error("Unexpected error fetching Gravatar: ${e.message}")
            null
        }
    }

    private fun fetchUiAvatar(name: String): ByteArray? {
        return try {
            val encodedName = URLEncoder.encode(name, StandardCharsets.UTF_8)
            val url = "https://ui-avatars.com/api/?name=$encodedName&background=random&format=png"

            val response = restTemplate.exchange(url, HttpMethod.GET, null, ByteArray::class.java)
            if (response.statusCode == HttpStatus.OK) {
                response.body
            } else {
                null
            }
        } catch (e: Exception) {
            log.warn("Failed to fetch UI Avatar for $name: ${e.message}")
            null
        }
    }

    private fun String.toSHA256(): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(this.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }


    private fun bytesToMultipart(bytes: ByteArray, filename: String): MultipartFile {
        return object : MultipartFile {
            override fun getName() = filename
            override fun getOriginalFilename() = filename
            override fun getContentType() = "image/png"
            override fun isEmpty() : Boolean = bytes.isEmpty()
            override fun getSize() = bytes.size.toLong()
            override fun getBytes() = bytes
            override fun getInputStream() = ByteArrayInputStream(bytes)
            override fun transferTo(dest: java.io.File) = dest.writeBytes(bytes)
        }
    }


    companion object {
        const val FOLDER = "avatars"
        const val DEFAULT_AVATAR = "avatar_default.jpg"
        private val log = LoggerFactory.getLogger(AvatarService::class.java)
    }
}
