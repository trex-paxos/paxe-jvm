# paxe-jvm: PAXE datagram encryption

## What this repository is

PAXE is AES-256-GCM authenticated encryption for datagrams: a wire format with
out-of-band pre-shared keys, key epochs, and standard one-recipient plus DEK
(fan-out) frame protection. It is a transport. It seals bytes and does not know
what they mean.

It was moved out of the `uvrr-jvm` repository, where it was a module named
`uvrr-paxe`, because a transport that must be consumed by a CLI, by a server, and
by other people's consensus libraries is not that project's business. The
separation is the same one the Rust side already has: the reference crate is
`lua-lunet/paxe-core`, a separate repository that depends on no consensus core.

## The reference is normative

`github.com/lua-lunet/paxe-core` is the reference implementation, and `PAXE.md`
in that repository is the wire and security contract. When this module and the
Rust crate disagree, the Rust crate wins and this module is the bug.

Two properties are load-bearing and must not be "simplified":

- **One opaque failure.** Every frame-level failure — parse, unknown key,
  authentication, even an unconfigured keystore — collapses to a single outcome.
  A receiver that explains why a forgery failed is a decryption oracle. Typed
  reasons are counted at the reject point, before the collapse, and never
  reported to the peer.
- **u16 peer ids, u32 channels.** Peer ids are 16 bits and channels are 32, as the
  reference has it. `PeerId` holds the value in an `int` so the whole unsigned
  range is representable, and the narrowing to `short` happens in exactly one
  place: the prefix codec, because that is the only place it is a wire fact. On
  read the mask is applied, or a peer above 32767 would come back negative.

## The no-consensus-dependency rule

This module names no other library's types at its boundary. It takes `PeerId` and
`Channel`, exactly as the reference takes `to_id: u16` and `channel: u32`.

The reason is concrete and not aesthetic: `PaxeNetwork` used to register uvrr-lib's
`PickleMsg` and `CommandPickler` for its two default channels. That one line was
the entire dependency. A transport cannot have a hardcoded opinion about what its
payload means, and a consumer who wants PAXE should not be forced to adopt a
consensus library to get it.

So the host registers a `Pickler` per channel with `Builder.withPickler`, and the
module ships none. If a change ever reintroduces a pickler that knows about a
specific protocol, it is a regression, not a convenience.

The tests follow the same rule. They use their own trivial payload types (`Slot`,
`Blob` in `Payloads.java`) rather than borrowing a consensus library's message
types as carriers. A transport test that imports a consensus library cannot tell a
codec bug from a protocol change.

## Boundaries

| Type | Who owns it |
|---|---|
| transport, timers, socket | the host |
| payload type and its codec | the host, via `Pickler` |
| membership | the host, via `NodeEndpoints` |
| sealing, opening, epochs, the keystore | PAXE |

## Testing

`mvn verify` runs the suite: the codec's byte-exact prefix layout, the keystore
and its epochs, broadcast and overlap, the security properties (tampered,
truncated, wrong-PSK, retired-epoch, oversized payload all collapse to the one
outcome without stopping the receive loop), and the TLS provisioner.

Integration tests that drive a real consensus cluster over PAXE do not belong here
— they belong with the consensus code, because they are that code's tests. They
moved to `uvrr-jvm` at `uvrr-lib/src/test/java/com/github/trex_paxos/integration/`.
