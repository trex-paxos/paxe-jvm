// SPDX-FileCopyrightText: 2024 - 2026 Simon Massey
// SPDX-License-Identifier: Apache-2.0
package com.github.trex_paxos.paxe;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertTrue;

class PaxeProxyTest {

  private NetworkTestHarness harness;
  private PaxeNetwork leaderNetwork;
  private PaxeNetwork followerNetwork;

  @BeforeEach
  void setup() throws Exception {
    harness = new NetworkTestHarness();
    leaderNetwork = harness.createNetwork((short) 1).network();
    followerNetwork = harness.createNetwork((short) 2).network();
    harness.waitForNetworkEstablishment();
  }

  @Test
  void proxyCommandReachesLeader() throws Exception {
    CountDownLatch latch = new CountDownLatch(1);
    leaderNetwork.subscribe(Channel.PROXY, (Blob cmd) -> latch.countDown(), "proxy-test");

    Blob cmd = new Blob(UUID.randomUUID(), new byte[]{1, 2, 3});
    followerNetwork.send(Channel.PROXY, new PeerId(1), cmd);

    assertTrue(latch.await(2, TimeUnit.SECONDS), "Leader did not receive proxied command");
  }

  @AfterEach
  void cleanup() {
    harness.close();
  }
}
