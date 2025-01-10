use std::borrow::Cow;
use tsify::Tsify;
#[tsify(into_wasm_abi, from_wasm_abi)]
struct Borrow<'a> {
    raw: &'a str,
    cow: Cow<'a, str>,
}
#[automatically_derived]
const _: () = {
    extern crate serde as _serde;
    use tsify::Tsify;
    use wasm_bindgen::{
        convert::{FromWasmAbi, IntoWasmAbi, OptionFromWasmAbi, OptionIntoWasmAbi},
        describe::WasmDescribe, prelude::*,
    };
    #[wasm_bindgen]
    extern "C" {
        #[wasm_bindgen(typescript_type = "Borrow")]
        pub type JsType;
    }
    impl<'a> Tsify for Borrow<'a> {
        type JsType = JsType;
        const DECL: &'static str = "export interface Borrow {\n    raw: string;\n    cow: string;\n}";
    }
    #[wasm_bindgen(typescript_custom_section)]
    const TS_APPEND_CONTENT: &'static str = "export interface Borrow {\n    raw: string;\n    cow: string;\n}";
    impl<'a> WasmDescribe for Borrow<'a> {
        #[inline]
        fn describe() {
            <Self as Tsify>::JsType::describe()
        }
    }
    impl<'a> IntoWasmAbi for Borrow<'a>
    where
        Self: _serde::Serialize,
    {
        type Abi = <JsType as IntoWasmAbi>::Abi;
        #[inline]
        fn into_abi(self) -> Self::Abi {
            self.into_js().unwrap_throw().into_abi()
        }
    }
    impl<'a> OptionIntoWasmAbi for Borrow<'a>
    where
        Self: _serde::Serialize,
    {
        #[inline]
        fn none() -> Self::Abi {
            <JsType as OptionIntoWasmAbi>::none()
        }
    }
    impl<'a> FromWasmAbi for Borrow<'a>
    where
        Self: _serde::de::DeserializeOwned,
    {
        type Abi = <JsType as FromWasmAbi>::Abi;
        #[inline]
        unsafe fn from_abi(js: Self::Abi) -> Self {
            let result = Self::from_js(&JsType::from_abi(js));
            if let Err(err) = result {
                wasm_bindgen::throw_str(err.to_string().as_ref());
            }
            result.unwrap_throw()
        }
    }
    impl<'a> OptionFromWasmAbi for Borrow<'a>
    where
        Self: _serde::de::DeserializeOwned,
    {
        #[inline]
        fn is_none(js: &Self::Abi) -> bool {
            <JsType as OptionFromWasmAbi>::is_none(js)
        }
    }
};
