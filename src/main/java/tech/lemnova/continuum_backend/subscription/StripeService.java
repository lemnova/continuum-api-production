package tech.lemnova.continuum_backend.subscription;

import com.stripe.Stripe;
import com.stripe.exception.StripeException;
import com.stripe.model.Customer;
import com.stripe.model.checkout.Session;
import com.stripe.param.CustomerCreateParams;
import com.stripe.param.checkout.SessionCreateParams;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tech.lemnova.continuum_backend.user.User;

@Service
public class StripeService {

    @Value("${stripe.api.key}")
    private String stripeApiKey;

    @Value("${stripe.price.pro}")
    private String stripePriceProId;

    @Value("${stripe.success.url}")
    private String successUrl;

    @Value("${stripe.cancel.url}")
    private String cancelUrl;

    @PostConstruct
    public void init() {
        Stripe.apiKey = stripeApiKey;
    }

    public Customer createCustomer(User user) throws StripeException {
        CustomerCreateParams params = CustomerCreateParams.builder()
            .setEmail(user.getEmail())
            .setName(user.getUsername())
            .putMetadata("userId", user.getId())
            .build();

        return Customer.create(params);
    }

    public Session createCheckoutSession(String customerId, String userId)
        throws StripeException {
        SessionCreateParams params = SessionCreateParams.builder()
            .setMode(SessionCreateParams.Mode.SUBSCRIPTION)
            .setCustomer(customerId)
            .addLineItem(
                SessionCreateParams.LineItem.builder()
                    .setPrice(stripePriceProId)
                    .setQuantity(1L)
                    .build()
            )
            .setSuccessUrl(successUrl + "?session_id={CHECKOUT_SESSION_ID}")
            .setCancelUrl(cancelUrl)
            .putMetadata("userId", userId)
            .build();

        return Session.create(params);
    }

    public Session retrieveSession(String sessionId) throws StripeException {
        return Session.retrieve(sessionId);
    }

    public com.stripe.model.Subscription retrieveSubscription(
        String subscriptionId
    ) throws StripeException {
        return com.stripe.model.Subscription.retrieve(subscriptionId);
    }

    public com.stripe.model.Subscription cancelSubscription(
        String subscriptionId
    ) throws StripeException {
        com.stripe.model.Subscription subscription =
            com.stripe.model.Subscription.retrieve(subscriptionId);
        return subscription.cancel();
    }
}
