// SPDX-FileCopyrightText: 2024 - 2026 Simon Massey
// SPDX-License-Identifier: Apache-2.0
package com.github.trex_paxos.paxe;

import java.io.Closeable;
import java.util.function.Consumer;

/// The transport surface a host programs against.
///
/// This is PAXE's whole contract with the outside world: subscribe to a
/// channel, send on a channel, start the flow. It is declared here rather than
/// inherited so that PAXE names no other library's types — a host that speaks
/// PAXE can satisfy this interface without adopting anything of ours.
public interface PaxeTransport extends Closeable {

    /// Registers `handler` for inbound traffic on `channel`. The host owns the
    /// payload type and the pickler that reads it.
    <T> void subscribe(Channel channel, Consumer<T> handler, String name);

    /// Queues `msg` for `to` on `channel`. The host's registered pickler for
    /// that channel serialises it, PAXE seals the bytes.
    <T> void send(Channel channel, PeerId to, T msg);

    /// Opens the datagram flow and begins delivering.
    void start();
}