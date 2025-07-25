/*
 * Copyright 2023 ACINQ SAS
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

@file:Suppress("DEPRECATION")

package fr.acinq.phoenix.android.settings.channels

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.acinq.lightning.db.AutomaticLiquidityPurchasePayment
import fr.acinq.lightning.db.LegacyPayToOpenIncomingPayment
import fr.acinq.lightning.db.LegacySwapInIncomingPayment
import fr.acinq.lightning.db.ManualLiquidityPurchasePayment
import fr.acinq.lightning.db.NewChannelIncomingPayment
import fr.acinq.lightning.db.SpliceCpfpOutgoingPayment
import fr.acinq.lightning.db.SpliceInIncomingPayment
import fr.acinq.lightning.db.SpliceOutgoingPayment
import fr.acinq.lightning.db.WalletPayment
import fr.acinq.phoenix.android.LocalBitcoinUnits
import fr.acinq.phoenix.android.R
import fr.acinq.phoenix.android.business
import fr.acinq.phoenix.android.components.AmountWithFiatBeside
import fr.acinq.phoenix.android.components.Button
import fr.acinq.phoenix.android.components.Card
import fr.acinq.phoenix.android.components.CardHeader
import fr.acinq.phoenix.android.components.DefaultScreenHeader
import fr.acinq.phoenix.android.components.DefaultScreenLayout
import fr.acinq.phoenix.android.components.dialogs.Dialog
import fr.acinq.phoenix.android.components.InlineButton
import fr.acinq.phoenix.android.components.ItemCard
import fr.acinq.phoenix.android.components.PhoenixIcon
import fr.acinq.phoenix.android.components.ProgressView
import fr.acinq.phoenix.android.components.settings.Setting
import fr.acinq.phoenix.android.components.settings.SettingWithCopy
import fr.acinq.phoenix.android.components.InlineTransactionLink
import fr.acinq.phoenix.android.navController
import fr.acinq.phoenix.android.navigateToPaymentDetails
import fr.acinq.phoenix.android.utils.converters.AmountFormatter.toPrettyString
import fr.acinq.phoenix.android.utils.converters.MSatDisplayPolicy
import fr.acinq.phoenix.android.utils.copyToClipboard
import fr.acinq.phoenix.android.utils.monoTypo
import fr.acinq.phoenix.android.utils.mutedBgColor
import fr.acinq.phoenix.android.utils.share
import fr.acinq.phoenix.data.LocalChannelInfo


@Composable
fun ChannelDetailsView(
    onBackClick: () -> Unit,
    channelId: String?,
) {
    val channelsState = business.peerManager.channelsFlow.collectAsState()

    DefaultScreenLayout(isScrollable = false) {
        DefaultScreenHeader(
            onBackClick = onBackClick,
            title = stringResource(id = R.string.channeldetails_title),
        )
        when (val channels = channelsState.value) {
            null -> ProgressView(text = stringResource(id = R.string.channelsview_loading_channels))
            else -> when (val channel = channels.values.firstOrNull { it.channelId == channelId }) {
                null -> NoChannelFound()
                else -> ChannelSummaryView(channel = channel)
            }
        }
    }
}

@Composable
private fun NoChannelFound() {
    Card (internalPadding = PaddingValues(16.dp)) {
        Text(text = stringResource(id = R.string.channeldetails_not_found))
        // maybe look in the db for closed channels?
    }
}

@Composable
private fun ChannelSummaryView(
    channel: LocalChannelInfo
) {
    var showJsonDialog by remember { mutableStateOf(false) }

    if (showJsonDialog) {
        JsonDialog(onDismiss = { showJsonDialog = false }, json = channel.json)
    }

    LazyColumn {
        item {
            Card {
                SettingWithCopy(title = stringResource(id = R.string.channeldetails_channel_id), value = channel.channelId)
                Setting(title = stringResource(id = R.string.channeldetails_state), description = channel.stateName)
                Setting(
                    title = stringResource(id = R.string.channeldetails_spendable),
                    subtitle = {
                        channel.localBalance?.let { AmountWithFiatBeside(amount = it) } ?: Text(text = stringResource(id = R.string.utils_unknown))
                    }
                )
                Setting(
                    title = stringResource(id = R.string.channeldetails_receivable),
                    subtitle = {
                        channel.availableForReceive?.let { AmountWithFiatBeside(amount = it) } ?: Text(text = stringResource(id = R.string.utils_unknown))
                    }
                )
                Setting(
                    title = stringResource(id = R.string.channeldetails_json),
                    leadingIcon = { PhoenixIcon(resourceId = R.drawable.ic_curly_braces, tint = MaterialTheme.colors.primary) },
                    onClick = { showJsonDialog = true }
                )
            }
        }
        if (channel.commitmentsInfo.isNotEmpty()) {
            item {
                CardHeader(text = stringResource(id = R.string.channeldetails_commitments))
                Spacer(modifier = Modifier.height(8.dp))
            }
            itemsIndexed(channel.commitmentsInfo) { index, commitment ->
                ItemCard(index = index, maxItemsCount = channel.commitmentsInfo.size) {
                    CommitmentDetailsView(commitment)
                }
            }
        }
        if (channel.inactiveCommitmentsInfo.isNotEmpty()) {
            item {
                CardHeader(text = stringResource(id = R.string.channeldetails_inactive_commitments))
                Spacer(modifier = Modifier.height(8.dp))
            }
            itemsIndexed(channel.inactiveCommitmentsInfo) { index, commitment ->
                ItemCard(index = index, maxItemsCount = channel.inactiveCommitmentsInfo.size) {
                    CommitmentDetailsView(commitment)
                }
            }
        }
        item { Spacer(modifier = Modifier.height(32.dp)) }
    }
}

@Composable
private fun CommitmentDetailsView(
    commitment: LocalChannelInfo.CommitmentInfo
) {
    val btcUnit = LocalBitcoinUnits.current.primary
    val paymentsManager = business.paymentsManager
    val linkedPayments by produceState<List<WalletPayment>>(initialValue = emptyList()) {
        value = paymentsManager.listPaymentsForTxId(commitment.fundingTxId)
    }

    Setting(
        title = "Index ${commitment.fundingTxIndex}",
        subtitle = {
            Row {
                Text(text = stringResource(id = R.string.channeldetails_commitment_funding_tx_id), modifier = Modifier.alignByBaseline())
                Spacer(modifier = Modifier.width(4.dp))
                InlineTransactionLink(txId = commitment.fundingTxId, modifier = Modifier.alignByBaseline())
            }
            Row {
                Text(text = stringResource(id = R.string.channeldetails_commitment_balance))
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = commitment.balanceForSend.toPrettyString(btcUnit, withUnit = true, mSatDisplayPolicy = MSatDisplayPolicy.HIDE),
                    style = MaterialTheme.typography.subtitle2.copy(color = MaterialTheme.colors.onSurface)
                )
            }
            Row {
                Text(text = stringResource(id = R.string.channeldetails_commitment_funding_capacity))
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = commitment.fundingAmount.toPrettyString(btcUnit, withUnit = true, mSatDisplayPolicy = MSatDisplayPolicy.HIDE),
                    style = MaterialTheme.typography.subtitle2.copy(color = MaterialTheme.colors.onSurface)
                )
            }
            if (linkedPayments.isNotEmpty()) {
                Row {
                    Text(text = stringResource(id = R.string.channeldetails_commitment_linked_payments), modifier = Modifier.alignByBaseline())
                    Spacer(modifier = Modifier.width(4.dp))
                    val navController = navController
                    Column(modifier = Modifier.alignByBaseline()) {
                        linkedPayments.forEach { payment ->
                            InlineButton(
                                text = when (payment) {
                                    is LegacySwapInIncomingPayment -> "swap-in (legacy)"
                                    is LegacyPayToOpenIncomingPayment -> "pay-to-open (legacy)"
                                    is NewChannelIncomingPayment -> "new-channel"
                                    is SpliceInIncomingPayment -> "splice-in"
                                    is SpliceOutgoingPayment -> "splice-out"
                                    is SpliceCpfpOutgoingPayment -> "cpfp"
                                    is ManualLiquidityPurchasePayment -> "manual liquidity"
                                    is AutomaticLiquidityPurchasePayment -> "auto liquidity"
                                    else -> "other"
                                },
                                onClick = {
                                    navigateToPaymentDetails(navController, payment.id, isFromEvent = false)
                                },
                                fontSize = 14.sp,
                            )
                        }
                    }
                }
            }
        },
    )
}

@Composable
private fun JsonDialog(
    onDismiss: () -> Unit,
    json: String,
) {
    val context = LocalContext.current
    Dialog(
        onDismiss = onDismiss,
        buttonsTopMargin = 0.dp,
        buttons = {
            Button(
                icon = R.drawable.ic_copy,
                onClick = { copyToClipboard(context, json, context.getString(R.string.channeldetails_share)) }
            )
            Button(
                icon = R.drawable.ic_share,
                onClick = { share(context, json, subject = context.getString(R.string.channeldetails_share_subject), chooserTitle = context.getString(R.string.channeldetails_share_title)) }
            )
            Spacer(modifier = Modifier.weight(1.0f))
            Button(text = stringResource(id = R.string.btn_close), onClick = onDismiss)
        }
    ) {
        Column(
            modifier = Modifier
                .background(mutedBgColor)
                .fillMaxWidth()
                .heightIn(min = 150.dp, max = 350.dp)
        ) {
            SelectionContainer {
                Text(
                    text = json,
                    modifier = Modifier
                        .weight(1.0f)
                        .horizontalScroll(rememberScrollState())
                        .verticalScroll(rememberScrollState()),
                    style = monoTypo,
                )
            }
        }
    }
}
