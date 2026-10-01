// SPDX-FileCopyrightText: 2024 - 2026 Simon Massey
// SPDX-License-Identifier: Apache-2.0
package com.github.trex_paxos.paxe;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.UUID;

/// Test payload types for PAXE's own tests.
///
/// These used to be `Fixed` and `Command` from uvrr-lib, borrowed as convenient
/// carriers for "some bytes". That was the wrong shape of test for a standalone
/// transport: PAXE must prove it carries *any* payload, not that it round-trips
/// one particular consensus protocol's messages. A transport test that imports a
/// consensus library cannot tell a codec bug from a protocol change.
///
/// The carriers are deliberately trivial, and the picklers are hand-written so
/// the tests exercise the same `Pickler` seam a host implements.

/// A minimal carrier: a slot and a counter, which is enough to tell payloads
/// apart and to detect reordering.
record Slot(int slot, int counter) {
    static byte[] encode(Slot value) {
        var buffer = ByteBuffer.allocate(8);
        buffer.putInt(value.slot());
        buffer.putInt(value.counter());
        return buffer.array();
    }

    static Slot decode(ByteBuffer buffer) {
        return new Slot(buffer.getInt(), buffer.getInt());
    }
}

/// A byte-blob carrier with an identity, the shape most hosts will use.
record Blob(UUID id, byte[] payload) {
    Blob {
        payload = payload.clone();
    }

    @Override
    public byte[] payload() {
        return payload.clone();
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Blob that
                && id.equals(that.id)
                && Arrays.equals(payload, that.payload);
    }

    @Override
    public int hashCode() {
        return 31 * id.hashCode() + Arrays.hashCode(payload);
    }
}

/// The pickler for `Slot`, as a host would write one.
final class SlotPickler implements Pickler<Slot> {

    static final SlotPickler INSTANCE = new SlotPickler();

    private SlotPickler() {}

    @Override
    public void serialize(Slot value, ByteBuffer buffer) {
        buffer.put(Slot.encode(value));
    }

    @Override
    public Slot deserialize(ByteBuffer buffer) {
        return Slot.decode(buffer);
    }

    @Override
    public int sizeOf(Slot value) {
        return 8;
    }
}

/// The pickler for `Blob`.
final class BlobPickler implements Pickler<Blob> {

    static final BlobPickler INSTANCE = new BlobPickler();

    private BlobPickler() {}

    @Override
    public void serialize(Blob value, ByteBuffer buffer) {
        buffer.putLong(value.id().getMostSignificantBits());
        buffer.putLong(value.id().getLeastSignificantBits());
        buffer.putInt(value.payload().length);
        buffer.put(value.payload());
    }

    @Override
    public Blob deserialize(ByteBuffer buffer) {
        var msb = buffer.getLong();
        var lsb = buffer.getLong();
        var bytes = new byte[buffer.getInt()];
        buffer.get(bytes);
        return new Blob(new UUID(msb, lsb), bytes);
    }

    @Override
    public int sizeOf(Blob value) {
        return 16 + 4 + value.payload().length;
    }
}