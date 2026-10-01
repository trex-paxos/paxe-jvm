// SPDX-FileCopyrightText: 2024 - 2026 Simon Massey
// SPDX-License-Identifier: Apache-2.0
package com.github.trex_paxos.paxe;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class PaxeNetworkSecurityTest {

  private NetworkTestHarness harness;
  private PaxeNetwork node1;
  private PaxeNetwork node2;
  private int node2Port;

  @BeforeEach
  void setup() throws Exception {
    harness = new NetworkTestHarness();
    node1 = harness.createNetwork((short) 1).network();
    NetworkWithTempPort node2Network = harness.createNetwork((short) 2);
    node2 = node2Network.network();
    node2Port = node2Network.port();
    harness.waitForNetworkEstablishment();
  }

  @AfterEach
  void cleanup() {
    harness.close();
  }

  @Test
  void rejectsDatagramSealedWithDifferentClusterPsk() throws Exception {
    byte[] receiverPsk = NetworkTestHarness.generateClusterPsk();
    byte[] alienPsk = NetworkTestHarness.generateClusterPsk();

    harness.close();
    harness = new NetworkTestHarness(receiverPsk);
    node2 = harness.createNetwork((short) 2, new ClusterKeyManager(receiverPsk)).network();
    PaxeNetwork alien = harness.createNetwork((short) 9, new ClusterKeyManager(alienPsk)).network();
    harness.waitForNetworkEstablishment();

    CountDownLatch latch = new CountDownLatch(1);
    node2.subscribe(Channel.CONSENSUS, msg -> latch.countDown(), "consensus");

    alien.send(Channel.CONSENSUS, new PeerId(2),
        new Slot(9, 1));

    assertFalse(latch.await(300, TimeUnit.MILLISECONDS), "Alien PSK must not decrypt on node 2");
  }

  @Test
  void rejectsDatagramAfterEpochRetired() throws Exception {
    byte[] epoch0 = NetworkTestHarness.generateClusterPsk();
    byte[] epoch1 = NetworkTestHarness.generateClusterPsk();
    var senderKeys = new ClusterKeyManager(epoch0, (byte) 0);
    senderKeys.installEpoch((byte) 1, epoch1);
    var receiverKeys = new ClusterKeyManager(epoch0, (byte) 0);
    receiverKeys.installEpoch((byte) 1, epoch1);

    harness.close();
    harness = new NetworkTestHarness(epoch0);
    node1 = harness.createNetwork((short) 1, senderKeys).network();
    node2 = harness.createNetwork((short) 2, receiverKeys).network();
    harness.waitForNetworkEstablishment();

    CountDownLatch firstDelivery = new CountDownLatch(1);
    node2.subscribe(Channel.CONSENSUS, msg -> firstDelivery.countDown(), "consensus");

    senderKeys.setCurrentEpoch((byte) 0);
    node1.send(Channel.CONSENSUS, new PeerId(2),
        new Slot(1, 1));
    assertTrue(firstDelivery.await(1, TimeUnit.SECONDS));

    receiverKeys.retireEpoch((byte) 0);
    CountDownLatch secondDelivery = new CountDownLatch(1);
    node2.subscribe(Channel.CONSENSUS, msg -> secondDelivery.countDown(), "consensus-second");
    node1.send(Channel.CONSENSUS, new PeerId(2),
        new Slot(1, 2));
    assertFalse(secondDelivery.await(300, TimeUnit.MILLISECONDS), "Retired epoch must not open frames");
  }

  @Test
  void tamperedDatagramDoesNotStopReceiveLoop() throws Exception {
    byte[] key = NetworkTestHarness.generateClusterPsk();
    PaxePacket valid = PaxePacket.seal(
        new PeerId(1),
        new PeerId(2),
        Channel.CONSENSUS,
        (byte) 0,
        new byte[]{1, 2, 3},
        key);

    byte[] tampered = valid.toDatagram();
    tampered[10] ^= 0x01;
    sendRaw(node2Port, tampered);

    CountDownLatch latch = new CountDownLatch(1);
    node2.subscribe(Channel.CONSENSUS, msg -> latch.countDown(), "consensus");

    node1.send(Channel.CONSENSUS, new PeerId(2),
        new Slot(1, 3));

    assertTrue(latch.await(1, TimeUnit.SECONDS), "Receive loop must continue after rejected datagram");
  }

  @Test
  void truncatedDatagramIsIgnored() throws Exception {
    CountDownLatch latch = new CountDownLatch(1);
    node2.subscribe(Channel.CONSENSUS, msg -> latch.countDown(), "consensus");

    sendRaw(node2Port, new byte[PaxePacket.FRAME_OVERHEAD - 1]);

    node1.send(Channel.CONSENSUS, new PeerId(2),
        new Slot(1, 4));

    assertTrue(latch.await(1, TimeUnit.SECONDS));
  }

  @Test
  void datagramForDifferentDestinationIsDropped() throws Exception {
    byte[] key = NetworkTestHarness.generateClusterPsk();
    PaxePacket packet = PaxePacket.seal(
        new PeerId(1),
        new PeerId((short) 99),
        Channel.CONSENSUS,
        (byte) 0,
        new byte[]{9},
        key);
    sendRaw(node2Port, packet.toDatagram());

    CountDownLatch latch = new CountDownLatch(1);
    node2.subscribe(Channel.CONSENSUS, msg -> latch.countDown(), "consensus");

    assertFalse(latch.await(300, TimeUnit.MILLISECONDS));
  }

  @Test
  void unknownChannelIsDropped() throws Exception {
    byte[] key = NetworkTestHarness.generateClusterPsk();
    Channel unknown = new Channel(0x00FF00FF);
    PaxePacket packet = PaxePacket.seal(
        new PeerId(1),
        new PeerId(2),
        unknown,
        (byte) 0,
        new byte[]{7},
        key);
    sendRaw(node2Port, packet.toDatagram());

    AtomicInteger deliveries = new AtomicInteger();
    node2.subscribe(Channel.CONSENSUS, msg -> deliveries.incrementAndGet(), "consensus");
    node2.subscribe(Channel.PROXY, msg -> deliveries.incrementAndGet(), "proxy");

    Thread.sleep(200);
    assertEquals(0, deliveries.get());
  }

  @Test
  void sendBeforeStartIsIgnored() throws Exception {
    harness.close();
    harness = new NetworkTestHarness();
    PaxeNetwork dormant = harness.createNetwork((short) 1).network();

    AtomicBoolean delivered = new AtomicBoolean();
    dormant.subscribe(Channel.CONSENSUS, msg -> delivered.set(true), "consensus");
    dormant.send(Channel.CONSENSUS, new PeerId(2),
        new Slot(1, 5));

    Thread.sleep(100);
    assertFalse(delivered.get());
  }

  @Test
  void rejectsOversizedApplicationPayload() {
    byte[] huge = new byte[PaxeProtocol.MAX_PLAINTEXT_SIZE + 1];
    Blob cmd = new Blob(UUID.randomUUID(), huge);
    assertThrows(IllegalArgumentException.class,
        () -> node1.send(Channel.PROXY, new PeerId(2), cmd));
  }

  private static void sendRaw(int port, byte[] datagram) throws Exception {
    try (DatagramChannel channel = DatagramChannel.open()) {
      channel.send(ByteBuffer.wrap(datagram), new InetSocketAddress("127.0.0.1", port));
    }
  }
}
