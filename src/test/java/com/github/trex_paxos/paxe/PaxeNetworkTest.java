// SPDX-FileCopyrightText: 2024 - 2026 Simon Massey
// SPDX-License-Identifier: Apache-2.0
package com.github.trex_paxos.paxe;

import org.junit.jupiter.api.*;

import java.nio.channels.Selector;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.ConsoleHandler;
import java.util.logging.Level;

import static com.github.trex_paxos.paxe.PaxeLogger.LOGGER;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class PaxeNetworkTest {
  private static final int TEST_TIMEOUT_MS = 1000;

  private PaxeNetwork network1;
  private PaxeNetwork network2;
  private Selector testSelector;
  private NetworkTestHarness harness;

  @BeforeAll
  static void setupLogging() {
    final var logLevel = System.getProperty("java.util.logging.ConsoleHandler.level", "WARNING");
    final Level level = Level.parse(logLevel);
    ConsoleHandler handler = new ConsoleHandler();
    handler.setLevel(level);
    LOGGER.addHandler(handler);
    LOGGER.setLevel(level);
    LOGGER.setUseParentHandlers(false);
  }

  @BeforeEach
  void setup() throws Exception {
    LOGGER.fine("Setting up test networks");
    testSelector = Selector.open();
    harness = new NetworkTestHarness();

    network1 = harness.createNetwork((short) 1).network();
    network2 = harness.createNetwork((short) 2).network();

    harness.waitForNetworkEstablishment();
    LOGGER.fine("Network establishment complete");
  }

  @Test
  void testSendAndReceiveMessages() throws Exception {
    Channel channel = Channel.CONSENSUS;
    CountDownLatch latch = new CountDownLatch(2);
    AtomicReference<Slot> received1 = new AtomicReference<>();
    AtomicReference<Slot> received2 = new AtomicReference<>();

    network1.subscribe(channel, (Slot msg) -> {
      received1.set(msg);
      latch.countDown();
    }, "test1");

    network2.subscribe(channel, (Slot msg) -> {
      received2.set(msg);
      latch.countDown();
    }, "test2");

    Slot msg1 = new Slot(1, 1);
    Slot msg2 = new Slot(2, 2);

    network1.send(channel, new PeerId(2), msg1);
    network2.send(channel, new PeerId(1), msg2);

    boolean exchangeComplete = latch.await(TEST_TIMEOUT_MS, TimeUnit.MILLISECONDS);
    assertTrue(exchangeComplete, "Message exchange timed out");
    assertEquals(msg2, received1.get(), "Network 1 received wrong message");
    assertEquals(msg1, received2.get(), "Network 2 received wrong message");
  }

  @AfterEach
  void cleanup() throws Exception {
    if (harness != null) harness.close();
    if (testSelector != null) testSelector.close();
  }
}
