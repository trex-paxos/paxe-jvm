// SPDX-FileCopyrightText: 2024 - 2026 Simon Massey
// SPDX-License-Identifier: Apache-2.0
package com.github.trex_paxos.paxe;

import java.util.Objects;

/// An unsigned 16-bit peer identifier, the addressing unit of a PAXE keystore.
///
/// PAXE is a transport. It has no opinion about consensus, so it does not name
/// consensus identities: this is a peer address, and the consensus layer maps
/// its own identity onto one. The width matches the Rust reference's boundary,
/// which takes `to_id: u16`.
public record PeerId(int value) implements Comparable<PeerId> {

    /// The largest addressable peer. Sixteen bits, unsigned.
    public static final int MAX = 0xFFFF;

    public static final PeerId ZERO = new PeerId(0);

    public PeerId {
        if (value < 0 || value > MAX) {
            throw new IllegalArgumentException(
                    "A peer id must fit in 16 unsigned bits: " + value);
        }
    }

    /// The unsigned value, as a `long`, for display and comparison.
    public long unsignedValue() {
        return Integer.toUnsignedLong(value);
    }

    @Override
    public int compareTo(PeerId that) {
        return Integer.compareUnsigned(this.value, that.value);
    }

    @Override
    public String toString() {
        return String.format("p%d", unsignedValue());
    }

    /// A peer from a wire value already masked to 16 bits.
    public static PeerId of(int unsigned) {
        return new PeerId(unsigned);
    }

    /// Parses the `p<n>` form `toString` produces.
    public static PeerId parse(String text) {
        Objects.requireNonNull(text, "text cannot be null");
        var body = text.startsWith("p") ? text.substring(1) : text;
        return new PeerId(Integer.parseUnsignedInt(body));
    }
}