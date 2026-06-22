package org.jellyfin.mobile.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jellyfin.mobile.data.dao.ServerDao
import org.jellyfin.mobile.data.dao.UserDao
import org.jellyfin.mobile.data.entity.ServerEntity
import org.jellyfin.mobile.data.entity.ServerUser
import org.jellyfin.mobile.data.entity.UserEntity
import org.jellyfin.mobile.utils.Ip2pDns
import org.jellyfin.mobile.utils.Ip2pResolver
import org.jellyfin.mobile.utils.Ip2pResult
import org.jellyfin.mobile.utils.Ip4pParser
import org.jellyfin.mobile.utils.Ip4pResolver
import org.jellyfin.mobile.utils.Ip4pResult
import org.jellyfin.sdk.Jellyfin
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.model.DeviceInfo
import timber.log.Timber
import java.util.UUID

class ApiClientController(
    private val appPreferences: AppPreferences,
    private val jellyfin: Jellyfin,
    private val apiClient: ApiClient,
    private val serverDao: ServerDao,
    private val userDao: UserDao,
) {
    private val baseDeviceInfo: DeviceInfo
        get() = jellyfin.options.deviceInfo!!

    /**
     * Store server with [hostname] in the database.
     * When [isIp4p] is true, the hostname is an IP4P address or a domain whose AAAA
     * record contains an IP4P address. It will be resolved to an IPv4:port URL before
     * being set as the API base URL, while the original hostname is kept in storage.
     * When [isIp2p] is true, the hostname is an IP2P domain — dual A-record resolution
     * returns a domain-based URL (TLS-friendly) with DNS override for the real IP.
     */
    suspend fun setupServer(hostname: String, isIp4p: Boolean = false, isIp2p: Boolean = false) {
        appPreferences.currentServerId = withContext(Dispatchers.IO) {
            serverDao.getServerByHostname(hostname)?.id
                ?: serverDao.insert(hostname, isIp4p, isIp2p)
        }
        // Resolve to a connectable URL before setting the API base URL
        val baseUrl = when {
            isIp2p -> resolveIp2pUrl(hostname)
            isIp4p -> resolveIp4pUrl(hostname)
            else -> null
        } ?: hostname
        apiClient.update(baseUrl = baseUrl)
    }

    suspend fun setupUser(serverId: Long, userId: UUID, accessToken: String) {
        appPreferences.currentUserId = withContext(Dispatchers.IO) {
            userDao.upsert(serverId, userId, accessToken)
        }
        configureApiClientUser(userId, accessToken)
    }

    suspend fun loadSavedServer(): ServerEntity? {
        val server = withContext(Dispatchers.IO) {
            val serverId = appPreferences.currentServerId ?: return@withContext null
            serverDao.getServer(serverId)
        }
        configureApiClientServer(server)
        return server
    }

    suspend fun loadSavedUser(): UserEntity? = withContext(Dispatchers.IO) {
        val userId = appPreferences.currentUserId ?: return@withContext null
        userDao.getUser(userId)
    }

    suspend fun loadSavedServerUser(): ServerUser? {
        val serverUser = withContext(Dispatchers.IO) {
            val serverId = appPreferences.currentServerId ?: return@withContext null
            val userId = appPreferences.currentUserId ?: return@withContext null
            userDao.getServerUser(serverId, userId)
        }

        configureApiClientServer(serverUser?.server)

        if (serverUser?.user?.accessToken != null) {
            configureApiClientUser(serverUser.user.userId, serverUser.user.accessToken)
        } else {
            resetApiClientUser()
        }

        return serverUser
    }

    suspend fun loadPreviouslyUsedServers(): List<ServerEntity> = withContext(Dispatchers.IO) {
        serverDao.getAllServers().filterNot { server ->
            server.id == appPreferences.currentServerId
        }
    }

    private suspend fun configureApiClientServer(server: ServerEntity?) {
        if (server == null) return
        val baseUrl = when {
            server.isIp2p -> {
                // Re-resolve IP2P on each load — DNS may have changed
                resolveIp2pUrl(server.hostname) ?: server.hostname
            }
            server.isIp4p -> {
                // Re-resolve IP4P on each load — the NAT mapping may have changed
                resolveIp4pUrl(server.hostname) ?: server.hostname
            }
            else -> server.hostname
        }
        apiClient.update(baseUrl = baseUrl)
    }

    private fun configureApiClientUser(userId: UUID, accessToken: String) {
        apiClient.update(
            accessToken = accessToken,
            // Append user id to device id to ensure uniqueness across sessions
            deviceInfo = baseDeviceInfo.copy(id = baseDeviceInfo.id + userId),
        )
    }

    private fun resetApiClientUser() {
        apiClient.update(
            accessToken = null,
            deviceInfo = baseDeviceInfo,
        )
    }

    fun getApiClient(server: Long, user: Long): ApiClient {
        val serverUser = userDao.getServerUser(server, user) ?: error("Invalid server user combination (server=$server, user=$user)")

        val baseUrl = when {
            serverUser.server.isIp2p -> {
                // IP2P uses domain-based URL — DnsOverride handles IP mapping
                serverUser.server.hostname
            }
            serverUser.server.isIp4p -> {
                Ip4pParser.toUrl(serverUser.server.hostname) ?: serverUser.server.hostname
            }
            else -> serverUser.server.hostname
        }

        return jellyfin.createApi(
            baseUrl = baseUrl,
            accessToken = serverUser.user.accessToken,
            deviceInfo = baseDeviceInfo.copy(id = baseDeviceInfo.id + serverUser.user.userId),
        )
    }

    /**
     * Resolve an IP2P hostname via dual A-record DNS and return a domain-based URL.
     * The real IP is registered with [Ip2pDns] for OkHttp DNS override.
     */
    private suspend fun resolveIp2pUrl(hostname: String): String? {
        val result = Ip2pResolver.resolveToUrl(hostname)
        return (result as? Ip2pResult.Success)?.url
    }

    /**
     * Resolve an IP4P hostname (raw IP4P address or domain with IP4P AAAA record)
     * to an HTTP URL. Returns null if the hostname cannot be resolved as IP4P.
     */
    private suspend fun resolveIp4pUrl(hostname: String): String? {
        val result = Ip4pResolver.resolveToUrl(hostname)
        return (result as? Ip4pResult.Success)?.url
    }
}
