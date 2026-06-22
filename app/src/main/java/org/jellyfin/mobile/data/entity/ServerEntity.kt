package org.jellyfin.mobile.data.entity

import android.os.Parcelable
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.parcelize.Parcelize
import org.jellyfin.mobile.data.entity.ServerEntity.Key.HOSTNAME
import org.jellyfin.mobile.data.entity.ServerEntity.Key.TABLE_NAME

@Parcelize
@Entity(tableName = TABLE_NAME, indices = [Index(value = arrayOf(HOSTNAME), unique = true)])
data class ServerEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = ID)
    val id: Long,
    @ColumnInfo(name = HOSTNAME)
    val hostname: String,
    @ColumnInfo(name = LAST_USED_TIMESTAMP)
    val lastUsedTimestamp: Long,
    @ColumnInfo(name = IS_IP4P, defaultValue = "0")
    val isIp4p: Boolean = false,
    @ColumnInfo(name = IS_IP2P, defaultValue = "0")
    val isIp2p: Boolean = false,
) : Parcelable {
    constructor(hostname: String, isIp4p: Boolean = false, isIp2p: Boolean = false) : this(
        id = 0,
        hostname = hostname,
        lastUsedTimestamp = System.currentTimeMillis(),
        isIp4p = isIp4p,
        isIp2p = isIp2p,
    )

    companion object Key {
        const val TABLE_NAME = "server"
        const val ID = "id"
        const val HOSTNAME = "hostname"
        const val LAST_USED_TIMESTAMP = "last_used_timestamp"
        const val IS_IP4P = "is_ip4p"
        const val IS_IP2P = "is_ip2p"
    }
}
