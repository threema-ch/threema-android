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
- [License](#license)


## <a name="issues"></a>Bug Reports / Feature Requests / Security Issues

To report bugs and request new features, please contact the Threema support
team through [threema.ch/support](https://threema.ch/support).

If you find a security issue in Threema, please follow responsible disclosure
and report it to us via Threema or by encrypted email, we will try to respond
as quickly as possible. You can find the contact details at
[threema.ch/contact](https://threema.ch/contact) (section "Security").


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
new Threema IDs if the random Device ID has been put on an "allow list" by a
Threema employee.


## <a name="build-variants"></a>Build Variants

There are currently six product flavors:

| Flavor              | Description                                   | License Checks |
| ------------------- | --------------------------------------------- | -------------- |
| `none`              | Used for development                          | Allowlist      |
| `store_google`      | Google Play Store version (regular, paid app) | Google Play    |
| `store_google_work` | Google Play Store version (work, free app)    | Threema Work   |
| `store_threema`     | Threema Store version                         | Threema Shop   |
| `sandbox`           | Uses sandbox test environment¹                | Allowlist      |
| `sandbox_work`      | Uses sandbox test environment¹                | Threema Work   |
| `red`               | Uses sandbox test environment¹                | Threema Work   |

For local testing, we recommend building the `store_google` or `store_threema` build variants.

¹ *The "sandbox" is a backend test environment that is used for internal testing
  at Threema. The sandbox backend can currently not be accessed from the public
  Internet.*


## <a name="building"></a>Building

Before building the app, please read the "Build Variants" section above. For
local testing, we recommend building and running the `store_google` or
`store_threema` build variant.

### Via Command Line

Prerequisites:

- Android SDK
- Android NDK
- bash shell

The application APK can be built using Gradle Wrapper:

    # Play Store variant
    ./gradlew assembleStore_googleDebug

    # Threema Store variant
    ./gradlew assembleStore_threemaDebug

*NOTE:* Threema for Android is developed on Linux machines, we cannot offer any
assistance for building on macOS, Windows, or other operating systems.

### Via Android Studio

The project can be imported into [Android Studio](https://developer.android.com/studio/).
To build and deploy it to a device, click the green "Play" icon.


## <a name="testing"></a>Testing

### Via Command Line

To run unit tests:

    ./gradlew testNoneDebug

To run integration tests (with a device or emulator attached):

    ./gradlew connectedNoneDebugAndroidTest

Note that integration tests run in the same app environment as your "real" app,
so data loss is possible. For example, if an integration test deletes your
Threema ID in order to test the backup restoration process, the Threema ID in
your "real" app may also be gone (if it was signed with the same signing key).
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
`app/src/main/java/ch/threema/client/` directory.

The code of the actual Android app is mainly located in the
`app/src/main/java/ch/threema/app/` directory.


## <a name="contributions"></a>Contributions

We accept GitHub pull requests. Please refer to
<https://threema.ch/open-source/contributions>
for more information on how to contribute.


## <a name="license"></a>License

Threema for Android is licensed under the GNU Affero General Public License v3.

    Copyright (c) 2013-2021 Threema GmbH

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
