package org.jellyfin.mobile.ui.screens.connect

import android.view.KeyEvent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.ListItem
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Surface
import androidx.compose.material.Switch
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.jellyfin.mobile.R
import org.jellyfin.mobile.app.ApiClientController
import org.jellyfin.mobile.setup.ConnectionHelper
import org.jellyfin.mobile.ui.state.CheckUrlState
import org.jellyfin.mobile.ui.state.ServerSelectionMode
import org.jellyfin.mobile.ui.utils.CenterRow
import org.jellyfin.mobile.utils.Ip4pParser
import org.jellyfin.mobile.utils.Ip4pResolver
import org.jellyfin.mobile.utils.Ip4pResult
import org.koin.compose.koinInject

@OptIn(ExperimentalComposeUiApi::class)
@Suppress("LongMethod")
@Composable
fun ServerSelection(
    showExternalConnectionError: Boolean,
    apiClientController: ApiClientController = koinInject(),
    connectionHelper: ConnectionHelper = koinInject(),
    onConnected: suspend (String, isIp4p: Boolean) -> Unit,
) {
    val coroutineScope = rememberCoroutineScope()
    val keyboardController = LocalSoftwareKeyboardController.current
    var serverSelectionMode by remember { mutableStateOf(ServerSelectionMode.ADDRESS) }
    var hostname by remember { mutableStateOf("") }
    val serverSuggestions = remember { mutableStateListOf<ServerSuggestion>() }
    var checkUrlState by remember<MutableState<CheckUrlState>> { mutableStateOf(CheckUrlState.Unchecked) }
    var externalError by remember { mutableStateOf(showExternalConnectionError) }
    var isIp4pMode by remember { mutableStateOf(false) }
    var ip4pHttps by remember { mutableStateOf(false) }

    // Capture IP4P error strings in composable scope for use in onSubmit
    val ip4pErrorInvalidFormat = stringResource(R.string.ip4p_error_invalid_format)
    val ip4pErrorDnsTimeout = stringResource(R.string.ip4p_error_dns_timeout)
    val ip4pErrorNoRecord = stringResource(R.string.ip4p_error_no_record)
    val ip4pErrorDnsFailed = stringResource(R.string.ip4p_error_dns_failed)

    // Preview decoded IP4P address while typing (instant, no network)
    val ip4pPreview = if (isIp4pMode) Ip4pParser.parse(hostname) else null

    // Prefill currently selected server if available
    LaunchedEffect(Unit) {
        val server = apiClientController.loadSavedServer()
        if (server != null) {
            hostname = server.hostname
            isIp4pMode = server.isIp4p
        }
    }

    LaunchedEffect(Unit) {
        // Suggest saved servers
        apiClientController.loadPreviouslyUsedServers().mapTo(serverSuggestions) { server ->
            ServerSuggestion(
                type = ServerSuggestion.Type.SAVED,
                name = server.hostname,
                address = server.hostname,
                timestamp = server.lastUsedTimestamp,
                isIp4p = server.isIp4p,
            )
        }

        // Prepend discovered servers to suggestions
        connectionHelper.discoverServersAsFlow().collect { serverInfo ->
            serverSuggestions.removeIf { existing -> existing.address == serverInfo.address }
            serverSuggestions.add(
                index = 0,
                ServerSuggestion(
                    type = ServerSuggestion.Type.DISCOVERED,
                    name = serverInfo.name,
                    address = serverInfo.address,
                    timestamp = System.currentTimeMillis(),
                ),
            )
        }
    }

    fun onSubmit() {
        externalError = false
        if (isIp4pMode) {
            // IP4P mode: resolve the IP4P address to an IPv4:port URL, then pass
            // the ORIGINAL hostname (not the decoded URL) to onConnected so the
            // domain/IP4P address is stored for re-resolution on reconnect.
            checkUrlState = CheckUrlState.Pending
            coroutineScope.launch {
                when (val result = Ip4pResolver.resolveToUrl(hostname, ip4pHttps)) {
                    is Ip4pResult.Success -> onConnected(hostname, true)
                    is Ip4pResult.InvalidFormat -> checkUrlState = CheckUrlState.Error(ip4pErrorInvalidFormat)
                    is Ip4pResult.DnsTimeout -> checkUrlState = CheckUrlState.Error(ip4pErrorDnsTimeout)
                    is Ip4pResult.NoIp4pRecord -> checkUrlState = CheckUrlState.Error(ip4pErrorNoRecord)
                    is Ip4pResult.DnsError -> checkUrlState = CheckUrlState.Error(ip4pErrorDnsFailed)
                }
            }
        } else {
            // Normal mode: use SDK discovery to find the server
            checkUrlState = CheckUrlState.Pending
            coroutineScope.launch {
                val state = connectionHelper.checkServerUrl(hostname)
                checkUrlState = state
                if (state is CheckUrlState.Success) {
                    onConnected(state.address, false)
                }
            }
        }
    }

    Column {
        Text(
            text = stringResource(R.string.connect_to_server_title),
            modifier = Modifier.padding(bottom = 8.dp),
            style = MaterialTheme.typography.h5,
        )
        Crossfade(
            targetState = serverSelectionMode,
            label = "Server selection mode",
        ) { selectionType ->
            when (selectionType) {
                ServerSelectionMode.ADDRESS -> AddressSelection(
                    text = hostname,
                    errorText = when {
                        externalError -> stringResource(R.string.connection_error_cannot_connect)
                        else -> (checkUrlState as? CheckUrlState.Error)?.message
                    },
                    loading = checkUrlState is CheckUrlState.Pending,
                    isIp4pMode = isIp4pMode,
                    ip4pHttps = ip4pHttps,
                    ip4pPreview = ip4pPreview,
                    onIp4pModeChange = {
                        isIp4pMode = it
                        externalError = false
                        checkUrlState = CheckUrlState.Unchecked
                    },
                    onIp4pHttpsChange = { ip4pHttps = it },
                    onTextChange = { value ->
                        externalError = false
                        checkUrlState = CheckUrlState.Unchecked
                        hostname = value
                    },
                    onDiscoveryClick = {
                        externalError = false
                        keyboardController?.hide()
                        serverSelectionMode = ServerSelectionMode.AUTO_DISCOVERY
                    },
                    onSubmit = {
                        onSubmit()
                    },
                )
                ServerSelectionMode.AUTO_DISCOVERY -> ServerDiscoveryList(
                    serverSuggestions = serverSuggestions,
                    onGoBack = {
                        serverSelectionMode = ServerSelectionMode.ADDRESS
                    },
                    onSelectServer = { url, isIp4p ->
                        hostname = url
                        isIp4pMode = isIp4p
                        serverSelectionMode = ServerSelectionMode.ADDRESS
                        onSubmit()
                    },
                )
            }
        }
    }
}

@Stable
@Composable
private fun AddressSelection(
    text: String,
    errorText: String?,
    loading: Boolean,
    isIp4pMode: Boolean,
    ip4pHttps: Boolean,
    ip4pPreview: Ip4pParser.Ip4pData?,
    onIp4pModeChange: (Boolean) -> Unit,
    onIp4pHttpsChange: (Boolean) -> Unit,
    onTextChange: (String) -> Unit,
    onDiscoveryClick: () -> Unit,
    onSubmit: () -> Unit,
) {
    Column {
        ServerUrlField(
            text = text,
            errorText = errorText,
            onTextChange = onTextChange,
            onSubmit = onSubmit,
        )
        AnimatedErrorText(errorText = errorText)
        Ip4pToggle(
            checked = isIp4pMode,
            onCheckedChange = onIp4pModeChange,
        )
        if (isIp4pMode) {
            HttpsToggle(
                checked = ip4pHttps,
                onCheckedChange = onIp4pHttpsChange,
            )
        }
        Ip4pPreview(preview = ip4pPreview)
        if (!loading) {
            Spacer(modifier = Modifier.height(12.dp))
            StyledTextButton(
                text = stringResource(R.string.connect_button_text),
                enabled = text.isNotBlank(),
                onClick = onSubmit,
            )
            StyledTextButton(
                text = stringResource(R.string.choose_server_button_text),
                onClick = onDiscoveryClick,
            )
        } else {
            CenterRow {
                CircularProgressIndicator(modifier = Modifier.padding(top = 16.dp, bottom = 8.dp))
            }
        }
    }
}

@Stable
@Composable
private fun ServerUrlField(
    text: String,
    errorText: String?,
    onTextChange: (String) -> Unit,
    onSubmit: () -> Unit,
) {
    OutlinedTextField(
        value = text,
        onValueChange = onTextChange,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
            .onKeyEvent { keyEvent ->
                when (keyEvent.nativeKeyEvent.keyCode) {
                    KeyEvent.KEYCODE_ENTER -> {
                        onSubmit()
                        true
                    }
                    else -> false
                }
            },
        label = {
            Text(text = stringResource(R.string.host_input_hint))
        },
        isError = errorText != null,
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Uri,
            imeAction = ImeAction.Go,
        ),
        keyboardActions = KeyboardActions(
            onGo = {
                onSubmit()
            },
        ),
        singleLine = true,
    )
}

@Stable
@Composable
private fun AnimatedErrorText(
    errorText: String?,
) {
    AnimatedVisibility(
        visible = errorText != null,
        exit = ExitTransition.None,
    ) {
        Text(
            text = errorText.orEmpty(),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            color = MaterialTheme.colors.error,
            style = MaterialTheme.typography.caption,
        )
    }
}

@Stable
@Composable
private fun Ip4pToggle(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
    ) {
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.padding(end = 8.dp),
        )
        Text(
            text = stringResource(R.string.ip4p_toggle_label),
            style = MaterialTheme.typography.body2,
            color = MaterialTheme.colors.onSurface,
        )
    }
}

@Stable
@Composable
private fun HttpsToggle(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, top = 2.dp, bottom = 2.dp),
    ) {
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.padding(end = 8.dp),
        )
        Text(
            text = "HTTPS",
            style = MaterialTheme.typography.body2,
            color = MaterialTheme.colors.onSurface.copy(alpha = 0.7f),
        )
    }
}

@Stable
@Composable
private fun Ip4pPreview(preview: Ip4pParser.Ip4pData?) {
    if (preview == null) return
    AnimatedVisibility(visible = true, exit = ExitTransition.None) {
        Text(
            text = "→ ${preview.ipv4}:${preview.port}",
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 2.dp),
            color = MaterialTheme.colors.primary,
            style = MaterialTheme.typography.body2,
        )
    }
}

@Stable
@Composable
private fun ServerDiscoveryList(
    serverSuggestions: SnapshotStateList<ServerSuggestion>,
    onGoBack: () -> Unit,
    onSelectServer: (String, Boolean) -> Unit,
) {
    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onGoBack) {
                Icon(imageVector = Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = null)
            }
            Text(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp),
                text = stringResource(R.string.available_servers_title),
            )
            CircularProgressIndicator(
                modifier = Modifier
                    .padding(horizontal = 12.dp)
                    .size(24.dp),
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        LazyColumn(
            modifier = Modifier
                .padding(bottom = 16.dp)
                .fillMaxSize()
                .background(
                    color = MaterialTheme.colors.surface,
                    shape = MaterialTheme.shapes.medium,
                ),
        ) {
            items(serverSuggestions) { server ->
                ServerDiscoveryItem(
                    serverSuggestion = server,
                    onClickServer = {
                        onSelectServer(server.address, server.isIp4p)
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterialApi::class)
@Stable
@Composable
private fun ServerDiscoveryItem(
    serverSuggestion: ServerSuggestion,
    onClickServer: () -> Unit,
) {
    ListItem(
        modifier = Modifier
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClickServer),
        text = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = serverSuggestion.name)
                if (serverSuggestion.isIp4p) {
                    Surface(
                        color = MaterialTheme.colors.primary.copy(alpha = 0.15f),
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.padding(start = 8.dp),
                    ) {
                        Text(
                            text = "IP4P",
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            color = MaterialTheme.colors.primary,
                            style = MaterialTheme.typography.caption,
                        )
                    }
                }
            }
        },
        secondaryText = {
            Text(text = serverSuggestion.address)
        },
    )
}
