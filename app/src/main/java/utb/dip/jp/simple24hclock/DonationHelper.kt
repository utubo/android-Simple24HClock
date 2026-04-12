package utb.dip.jp.simple24hclock

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.widget.Toast
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ConsumeParams
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.QueryProductDetailsParams
import androidx.core.content.edit

object DonationHelper {
    private const val PRODUCT_ID = "tip100"
    private const val PREF_NAME = "donation_prefs"
    private const val PREF_KEY = "donation_count"

    fun start(activity: Activity, onUpdate: () -> Unit) {
        val billingClient = BillingClient.newBuilder(activity)
            .setListener { billingResult, purchases ->
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
                    for (purchase in purchases) {
                        handleConsume(activity, purchase, onUpdate)
                    }
                }
            }
            .enablePendingPurchases(
                PendingPurchasesParams.newBuilder()
                    .enableOneTimeProducts()
                    .build()
            )
            .build()

        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    queryProduct(activity, billingClient)
                } else {
                    activity.runOnUiThread {
                        Toast.makeText(
                            activity,
                            "${activity.getString(R.string.donation_error)}: ${billingResult.debugMessage}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }

            override fun onBillingServiceDisconnected() {}
        })
    }

    private fun queryProduct(activity: Activity, client: BillingClient) {
        val product = QueryProductDetailsParams.Product.newBuilder()
            .setProductId(PRODUCT_ID)
            .setProductType(BillingClient.ProductType.INAPP)
            .build()

        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(listOf(product))
            .build()

        client.queryProductDetailsAsync(params) { billingResult, queryProductDetailsResult ->
            val productDetails = queryProductDetailsResult.productDetailsList.firstOrNull()
            activity.runOnUiThread {
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && productDetails != null) {
                    showDialog(activity, productDetails, client)
                } else {
                    Toast.makeText(
                        activity,
                        activity.getString(R.string.donation_error),
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    private fun showDialog(activity: Activity, item: ProductDetails, client: BillingClient) {
        val price = item.oneTimePurchaseOfferDetails?.formattedPrice ?: ""
        AlertDialog.Builder(activity)
            .setIcon(R.drawable.baseline_coffee_24)
            .setTitle(activity.getString(R.string.donation_dialog_title))
            .setMessage(activity.getString(R.string.donation_dialog_message))
            .setPositiveButton(
                activity.getString(
                    R.string.donation_button_positive,
                    price
                )
            ) { _, _ ->
                val flowParams = BillingFlowParams.newBuilder()
                    .setProductDetailsParamsList(
                        listOf(
                            BillingFlowParams.ProductDetailsParams.newBuilder()
                                .setProductDetails(item)
                                .build()
                        )
                    ).build()
                client.launchBillingFlow(activity, flowParams)
            }
            .setNegativeButton(activity.getString(R.string.donation_button_negative), null)
            .show()
    }

    private fun handleConsume(activity: Activity, purchase: Purchase, onUpdate: () -> Unit) {
        val client = BillingClient.newBuilder(activity)
            .setListener { _, _ -> }
            .enablePendingPurchases(
                PendingPurchasesParams.newBuilder().enableOneTimeProducts().build()
            )
            .build()

        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(res: BillingResult) {
                val consumeParams = ConsumeParams.newBuilder()
                    .setPurchaseToken(purchase.purchaseToken)
                    .build()
                client.consumeAsync(consumeParams) { result, _ ->
                    if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                        val prefs = activity.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                        val currentCount = prefs.getInt("donation_count", 0)
                        prefs.edit { putInt(PREF_KEY, currentCount + 1) }
                        activity.runOnUiThread {
                            Toast.makeText(
                                activity,
                                activity.getString(R.string.donation_thanks),
                                Toast.LENGTH_LONG
                            ).show()
                            onUpdate()
                        }
                    }
                }
            }

            override fun onBillingServiceDisconnected() {}
        })
    }

    fun getDonationCount(activity: Activity): Int {
        val prefs = activity.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return prefs.getInt(PREF_KEY, 0)
    }
}
