package org.jellyfin.mobile.utils

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.net.Inet6Address
import java.net.InetAddress
import java.net.UnknownHostException
import timber.log.Timber
import kotlin.time.Duration.Companion.seconds

/**
 * DNS-based IP4P address resolver.
 *
 * Resolves a hostname via DNS and checks whether any of its IPv6 (AAAA) addresses
 * conform to the IP4P format. If found, the address is decoded into an IPv4:port pair.
 *
 * This enables the standard IP4P workflow: natmap updates a DNS AAAA record with the
 * IP4P-encoded address, and the client resolves the domain to discover the real endpoint.
 */
object Ip4pResolver {

    /** DNS resolution timeout. */
    private val DNS_TIMEOUT = 5.seconds

    /**
     * Resolves an IP4P hostname (raw IP4P address or domain with IP4P AAAA record)
     * to a connectable HTTP URL.
     *
     * Tries raw [Ip4pParser] parsing first (instant), then falls back to DNS AAAA
     * resolution with a [DNS_TIMEOUT].
     *
     * @return [Ip4pResult.Success] if resolution succeeds, or a specific error type.
     */
    suspend fun resolveToUrl(hostname: String, https: Boolean = false): Ip4pResult {
        // Try raw IP4P address first (instant, no network)
        Ip4pParser.toUrl(hostname, https)?.let { url ->
            val data = Ip4pParser.parse(hostname)!!
            Timber.d("IP4P raw parse: $hostname → $url")
            return Ip4pResult.Success(data.ipv4, data.port, url)
        }

        // For domain names, try DNS AAAA resolution
        // But first check if it even looks like a domain (not an IP address)
        if (Ip4pParser.isIp4p(hostname)) {
            // It looks like IP4P format but parse failed → invalid format
            return Ip4pResult.InvalidFormat
        }

        return resolveViaDns(hostname, https)
    }

    /**
     * Resolves [hostname] via DNS and checks AAAA results for IP4P-encoded addresses.
     */
    suspend fun resolve(hostname: String): Ip4pParser.Ip4pData? = withContext(Dispatchers.IO) {
        // Skip DNS resolution if the input already looks like an IP4P address
        if (Ip4pParser.isIp4p(hostname)) {
            return@withContext Ip4pParser.parse(hostname)
        }

        try {
            val addresses = withTimeoutOrNull(DNS_TIMEOUT) {
                InetAddress.getAllByName(hostname)
            }

            if (addresses == null) {
                Timber.w("DNS timeout for $hostname")
                return@withContext null
            }

            Timber.d("Resolved $hostname → ${addresses.map { it.hostAddress }}")

            addresses
                .filterIsInstance<Inet6Address>()
                .map { it.hostAddress?.lowercase() }
                .firstNotNullOfOrNull { addr ->
                    if (addr != null) {
                        val data = Ip4pParser.parse(addr)
                        if (data != null) {
                            Timber.i("Found IP4P AAAA record for $hostname: $addr → ${data.ipv4}:${data.port}")
                        }
                        data
                    } else null
                }
        } catch (e: UnknownHostException) {
            Timber.d(e, "DNS resolution failed for $hostname")
            null
        }
    }

    /**
     * DNS AAAA resolution with timeout and detailed error reporting.
     */
    private suspend fun resolveViaDns(hostname: String, https: Boolean = false): Ip4pResult = withContext(Dispatchers.IO) {
        val scheme = if (https) "https" else "http"
        try {
            val addresses = withTimeoutOrNull(DNS_TIMEOUT) {
                InetAddress.getAllByName(hostname)
            }

            if (addresses == null) {
                Timber.w("DNS timeout resolving $hostname")
                return@withContext Ip4pResult.DnsTimeout
            }

            Timber.d("Resolved $hostname → ${addresses.map { it.hostAddress }}")

            val ip4pAddr = addresses
                .filterIsInstance<Inet6Address>()
                .map { it.hostAddress?.lowercase() }
                .firstNotNullOfOrNull { addr ->
                    if (addr != null) Ip4pParser.parse(addr) else null
                }

            if (ip4pAddr != null) {
                val url = "$scheme://${ip4pAddr.ipv4}:${ip4pAddr.port}"
                Timber.i("IP4P DNS resolved: $hostname → $url")
                Ip4pResult.Success(ip4pAddr.ipv4, ip4pAddr.port, url)
            } else {
                Timber.w("No IP4P AAAA record found for $hostname")
                Ip4pResult.NoIp4pRecord
            }
        } catch (e: UnknownHostException) {
            Timber.d(e, "DNS resolution failed for $hostname")
            Ip4pResult.DnsError(e.message)
        } catch (e: Exception) {
            Timber.e(e, "Unexpected error resolving $hostname")
            Ip4pResult.DnsError(e.message)
        }
    }
}
