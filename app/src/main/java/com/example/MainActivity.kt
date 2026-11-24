package com.example

import android.content.Context
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CastConnected
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.CastConnected
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailDefaults
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.model.RemoteSessionState
import com.example.ui.components.AccessAuthorizationDialog
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.PermissionsAndSetupScreen
import com.example.ui.screens.RemotePlayerScreen
import com.example.ui.theme.AirLinkTheme
import com.example.ui.viewmodel.RemoteControlViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.launch

const val ROUTE_DASHBOARD = "dashboard"
const val ROUTE_REMOTE_PLAYER = "remote_player"
const val ROUTE_SETUP = "setup"

private data class NavDestination(
    val route: String,
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

private val TOP_DESTINATIONS = listOf(
    NavDestination(
        route = ROUTE_DASHBOARD,
        labelRes = R.string.nav_dashboard,
        selectedIcon = Icons.Filled.Dashboard,
        unselectedIcon = Icons.Outlined.Dashboard
    ),
    NavDestination(
        route = ROUTE_REMOTE_PLAYER,
        labelRes = R.string.nav_remote_player,
        selectedIcon = Icons.Filled.CastConnected,
        unselectedIcon = Icons.Outlined.CastConnected
    ),
    NavDestination(
        route = ROUTE_SETUP,
        labelRes = R.string.nav_setup,
        selectedIcon = Icons.Filled.Settings,
        unselectedIcon = Icons.Outlined.Settings
    )
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AirLinkTheme {
                MainRootContainer()
            }
        }
    }
}

@Composable
fun MainRootContainer() {
    var currentUser by remember { mutableStateOf(Firebase.auth.currentUser) }
    val viewModel: RemoteControlViewModel = viewModel()

    if (currentUser == null) {
        AuthScreen(
            onGoogleSignInCompleted = { idToken ->
                viewModel.signInWithGoogle(idToken) { success ->
                    if (success) {
                        currentUser = Firebase.auth.currentUser
                    }
                }
            }
        )
    } else {
        key(currentUser!!.uid) {
            AirLinkApp(viewModel = viewModel, onSignOut = {
                Firebase.auth.signOut()
                currentUser = null
            })
        }
    }
}

@Composable
fun AirLinkApp(viewModel: RemoteControlViewModel, onSignOut: () -> Unit) {
    val context = LocalContext.current
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val deviceConfig by viewModel.deviceConfig.collectAsStateWithLifecycle()
    val signalingStatus by viewModel.signalingStatus.collectAsStateWithLifecycle()
    val sessionState by viewModel.sessionState.collectAsStateWithLifecycle()
    val incomingCall by viewModel.incomingCall.collectAsStateWithLifecycle()
    val recentDevices by viewModel.recentDevices.collectAsStateWithLifecycle()
    val targetIdInput by viewModel.targetIdInput.collectAsStateWithLifecycle()
    val targetPinInput by viewModel.targetPinInput.collectAsStateWithLifecycle()
    val targetPlatform by viewModel.targetPlatform.collectAsStateWithLifecycle()
    val isAccessibilityEnabled by viewModel.isAccessibilityEnabled.collectAsStateWithLifecycle()
    val isScreenCaptureActive by viewModel.isScreenCaptureActive.collectAsStateWithLifecycle()
    val remoteVideoTrack by viewModel.remoteVideoTrack.collectAsStateWithLifecycle()
    val telemetry by viewModel.telemetry.collectAsStateWithLifecycle()
    val inputControlMode by viewModel.inputControlMode.collectAsStateWithLifecycle()
    val bannerMessage by viewModel.statusBannerMessage.collectAsStateWithLifecycle()

    val mediaProjectionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        viewModel.onMediaProjectionResult(result.resultCode, result.data)
    }

    LaunchedEffect(sessionState) {
        when (sessionState) {
            is RemoteSessionState.AwaitingMediaProjection -> {
                val mgr = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                mediaProjectionLauncher.launch(mgr.createScreenCaptureIntent())
            }
            is RemoteSessionState.ConnectingPeer,
            is RemoteSessionState.Active -> {
                if (navController.currentDestination?.route != ROUTE_REMOTE_PLAYER) {
                    navController.navigate(ROUTE_REMOTE_PLAYER) {
                        launchSingleTop = true
                    }
                }
            }
            else -> Unit
        }
    }

    LaunchedEffect(bannerMessage) {
        val message = bannerMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        viewModel.clearBannerMessage()
    }

    incomingCall?.let { call ->
        AccessAuthorizationDialog(
            request = call,
            onAllow = { viewModel.approveIncomingCallAndRequestProjection() },
            onDeny = { viewModel.denyIncomingCall() }
        )
    }

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: ROUTE_DASHBOARD
    val isFullScreenStream = currentRoute == ROUTE_REMOTE_PLAYER &&
        sessionState is RemoteSessionState.Active &&
        !(sessionState as RemoteSessionState.Active).isHostSharingScreen

    fun navigateTopLevel(route: String) {
        navController.navigate(route) {
            popUpTo(ROUTE_DASHBOARD) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useNavigationRail = maxWidth >= 600.dp

        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            contentWindowInsets = if (isFullScreenStream) WindowInsets(0, 0, 0, 0) else WindowInsets.statusBars,
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            bottomBar = {
                if (!useNavigationRail && !isFullScreenStream) {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface,
                        windowInsets = NavigationBarDefaults.windowInsets
                    ) {
                        TOP_DESTINATIONS.forEach { dest ->
                            val selected = currentRoute == dest.route
                            NavigationBarItem(
                                selected = selected,
                                onClick = { navigateTopLevel(dest.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                        contentDescription = stringResource(dest.labelRes)
                                    )
                                },
                                label = { Text(stringResource(dest.labelRes)) },
                                modifier = Modifier.testTag("nav_${dest.route}")
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useNavigationRail && !isFullScreenStream) {
                    NavigationRail(
                        containerColor = MaterialTheme.colorScheme.surface,
                        windowInsets = NavigationRailDefaults.windowInsets
                    ) {
                        TOP_DESTINATIONS.forEach { dest ->
                            val selected = currentRoute == dest.route
                            NavigationRailItem(
                                selected = selected,
                                onClick = { navigateTopLevel(dest.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                        contentDescription = stringResource(dest.labelRes)
                                    )
                                },
                                label = { Text(stringResource(dest.labelRes)) },
                                modifier = Modifier.testTag("rail_${dest.route}")
                            )
                        }
                    }
                }

                NavHost(
                    navController = navController,
                    startDestination = ROUTE_DASHBOARD,
                    modifier = Modifier.weight(1f)
                ) {
                    composable(ROUTE_DASHBOARD) {
                        DashboardScreen(
                            deviceConfig = deviceConfig,
                            signalingStatus = signalingStatus,
                            sessionState = sessionState,
                            isAccessibilityEnabled = isAccessibilityEnabled,
                            isScreenCaptureActive = isScreenCaptureActive,
                            targetIdInput = targetIdInput,
                            targetPinInput = targetPinInput,
                            targetPlatform = targetPlatform,
                            recentDevices = recentDevices,
                            onTargetIdChange = viewModel::updateTargetIdInput,
                            onTargetPinChange = viewModel::updateTargetPinInput,
                            onTargetPlatformChange = viewModel::updateTargetPlatform,
                            onConnectClick = viewModel::initiateRemoteConnection,
                            onRegeneratePin = viewModel::regeneratePin,
                            onSelectRecentDevice = viewModel::selectRecentDevice,
                            onToggleFavorite = viewModel::toggleFavoriteDevice,
                            onDeleteRecentDevice = viewModel::deleteDeviceHistory,
                            onOpenSetup = { navigateTopLevel(ROUTE_SETUP) },
                            onNotifyCopied = { msg ->
                                scope.launch { snackbarHostState.showSnackbar(msg) }
                            }
                        )
                    }

                    composable(ROUTE_REMOTE_PLAYER) {
                        RemotePlayerScreen(
                            sessionState = sessionState,
                            remoteVideoTrack = remoteVideoTrack,
                            eglBaseContext = viewModel.eglBaseContext,
                            telemetry = telemetry,
                            currentQuality = deviceConfig.streamQuality,
                            inputControlMode = inputControlMode,
                            isAccessibilityEnabled = isAccessibilityEnabled,
                            onSendInput = viewModel::sendInputPacket,
                            onQualityChange = viewModel::setStreamQuality,
                            onInputModeToggle = viewModel::setInputControlMode,
                            onDisconnect = viewModel::disconnectSession,
                            onGoToDashboard = { navigateTopLevel(ROUTE_DASHBOARD) }
                        )
                    }

                    composable(ROUTE_SETUP) {
                        PermissionsAndSetupScreen(
                            deviceConfig = deviceConfig,
                            signalingStatus = signalingStatus,
                            isAccessibilityEnabled = isAccessibilityEnabled,
                            onConnectSignaling = { url -> viewModel.connectToSignaling(url) },
                            onQualityChange = viewModel::setStreamQuality,
                            onRequirePinApprovalChange = viewModel::setRequirePinApproval
                        )
                    }
                }
            }
        }
    }
}
