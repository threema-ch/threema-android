use serde::{Deserialize, Serialize};
use tsify::Tsify;
use wasm_bindgen_test::wasm_bindgen_test;

#[wasm_bindgen_test]
fn test_convert() {
    #[derive(Debug, PartialEq, Serialize, Deserialize, Tsify)]
    #[tsify(into_wasm_abi, from_wasm_abi)]
    struct Unit;

    let js = Unit.into_js().unwrap();

    if cfg!(feature = "js") {
        assert!(js.is_undefined());
    } else {
        assert!(js.is_null());
    }

    assert_eq!(Unit::from_js(js).unwrap(), Unit);
}
