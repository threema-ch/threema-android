#![allow(dead_code)]

use indoc::indoc;
use pretty_assertions::assert_eq;
use tsify::Tsify;

#[test]
fn test_rename() {
    #[derive(Tsify)]
    struct RenamedStruct {
        #[serde(rename = "X")]
        x: i32,
        #[serde(rename = "Y")]
        y: i32,
    }

    assert_eq!(
        RenamedStruct::DECL,
        indoc! {"
            export interface RenamedStruct {
                X: number;
                Y: number;
            }"
        }
    );

    #[derive(Tsify)]
    enum RenamedEnum {
        #[serde(rename = "X")]
        A(bool),
        #[serde(rename = "Y")]
        B(i64),
        #[serde(rename = "Z")]
        C(String),
        #[serde(skip)]
        D(i32),
    }

    let expected = indoc! {r#"
        export type RenamedEnum = { X: boolean } | { Y: number } | { Z: string };"#

    };

    assert_eq!(RenamedEnum::DECL, expected);
}

#[test]
fn test_rename_all() {
    #[allow(clippy::enum_variant_names)]
    #[derive(Tsify)]
    #[serde(rename_all = "snake_case")]
    #[tsify(namespace)]
    enum Enum {
        SnakeCase {
            foo: bool,
            foo_bar: bool,
        },
        #[serde(rename_all = "camelCase")]
        CamelCase {
            foo: bool,
            foo_bar: bool,
        },
        #[serde(rename_all = "kebab-case")]
        KebabCase {
            foo: bool,
            foo_bar: bool,
        },
        #[serde(rename_all = "SCREAMING_SNAKE_CASE")]
        ScreamingSnakeCase {
            foo: bool,
            foo_bar: bool,
        },
    }

    #[derive(Tsify)]
    #[serde(rename_all = "PascalCase")]
    struct PascalCase {
        foo: bool,
        foo_bar: bool,
    }

    #[derive(Tsify)]
    #[serde(rename_all = "SCREAMING-KEBAB-CASE")]
    struct ScreamingKebab {
        foo: bool,
        foo_bar: bool,
    }

    let expected = indoc! {r#"
        declare namespace Enum {
            export type snake_case = { snake_case: { foo: boolean; foo_bar: boolean } };
            export type camel_case = { camel_case: { foo: boolean; fooBar: boolean } };
            export type kebab_case = { kebab_case: { foo: boolean; "foo-bar": boolean } };
            export type screaming_snake_case = { screaming_snake_case: { FOO: boolean; FOO_BAR: boolean } };
        }
        
        export type Enum = { snake_case: { foo: boolean; foo_bar: boolean } } | { camel_case: { foo: boolean; fooBar: boolean } } | { kebab_case: { foo: boolean; "foo-bar": boolean } } | { screaming_snake_case: { FOO: boolean; FOO_BAR: boolean } };"#
    };

    assert_eq!(Enum::DECL, expected);

    assert_eq!(
        PascalCase::DECL,
        indoc! {"
            export interface PascalCase {
                Foo: boolean;
                FooBar: boolean;
            }"
        }
    );

    assert_eq!(
        ScreamingKebab::DECL,
        indoc! {r#"
            export interface ScreamingKebab {
                FOO: boolean;
                "FOO-BAR": boolean;
            }"#
        }
    );
}
