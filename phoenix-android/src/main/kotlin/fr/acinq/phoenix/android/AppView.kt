/*
 * Copyright 2020 ACINQ SAS
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package fr.acinq.phoenix.android

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.text.format.DateUtils
import androidx.activity.compose.BackHandler
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import androidx.navigation.navDeepLink
import androidx.navigation.navOptions
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.firebase.messaging.FirebaseMessaging
import fr.acinq.lightning.utils.UUID
import fr.acinq.lightning.utils.currentTimestampMillis
import fr.acinq.phoenix.PhoenixBusiness
import fr.acinq.phoenix.android.components.Button
import fr.acinq.phoenix.android.components.dialogs.Dialog
import fr.acinq.phoenix.android.components.openLink
import fr.acinq.phoenix.android.components.auth.screenlock.ScreenLockPrompt
import fr.acinq.phoenix.android.home.HomeView
import fr.acinq.phoenix.android.initwallet.create.CreateWalletView
import fr.acinq.phoenix.android.initwallet.InitWallet
import fr.acinq.phoenix.android.initwallet.restore.RestoreWalletView
import fr.acinq.phoenix.android.intro.IntroView
import fr.acinq.phoenix.android.payments.details.PaymentDetailsView
import fr.acinq.phoenix.android.payments.history.PaymentsExportView
import fr.acinq.phoenix.android.payments.history.PaymentsHistoryView
import fr.acinq.phoenix.android.payments.send.liquidity.RequestLiquidityView
import fr.acinq.phoenix.android.payments.receive.ReceiveView
import fr.acinq.phoenix.android.payments.send.SendView
import fr.acinq.phoenix.android.services.NodeServiceState
import fr.acinq.phoenix.android.settings.AboutView
import fr.acinq.phoenix.android.settings.AppAccessSettings
import fr.acinq.phoenix.android.settings.DisplayPrefsView
import fr.acinq.phoenix.android.settings.electrum.ElectrumView
import fr.acinq.phoenix.android.settings.ExperimentalView
import fr.acinq.phoenix.android.settings.ForceCloseView
import fr.acinq.phoenix.android.settings.LogsView
import fr.acinq.phoenix.android.settings.MutualCloseView
import fr.acinq.phoenix.android.settings.NotificationsView
import fr.acinq.phoenix.android.settings.PaymentSettingsView
import fr.acinq.phoenix.android.settings.ResetWallet
import fr.acinq.phoenix.android.settings.SettingsContactsView
import fr.acinq.phoenix.android.settings.SettingsView
import fr.acinq.phoenix.android.settings.TorConfigView
import fr.acinq.phoenix.android.settings.channels.ChannelDetailsView
import fr.acinq.phoenix.android.settings.channels.ChannelsView
import fr.acinq.phoenix.android.settings.channels.ImportChannelsData
import fr.acinq.phoenix.android.settings.channels.SpendFromChannelAddress
import fr.acinq.phoenix.android.settings.displayseed.DisplaySeedView
import fr.acinq.phoenix.android.settings.fees.AdvancedIncomingFeePolicy
import fr.acinq.phoenix.android.settings.fees.LiquidityPolicyView
import fr.acinq.phoenix.android.settings.walletinfo.FinalWalletInfo
import fr.acinq.phoenix.android.settings.walletinfo.FinalWalletRefundView
import fr.acinq.phoenix.android.settings.walletinfo.SendSwapInRefundView
import fr.acinq.phoenix.android.settings.walletinfo.SwapInAddresses
import fr.acinq.phoenix.android.settings.walletinfo.SwapInSignerView
import fr.acinq.phoenix.android.settings.walletinfo.SwapInWallet
import fr.acinq.phoenix.android.settings.walletinfo.WalletInfoView
import fr.acinq.phoenix.android.startup.StartupView
import fr.acinq.phoenix.android.utils.appBackground
import fr.acinq.phoenix.android.utils.datastore.PreferredBitcoinUnits
import fr.acinq.phoenix.android.utils.extensions.findActivitySafe
import fr.acinq.phoenix.android.utils.logger
import fr.acinq.phoenix.data.BitcoinUnit
import fr.acinq.phoenix.data.ExchangeRate
import fr.acinq.phoenix.data.FiatCurrency
import fr.acinq.phoenix.managers.AppConfigurationManager
import io.ktor.http.encodeURLParameter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first


@Composable
fun LoadingAppView() {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = "Initializing...")
    }
}

@Composable
fun AppView(
    business: PhoenixBusiness,
    appVM: AppViewModel,
    navController: NavHostController,
) {
    val log = logger("Navigation")
    log.debug("init app view composition")

    val context = LocalContext.current
    val isAmountInFiat = userPrefs.getIsAmountInFiat.collectAsState(false)
    val bitcoinUnits = userPrefs.getBitcoinUnits.collectAsState(initial = PreferredBitcoinUnits(primary = BitcoinUnit.Sat))
    val fiatCurrencies = userPrefs.getFiatCurrencies.collectAsState(initial = AppConfigurationManager.PreferredFiatCurrencies(primary = FiatCurrency.USD, others = emptyList()))
    val fiatRates by business.currencyManager.ratesFlow.collectAsState(emptyList())
    val fiatRatesMap by produceState(initialValue = emptyMap<FiatCurrency, ExchangeRate.BitcoinPriceRate>(), key1 = fiatRates) {
        val usdPriceRate = fiatRates.filterIsInstance<ExchangeRate.BitcoinPriceRate>().firstOrNull { it.fiatCurrency == FiatCurrency.USD }
        value = if (usdPriceRate != null) {
            fiatRates.associate { rate ->
                rate.fiatCurrency to when (rate) {
                    is ExchangeRate.BitcoinPriceRate -> rate
                    is ExchangeRate.UsdPriceRate -> ExchangeRate.BitcoinPriceRate(
                        fiatCurrency = rate.fiatCurrency,
                        price = rate.price * usdPriceRate.price,
                        source = rate.source,
                        timestampMillis = rate.timestampMillis
                    )
                }
            }
        } else {
            emptyMap()
        }
    }

    CompositionLocalProvider(
        LocalBusiness provides business,
        LocalControllerFactory provides business.controllers,
        LocalNavController provides navController,
        LocalExchangeRatesMap provides fiatRatesMap,
        LocalBitcoinUnits provides bitcoinUnits.value,
        LocalFiatCurrencies provides fiatCurrencies.value,
        LocalShowInFiat provides isAmountInFiat.value,
    ) {
        // we keep a view model storing payments so that we don't have to fetch them every time
        val paymentsViewModel = viewModel<PaymentsViewModel>(
            factory = PaymentsViewModel.Factory(business.paymentsManager,)
        )
        val noticesViewModel = viewModel<NoticesViewModel>(
            factory = NoticesViewModel.Factory(
                appConfigurationManager = business.appConfigurationManager,
                peerManager = business.peerManager,
                connectionsManager = business.connectionsManager,
            )
        )
        MonitorNotices(vm = noticesViewModel)

        val walletState by appVM.serviceState.observeAsState(null)

        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                Modifier
                    .background(appBackground())
                    .fillMaxWidth()
                    .fillMaxHeight()
                    .padding(top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding())
            ) {
                NavHost(
                    navController = navController,
                    startDestination = "${Screen.Startup.route}?next={next}",
                    enterTransition = { EnterTransition.None },
                    exitTransition = { ExitTransition.None },
                ) {
                    composable(
                        route = "${Screen.Startup.route}?next={next}",
                        arguments = listOf(
                            navArgument("next") { type = NavType.StringType; nullable = true }
                        ),
                    ) {
                        val nextScreenLink = it.arguments?.getString("next")
                        StartupView(
                            appVM = appVM,
                            onShowIntro = { navController.navigate(Screen.Intro.route) },
                            onKeyAbsent = { navController.navigate(Screen.InitWallet.route) },
                            onBusinessStarted = {
                                val next = nextScreenLink?.takeUnless { it.isBlank() }?.let { Uri.parse(it) }
                                if (next == null) {
                                    log.debug("redirecting from startup to home")
                                    navController.popToHome()
                                } else if (!navController.graph.hasDeepLink(next)) {
                                    log.debug("redirecting from startup to home, ignoring invalid next=$nextScreenLink")
                                    navController.popToHome()
                                } else {
                                    log.debug("redirecting from startup to {}", next)
                                    navController.navigate(next, navOptions = navOptions {
                                        popUpTo(navController.graph.id) { inclusive = true }
                                    })
                                }
                            }
                        )
                    }
                    composable(Screen.Intro.route) {
                        IntroView(onFinishClick = { navController.navigate(Screen.Startup.route) })
                    }
                    composable(Screen.InitWallet.route) {
                        InitWallet(
                            onCreateWalletClick = { navController.navigate(Screen.CreateWallet.route) },
                            onRestoreWalletClick = { navController.navigate(Screen.RestoreWallet.route) },
                        )
                    }
                    composable(Screen.CreateWallet.route) {
                        CreateWalletView(onSeedWritten = { navController.navigate(Screen.Startup.route) })
                    }
                    composable(Screen.RestoreWallet.route) {
                        RestoreWalletView(onRestoreDone = { navController.navigate(Screen.Startup.route) })
                    }
                    composable(Screen.Home.route) {
                        RequireStarted(walletState) {
                            HomeView(
                                paymentsViewModel = paymentsViewModel,
                                noticesViewModel = noticesViewModel,
                                onPaymentClick = { navigateToPaymentDetails(navController, id = it, isFromEvent = false) },
                                onSettingsClick = { navController.navigate(Screen.Settings.route) },
                                onReceiveClick = { navController.navigate(Screen.Receive.route) },
                                onSendClick = { navController.navigate(Screen.Send.route) },
                                onPaymentsHistoryClick = { navController.navigate(Screen.PaymentsHistory.route) },
                                onTorClick = { navController.navigate(Screen.TorConfig.route) },
                                onElectrumClick = { navController.navigate(Screen.ElectrumServer.route) },
                                onNavigateToSwapInWallet = { navController.navigate(Screen.WalletInfo.SwapInWallet.route) },
                                onNavigateToFinalWallet = { navController.navigate(Screen.WalletInfo.FinalWallet.route) },
                                onShowNotifications = { navController.navigate(Screen.Notifications.route) },
                                onRequestLiquidityClick = { navController.navigate(Screen.LiquidityRequest.route) },
                            )
                        }
                    }
                    composable(Screen.Receive.route) {
                        ReceiveView(
                            onBackClick = { navController.popBackStack() },
                            onScanDataClick = { navController.navigate("${Screen.Send.route}?openScanner=true&forceNavOnBack=true") },
                            onFeeManagementClick = { navController.navigate(Screen.LiquidityPolicy.route) },
                        )
                    }
                    composable(
                        route = "${Screen.Send.route}?input={input}&openScanner={openScanner}&forceNavOnBack={forceNavOnBack}",
                        arguments = listOf(
                            navArgument("input") { type = NavType.StringType ; nullable = true },
                            navArgument("openScanner") { type = NavType.BoolType ; defaultValue = false },
                            navArgument("forceNavOnBack") { type = NavType.BoolType ; defaultValue = false },
                        ),
                        deepLinks = listOf(
                            navDeepLink { uriPattern = "lightning:{data}" },
                            navDeepLink { uriPattern = "bitcoin:{data}" },
                            navDeepLink { uriPattern = "lnurl:{data}" },
                            navDeepLink { uriPattern = "lnurlp:{data}" },
                            navDeepLink { uriPattern = "lnurlw:{data}" },
                            navDeepLink { uriPattern = "keyauth:{data}" },
                            navDeepLink { uriPattern = "phoenix:lightning:{data}" },
                            navDeepLink { uriPattern = "phoenix:bitcoin:{data}" },
                            navDeepLink { uriPattern = "scanview:{data}" },
                        )
                    ) {
                        @Suppress("DEPRECATION")
                        val intent = try {
                            it.arguments?.getParcelable<Intent>(NavController.KEY_DEEP_LINK_INTENT)
                        } catch (e: Exception) {
                            null
                        }
                        // prevents forwarding an internal deeplink intent coming from androidx-navigation framework.
                        // TODO properly parse deeplinks following f0ae90444a23cc17d6d7407dfe43c0c8d20e62fc
                        val isIntentFromNavigation = intent?.dataString?.contains("androidx.navigation") ?: true
                        log.debug("isIntentFromNavigation=$isIntentFromNavigation")
                        val input = if (isIntentFromNavigation) {
                            it.arguments?.getString("input")
                        } else {
                            intent?.data?.toString()?.substringAfter("scanview:")
                        }
                        RequireStarted(walletState, nextUri = "scanview:${intent?.data?.toString()}") {
                            log.info("navigating to send-payment with input=$input")
                            SendView(
                                initialInput = input,
                                fromDeepLink = !isIntentFromNavigation,
                                immediatelyOpenScanner = it.arguments?.getBoolean("openScanner") ?: false,
                                forceNavOnBack = it.arguments?.getBoolean("forceNavOnBack") ?: false,
                            )
                        }
                    }
                    composable(
                        route = "${Screen.PaymentDetails.route}?id={id}&fromEvent={fromEvent}",
                        arguments = listOf(
                            navArgument("id") { type = NavType.StringType },
                            navArgument("fromEvent") {
                                type = NavType.BoolType
                                defaultValue = false
                            }
                        ),
                        deepLinks = listOf(navDeepLink { uriPattern = "phoenix:payments/{id}" })
                    ) {
                        val paymentId = remember {
                            try {
                                UUID.fromString(it.arguments!!.getString("id")!!)
                            } catch (e: Exception) {
                                null
                            }
                        }
                        if (paymentId != null) {
                            RequireStarted(walletState, nextUri = "phoenix:payments/${id}") {
                                log.debug("navigating to payment=$id")
                                val fromEvent = it.arguments?.getBoolean("fromEvent") ?: false
                                PaymentDetailsView(
                                    paymentId = paymentId,
                                    onBackClick = {
                                        val previousNav = navController.previousBackStackEntry
                                        if (fromEvent && previousNav?.destination?.route == Screen.Send.route) {
                                            navController.popToHome()
                                        } else if (navController.previousBackStackEntry != null) {
                                            navController.popBackStack()
                                        } else {
                                            navController.popToHome()
                                        }
                                    },
                                    fromEvent = fromEvent
                                )
                            }
                        }
                    }
                    composable(Screen.PaymentsHistory.route) {
                        PaymentsHistoryView(
                            onBackClick = { navController.popBackStack() },
                            paymentsViewModel = paymentsViewModel,
                            onPaymentClick = { navigateToPaymentDetails(navController, id = it, isFromEvent = false) },
                            onCsvExportClick = { navController.navigate(Screen.PaymentsExport.route) },
                        )
                    }
                    composable(Screen.PaymentsExport.route) {
                        PaymentsExportView(onBackClick = {
                            navController.navigate(Screen.PaymentsHistory.route) {
                                popUpTo(Screen.PaymentsHistory.route) { inclusive = true }
                            }
                        })
                    }
                    composable(Screen.Settings.route) {
                        SettingsView(noticesViewModel)
                    }
                    composable(Screen.DisplaySeed.route) {
                        DisplaySeedView()
                    }
                    composable(Screen.ElectrumServer.route) {
                        ElectrumView(onBackClick = { navController.popBackStack() })
                    }
                    composable(Screen.TorConfig.route) {
                        TorConfigView(appViewModel = appVM, onBackClick = { navController.popBackStack() }, onBusinessTeardown = { navController.popToHome() })
                    }
                    composable(Screen.Channels.route) {
                        ChannelsView(
                            onBackClick = {
                                navController.navigate(Screen.Settings.route) {
                                    popUpTo(Screen.Settings.route) { inclusive = true }
                                }
                            },
                            onChannelClick = { navController.navigate("${Screen.ChannelDetails.route}?id=$it") },
                            onImportChannelsDataClick = { navController.navigate(Screen.ImportChannelsData.route)},
                            onSpendFromChannelBalance = { navController.navigate(Screen.SpendChannelAddress.route)},
                        )
                    }
                    composable(
                        route = "${Screen.ChannelDetails.route}?id={id}",
                        arguments = listOf(navArgument("id") { type = NavType.StringType })
                    ) {
                        val channelId = it.arguments?.getString("id")
                        ChannelDetailsView(onBackClick = { navController.popBackStack() }, channelId = channelId)
                    }
                    composable(Screen.ImportChannelsData.route) {
                        ImportChannelsData(onBackClick = { navController.popBackStack() })
                    }
                    composable(Screen.SpendChannelAddress.route) {
                        SpendFromChannelAddress(onBackClick = { navController.popBackStack() })
                    }
                    composable(Screen.MutualClose.route) {
                        MutualCloseView(onBackClick = { navController.popBackStack() })
                    }
                    composable(Screen.ForceClose.route) {
                        ForceCloseView(onBackClick = { navController.popBackStack() })
                    }
                    composable(Screen.Preferences.route) {
                        DisplayPrefsView()
                    }
                    composable(Screen.About.route) {
                        AboutView()
                    }
                    composable("${Screen.PaymentSettings.route}?showAuthSchemeDialog={showAuthSchemeDialog}", arguments = listOf(
                        navArgument("showAuthSchemeDialog") { type = NavType.BoolType ; defaultValue = false }
                    )) {
                        val showAuthSchemeDialog = it.arguments?.getBoolean("showAuthSchemeDialog") ?: false
                        PaymentSettingsView(initialShowLnurlAuthSchemeDialog = showAuthSchemeDialog)
                    }
                    composable(Screen.AppLock.route) {
                        AppAccessSettings(onBackClick = { navController.popBackStack() }, appViewModel = appVM)
                    }
                    composable(Screen.Logs.route) {
                        LogsView()
                    }
                    composable(Screen.WalletInfo.route) {
                        WalletInfoView(
                            onBackClick = { navController.popBackStack() },
                            onLightningWalletClick = { navController.navigate(Screen.Channels.route) },
                            onSwapInWalletClick = { navController.navigate(Screen.WalletInfo.SwapInWallet.route) },
                            onSwapInWalletInfoClick = { navController.navigate(Screen.WalletInfo.SwapInAddresses.route) },
                            onFinalWalletClick = { navController.navigate(Screen.WalletInfo.FinalWallet.route) },
                        )
                    }
                    composable(
                        Screen.WalletInfo.SwapInWallet.route,
                        deepLinks = listOf(
                            navDeepLink { uriPattern = "phoenix:swapinwallet" }
                        )
                    ) {
                        SwapInWallet(
                            onBackClick = { navController.popBackStack() },
                            onViewChannelPolicyClick = { navController.navigate(Screen.LiquidityPolicy.route) },
                            onAdvancedClick = { navController.navigate(Screen.WalletInfo.SwapInSigner.route) },
                            onSpendRefundable = { navController.navigate(Screen.WalletInfo.SwapInRefund.route) },
                        )
                    }
                    composable(Screen.WalletInfo.SwapInAddresses.route) {
                        SwapInAddresses(onBackClick = { navController.popBackStack() })
                    }
                    composable(Screen.WalletInfo.SwapInSigner.route) {
                        SwapInSignerView(onBackClick = { navController.popBackStack() })
                    }
                    composable(Screen.WalletInfo.SwapInRefund.route) {
                        SendSwapInRefundView(onBackClick = { navController.popBackStack() })
                    }
                    composable(Screen.WalletInfo.FinalWallet.route) {
                        FinalWalletInfo(onBackClick = { navController.popBackStack() }, onSpendClick = { navController.navigate(Screen.WalletInfo.FinalWalletRefund.route) })
                    }
                    composable(Screen.WalletInfo.FinalWalletRefund.route) {
                        FinalWalletRefundView(onBackClick = { navController.popBackStack() })
                    }
                    composable(Screen.LiquidityPolicy.route, deepLinks = listOf(navDeepLink { uriPattern ="phoenix:liquiditypolicy" })) {
                        LiquidityPolicyView(
                            onBackClick = { navController.popBackStack() },
                            onAdvancedClick = { navController.navigate(Screen.AdvancedLiquidityPolicy.route) },
                        )
                    }
                    composable(Screen.LiquidityRequest.route, deepLinks = listOf(navDeepLink { uriPattern ="phoenix:requestliquidity" })) {
                        RequestLiquidityView(onBackClick = { navController.popBackStack() },)
                    }
                    composable(Screen.AdvancedLiquidityPolicy.route) {
                        AdvancedIncomingFeePolicy(onBackClick = { navController.popBackStack() })
                    }
                    composable(Screen.Notifications.route) {
                        NotificationsView(
                            noticesViewModel = noticesViewModel,
                            onBackClick = { navController.popBackStack() },
                        )
                    }
                    composable(Screen.ResetWallet.route) {
                        appVM.service?.let { nodeService ->
                            val application = application
                            ResetWallet(
                                onShutdownBusiness = application::shutdownBusiness,
                                onShutdownService = nodeService::shutdown,
                                onPrefsClear = application::clearPreferences,
                                onBusinessReset = {
                                    application.resetBusiness()
                                    FirebaseMessaging.getInstance().deleteToken().addOnCompleteListener { task ->
                                        if (task.isSuccessful) nodeService.refreshFcmToken()
                                    }
                                },
                                onBackClick = { navController.popBackStack() }
                            )
                        }
                    }
                    composable("${Screen.Contacts.route}?showAddContactDialog={showAddContactDialog}", arguments = listOf(
                        navArgument("showAddContactDialog") { type = NavType.BoolType ; defaultValue = false }
                    )) {
                        val showAddContactDialog = it.arguments?.getBoolean("showAddContactDialog") ?: false
                        SettingsContactsView(onBackClick = { navController.popBackStack() }, immediatelyShowAddContactDialog = showAddContactDialog)
                    }
                    composable(Screen.Experimental.route) {
                        ExperimentalView(onBackClick = { navController.popBackStack() })
                    }
                }
            }

            val isScreenLocked by appVM.isScreenLocked
            val isBiometricLockEnabledState = userPrefs.getIsScreenLockBiometricsEnabled.collectAsState(initial = null)
            val isBiometricLockEnabled = isBiometricLockEnabledState.value
            val isCustomPinLockEnabledState = userPrefs.getIsScreenLockPinEnabled.collectAsState(initial = null)
            val isCustomPinLockEnabled = isCustomPinLockEnabledState.value

            if ((isBiometricLockEnabled == true || isCustomPinLockEnabled == true) && isScreenLocked) {
                BackHandler {
                    // back button minimises the app
                    context.findActivitySafe()?.moveTaskToBack(false)
                }
                ScreenLockPrompt(
                    promptScreenLockImmediately = appVM.promptScreenLockImmediately.value,
                    onLock = { appVM.lockScreen() },
                    onUnlock = {
                        appVM.unlockScreen()
                        appVM.promptScreenLockImmediately.value = false
                    },
                )
            }
        }
    }

    val lastCompletedPayment by business.paymentsManager.lastCompletedPayment.collectAsState()
    lastCompletedPayment?.let { payment ->
        LaunchedEffect(key1 = payment.id) {
            navigateToPaymentDetails(navController, id = payment.id, isFromEvent = true)
        }
    }

    val isUpgradeRequired by business.peerManager.upgradeRequired.collectAsState(false)
    if (isUpgradeRequired) {
        UpgradeRequiredBlockingDialog()
    }
}

fun navigateToPaymentDetails(navController: NavController, id: UUID, isFromEvent: Boolean) {
    try {
        navController.navigate("${Screen.PaymentDetails.route}?id=${id}&fromEvent=${isFromEvent}")
    } catch (_: Exception) { }
}


@OptIn(ExperimentalPermissionsApi::class)
@Composable
private fun MonitorNotices(
    vm: NoticesViewModel
) {
    val internalData = internalData
    val userPrefs = userPrefs

    LaunchedEffect(Unit) {
        internalData.showSeedBackupNotice.collect {
            if (it) {
                vm.addNotice(Notice.BackupSeedReminder)
            } else {
                vm.removeNotice<Notice.BackupSeedReminder>()
            }
        }
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val notificationPermission = rememberPermissionState(permission = Manifest.permission.POST_NOTIFICATIONS)
        if (!notificationPermission.status.isGranted) {
            LaunchedEffect(Unit) {
                if (userPrefs.getShowNotificationPermissionReminder.first()) {
                    vm.addNotice(Notice.NotificationPermission)
                }
            }
        } else {
            vm.removeNotice<Notice.NotificationPermission>()
        }
        LaunchedEffect(Unit) {
            userPrefs.getShowNotificationPermissionReminder.collect {
                if (it && !notificationPermission.status.isGranted) {
                    vm.addNotice(Notice.NotificationPermission)
                } else {
                    vm.removeNotice<Notice.NotificationPermission>()
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        internalData.getChannelsWatcherOutcome.filterNotNull().collect {
            if (currentTimestampMillis() - it.timestamp > 6 * DateUtils.DAY_IN_MILLIS) {
                vm.addNotice(Notice.WatchTowerLate)
            } else {
                vm.removeNotice<Notice.WatchTowerLate>()
            }
        }
    }
}

@Composable
private fun RequireStarted(
    serviceState: NodeServiceState?,
    nextUri: String? = null,
    children: @Composable () -> Unit
) {
    val navController = navController
    val currentRoute = navController.currentDestination?.route
    if (serviceState != null && serviceState is NodeServiceState.Off && currentRoute != Screen.Startup.route) {
        val log = logger("Navigation")
        LaunchedEffect(key1 = Unit) {
            log.info("service off, navigating to startup then $nextUri")
            navController.navigate("${Screen.Startup.route}?next=${nextUri?.encodeURLParameter()}") {
                popUpTo(navController.graph.id) { inclusive = true }
            }
        }
    } else {
        children()
    }
}

@Composable
private fun UpgradeRequiredBlockingDialog() {
    val context = LocalContext.current
    Dialog(
        onDismiss = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false, usePlatformDefaultWidth = false),
        title = stringResource(id = R.string.upgraderequired_title),
        buttons = null
    ) {
        Text(
            text = stringResource(id = R.string.upgraderequired_message, BuildConfig.VERSION_NAME),
            modifier = Modifier.padding(horizontal = 24.dp),
        )
        Spacer(modifier = Modifier.height(8.dp))
        Button(
            text = stringResource(id = R.string.upgraderequired_button),
            icon = R.drawable.ic_external_link,
            space = 8.dp,
            shape = RoundedCornerShape(12.dp),
            onClick = { openLink(context = context, link = "https://play.google.com/store/apps/details?id=fr.acinq.phoenix.mainnet") },
            horizontalArrangement = Arrangement.Start,
            modifier = Modifier
                .padding(horizontal = 8.dp)
                .fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(8.dp))
    }
}