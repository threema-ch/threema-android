This is a fork of https://github.com/madonoharu/tsify with a patch applied to
map bytes-like types to `Uint8Array` correctly as we use them.

The patch resides in lgr/tsify/-/tree/map-bytes-like-to-uint8array

TODO(LIB3-8): We shall get this upstream or remove the integration of tsify.

# Tsify

Tsify is a library for generating TypeScript definitions from Rust code.

Using this with [`wasm-bindgen`](https://github.com/rustwasm/wasm-bindgen) will
automatically output the types to `.d.ts`.

Inspired by
[`typescript-definitions`](https://github.com/arabidopsis/typescript-definitions)
and [`ts-rs`](https://github.com/Aleph-Alpha/ts-rs).

## Example

<details>
<summary>
Click to show Cargo.toml.
</summary>

```toml
[dependencies]
tsify = "0.4.5"
serde = { version = "1.0", features = ["derive"] }
wasm-bindgen = { version = "0.2" }
```

</details>

```rust
use serde::{Deserialize, Serialize};
use tsify::Tsify;
use wasm_bindgen::prelude::*;

#[derive(Tsify, Serialize, Deserialize)]
#[tsify(into_wasm_abi, from_wasm_abi)]
pub struct Point {
    x: i32,
    y: i32,
}

#[wasm_bindgen]
pub fn into_js() -> Point {
    Point { x: 0, y: 0 }
}

#[wasm_bindgen]
pub fn from_js(point: Point) {}
```

Will generate the following `.d.ts` file:

```ts
/* tslint:disable */
/* eslint-disable */
/**
 * @returns {Point}
 */
export function into_js(): Point;
/**
 * @param {Point} point
 */
export function from_js(point: Point): void;
export interface Point {
  x: number;
  y: number;
}
```

This is the behavior due to
[`typescript_custom_section`](https://rustwasm.github.io/docs/wasm-bindgen/reference/attributes/on-rust-exports/typescript_custom_section.html)
and
[`Rust Type conversions`](https://rustwasm.github.io/docs/wasm-bindgen/contributing/design/rust-type-conversions.html).

## Crate Features

- `json` (default) enables serialization through
  [`serde_json`](https://github.com/serde-rs/json).
- `js` enables serialization through
  [`serde-wasm-bindgen`](https://github.com/cloudflare/serde-wasm-bindgen) and
  generates the appropriate types for it. This will be the default in future
  versions.

## Attributes

Tsify container attributes

- `into_wasm_abi` implements `IntoWasmAbi` and `OptionIntoWasmAbi`. This can be
  converted directly from Rust to JS via `serde_json` or `serde-wasm-bindgen`.
- `from_wasm_abi` implements `FromWasmAbi` and `OptionFromWasmAbi`. This is the
  opposite operation of the above.
- `namespace` generates a namespace for the enum variants.

Tsify field attributes

- `type`
- `optional`

Serde attributes

- `rename`
- `rename-all`
- `tag`
- `content`
- `untagged`
- `skip`
- `skip_serializing`
- `skip_deserializing`
- `skip_serializing_if = "Option::is_none"`
- `flatten`
- `default`
- `transparent`

## Type Override

```rust
use tsify::Tsify;

#[derive(Tsify)]
pub struct Foo {
    #[tsify(type = "0 | 1 | 2")]
    x: i32,
}
```

Generated type:

```ts
export interface Foo {
  x: 0 | 1 | 2;
}
```

## Optional Properties

```rust
#[derive(Tsify)]
struct Optional {
    #[tsify(optional)]
    a: Option<i32>,
    #[serde(skip_serializing_if = "Option::is_none")]
    b: Option<String>,
    #[serde(default)]
    c: i32,
}
```

Generated type:

```ts
export interface Optional {
  a?: number;
  b?: string;
  c?: number;
}
```

## Enum

```rust
#[derive(Tsify)]
enum Color {
    Red,
    Blue,
    Green,
    Rgb(u8, u8, u8),
    Hsv {
        hue: f64,
        saturation: f64,
        value: f64,
    },
}
```

Generated type:

```ts
export type Color =
  | 'Red'
  | 'Blue'
  | 'Green'
  | {Rgb: [number, number, number]}
  | {Hsv: {hue: number; saturation: number; value: number}};
```

## Enum with namespace

```rust
#[derive(Tsify)]
#[tsify(namespace)]
enum Color {
    Red,
    Blue,
    Green,
    Rgb(u8, u8, u8),
    Hsv {
        hue: f64,
        saturation: f64,
        value: f64,
    },
}
```

Generated type:

```ts
declare namespace Color {
  export type Red = 'Red';
  export type Blue = 'Blue';
  export type Green = 'Green';
  export type Rgb = {Rgb: [number, number, number]};
  export type Hsv = {Hsv: {hue: number; saturation: number; value: number}};
}

export type Color =
  | 'Red'
  | 'Blue'
  | 'Green'
  | {Rgb: [number, number, number]}
  | {Hsv: {hue: number; saturation: number; value: number}};
```

## Type Aliases

```rust
use tsify::{declare, Tsify};

#[derive(Tsify)]
struct Foo<T>(T);

#[declare]
type Bar = Foo<i32>;
```

Generated type:

```ts
export type Foo<T> = T;
export type Bar = Foo<number>;
```
