#![allow(dead_code)]

use indoc::indoc;
use pretty_assertions::assert_eq;
use tsify::Tsify;

#[test]
fn test_skip() {
    #[derive(Tsify)]
    struct Struct {
        a: i32,
        #[serde(skip)]
        b: i32,
        #[serde(skip_serializing)]
        c: i32,
        #[serde(skip_deserializing)]
        d: i32,
    }

    assert_eq!(
        Struct::DECL,
        indoc! {"
            export interface Struct {
                a: number;
            }"
        }
    );

    #[derive(Tsify)]
    struct Tuple(#[serde(skip)] String, i32);

    assert_eq!(Tuple::DECL, "export type Tuple = [number];");

    #[derive(Tsify)]
    #[tsify(namespace)]
    enum Enum {
        #[serde(skip)]
        A,
        #[serde(skip_serializing)]
        B,
        #[serde(skip_deserializing)]
        C,
        D,
    }

    let expected = indoc! {r#"
        declare namespace Enum {
            export type D = "D";
        }
        
        export type Enum = "D";"#
    };

    assert_eq!(Enum::DECL, expected);
}
