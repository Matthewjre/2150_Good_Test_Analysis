package cpsc2150.supporting;

import java.math.BigDecimal;

/**
 * FraudClient performs fraud analysis on payment requests and returns a {@link FraudResult}
 * containing a risk score. Implementations decide how to compute the score (e.g. 0-100 scale).
 */
public interface FraudClient {
  /**
   * Analyze the provided payment context and return a fraud risk result.
   *
   * @param userId identifier of the user performing the payment
   * @param orderId identifier of the order being paid
   * @param amount monetary amount being charged
   * @param currency ISO currency code for the amount.
   * @return a {@link FraudResult} containing an integer riskScore; higher values indicate greater
   *     risk
   * @pre userId, orderId, amount, and currency are non-null and valid (ids/currency non-blank;
   *     amount.compareTo(BigDecimal.ZERO) >= 0)
   * @post A FraudResult is returned; the interpretation of riskScore is determined by the
   *     implementation (commonly 0-100).
   */
  FraudResult check(String userId, String orderId, BigDecimal amount, String currency);

  /**
   * Result of a fraud check.
   *
   * @param riskScore numeric score indicating fraud risk; higher means greater risk
   * @post riskScore represents the computed fraud risk for the request
   */
  record FraudResult(int riskScore) {}
}
