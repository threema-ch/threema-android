#![allow(clippy::wrong_self_convention)]

#[cfg(all(feature = "json", not(feature = "js")))]
pub use gloo_utils::format::JsValueSerdeExt;
pub use tsify_macros::*;
#[cfg(feature = "wasm-bindgen")]
use wasm_bindgen::{JsCast, JsValue};

pub trait Tsify {
    #[cfg(feature = "wasm-bindgen")]
    type JsType: JsCast;

    const DECL: &'static str;

    #[cfg(all(feature = "json", not(feature = "js")))]
    #[inline]
    fn into_js(&self) -> serde_json::Result<Self::JsType>
    where
        Self: serde::Serialize,
    {
        JsValue::from_serde(self).map(JsCast::unchecked_from_js)
    }

    #[cfg(all(feature = "json", not(feature = "js")))]
    #[inline]
    fn from_js<T: Into<JsValue>>(js: T) -> serde_json::Result<Self>
    where
        Self: serde::de::DeserializeOwned,
    {
        js.into().into_serde()
    }

    #[cfg(feature = "js")]
    #[inline]
    fn into_js(&self) -> Result<Self::JsType, serde_wasm_bindgen::Error>
    where
        Self: serde::Serialize,
    {
        serde_wasm_bindgen::to_value(self).map(JsCast::unchecked_from_js)
    }

    #[cfg(feature = "js")]
    #[inline]
    fn from_js<T: Into<JsValue>>(js: T) -> Result<Self, serde_wasm_bindgen::Error>
    where
        Self: serde::de::DeserializeOwned,
    {
        serde_wasm_bindgen::from_value(js.into())
    }
}
