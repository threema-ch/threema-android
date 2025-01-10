#![allow(dead_code)]

use indoc::indoc;
use pretty_assertions::assert_eq;
use tsify::Tsify;

#[test]
fn test_flatten() {
    #[derive(Tsify)]
    struct A {
        a: i32,
        b: String,
    }

    #[derive(Tsify)]
    struct B {
        #[serde(flatten)]
        extra: A,
        c: i32,
    }

    assert_eq!(
        B::DECL,
        indoc! {"
            export interface B extends A {
                c: number;
            }"
        }
    );
}

#[test]
fn test_flatten_option() {
    #[derive(Tsify)]
    struct A {
        a: i32,
        b: String,
    }

    #[derive(Tsify)]
    struct B {
        #[serde(flatten)]
        extra: Option<A>,
        c: i32,
    }

    assert_eq!(
        B::DECL,
        indoc! {"
            export type B = { c: number } & (A | {});"
        }
    );
}
