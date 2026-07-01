package cpsc2150;

import static org.junit.jupiter.api.Assertions.*;

import cpsc2150.PaymentService.PaymentResult;
import cpsc2150.supporting.FraudClient;
import cpsc2150.supporting.FraudClient.FraudResult;
import cpsc2150.supporting.Ledger;
import cpsc2150.supporting.PaymentGateway;
import cpsc2150.supporting.PaymentGateway.GatewayResponse;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

public class PaymentServiceTest {

    @Test
    void authorizePaymentReturnsAResultForValidInput() {
        FakeFraudClient fraudClient = new FakeFraudClient(new FraudClient.FraudResult(10));
        FakePaymentGateway paymentGateway = new FakePaymentGateway(
                new GatewayResponse(true, "txn_123", null)
        );
        FakeLedger ledger = new FakeLedger();

        PaymentService service = new PaymentService(fraudClient, paymentGateway, ledger);

        PaymentResult result = service.authorizePayment(
                "user_123",
                "order_456",
                new BigDecimal("100.00"),
                "USD"
        );

        assertNotNull(result);
    }

    @Test
    void zeroAmountThrowsIllegalArgumentException() {
        FakeFraudClient fraudClient = new FakeFraudClient(new FraudResult(10));
        FakePaymentGateway paymentGateway = new FakePaymentGateway(
                new GatewayResponse(true, "txn_123", null)
        );
        FakeLedger ledger = new FakeLedger();

        PaymentService service = new PaymentService(fraudClient, paymentGateway, ledger);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.authorizePayment(
                        "user_123",
                        "order_456",
                        BigDecimal.ZERO,
                        "USD"
                )
        );

        assertEquals("Amount must be positive", exception.getMessage());
        assertEquals(0, fraudClient.checkCallCount);
        assertEquals(0, paymentGateway.authorizeCallCount);
        assertEquals(0, ledger.authorizations.size());
        assertEquals(0, ledger.declines.size());
    }

    @Test
    void approvedGatewayResponseReturnsApprovedStatus() {
        FakeFraudClient fraudClient = new FakeFraudClient(new FraudResult(15));
        FakePaymentGateway paymentGateway = new FakePaymentGateway(
                new GatewayResponse(true, "txn_456", null)
        );
        FakeLedger ledger = new FakeLedger();

        PaymentService service = new PaymentService(fraudClient, paymentGateway, ledger);

        PaymentResult result = service.authorizePayment(
                "user_777",
                "order_888",
                new BigDecimal("25.00"),
                "USD"
        );

        assertEquals("approved", result.status());
    }

    @Test
    void highFraudRiskDeclinesBeforeGatewayAuthorization() {
        FakeFraudClient fraudClient = new FakeFraudClient(new FraudResult(95));
        FakePaymentGateway paymentGateway = new FakePaymentGateway(
                new GatewayResponse(true, "txn_should_not_happen", null)
        );
        FakeLedger ledger = new FakeLedger();

        PaymentService service = new PaymentService(fraudClient, paymentGateway, ledger);

        PaymentResult result = service.authorizePayment(
                "user_123",
                "order_456",
                new BigDecimal("100.00"),
                "USD"
        );

        assertEquals("declined", result.status());
        assertNull(result.transactionId());
        assertEquals("fraud_risk", result.reason());

        assertEquals(1, fraudClient.checkCallCount);
        assertEquals(0, paymentGateway.authorizeCallCount);

        assertEquals(1, ledger.declines.size());
        assertEquals("order_456", ledger.declines.get(0).orderId);
        assertEquals("HIGH_FRAUD_RISK", ledger.declines.get(0).reason);

        assertEquals(0, ledger.authorizations.size());
    }

    @Test
    void positiveAmountDoesNotThrowException() {
        FakeFraudClient fraudClient = new FakeFraudClient(new FraudResult(20));
        FakePaymentGateway paymentGateway = new FakePaymentGateway(
                new GatewayResponse(true, "txn_789", null)
        );
        FakeLedger ledger = new FakeLedger();

        PaymentService service = new PaymentService(fraudClient, paymentGateway, ledger);

        assertDoesNotThrow(() -> service.authorizePayment(
                "user_123",
                "order_456",
                new BigDecimal("100.00"),
                "USD"
        ));
    }

    @Test
    void gatewayDeclineRecordsGatewayReason() {
        FakeFraudClient fraudClient = new FakeFraudClient(new FraudResult(10));
        FakePaymentGateway paymentGateway = new FakePaymentGateway(
                new GatewayResponse(false, null, "INSUFFICIENT_FUNDS")
        );
        FakeLedger ledger = new FakeLedger();

        PaymentService service = new PaymentService(fraudClient, paymentGateway, ledger);

        PaymentResult result = service.authorizePayment(
                "user_123",
                "order_456",
                new BigDecimal("100.00"),
                "USD"
        );

        assertEquals("declined", result.status());
        assertNull(result.transactionId());
        assertEquals("INSUFFICIENT_FUNDS", result.reason());

        assertEquals(1, paymentGateway.authorizeCallCount);

        assertEquals(1, ledger.declines.size());
        assertEquals("order_456", ledger.declines.get(0).orderId);
        assertEquals("INSUFFICIENT_FUNDS", ledger.declines.get(0).reason);

        assertEquals(0, ledger.authorizations.size());
    }

    @Test
    void fraudClientIsCalledForValidPayment() {
        FakeFraudClient fraudClient = new FakeFraudClient(new FraudResult(10));
        FakePaymentGateway paymentGateway = new FakePaymentGateway(
                new GatewayResponse(true, "txn_123", null)
        );
        FakeLedger ledger = new FakeLedger();

        PaymentService service = new PaymentService(fraudClient, paymentGateway, ledger);

        service.authorizePayment(
                "user_123",
                "order_456",
                new BigDecimal("100.00"),
                "USD"
        );

        assertTrue(fraudClient.checkCallCount > 0);
    }

    @Test
    void fraudScoreAtThresholdIsDeclined() {
        FakeFraudClient fraudClient = new FakeFraudClient(new FraudResult(80));
        FakePaymentGateway paymentGateway = new FakePaymentGateway(
                new GatewayResponse(true, "txn_should_not_happen", null)
        );
        FakeLedger ledger = new FakeLedger();

        PaymentService service = new PaymentService(fraudClient, paymentGateway, ledger);

        PaymentResult result = service.authorizePayment(
                "user_123",
                "order_456",
                new BigDecimal("100.00"),
                "USD"
        );

        assertEquals("declined", result.status());
        assertEquals("fraud_risk", result.reason());

        assertEquals(0, paymentGateway.authorizeCallCount);

        assertEquals(1, ledger.declines.size());
        assertEquals("HIGH_FRAUD_RISK", ledger.declines.get(0).reason);
        assertEquals(0, ledger.authorizations.size());
    }

    @Test
    void declinedPaymentHasDeclinedStatus() {
        FakeFraudClient fraudClient = new FakeFraudClient(new FraudResult(90));
        FakePaymentGateway paymentGateway = new FakePaymentGateway(
                new GatewayResponse(true, "txn_123", null)
        );
        FakeLedger ledger = new FakeLedger();

        PaymentService service = new PaymentService(fraudClient, paymentGateway, ledger);

        PaymentResult result = service.authorizePayment(
                "user_123",
                "order_456",
                new BigDecimal("100.00"),
                "USD"
        );

        assertEquals("declined", result.status());
    }

    @Test
    void approvedPaymentRecordsAuthorizationWithTransactionDetails() {
        FakeFraudClient fraudClient = new FakeFraudClient(new FraudResult(20));
        FakePaymentGateway paymentGateway = new FakePaymentGateway(
                new GatewayResponse(true, "txn_789", null)
        );
        FakeLedger ledger = new FakeLedger();

        PaymentService service = new PaymentService(fraudClient, paymentGateway, ledger);

        PaymentResult result = service.authorizePayment(
                "user_123",
                "order_456",
                new BigDecimal("100.00"),
                "USD"
        );

        assertEquals("approved", result.status());
        assertEquals("txn_789", result.transactionId());
        assertNull(result.reason());

        assertEquals(1, paymentGateway.authorizeCallCount);
        assertEquals("order_456", paymentGateway.lastOrderId);
        assertEquals(new BigDecimal("100.00"), paymentGateway.lastAmount);
        assertEquals("USD", paymentGateway.lastCurrency);

        assertEquals(1, ledger.authorizations.size());
        assertEquals("order_456", ledger.authorizations.get(0).orderId);
        assertEquals("txn_789", ledger.authorizations.get(0).transactionId);
        assertEquals(new BigDecimal("100.00"), ledger.authorizations.get(0).amount);
        assertEquals("USD", ledger.authorizations.get(0).currency);

        assertEquals(0, ledger.declines.size());
    }

  /**
   * A simple hand-written test double for {@link FraudClient}.
   *
   * <p>This class is used instead of a mocking framework. It lets the test control
   * what fraud score the {@code PaymentService} receives, while also recording how
   * the service interacted with the fraud-checking dependency.</p>
   *
   * <p>In these tests, this fake helps students observe whether the fraud check was
   * called, how many times it was called, and whether the correct user, order,
   * amount, and currency were passed into the dependency.</p>
   */
  private static class FakeFraudClient implements FraudClient {
    private final FraudResult resultToReturn;

    int checkCallCount;
    String lastUserId;
    String lastOrderId;
    BigDecimal lastAmount;
    String lastCurrency;

    /**
     * Creates a fake fraud client that always returns the provided fraud result.
     *
     * @param resultToReturn the fraud result this fake should return whenever
     *                       {@link #check(String, String, BigDecimal, String)}
     *                       is called
     */
    FakeFraudClient(FraudResult resultToReturn) {
      this.resultToReturn = resultToReturn;
    }

    /**
     * Records the input values passed by the system under test and returns the
     * preconfigured fraud result.
     *
     * @param userId the user attempting the payment
     * @param orderId the order being paid for
     * @param amount the payment amount
     * @param currency the payment currency
     * @return the preconfigured fraud result
     */
    @Override
    public FraudResult check(
            String userId,
            String orderId,
            BigDecimal amount,
            String currency
    ) {
      checkCallCount++;
      lastUserId = userId;
      lastOrderId = orderId;
      lastAmount = amount;
      lastCurrency = currency;
      return resultToReturn;
    }
  }

  /**
   * A simple hand-written test double for {@link PaymentGateway}.
   *
   * <p>This fake represents the external payment processor. It allows a test to
   * decide whether the gateway should approve or reject a payment without making
   * a real network call or depending on an actual payment system.</p>
   *
   * <p>The fake also records whether authorization was attempted and which values
   * were passed to the gateway. This is useful because some important tests should
   * verify not only the final result, but also whether risky external behavior did
   * or did not happen.</p>
   */
  private static class FakePaymentGateway implements PaymentGateway {
    private final GatewayResponse responseToReturn;

    int authorizeCallCount;
    String lastOrderId;
    BigDecimal lastAmount;
    String lastCurrency;

    /**
     * Creates a fake payment gateway that always returns the provided response.
     *
     * @param responseToReturn the gateway response this fake should return
     *                         whenever authorization is attempted
     */
    FakePaymentGateway(GatewayResponse responseToReturn) {
      this.responseToReturn = responseToReturn;
    }

    /**
     * Records the authorization attempt and returns the preconfigured gateway
     * response.
     *
     * @param orderId the order being authorized
     * @param amount the payment amount
     * @param currency the payment currency
     * @return the preconfigured gateway response
     */
    @Override
    public GatewayResponse authorize(
            String orderId,
            BigDecimal amount,
            String currency
    ) {
      authorizeCallCount++;
      lastOrderId = orderId;
      lastAmount = amount;
      lastCurrency = currency;
      return responseToReturn;
    }
  }

  /**
   * A simple hand-written test double for {@link Ledger}.
   *
   * <p>The real ledger would usually persist financial records to a database,
   * accounting system, audit log, or another external service. This fake avoids
   * those external dependencies by storing authorization and decline records in
   * memory.</p>
   *
   * <p>This lets tests verify important side effects. For payment code, checking
   * the returned {@code PaymentResult} is often not enough; the test may also need
   * to prove that the correct ledger record was written, or that an incorrect
   * ledger record was not written.</p>
   */
  private static class FakeLedger implements Ledger {
    final List<AuthorizationRecord> authorizations = new ArrayList<>();
    final List<DeclineRecord> declines = new ArrayList<>();

    /**
     * Stores an authorization record in memory so the test can inspect it later.
     *
     * @param orderId the authorized order
     * @param transactionId the transaction id returned by the payment gateway
     * @param amount the authorized payment amount
     * @param currency the authorized payment currency
     */
    @Override
    public void recordAuthorization(
            String orderId,
            String transactionId,
            BigDecimal amount,
            String currency
    ) {
      authorizations.add(new AuthorizationRecord(
              orderId,
              transactionId,
              amount,
              currency
      ));
    }

    /**
     * Stores a decline record in memory so the test can inspect it later.
     *
     * @param orderId the declined order
     * @param reason the reason the payment was declined
     */
    @Override
    public void recordDecline(String orderId, String reason) {
      declines.add(new DeclineRecord(orderId, reason));
    }
  }

    /**
       * A small in-memory value object used by {@link FakeLedger}.
       *
       * <p>This class represents one successful authorization record that would have
       * been written to the real ledger. Tests inspect instances of this class to
       * verify that the service recorded the correct order id, transaction id, amount,
       * and currency after an approved payment.</p>
       */
      private record AuthorizationRecord(String orderId, String transactionId, BigDecimal amount,
                                         String currency) {
        /**
         * Creates an in-memory authorization record.
         *
         * @param orderId the authorized order
         * @param transactionId the payment gateway transaction id
         * @param amount the authorized payment amount
         * @param currency the authorized payment currency
         */
        private AuthorizationRecord {
        }
      }

    /**
       * A small in-memory value object used by {@link FakeLedger}.
       *
       * <p>This class represents one declined payment record that would have been
       * written to the real ledger. Tests inspect instances of this class to verify
       * that the service recorded the correct order id and decline reason.</p>
       */
      private record DeclineRecord(String orderId, String reason) {
        /**
         * Creates an in-memory decline record.
         *
         * @param orderId the declined order
         * @param reason the reason the payment was declined
         */
        private DeclineRecord {
        }
      }
}