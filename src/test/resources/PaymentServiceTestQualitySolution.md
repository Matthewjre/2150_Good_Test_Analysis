# PaymentServiceTestCases Solution Key

This solution key identifies which tests in `PaymentServiceTestCases.java` are strong tests and which are weak tests. The classifications are based on the lecture ideas that coverage is only feedback, not proof of correctness, and that good tests should be focused, independent, deterministic, readable, contract-based, and capable of failing for realistic faults.

## Summary Table

| Test Method | Classification | Main Reason |
|---|---|---|
| `authorizePaymentReturnsAResultForValidInput` | Not Good | Uses a weak assertion that only checks the result is non-null. |
| `zeroAmountThrowsIllegalArgumentException` | Good | Verifies the invalid amount contract and confirms no external side effects occur. |
| `approvedGatewayResponseReturnsApprovedStatus` | Not Good | Checks only a partial result and ignores ledger correctness and gateway details. |
| `highFraudRiskDeclinesBeforeGatewayAuthorization` | Good | Protects a risky fraud branch and verifies the gateway is not called. |
| `positiveAmountDoesNotThrowException` | Not Good | Only verifies that no exception happens, not that correct behavior happens. |
| `gatewayDeclineRecordsGatewayReason` | Good | Verifies decline result, gateway interaction, and ledger side effect. |
| `fraudClientIsCalledForValidPayment` | Not Good | Checks an implementation-level call count without verifying meaningful business behavior. |
| `fraudScoreAtThresholdIsDeclined` | Good | Verifies the important boundary case at the fraud threshold. |
| `declinedPaymentHasDeclinedStatus` | Not Good | Checks only one shallow output and misses critical side effects. |
| `approvedPaymentRecordsAuthorizationWithTransactionDetails` | Good | Verifies approval output, gateway input, and ledger authorization state. |

## Detailed Explanation

### 1. `authorizePaymentReturnsAResultForValidInput`

**Classification: Not Good**

This test is weak because its only assertion is:

```java
assertNotNull(result);
```

That means the test would pass even if the payment returned the wrong status, used the wrong transaction ID, skipped the ledger, called the gateway with the wrong amount, or ignored fraud rules. This directly matches the lecture warning that coverage can tell us that code ran, but it cannot tell us whether the right behavior was checked.

This is an example of the bad-test smell "no or weak assertion." It may increase line coverage, but it gives false confidence.

A realistic bug that this test would miss:

```java
return PaymentResult.declined("wrong_reason");
```

The result would still be non-null, so the test would still pass.

---

### 2. `zeroAmountThrowsIllegalArgumentException`

**Classification: Good**

This test verifies a clear contract: a payment amount must be positive. It checks both the expected exception and the important side effects that should not happen after invalid input.

The test checks:

```java
assertThrows(IllegalArgumentException.class, ...);
assertEquals("Amount must be positive", exception.getMessage());
assertEquals(0, fraudClient.checkCallCount);
assertEquals(0, paymentGateway.authorizeCallCount);
assertEquals(0, ledger.authorizations.size());
assertEquals(0, ledger.declines.size());
```

This is good because it is focused, deterministic, contract-based, and capable of failing for realistic faults. It verifies not only that the exception occurs, but also that invalid payment input does not trigger fraud checks, gateway authorization, or ledger writes.

A realistic bug this test would catch:

```java
fraudClient.check(userId, orderId, amount, currency);
if (amount.compareTo(BigDecimal.ZERO) <= 0) {
    throw new IllegalArgumentException("Amount must be positive");
}
```

The method would still throw, but it would incorrectly call an external dependency first. This test would fail because it checks side effects.

---

### 3. `approvedGatewayResponseReturnsApprovedStatus`

**Classification: Not Good**

This test only checks:

```java
assertEquals("approved", result.status());
```

That assertion is too shallow. It does not verify that the returned transaction ID is correct, that the gateway was called with the right order ID, amount, and currency, or that the ledger recorded the authorization.

According to the lecture, a good test should protect a promise and fail when a realistic fault is introduced. This test protects only a small part of the behavior. It is not completely useless, but it is not a strong test because many important payment bugs would still pass.

A realistic bug this test would miss:

```java
ledger.recordAuthorization(orderId, "wrong_txn", amount, currency);
return PaymentResult.approved("txn_456");
```

The method result is approved, so this test would pass even though the ledger is wrong.

---

### 4. `highFraudRiskDeclinesBeforeGatewayAuthorization`

**Classification: Good**

This test targets one of the riskiest areas of the code: fraud prevention. The lecture emphasizes risk-based confidence, especially around security decisions, money calculations, persistence, complex branching, and code that would be expensive to get wrong.

This test verifies:

- The payment is declined.
- The decline reason is `fraud_risk`.
- The gateway is not called.
- The ledger records a high fraud risk decline.
- No authorization is recorded.

This is a strong test because it checks both the method output and the observable state changes. It is also capable of failing for realistic bugs.

A realistic bug this test would catch:

```java
GatewayResponse gatewayResponse = paymentGateway.authorize(orderId, amount, currency);
if (fraudResult.riskScore() >= 80) {
    return PaymentResult.declined("fraud_risk");
}
```

Even if the method returns declined, the gateway was called when it should not have been. This test would catch that.

---

### 5. `positiveAmountDoesNotThrowException`

**Classification: Not Good**

This test checks only that the method does not throw:

```java
assertDoesNotThrow(() -> service.authorizePayment(...));
```

That is not enough to prove the method behaved correctly. The payment could be approved incorrectly, declined incorrectly, fail to write to the ledger, or call dependencies with wrong values, and this test could still pass.

This matches the lecture idea that simply executing code is not the same as checking behavior. It is similar to a smoke test: it may prove the code path does not crash, but it does not verify the important result.

A realistic bug this test would miss:

```java
return PaymentResult.approved("hardcoded_txn");
```

The method would not throw, so this test would pass even if the transaction ID and side effects were wrong.

---

### 6. `gatewayDeclineRecordsGatewayReason`

**Classification: Good**

This test verifies the behavior when the fraud score is low enough to call the gateway, but the gateway rejects the payment. That is an important branch in the code.

The test checks:

- The method returns declined.
- The returned reason is `INSUFFICIENT_FUNDS`.
- The gateway was called exactly once.
- The ledger records the same decline reason.
- No authorization is recorded.

This is good because it has meaningful branch coverage and meaningful assertions. It checks a business outcome and an audit-related side effect.

A realistic bug this test would catch:

```java
ledger.recordDecline(orderId, "DECLINED");
return PaymentResult.declined(gatewayResponse.reason());
```

The returned result may be correct, but the ledger reason would be wrong. This test would fail.

---

### 7. `fraudClientIsCalledForValidPayment`

**Classification: Not Good**

This test only checks:

```java
assertTrue(fraudClient.checkCallCount > 0);
```

That is weak because it focuses on a low-value implementation detail without checking the actual business behavior. It does not verify whether the fraud result was handled correctly, whether the gateway was called correctly, whether the payment result is correct, or whether the ledger changed correctly.

It also uses "greater than 0" instead of a precise expectation, so it would pass even if the fraud client were called multiple times unnecessarily.

This violates the lecture's idea that good tests should make failures meaningful. If this test fails, it says little about the larger payment behavior. If it passes, it also says little.

A realistic bug this test would miss:

```java
fraudClient.check(userId, orderId, amount, currency);
fraudClient.check(userId, orderId, amount, currency);
return PaymentResult.approved("txn_123");
```

The fraud client was called, so this test passes, even though calling it twice may be wrong.

---

### 8. `fraudScoreAtThresholdIsDeclined`

**Classification: Good**

This is a strong boundary test. The code uses this decision:

```java
if (fraudResult.riskScore() >= 80)
```

A score of exactly 80 is important because it is the boundary between allowing and declining a payment. The lecture discusses branch coverage and path thinking, and this test is a good example of choosing an input that exercises an important decision point.

The test checks:

- A score of 80 is declined.
- The reason is `fraud_risk`.
- The gateway is not called.
- The ledger records the high-fraud decline.
- No authorization is recorded.

A realistic bug this test would catch:

```java
if (fraudResult.riskScore() > 80)
```

That bug would allow a risk score of exactly 80. This test would fail, which means it has strong failure-detection value.

---

### 9. `declinedPaymentHasDeclinedStatus`

**Classification: Not Good**

This test checks only:

```java
assertEquals("declined", result.status());
```

For a high fraud score, that is not enough. The risky part of the requirement is not only that the result says declined, but also that the gateway is not called and the ledger records the correct decline reason.

This is a classic weak assertion problem. It may cover the high-fraud branch, but it does not fully check the behavior that matters.

A realistic bug this test would miss:

```java
paymentGateway.authorize(orderId, amount, currency);
ledger.recordDecline(orderId, "WRONG_REASON");
return PaymentResult.declined("fraud_risk");
```

The result status is still declined, so this test passes even though two important side effects are wrong.

---

### 10. `approvedPaymentRecordsAuthorizationWithTransactionDetails`

**Classification: Good**

This test verifies the successful payment path in detail. It checks the method output, the gateway interaction, and the ledger authorization record.

The test verifies:

- The payment status is approved.
- The transaction ID is the one returned by the gateway.
- No decline reason is returned.
- The gateway receives the correct order ID, amount, and currency.
- The ledger records the correct order ID, transaction ID, amount, and currency.
- No decline is recorded.

This is a good test because it is contract-based and capable of failing for realistic bugs in a risky area of the code. Since the service handles money and persistence-like audit records, verifying the ledger state is important.

A realistic bug this test would catch:

```java
ledger.recordAuthorization(orderId, gatewayResponse.transactionId(), BigDecimal.ZERO, currency);
```

The result may still look approved, but the ledger would record the wrong amount. This test would fail.

---

## Final Classification

### Good Tests

| Test Method | Why It Is Good |
|---|---|
| `zeroAmountThrowsIllegalArgumentException` | Checks invalid input behavior and prevents unwanted side effects. |
| `highFraudRiskDeclinesBeforeGatewayAuthorization` | Checks a risky fraud branch and confirms the gateway is not called. |
| `gatewayDeclineRecordsGatewayReason` | Checks output and ledger behavior for gateway rejection. |
| `fraudScoreAtThresholdIsDeclined` | Checks an important boundary condition. |
| `approvedPaymentRecordsAuthorizationWithTransactionDetails` | Checks approval output, gateway input, and ledger state. |

### Not Good Tests

| Test Method | Why It Is Not Good |
|---|---|
| `authorizePaymentReturnsAResultForValidInput` | Weak `assertNotNull` assertion. |
| `approvedGatewayResponseReturnsApprovedStatus` | Checks only one part of the output. |
| `positiveAmountDoesNotThrowException` | Only checks that the method does not crash. |
| `fraudClientIsCalledForValidPayment` | Checks a shallow call-count detail instead of behavior. |
| `declinedPaymentHasDeclinedStatus` | Checks only status and ignores critical side effects. |

## Connection to the Lecture

This solution uses the lecture's main testing ideas:

1. **Coverage is a flashlight, not a guarantee.** Several weak tests execute the code, but they do not verify the important behavior.
2. **Good tests need meaningful assertions.** The good tests check exact outputs, exception behavior, and observable state changes.
3. **Good tests should be focused, independent, deterministic, readable, contract-based, and capable of failing.** Each good test targets one main behavior and would fail for a realistic payment-service fault.
4. **Bad-test smells include weak assertions and shallow checks.** The not-good tests often use `assertNotNull`, `assertDoesNotThrow`, or one incomplete assertion.
5. **Risk-based testing matters.** Fraud handling, payment authorization, and ledger records are risky areas, so the strongest tests focus on those behaviors.
