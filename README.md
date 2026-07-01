# Good Test Analysis Practice: Payment Service

This repository is a practice activity for learning how to identify **good tests** and **weak tests**.

The goal is not just to run the tests or get high code coverage. The goal is to read a realistic piece of code, read its requirements, inspect a mixed test suite, and decide which tests provide meaningful confidence.

This activity connects to a key idea from lecture:

> Coverage shows what code ran. Good testing asks whether the right behavior was checked.

---

## What this project is about

This project models a simplified payment authorization system.

Imagine a customer is checking out online and wants to pay for an order. Before the payment can be approved, the system has to make a few decisions:

1. Is the payment amount valid?
2. Is the payment risky or possibly fraudulent?
3. Should the payment be sent to the payment gateway?
4. Did the payment gateway approve or reject the payment?
5. Was the final decision recorded in the ledger?

The central class is:

```text
src/main/java/cpsc2150/PaymentService.java
```

The main method being tested is:

```java
authorizePayment(String userId, String orderId, BigDecimal amount, String currency)
```

That method coordinates three supporting dependencies:

| Dependency       | What it represents                          | Why it matters                                            |
|------------------|---------------------------------------------|-----------------------------------------------------------|
| `FraudClient`    | A service that gives a fraud risk score     | High-risk payments should be stopped before authorization |
| `PaymentGateway` | A service that approves or rejects payments | Low-risk payments are sent here for authorization         |
| `Ledger`         | A record of approved and declined payments  | Payment decisions need to be auditable                    |

The system is intentionally small, but the behavior is important because payment code involves risk: money, fraud decisions, external services, and audit records.

---

## Payment flow simplified

The `PaymentService` follows this flow:

1. If the amount is zero or negative, throw an exception.
2. Otherwise, ask the fraud client for a fraud risk score.
3. If the fraud risk score is `80` or higher:
   - decline the payment,
   - do not call the payment gateway,
   - record a fraud decline in the ledger.
4. If the fraud risk score is below `80`:
   - send the payment to the payment gateway.
5. If the gateway approves:
   - return an approved result,
   - record an authorization in the ledger.
6. If the gateway rejects:
   - return a declined result,
   - record the gateway decline reason in the ledger.

---

## Things to be on the lookout for

In this example, not all passing tests are equally valuable.

Some tests in this project are strong because they check meaningful behavior, such as:

- invalid amounts are rejected before external dependencies are called,
- high-risk fraud scores are declined,
- the payment gateway is not called for risky payments,
- approved payments are recorded correctly in the ledger,
- gateway declines preserve the gateway's decline reason.

Some tests are weak because they only check shallow things, such as:

- a result is not null,
- no exception was thrown,
- the status is `"approved"` or `"declined"` without checking important side effects,
- a dependency was called at least once without checking whether the overall behavior was correct.

The practice is to decide which tests are actually protecting important behavior.

---

## Recommended order for the activity

Use the files in this order.

### 1. Requirements analysis

```text
docs/PaymentServiceRequirementsAnalysis.md
```

This explains the system using:

- system overview,
- stakeholders,
- functional requirements,
- non-functional requirements,
- user stories,
- acceptance criteria.

Read this first. The requirements and acceptance criteria define what the system is supposed to do.

### 2. Production code

```text
src/main/java/cpsc2150/PaymentService.java
src/main/java/cpsc2150/supporting/FraudClient.java
src/main/java/cpsc2150/supporting/PaymentGateway.java
src/main/java/cpsc2150/supporting/Ledger.java
```

These files contain the code being tested.

Do not worry if the payment domain feels unfamiliar. Focus on the control flow:

```text
validate amount -> check fraud -> maybe call gateway -> record ledger result
```

### 3. Test plan

```text
docs/PaymentServiceTestPlan.md
```

The test plan lists each planned test, its method input, expected output, input state, and expected output state.

Use the test plan to understand what each test is trying to check before reading the JUnit code.

### 4. JUnit test suite

```text
src/test/java/cpsc2150/PaymentServiceTestCases.java
```

This file contains 10 tests.

Your job is to evaluate them and decide which ones are strong and which ones are weak.

The tests use handwritten fake classes instead of a mocking framework, which is normally what you'd use in a real 
system like this for reasons outside of 2150. These fake classes let the tests control the fraud score and gateway
response while also recording what happened during the test.

### 5. Solution key

```text
docs/PaymentServiceTestQualitySolution.md
```

Read this only after completing your own analysis.

The solution key identifies which tests are stronger and which are weaker, and explains why.

---

## Your task

For each test in `PaymentServiceTest.java`, answer these questions:

1. What behavior does this test claim to check?
2. Which requirement or acceptance criterion does it connect to?
3. What assertions does it make?
4. What important behavior, if any, does it fail to check?
5. What realistic bug would make this test fail?
6. Would you classify the test as a good test or a weak test?

A test is not good just because it passes.

A test is not good just because it executes lines of code.

A stronger test should check behavior that matters.

---

## What makes a test good in this activity?

Use this checklist from lecture.

A good test should be:

| Quality            | What it means in this project                                          |
|--------------------|------------------------------------------------------------------------|
| Focused            | The test checks one main payment behavior                              |
| Independent        | The test can run by itself and does not depend on another test         |
| Deterministic      | The same setup always produces the same result                         |
| Readable           | The setup and assertions make the scenario understandable              |
| Contract-based     | The expected result comes from the requirements or acceptance criteria |
| Capable of failing | A realistic bug would cause the test to turn red                       |

The most important question is:

```text
Would this test fail if the code violated an important requirement?
```

---

## Examples of stronger checks

For this payment system, stronger tests often check more than just the returned status.

For example, if a payment has a fraud score of `95`, it is not enough to check this:

```java
assertEquals("declined", result.status());
```

That only checks part of the behavior.

A stronger test would also check things like:

```java
assertEquals("fraud_risk", result.reason());
assertEquals(0, paymentGateway.authorizeCallCount);
assertEquals(1, ledger.declines.size());
assertEquals("HIGH_FRAUD_RISK", ledger.declines.get(0).reason);
assertEquals(0, ledger.authorizations.size());
```

Why?

Because the important behavior is not only that the result says declined. The important behavior is that a risky payment:

- is declined for the correct reason,
- is not sent to the gateway,
- is recorded correctly in the ledger,
- does not create an authorization record.

---

## Examples of "weak test smells" (anti-patterns, we'll talk about these in the patterns unit, probably...)

Watch for these weak-test patterns.

### Weak assertion

```java
assertNotNull(result);
```

This proves that something came back, but it does not prove the payment was handled correctly.

### Only checking that no exception happened

```java
assertDoesNotThrow(() -> service.authorizePayment(...));
```

This does not prove the result was correct, the ledger was updated, or the gateway was called correctly.

### Checking only one small part of a larger behavior

```java
assertEquals("approved", result.status());
```

This may miss serious bugs, such as:

- wrong transaction ID,
- missing ledger authorization,
- wrong amount recorded,
- decline record accidentally written,
- gateway called with incorrect arguments.

### Checking an implementation event without checking the outcome

```java
assertTrue(fraudClient.checkCallCount > 0);
```

This might be true even if the final payment behavior is wrong.

---

## How the fake classes work

The test suite does not use Mockito or any other mocking framework.

Instead, it uses small hand-written fake classes inside the test file:

- `FakeFraudClient`
- `FakePaymentGateway`
- `FakeLedger`
- `AuthorizationRecord`
- `DeclineRecord`

These classes are not part of the production system. They exist only to help the tests.

### Why use fakes?

Real payment systems may use databases, APIs, network calls, or third-party services. Unit tests should not depend on
those systems (again, for many reasons that _should_ be 3720's or 4910's problem...)

The fake classes let the tests:

- choose what fraud score is returned,
- choose whether the gateway approves or rejects,
- count whether a dependency was called,
- inspect what values were passed into a dependency,
- inspect what ledger records were written.

This makes the tests deterministic and easier to reason about without relying on external systems.

---

## How to run the tests

From the project root, run:

```bash
./gradlew test
```
Or use the little elephant icon to access the Gradle tasks in IntelliJ.


All tests may pass. Passing does not mean all tests are equally good.

---

## How to run tests with coverage

This project uses JaCoCo for coverage.

Run:

```bash
./gradlew clean test jacocoTestReport
```

On Windows:

```bash
gradlew.bat clean test jacocoTestReport
```

After running the command, open the HTML report:

```text
build/reports/jacoco/test/html/index.html
```

Coverage can help you see which lines and branches ran, but coverage does not prove the assertions were meaningful.

A weak test can still increase coverage.

---

## If JaCoCo reports class mismatch warnings

You may see a warning like this:

```text
Execution data for class ... does not match.
```

This usually means JaCoCo collected coverage data for one compiled version of the code, but the report is being generated from a different compiled version.

The usual fix is to clean and rerun everything:

```bash
./gradlew clean test jacocoTestReport
```

On Windows:

```bash
gradlew.bat clean test jacocoTestReport
```

---

## Suggested classroom workflow

### Step 1: Read the requirements

Start with:

```text
docs/PaymentServiceRequirementsAnalysis.md
```

Identify the risky behaviors:

- invalid amounts,
- fraud threshold,
- gateway call/no-call behavior,
- ledger records,
- approved result,
- declined result.

### Step 2: Read the test plan

Open:

```text
docs/PaymentServiceTestPlan.md
```

For each row, ask:

```text
Is the expected output specific enough?
Is the expected output state checked?
Would this catch an important bug?
```

### Step 3: Read the JUnit tests

Open:

```text
src/test/java/cpsc2150/PaymentServiceTestCases.java
```

Classify each test as stronger or weaker.

Do not rely only on the test name. Look at the assertions.

### Step 4: Run the tests

Run:

```bash
./gradlew test
```

All tests may pass. Passing does not mean all tests are equally good.

### Step 5: Run coverage

Run:

```bash
./gradlew clean test jacocoTestReport
```

Then inspect the coverage report.

Ask:

```text
Did coverage help me find code that was not exercised?
Did any weak tests still contribute to coverage?
Did any test execute code without checking the most important behavior?
```

### Step 6: Check your answers

After finishing your own classification, read:

```text
docs/PaymentServiceTestQualitySolution.md
```

Compare your reasoning with the provided explanation.

---

## Important vocabulary

### System under test

The main code being tested.

In this project:

```text
PaymentService
```

### Collaborator

Another object that the system under test uses.

In this project:

```text
FraudClient
PaymentGateway
Ledger
```

### Fake

A simple hand-written test object that stands in for a real dependency.

The tests use fakes so they can control the fraud score and gateway response without using real external systems.

### Side effect

A change that happens outside the returned value.

In this project, ledger records are side effects.

For example:

```text
ledger.recordAuthorization(...)
ledger.recordDecline(...)
```

Good tests often need to check side effects, not just return values.

### Risky code

Code where a bug could be costly or hard to detect.

In this project, risky areas include:

- fraud threshold logic,
- gateway authorization,
- ledger recording,
- invalid money amounts.

---
