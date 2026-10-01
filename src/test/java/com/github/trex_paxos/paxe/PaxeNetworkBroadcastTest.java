// SPDX-FileCopyrightText: 2024 - 2026 Simon Massey
// SPDX-License-Identifier: Apache-2.0
package com.github.trex_paxos.paxe;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class PaxeNetworkBroadcastTest {

  private NetworkTestHarness harness;
  private PaxeNetwork sender;
  private PaxeNetwork recipient1;
  private PaxeNetwork recipient2;

  @BeforeEach
  void setup() throws Exception {
    harness = new NetworkTestHarness();
    sender = harness.createNetwork((short) 1).network();
    recipient1 = harness.createNetwork((short) 2).network();
    recipient2 = harness.createNetwork((short) 3).network();
    harness.waitForNetworkEstablishment();
  }

  @AfterEach
  void cleanup() {
    harness.close();
  }

  @Test
  void independentlySealedDatagramsReachEachRecipient() throws Exception {
    Channel channel = Channel.CONSENSUS;
    Slot message = new Slot(1, 10);

    CountDownLatch latch = new CountDownLatch(2);
    AtomicReference<Slot> got1 = new AtomicReference<>();
    AtomicReference<Slot> got2 = new AtomicReference<>();

    recipient1.subscribe(channel, (Slot msg) -> {
      got1.set(msg);
      latch.countDown();
    }, "r1");
    recipient2.subscribe(channel, (Slot msg) -> {
      got2.set(msg);
      latch.countDown();
    }, "r2");

    sender.send(channel, new PeerId(2), message);
    sender.send(channel, new PeerId((short) 3), message);

    assertTrue(latch.await(1, TimeUnit.SECONDS));
    assertEquals(message, got1.get());
    assertEquals(message, got2.get());
  }

  @Test
  void broadcastUsesDistinctWireBytesPerRecipient() throws Exception {
    byte[] key = NetworkTestHarness.generateClusterPsk();
    byte[] payload = "broadcast-payload".getBytes();
    Channel channel = new Channel(0xABCDEF01);

    PaxePacket toTwo = PaxePacket.seal(new PeerId(1), new PeerId(2), channel, (byte) 0, payload, key);
    PaxePacket toThree = PaxePacket.seal(new PeerId(1), new PeerId((short) 3), channel, (byte) 0, payload, key);

    assertFalse(Arrays.equals(toTwo.toDatagram(), toThree.toDatagram()),
        "Independent sealed datagrams must differ on the wire");
    assertArrayEquals(payload, toTwo.decrypt(key));
    assertArrayEquals(payload, toThree.decrypt(key));
  }
}
