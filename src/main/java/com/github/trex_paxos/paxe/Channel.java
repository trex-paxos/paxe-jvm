// SPDX-FileCopyrightText: 2024 - 2026 Simon Massey
// SPDX-License-Identifier: Apache-2.0
package com.github.trex_paxos.paxe;

/// Unsigned 32-bit channel identifier for multiplexing logical streams over one
/// encrypted datagram flow.
///
/// PAXE seals a datagram for a peer and a channel; the channel is the
/// multiplexing unit the host routes on. Values are big-endian `u32` on the
/// wire; Java holds the bit pattern in a signed `int`.
public record Channel(int id) {

    /// The consensus traffic the host registers by default.
    public static final Channel CONSENSUS = new Channel(1);

    /// The proxy/forwarder traffic the host registers by default.
    public static final Channel PROXY = new Channel(2);

    /// The unsigned 32-bit value, as a `long`, for display and comparison.
    public long unsignedValue() {
        return Integer.toUnsignedLong(id);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Channel that && Integer.compareUnsigned(id, that.id) == 0;
    }

    @Override
    public int hashCode() {
        return Integer.hashCode(id);
    }

    @Override
    public String toString() {
        return String.format("c%d", unsignedValue());
    }
}
