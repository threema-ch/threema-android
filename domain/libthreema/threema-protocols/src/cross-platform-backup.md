# TODOs

- Add metadata about platform and version?

# Cross-Platform Backup Protocol

The Cross-Platform Backup Protocol allows to create and restore encrypted,
streamable, incremental and validated backups across all platforms.

## Terminology

- `BRK`: The Backup Root Key
- `BPW`: layout-keyThe Backup Password
- `BLK`: The Backup Layout Key
- `BPK`: The Backup Page Key

## General Information

**Endianness:** All integers use little-endian encoding.

**Encryption cipher:** XChaCha-Poly1305

## Key derivation

All cross-platform keys are derived from the backup password `BP`. The `BP`
consists of 64 randomly sampled characters in `[0-9A-Z]` that must be safely
stored by the user.

    BRK = Blake2b(key=BPW, salt='r', personal='3ma-bckp-v1', input=<threema-id> || <salt>)
    BLK = Blake2b(key=BRK, salt='l', personal='3ma-bckp-v1')
    BPK = Blake2b(key=BRK, salt='p', personal='3ma-bckp-v1')

## Backup Storage Layer

### Local File

The following steps are defined as the _Storage Layer Allocation Steps_ for
destination _local file_:

1. Create a new `file` with name '<timestamp>-threema.backup'.
1. Allocate space for the `cross-platform-backup.header` and
   `cross-platform-backup.layout-v1` structs.

The following steps are defined as the _Storage Layer Metadata Write Steps_ for
destination _local file_.
1. Let `header` be a `cross-platform-backup.header` struct.
1. Let `layout` ba a `cross-platform-backup.layout-v1` struct.
1. Let `metadata` be the concatenation of `header` and `layout`.
1. Write `metadata` to the allocated space at the beginning of `file`.

The following steps are defined as the _Storage Layer Data Appending Steps_ for
destination _local file_.

1. Let `encrypted-page` be the `cross-platform-backup.encrypted-page` that should be appended.
1. Append `encrypted-page` to `file`.

### Remote Backup Server

TODO

## Protocol Flows

### Backup Creation

#### Page Allocation Steps

1. Let `category` be one of the data categories for which a page should be
   allocated.
1. Let `encrypted-page-size` be the encrypted page size for the given category:
   <!-- TODO(LIB-48): These numbers should be adapted after a carefully testing with existing data -->
   - past messages: 1 KiB,
   - essential data, hashed csp nonces, hashed d2m nonces: 2 KiB
   - blobs: 100 KiB
   -
1. Set `next-page` to the current allocation offset advance the current
   allocation offset by `encrypted-page-size`.
1. Return `next-page`.

#### Page Writing steps

1. Let `data` be a list of encoded messages of the same category.
1. Let `reserved-page` be the page reserved for the type of `data`.¹
1. Let `remainder` indicate whether there is still more data of this type to be
   written , i.e., either _concluding_ or _pending_.
1. Join `data` to a single byte string, partition it to chunks each fitting into
   a single page and let `chunks` be the result.
1. For each `chunk` in `chunks`:
   1. If `chunk` is not the last chunk or `remainder` is _pending_, run the
      _Page Allocation Steps_ and let `next-page` be the result.
   1. Create an `encrypted-page` message with `chunk` and let `encrypted-page`
      be the result.
   1. If not all pages prior to `reserved-page` are marked as written: Add
      `encrypted-page` to the pending pages.
   1. If all pages prior to `reserved-page` are marked as written:
      1. Run the _Storage Layer Appending Steps_ with `encrypted-page`.
      1. Run the _Storage Layer Appending Steps_ for all pending pages
         directly following `reserved-page`.
   1. Set `reserved-page` to `next-page`.
1. Return `reserved-page`.

¹: These steps have been carefully crafted to handle interleaved page writes for
data of different types.

#### Essential Data Backup Steps

1. Run the _Page Allocation Steps_ and let `first-page` be the result.
1. Let `reserved-page` be `first-page`.
1. For each batch of blobs referenced of all kinds of profile pictures:
   1. Let `data` be the list of all blobs encoded as
      `EssentialDataContainer.blob_data`.
   1. Run the _Page Writing Steps_ with `data` and let `next-page` be the
      result.
   1. Set `reserved-page` to `next-page`.
1. Create the `EssentialDataContainer.essential_data` message and let `data` be
   the result.
1. Run the _Page Writing Steps_.
1. Return `first-page`.

#### Common Nonce Backup Steps

1. Let `hashed-nonces` be the hashed nonces to be backed up.
<!-- 1. Let `protocol` be the protocol of the hashed nonces that should be backed up, i.e., -->
<!--    either _csp_ or _d2m_. -->
1. Run the _Page Allocation Steps_ and let `first-page` be the result.
1. Set `reserved-page` to `first-page`.
1. For each batch of `hashed-nonces`:
   1. Let `data` be the concatenation of all hashed nonces of the batch encoded
      as `hashed-nonce-container`.
   1. Run the _Page Writing Steps_ and let `next-page` be the result.
   1. Set `reserved-page` to `next-page`.
1. Return `first-page`.

#### Message Backup Steps

#### Backup Creation Abortion Steps

1. Let `error` be the cause of the abortion.
1. Delete all previously written data.
1. Log `error` and inform the user that the backup creation has failed.
1. Leave the _read-only_ mode.

#### Backup Creation Steps

The following steps define the process to create a cross-platform backup:

1. Retrieve `BPW` from the application storage. If it does not exist, randomly
   sample a new `BPW`, display it to the user and store it in the application
   storage.
1. Let `destination` be either _local file_ or _backup server_.
1. Let `salt` be 32 randomly generated bytes.
1. Derive `BRK`, `BLK`, and `BPK`.
1. Enter _read-only_ mode and inform the user that the backup creation is in
   progress.
1. Run the _Storage Layer Allocation Steps_. If this
   fails, run the _Backup Creation Abortion Steps_ and exceptionally abort these
   steps.
1. Run the _Essential Data Backup Steps_ and let `essential-data-offset` be the
   result. If this fails, run the _Backup Creation Abortion Steps_ and
   exceptionally abort these steps.
1. Let `hashed-csp-nonces` be all hashed CSP nonces.
1. Run the _Common Nonce Backup Steps_ with `hashed-csp-nonces`.
   and let `hashed-csp-nonces-offset` be the result. If this fails, run the
   _Backup Creation Abortion Steps_ and exceptionally abort these steps.
1. Let `hashed-d2m-nonces` be all hashed D2M nonces.
1. Run the _Common Nonce Backup Steps_ with `hashed-d2m-nonces`
   and let `hashed-d2m-nonces-offset` be the result. If this fails, run the
   _Backup Creation Abortion Steps_ and exceptionally abort these steps.
1. <!-- TODO: Backup all other data -->
1. Let `delta-offset` be the current allocation offset.
1. Create the `cross-platform-backup.header` struct and let `header` be the
   result.
1. Create the `cross-platform-backup.layout-v1-encrypted` struct and let `layout` be the result.
1. Run the _Storage Layer Metadata Write Steps_ with `header` and `layout`. If this
   fails, run the _Backup Creation Abortion Steps_ and exceptionally abort these
   steps.
1. Leave _read-only_ mode and inform the user that the backup creation
   succeeded.

### Backup Restoring

<!-- TODO -->
