// SPDX-FileCopyrightText: 2024 - 2026 Simon Massey
// SPDX-License-Identifier: Apache-2.0
package com.github.trex_paxos.paxe;

import java.nio.ByteBuffer;

/// Serialises the host's payload type for a channel.
///
/// PAXE is a transport: it seals bytes and does not know what they mean. The
/// host registers one of these per channel, so a peer on the far side can read
/// them back. This is the seam that keeps paxe independent of any consensus
/// protocol, and it is why paxe hardcodes no pickler of its own.
public interface Pickler<T> {

    /// Writes `value` into `buffer`.
    void serialize(T value, ByteBuffer buffer);

    /// Reads one value from `buffer`, advancing it.
    T deserialize(ByteBuffer buffer);

    /// The exact byte length `serialize` will write, so a datagram can be
    /// sized before it is sealed.
    int sizeOf(T value);
}
