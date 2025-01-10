use tsify::Tsify;
#[tsify(into_wasm_abi, from_wasm_abi)]
pub enum GenericEnum<T, U> {
    Unit,
    NewType(T),
    Seq(T, U),
    Map { x: T, y: U },
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
        #[wasm_bindgen(typescript_type = "GenericEnum")]
        pub type JsType;
    }
    impl<T, U> Tsify for GenericEnum<T, U> {
        type JsType = JsType;
        const DECL: &'static str = "export type GenericEnum<T, U> = \"Unit\" | { NewType: T } | { Seq: [T, U] } | { Map: { x: T; y: U } };";
    }
    #[wasm_bindgen(typescript_custom_section)]
    const TS_APPEND_CONTENT: &'static str = "export type GenericEnum<T, U> = \"Unit\" | { NewType: T } | { Seq: [T, U] } | { Map: { x: T; y: U } };";
    impl<T, U> WasmDescribe for GenericEnum<T, U> {
        #[inline]
        fn describe() {
            <Self as Tsify>::JsType::describe()
        }
    }
    impl<T, U> IntoWasmAbi for GenericEnum<T, U>
    where
        Self: _serde::Serialize,
    {
        type Abi = <JsType as IntoWasmAbi>::Abi;
        #[inline]
        fn into_abi(self) -> Self::Abi {
            self.into_js().unwrap_throw().into_abi()
        }
    }
    impl<T, U> OptionIntoWasmAbi for GenericEnum<T, U>
    where
        Self: _serde::Serialize,
    {
        #[inline]
        fn none() -> Self::Abi {
            <JsType as OptionIntoWasmAbi>::none()
        }
    }
    impl<T, U> FromWasmAbi for GenericEnum<T, U>
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
    impl<T, U> OptionFromWasmAbi for GenericEnum<T, U>
    where
        Self: _serde::de::DeserializeOwned,
    {
        #[inline]
        fn is_none(js: &Self::Abi) -> bool {
            <JsType as OptionFromWasmAbi>::is_none(js)
        }
    }
};
