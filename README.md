# paxe-jvm

PAXE: AES-256-GCM authenticated encryption for **datagrams**, with out-of-band
pre-shared keys (PSKs) and key epochs.

The wire and security contract is defined by the Rust reference at
[lua-lunet/paxe-core](https://github.com/lua-lunet/paxe-core), which is normative
for both the format and the failure handling.

## Sans-I/O

PAXE is a transport. It seals bytes and does not know what they mean:

```java
var store = new ClusterKeyManager(clusterPsk, (byte) 0);
var frame = PaxePacket.seal(payload, store, new PeerId(200), Channel.CONSENSUS, (byte) 0);
var (header, flags, body) = PaxePacket.open(frame, store);
```

No sockets at the codec, no threads, no async runtime, no callbacks. The host owns
transport and timers.

## Zero dependencies on any consensus library

This module names no other library's types at its boundary. It takes plain
`PeerId` (a `u16`) and `Channel` (a `u32`), exactly as the Rust reference takes
`to_id: u16` and `channel: u32`. That is deliberate: PAXE is a transport, so a
host must be able to use it without adopting a consensus protocol.

The payload type belongs to the host. Register a `Pickler` per channel and PAXE
carries whatever your consensus protocol puts there:

```java
var paxe = new PaxeNetwork.Builder(keyManager, port, local, membership)
    .withPickler(Channel.CONSENSUS, myConsensusPickler)
    .build();
```

`PaxeNetwork` ships **no pickler of its own**. Registering one that knew about a
consensus protocol is what made this module depend on that protocol in the first
place; the seam is now explicit and the tests exercise it with their own trivial
payload types (`Slot`, `Blob`) rather than borrowing a protocol's message types
as convenient carriers. A transport test that imports a consensus library cannot
tell a codec bug from a protocol change.

## Runtime dependencies

- `org.bouncycastle:bcprov-jdk18on`, `org.bouncycastle:bctls-jdk18on` — the TLS
  provisioner only. The datagram codec itself is JDK `javax.crypto`.

Java 25, built with `-Werror`.

## Layout

- `src/main/java/com/github/trex_paxos/paxe/` — `PaxeNetwork` (the host-facing
  transport), `PaxePacket` (the codec), `ClusterKeyManager`, `PaxeProtocol`,
  `PaxeTransport`, and the boundary types `PeerId`, `Channel`, `Pickler`,
  `NetworkAddress`, `NodeEndpoints`
- `src/main/java/com/github/trex_paxos/paxe/provision/` — the TLS provisioning
  handshake that distributes cluster keys out of band

## Failure handling

Every frame-level failure — parse, unknown key, authentication, even an
unconfigured keystore — collapses to **one** opaque outcome. A receiver that
explains why a forgery failed is a decryption oracle. The typed reason is counted
before the collapse, never reported to the peer.

## Build

```sh
mvn verify
```