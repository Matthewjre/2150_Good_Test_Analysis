package cpsc2150.supporting;

import java.math.BigDecimal;

/**
 * Ledger records payment events such as successful authorizations and
 * declines. Implementations persist or emit audit events as appropriate.
 *
 * Design-by-contract: callers should provide valid, non-null information
 * to be recorded.
 */
public interface Ledger {
    /**
     * Record a successful authorization for an order.
     *
     * @param orderId identifier of the order being authorized
     * @param transactionId id returned from the payment gateway
     * @param amount monetary amount authorized
     * @param currency ISO currency code for the amount
     *
     * @pre orderId, transactionId, amount, and currency are non-null and
     *      valid (ids/currency are non-blank; amount.compareTo(BigDecimal.ZERO) >= 0)
     *
     * @post An authorization record for the provided orderId and
     *       transactionId has been persisted or emitted.
     */
    void recordAuthorization(
            String orderId,
            String transactionId,
            BigDecimal amount,
            String currency
    );

    /**
     * Record a decline for the given order.
     *
     * @param orderId identifier of the order that was declined
     * @param reason textual reason for the decline
     *
     * @pre orderId and reason are non-null and non-blank
     *
     * @post A decline record for the provided orderId and reason has been
     *       persisted or emitted.
     */
    void recordDecline(String orderId, String reason);
}
