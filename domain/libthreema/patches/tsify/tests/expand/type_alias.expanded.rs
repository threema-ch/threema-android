type TypeAlias<T, U> = Foo<T, i32, U>;
#[automatically_derived]
const _: () = {
    use wasm_bindgen::prelude::*;
    #[wasm_bindgen(typescript_custom_section)]
    const TS_APPEND_CONTENT: &'static str = "export type TypeAlias<T, U> = Foo<T, number, U>;";
};
