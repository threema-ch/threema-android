#![allow(dead_code)]

use pretty_assertions::assert_eq;
use tsify::Tsify;

#[test]
fn test_transparent() {
    #[derive(Tsify)]
    #[serde(transparent)]
    struct A(String, #[serde(skip)] f64);

    #[derive(Tsify)]
    #[serde(transparent)]
    struct B {
        #[serde(skip)]
        x: String,
        y: f64,
    }

    assert_eq!("export type A = string;", A::DECL);
    assert_eq!("export type B = number;", B::DECL);
}
