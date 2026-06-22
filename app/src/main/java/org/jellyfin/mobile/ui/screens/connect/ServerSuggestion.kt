package org.jellyfin.mobile.ui.screens.connect

data class ServerSuggestion(
    val type: Type,
    val name: String,
    val address: String,
    /**
     * A timestamp for this suggestion, used for sorting.
     * For discovered servers, this should be the discovery time,
     * for saved servers, this should be the last used time.
     */
    val timestamp: Long,
    /**
     * Whether this server was added as an IP4P address.
     */
    val isIp4p: Boolean = false,
    /**
     * Whether this server was added as an IP2P address.
     */
    val isIp2p: Boolean = false,
) {
    enum class Type {
        DISCOVERED,
        SAVED,
    }
}
