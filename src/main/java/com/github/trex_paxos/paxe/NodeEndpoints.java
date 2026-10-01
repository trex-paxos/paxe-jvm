// SPDX-FileCopyrightText: 2024 - 2026 Simon Massey
// SPDX-License-Identifier: Apache-2.0
package com.github.trex_paxos.paxe;

import java.util.Map;
import java.util.Optional;

/// The cluster's address book: which transport address serves which peer.
///
/// The host owns membership, so this is an immutable snapshot the host hands
/// over rather than something paxe discovers or mutates.
public record NodeEndpoints(Map<PeerId, NetworkAddress> addresses) {

    public NodeEndpoints {
        addresses = Map.copyOf(addresses);
    }

    /// The address serving `peer`, empty when the host does not name one.
    public Optional<NetworkAddress> addressFor(PeerId peer) {
        return Optional.ofNullable(addresses.get(peer));
    }
}
