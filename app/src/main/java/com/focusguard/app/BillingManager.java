package com.focusguard.app;

import android.app.Activity;
import com.android.billingclient.api.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Collections;

/** Play-managed subscription flow. Configure this product in Play Console before release. */
final class BillingManager implements PurchasesUpdatedListener {
    static final String PRODUCT_ID = "focusguard_monthly";
    interface Listener { void onBillingChanged(String message, String price, boolean available); }
    private final Activity activity;
    private final AppState state;
    private final Listener listener;
    private final BillingClient client;
    private ProductDetails product;
    private String offerToken;
    private String price = "Monthly subscription";

    BillingManager(Activity activity, AppState state, Listener listener) {
        this.activity = activity;
        this.state = state;
        this.listener = listener;
        client = BillingClient.newBuilder(activity).setListener(this)
                .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
                .enableAutoServiceReconnection().build();
    }
    void connect() {
        if (client.isReady()) { refresh(); return; }
        client.startConnection(new BillingClientStateListener() {
            @Override public void onBillingSetupFinished(BillingResult result) {
                if (result.getResponseCode() == BillingClient.BillingResponseCode.OK) { loadProduct(); refresh(); }
                else report("Subscriptions require a Google Play installation and configured product.");
            }
            @Override public void onBillingServiceDisconnected() { report("Google Play disconnected. Try Restore purchases."); }
        });
    }
    private void loadProduct() {
        QueryProductDetailsParams.Product requested = QueryProductDetailsParams.Product.newBuilder()
                .setProductId(PRODUCT_ID).setProductType(BillingClient.ProductType.SUBS).build();
        client.queryProductDetailsAsync(QueryProductDetailsParams.newBuilder().setProductList(Collections.singletonList(requested)).build(),
                (result, response) -> {
                    if (result.getResponseCode() != BillingClient.BillingResponseCode.OK || response.getProductDetailsList().isEmpty()) {
                        report("Monthly plan unavailable. The publisher must configure it in Google Play."); return;
                    }
                    product = response.getProductDetailsList().get(0);
                    if (product.getSubscriptionOfferDetails() != null) {
                        for (ProductDetails.SubscriptionOfferDetails offer : product.getSubscriptionOfferDetails()) {
                            List<ProductDetails.PricingPhase> phases = offer.getPricingPhases().getPricingPhaseList();
                            if (offer.getOfferId() == null && !phases.isEmpty()) {
                                ProductDetails.PricingPhase phase = phases.get(phases.size() - 1);
                                if ("P1M".equals(phase.getBillingPeriod())
                                        && phase.getRecurrenceMode() == ProductDetails.RecurrenceMode.INFINITE_RECURRING) {
                                    offerToken = offer.getOfferToken();
                                    price = phase.getFormattedPrice() + " / month";
                                    report("Renews monthly. Cancel anytime in Google Play."); return;
                                }
                            }
                        }
                    }
                    report("No eligible monthly base plan is available.");
                });
    }
    void refresh() {
        if (!client.isReady()) { connect(); return; }
        client.queryPurchasesAsync(QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.SUBS).build(),
                (result, purchases) -> {
                    if (result.getResponseCode() == BillingClient.BillingResponseCode.OK) handlePurchases(purchases, true);
                    else report("Could not restore purchases. Check your connection and try again.");
                });
    }
    void subscribe() {
        if (!client.isReady() || product == null || offerToken == null) {
            report("Monthly plan not ready. Try Restore purchases."); return;
        }
        BillingFlowParams.ProductDetailsParams details = BillingFlowParams.ProductDetailsParams.newBuilder()
                .setProductDetails(product).setOfferToken(offerToken).build();
        BillingResult result = client.launchBillingFlow(activity,
                BillingFlowParams.newBuilder().setProductDetailsParamsList(Collections.singletonList(details)).build());
        if (result.getResponseCode() != BillingClient.BillingResponseCode.OK) report("Google Play could not open checkout. Please try again.");
    }
    @Override public void onPurchasesUpdated(BillingResult result, List<Purchase> purchases) {
        if (result.getResponseCode() == BillingClient.BillingResponseCode.OK && purchases != null) handlePurchases(purchases, false);
        else if (result.getResponseCode() == BillingClient.BillingResponseCode.USER_CANCELED) report("Purchase canceled. You have not been charged by this app.");
        else if (result.getResponseCode() == BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED) refresh();
        else report("Purchase could not finish. Try Restore purchases.");
    }
    private void handlePurchases(List<Purchase> purchases, boolean completeSnapshot) {
        List<Purchase> active = new ArrayList<>();
        boolean pending = false;
        for (Purchase purchase : purchases) {
            if (!purchase.getProducts().contains(PRODUCT_ID)) continue;
            if (purchase.getPurchaseState() == Purchase.PurchaseState.PURCHASED) active.add(purchase);
            if (purchase.getPurchaseState() == Purchase.PurchaseState.PENDING) pending = true;
        }
        if (active.isEmpty()) {
            if (completeSnapshot) state.purchase(false);
            report(pending ? "Payment pending. Blocking unlocks when Google Play confirms payment."
                    : "Choose a monthly plan to enable blocking."); return;
        }
        for (Purchase purchase : active) {
            if (purchase.isAcknowledged()) { state.purchase(true); report("Your monthly subscription is active."); }
            else client.acknowledgePurchase(AcknowledgePurchaseParams.newBuilder().setPurchaseToken(purchase.getPurchaseToken()).build(),
                    result -> {
                        if (result.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                            state.purchase(true); report("Your monthly subscription is active.");
                        } else report("Payment received; confirmation needs retrying. Tap Restore purchases.");
                    });
        }
    }
    private void report(String message) {
        activity.runOnUiThread(() -> listener.onBillingChanged(message, price, offerToken != null && client.isReady()));
    }
    void close() { client.endConnection(); }
}
