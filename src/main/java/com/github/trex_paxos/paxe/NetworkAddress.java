// SPDX-FileCopyrightText: 2024 - 2026 Simon Massey
// SPDX-License-Identifier: Apache-2.0
package com.github.trex_paxos.paxe;

/// A peer's transport address. PAXE is datagram-only and reaches the host's
/// sockets, so this is a host and a port and nothing else.
public record NetworkAddress(String host, int port) {

    public NetworkAddress {
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("A host cannot be null or blank");
        }
        if (port < 0 || port > 65535) {
            throw new IllegalArgumentException("A port must be 0..65535: " + port);
        }
    }

    /// The loopback address on `port`.
    public NetworkAddress(int port) {
        this("127.0.0.1", port);
    }
}
