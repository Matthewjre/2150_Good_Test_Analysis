package cpsc2150.supporting;

import java.math.BigDecimal;

/**
 * PaymentGateway is an abstraction over an external payment provider that can authorize payments.
 * Implementations encapsulate communication with a specific provider.
 */
public interface PaymentGateway {
  /**
   * Request authorization for the specified order and amount.
   *
   * @param orderId identifier of the order to authorize
   * @param amount monetary amount to authorize
   * @param currency ISO currency code for the amount
   * @return a {@link GatewayResponse} describing whether the request was approved and, if so, the
   *     transaction id; if declined the reason should be provided
   * @pre orderId, amount, and currency are non-null and valid (orderId/currency non-blank;
   *     amount.compareTo(BigDecimal.ZERO) >= 0)
   * @post A GatewayResponse is returned indicating approval status and associated metadata
   *     (transactionId or reason)
   */
  GatewayResponse authorize(String orderId, BigDecimal amount, String currency);

  /**
   * Response from the payment gateway.
   *
   * <p>Fields:
   *
   * <p>- approved: true when the gateway approved the authorization
   *
   * <p>- transactionId: populated when approved
   *
   * <p>- reason: populated when declined
   *
   * <p>
   *
   * @post GatewayResponse is an immutable carrier describing the gateway's decision.
   */
  record GatewayResponse(boolean approved, String transactionId, String reason) {}
}
