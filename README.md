<div align="center">
  <!-- Centered README header hack -->
  <img width="400" src="logo.svg">
  <br><br>
</div>

# Threema for Android

This repository contains the complete source code of
[Threema](https://threema.ch/) for Android.


## Table of Contents

- [Bug Reports / Feature Requests / Security Issues](#issues)
- [Source Code Release Policy](#release-policy)
- [License Checks](#license-checks)
- [Build Variants](#build-variants)
- [Building](#building)
- [Testing](#testing)
- [Reproducible Builds](#reproducible-builds)
- [Code Organization / Architecture](#architecture)
- [Contributions](#contributions)
- [Translating](#translating)
- [License](#license)


## <a name="issues"></a>Bug Reports / Feature Requests / Security Issues

To report bugs and request new features, please contact the Threema support
team through [threema.ch/support](https://threema.ch/support).

If you discover a security issue in Threema, please adhere to the coordinated
vulnerability disclosure model. To be eligible for a bug bounty, please [file a
report on GObugfree](https://app.gobugfree.com/programs/threema) (where all the
details, including the bounty levels, are listed). If you’re not interested in
the bug bounty program, you can contact us via Threema or by email; for contact
details, see [threema.ch/contact](https://threema.ch/en/contact) (section
“Security”).


## <a name="release-policy"></a>Source Code Release Policy

This source code repository will be updated for every public non-beta release.
There will be one commit per released version.

Commits are signed using PGP. See [SECURITY.md](SECURITY.md) for more
information.


## <a name="license-checks"></a>License Checks

While the source code for Threema for Android is published under an open source
license, Threema is still a paid app. To run the app in combination with our
official server infrastructure, you must have bought a license either on Google
Play or in the Threema shop.

The app uses four different license check types, depending on the build variant:

### Google Play Licensing

When creating a new Threema ID using the Threema app bought on Google Play, the
app sends a [LVL license verification token](https://developer.android.com/google/play/licensing/)
to the directory server. This allows the server to verify that you have indeed
bought the app, without being able to identify you.

This means that a self-compiled app using the `google` build variant cannot be
used to create a new Threema ID. You can, however, use an app that was
purchased over Google Play to create an ID and then export a backup. This
backup can then be imported into the self-compiled app.

Note that the ID creation endpoint is monitored for abuse.

### Huawei HMS Licensing

When creating a new Threema ID using the Threema app bought on Huawei AppGallery, the
app sends a [Huawei DRM Signature](https://developer.huawei.com/consumer/en/doc/development/AppGallery-connect-Guides/appgallerykit-paidapps-devguide-0000001073913394)
to the directory server. This allows the server to verify that you have indeed
bought the app, without being able to identify you.

This means that a self-compiled app using the `hms` build variant cannot be
used to create a new Threema ID. You can, however, use an app that was
purchased over Huawei AppGallery to create an ID and then export a backup. This
backup can then be imported into the self-compiled app.

Note that the ID creation endpoint is monitored for abuse.

### Threema Shop Licensing

If you bought a Threema for Android license in the [Threema Shop](https://shop.threema.ch/),
you have received a license key. This license key can be used for license
verification in the `store_threema` build variant.

### Threema Work

If you build the `work` build variant, credentials from the [Threema
Work](https://work.threema.ch/) subscription must be provided in order to use
the app.

### Allowlist

The `none` build variant is used for development. It can only be used to create
new Threema IDs if the random Device ID has been put on an “allow list” by a
Threema employee.


## <a name="build-variants"></a>Build Variants

**Consumer**

There are currently the following product flavors relevant for the consumer
version of Threema:

| Flavor                 | Description                                    | License Checks |
| ---------------------- | ---------------------------------------------- | -------------- |
| `store_google`         | Google Play Store version (regular, paid app)  | Google Play    |
| `hms`                  | Huawei AppGallery version (regular, paid app)  | Huawei HMS     |
| `store_threema`¹       | Threema Shop version (with play services)      | Threema Shop   |
| `libre`¹               | Libre (F-Droid) version (no proprietary code)  | Threema Shop   |

For local testing, we recommend building the `store_google` or `store_threema`
build variants.

¹ The main difference between `store_threema` and `libre` is that the former
  contains proprietary push services and a self-updater while the latter does
  not. Additionally, the `libre` version will use your system emoji, instead
  of bundling emoji graphics.


**Threema Work / OnPrem**

Additionally, these build variants are only relevant for Threema Work or
Threema OnPrem customers:

| Flavor               | Description                                    | License Checks |
| -------------------- | ---------------------------------------------- | -------------- |
| `store_google_work`  | Google Play Store version (work, free app)     | Threema Work   |
| `hms_work`           | Huawei AppGallery version (work, free app)     | Threema Work   |
| `onprem`             | Threema OnPremises version                     | Threema Work   |

**Internal Development**

The following variants are only used for development and testing within Threema:

| Flavor         | Description                                    | License Checks |
|----------------| ---------------------------------------------- | -------------- |
| `none`         | Used for development                           | Allowlist      |
| `green`        | Uses sandbox test environment¹                 | Allowlist      |
| `sandbox_work` | Uses sandbox test environment¹                 | Threema Work   |
| `blue`         | Uses sandbox test environment¹                 | Threema Work   |

¹ *The “sandbox” is a backend test environment that is used for internal testing
  at Threema. The sandbox backend can currently not be accessed from the public
  Internet.*


## <a name="building"></a>Building

Before building the app, please read the “Build Variants” section above. For
local testing, we recommend building and running the `store_google` or
`store_threema` build variant.

### Via Command Line

Prerequisites:

- Android SDK
- Android NDK
- bash shell
- protobuf compiler version 21.12
- Rust compiler and cargo (including the target architectures)

The best way to install all required target architectures for Rust is
through [rustup](https://rustup.rs/):

    TOOLCHAIN=$(grep channel domain/libthreema/rust-toolchain.toml | cut -d'"' -f2)
    rustup target add --toolchain $TOOLCHAIN armv7-linux-androideabi aarch64-linux-android i686-linux-android x86_64-linux-android

The application APK can be built using Gradle Wrapper:

    # Play Store variant
    ./gradlew assembleStore_googleDebug

    # Threema Store variant
    ./gradlew assembleStore_threemaDebug

*NOTE:* Threema for Android is developed on Linux machines, we cannot offer any
assistance for building on macOS, Windows, or other operating systems.

### Via Android Studio

The project can be imported into [Android Studio](https://developer.android.com/studio/).
To build and deploy it to a device, click the green “Play” icon.


## <a name="testing"></a>Testing

### Via Command Line

To run unit tests:

    ./gradlew testNoneDebug

To run integration tests (with a device or emulator attached):

    ./gradlew connectedNoneDebugAndroidTest

Note that integration tests run in the same app environment as your “real” app,
so data loss is possible. For example, if an integration test deletes your
Threema ID in order to test the backup restoration process, the Threema ID in
your “real” app may also be gone (if it was signed with the same signing key).
It is best to only run integration tests on a non-productive device or in an
emulator.

### Via Android Studio

You can also run tests through Android Studio.


## <a name="reproducible-builds"></a>Reproducible Builds

Instructions on how to reproduce the build process used to publish the official
Threema app can be found at
[threema.ch/open-source/reproducible-builds/](https://threema.ch/open-source/reproducible-builds/).


## <a name="architecture"></a>Code Organization / Architecture

Before digging into the codebase, you should read the [Cryptography
Whitepaper](https://threema.ch/press-files/2_documentation/cryptography_whitepaper.pdf)
to understand the design concepts.

Code related to the core functionality (e.g., connecting to the chat server,
encrypting messages, etc.) can be found in the
`domain/src/main/java/ch/threema/` directory.

The code of the actual Android app is located in the
`app/src/main/java/ch/threema/` directory.


## <a name="contributions"></a>Contributions

We accept GitHub pull requests. Please refer to
<https://threema.ch/open-source/contributions>
for more information on how to contribute.

Note that translation fixes should not be contributed through GitHub but
on Crowdin, see next section.


## <a name="translating"></a>Translating

We manage our app translations on Crowdin. If you’re interested in
improving translations, or if you would like to translate Threema to a new
language, please contact us at `support at threema dot ch`.


## <a name="license"></a>License

Threema for Android is licensed under the GNU Affero General Public License v3.

    Copyright (c) 2013-2025 Threema GmbH

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU Affero General Public License, version 3,
    as published by the Free Software Foundation.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
    GNU Affero General Public License for more details.

    You should have received a copy of the GNU Affero General Public License
    along with this program. If not, see <https://www.gnu.org/licenses/>.

The full license text can be found in [`LICENSE.txt`](LICENSE.txt).

If you have questions about the use of self-compiled apps or the license in
general, feel free to [contact us](mailto:opensource@threema.ch). We are
publishing the source code in good faith, with transparency being the main
goal. By having users pay for the development of the app, we can ensure that
our goals sustainably align with the goals of our users: Great privacy and
security, no ads, no collection of user data!
