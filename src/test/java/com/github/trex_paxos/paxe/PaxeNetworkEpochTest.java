// SPDX-FileCopyrightText: 2024 - 2026 Simon Massey
// SPDX-License-Identifier: Apache-2.0
package com.github.trex_paxos.paxe;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PaxeNetworkEpochTest {

  @Test
  void overlapAcceptsBothEpochs() throws Exception {
    byte[] epoch0Key = NetworkTestHarness.generateClusterPsk();
    byte[] epoch1Key = NetworkTestHarness.generateClusterPsk();

    var keyManager = new ClusterKeyManager(epoch0Key, (byte) 0);
    keyManager.installEpoch((byte) 1, epoch1Key);

    try (NetworkTestHarness harness = new NetworkTestHarness(epoch0Key)) {
      PaxeNetwork sender = harness.createNetwork((short) 1, keyManager).network();
      PaxeNetwork receiver = harness.createNetwork((short) 2, keyManager).network();
      harness.waitForNetworkEstablishment();

      CountDownLatch epoch0Latch = new CountDownLatch(1);
      CountDownLatch epoch1Latch = new CountDownLatch(1);
      receiver.subscribe(Channel.CONSENSUS, (Slot msg) -> {
        if (msg.slot() == 1) {
          epoch0Latch.countDown();
        } else if (msg.slot() == 2) {
          epoch1Latch.countDown();
        }
      }, "consensus");

      // Distinct payloads per epoch: the overlap test is that a datagram sealed
      // under the old epoch and one under the new both arrive, so the receiver
      // has to be able to tell them apart.
      keyManager.setCurrentEpoch((byte) 0);
      sender.send(Channel.CONSENSUS, new PeerId(2), new Slot(1, 1));
      assertTrue(epoch0Latch.await(1, TimeUnit.SECONDS));

      keyManager.setCurrentEpoch((byte) 1);
      sender.send(Channel.CONSENSUS, new PeerId(2), new Slot(2, 2));
      assertTrue(epoch1Latch.await(1, TimeUnit.SECONDS));
    }
  }

  @Test
  void unknownEpochIsRejected() throws Exception {
    byte[] epoch0Key = NetworkTestHarness.generateClusterPsk();
    var keyManager = new ClusterKeyManager(epoch0Key, (byte) 0);

    try (NetworkTestHarness harness = new NetworkTestHarness(epoch0Key)) {
      PaxeNetwork sender = harness.createNetwork((short) 1, keyManager).network();
      PaxeNetwork receiver = harness.createNetwork((short) 2, keyManager).network();
      harness.waitForNetworkEstablishment();

      PaxePacket alienEpoch = PaxePacket.seal(
          new PeerId(1),
          new PeerId(2),
          Channel.CONSENSUS,
          (byte) 7,
          new byte[]{1},
          epoch0Key);

      CountDownLatch latch = new CountDownLatch(1);
      receiver.subscribe(Channel.CONSENSUS, msg -> latch.countDown(), "consensus");

      try (var channel = java.nio.channels.DatagramChannel.open()) {
        channel.send(
            java.nio.ByteBuffer.wrap(alienEpoch.toDatagram()),
            new java.net.InetSocketAddress("127.0.0.1", harness.portFor(new PeerId(2))));
      }

      assertFalse(latch.await(300, TimeUnit.MILLISECONDS));

      sender.send(Channel.CONSENSUS, new PeerId(2),
          new Slot(1, 3));
      assertTrue(latch.await(1, TimeUnit.SECONDS), "Receiver must stay operational");
    }
  }
}
