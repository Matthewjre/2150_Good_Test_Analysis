package cpsc2150;

import cpsc2150.supporting.FraudClient;
import cpsc2150.supporting.Ledger;
import cpsc2150.supporting.PaymentGateway;
import java.math.BigDecimal;

/**
 * PaymentService coordinates payment authorization requests by performing a fraud check, delegating
 * to a payment gateway, and recording outcomes in a ledger. Instances of {@code PaymentService}
 * must be constructed with non-null collaborators (fraud client, payment gateway, ledger). A
 * PaymentService is created that will delegate operations to the provided collaborators.
 */
public class PaymentService {

  private final FraudClient fraudClient;
  private final PaymentGateway paymentGateway;
  private final Ledger ledger;

  /**
   * Construct a PaymentService with the required collaborators.
   *
   * @param fraudClient client used to perform fraud checks
   * @param paymentGateway gateway used to authorize payments
   * @param ledger ledger used to record authorizations and declines
   * @pre fraudClient != null && paymentGateway != null && ledger != null
   * @post The returned PaymentService delegates to the provided collaborators for fraud checks,
   *     gateway calls, and ledger recording.
   */
  public PaymentService(FraudClient fraudClient, PaymentGateway paymentGateway, Ledger ledger) {
    this.fraudClient = fraudClient;
    this.paymentGateway = paymentGateway;
    this.ledger = ledger;
  }

  /**
   * Attempt to authorize a payment for the given user and order.
   *
   * <p>This method follows a simple flow: 1) Validate input (amount must be positive). 2) Perform a
   * fraud check via {@code fraudClient}. 3) If fraud risk is high, record a decline and return a
   * declined result. 4) Otherwise, ask {@code paymentGateway} to authorize the payment. 5) Record
   * the outcome in {@code ledger} and return an appropriate PaymentResult.
   *
   * @param userId identifier of the user making the payment
   * @param orderId identifier of the order to be charged
   * @param amount monetary amount to authorize (must be > 0)
   * @param currency ISO currency code (e.g. "USD")
   * @return a {@link PaymentResult} describing the authorization outcome.
   * @throws IllegalArgumentException if {@code amount} is null or non-positive
   * @pre userID, orderId, amount, and currency are not blank and non-null AND
   *     amount.compareTo(java.math.BigDecimal.ZERO) > 0
   * @post If {@code amount} is non-positive an {@link IllegalArgumentException} is thrown.
   * @post If the fraud check yields a riskScore >= 80, the ledger will have a decline recorded for
   *     the order and a declined PaymentResult with reason "fraud_risk" is returned.
   * @post If the gateway approves, the ledger will have an authorization recorded and an approved
   *     PaymentResult with a non-null transactionId is returned.
   * @post If the gateway declines, the ledger will have a decline recorded with the
   *     gateway-provided reason and a declined PaymentResult is returned containing that reason.
   */
  public PaymentResult authorizePayment(
      String userId, String orderId, BigDecimal amount, String currency) {
    if (amount.compareTo(BigDecimal.ZERO) <= 0) {
      throw new IllegalArgumentException("Amount must be positive");
    }

    FraudClient.FraudResult fraudResult = fraudClient.check(userId, orderId, amount, currency);

    if (fraudResult.riskScore() >= 80) {
      ledger.recordDecline(orderId, "HIGH_FRAUD_RISK");

      return PaymentResult.declined("fraud_risk");
    }

    PaymentGateway.GatewayResponse gatewayResponse =
        paymentGateway.authorize(orderId, amount, currency);

    if (gatewayResponse.approved()) {
      ledger.recordAuthorization(orderId, gatewayResponse.transactionId(), amount, currency);

      return PaymentResult.approved(gatewayResponse.transactionId());
    }

    ledger.recordDecline(orderId, gatewayResponse.reason());

    return PaymentResult.declined(gatewayResponse.reason());
  }

  /**
   * Embedded record describing the result of an authorization attempt.
   *
   * <p>Fields:
   *
   * <p>- status: textual status ("approved" or "declined")
   *
   * <p>- transactionId: non-null when status is "approved"
   *
   * <p>- reason: non-null when status is "declined"
   *
   * <p>
   *
   * @pre none
   * @post Instances are immutable value objects describing the outcome of an authorization attempt.
   */
  public record PaymentResult(String status, String transactionId, String reason) {
    /**
     * Create an approved result.
     *
     * @param transactionId id returned by the payment gateway for the approved transaction
     * @return an approved PaymentResult
     * @pre transactionId != null && !transactionId.isBlank()
     * @post returned PaymentResult has status "approved" and the provided transactionId; reason
     *     will be null
     */
    public static PaymentResult approved(String transactionId) {
      return new PaymentResult("approved", transactionId, null);
    }

    /**
     * Create a declined result.
     *
     * @param reason textual reason describing the decline
     * @return a declined PaymentResult
     * @pre reason != null && !reason.isBlank()
     * @post returned PaymentResult has status "declined" and the provided reason; transactionId
     *     will be null
     */
    public static PaymentResult declined(String reason) {
      return new PaymentResult("declined", null, reason);
    }
  }
}
