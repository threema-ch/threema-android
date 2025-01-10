#![allow(dead_code)]

use indoc::indoc;
use pretty_assertions::assert_eq;
use tsify::Tsify;

struct Foo {
    a: i32,
    b: String,
}

#[test]
fn test_externally_tagged_enum() {
    #[derive(Tsify)]
    enum External {
        Struct { x: String, y: i32 },
        EmptyStruct {},
        Tuple(i32, String),
        EmptyTuple(),
        Newtype(Foo),
        Unit,
    }

    let expected = indoc! {r#"
        export type External = { Struct: { x: string; y: number } } | { EmptyStruct: {} } | { Tuple: [number, string] } | { EmptyTuple: [] } | { Newtype: Foo } | "Unit";"#
    };

    assert_eq!(External::DECL, expected);
}

#[test]
fn test_externally_tagged_enum_with_namespace() {
    #[derive(Tsify)]
    #[tsify(namespace)]
    enum External {
        Struct { x: String, y: i32 },
        EmptyStruct {},
        Tuple(i32, String),
        EmptyTuple(),
        Newtype(Foo),
        Unit,
    }

    let expected = indoc! {r#"
        type __ExternalFoo = Foo;
        declare namespace External {
            export type Struct = { Struct: { x: string; y: number } };
            export type EmptyStruct = { EmptyStruct: {} };
            export type Tuple = { Tuple: [number, string] };
            export type EmptyTuple = { EmptyTuple: [] };
            export type Newtype = { Newtype: __ExternalFoo };
            export type Unit = "Unit";
        }
        
        export type External = { Struct: { x: string; y: number } } | { EmptyStruct: {} } | { Tuple: [number, string] } | { EmptyTuple: [] } | { Newtype: Foo } | "Unit";"#
    };

    assert_eq!(External::DECL, expected);
}

#[test]
fn test_internally_tagged_enum() {
    #[derive(Tsify)]
    #[serde(tag = "t")]
    enum Internal {
        Struct { x: String, y: i32 },
        EmptyStruct {},
        Newtype(Foo),
        Unit,
    }

    let expected = indoc! {r#"
        export type Internal = { t: "Struct"; x: string; y: number } | { t: "EmptyStruct" } | ({ t: "Newtype" } & Foo) | { t: "Unit" };"#
    };

    assert_eq!(Internal::DECL, expected);
}

#[test]
fn test_internally_tagged_enum_with_namespace() {
    #[derive(Tsify)]
    #[serde(tag = "t")]
    #[tsify(namespace)]
    enum Internal {
        Struct { x: String, y: i32 },
        EmptyStruct {},
        Newtype(Foo),
        Unit,
    }

    let expected = indoc! {r#"
        type __InternalFoo = Foo;
        declare namespace Internal {
            export type Struct = { t: "Struct"; x: string; y: number };
            export type EmptyStruct = { t: "EmptyStruct" };
            export type Newtype = { t: "Newtype" } & __InternalFoo;
            export type Unit = { t: "Unit" };
        }
        
        export type Internal = { t: "Struct"; x: string; y: number } | { t: "EmptyStruct" } | ({ t: "Newtype" } & Foo) | { t: "Unit" };"#
    };

    assert_eq!(Internal::DECL, expected);
}

#[test]
fn test_adjacently_tagged_enum() {
    #[derive(Tsify)]
    #[serde(tag = "t", content = "c")]
    enum Adjacent {
        Struct { x: String, y: i32 },
        EmptyStruct {},
        Tuple(i32, String),
        EmptyTuple(),
        Newtype(Foo),
        Unit,
    }

    let expected = indoc! {r#"
    export type Adjacent = { t: "Struct"; c: { x: string; y: number } } | { t: "EmptyStruct"; c: {} } | { t: "Tuple"; c: [number, string] } | { t: "EmptyTuple"; c: [] } | { t: "Newtype"; c: Foo } | { t: "Unit" };"#
    };

    assert_eq!(Adjacent::DECL, expected);
}

#[test]
fn test_adjacently_tagged_enum_with_namespace() {
    #[derive(Tsify)]
    #[serde(tag = "t", content = "c")]
    #[tsify(namespace)]
    enum Adjacent {
        Struct { x: String, y: i32 },
        EmptyStruct {},
        Tuple(i32, String),
        EmptyTuple(),
        Newtype(Foo),
        Unit,
    }

    let expected = indoc! {r#"
        type __AdjacentFoo = Foo;
        declare namespace Adjacent {
            export type Struct = { t: "Struct"; c: { x: string; y: number } };
            export type EmptyStruct = { t: "EmptyStruct"; c: {} };
            export type Tuple = { t: "Tuple"; c: [number, string] };
            export type EmptyTuple = { t: "EmptyTuple"; c: [] };
            export type Newtype = { t: "Newtype"; c: __AdjacentFoo };
            export type Unit = { t: "Unit" };
        }
    
        export type Adjacent = { t: "Struct"; c: { x: string; y: number } } | { t: "EmptyStruct"; c: {} } | { t: "Tuple"; c: [number, string] } | { t: "EmptyTuple"; c: [] } | { t: "Newtype"; c: Foo } | { t: "Unit" };"#
    };

    assert_eq!(Adjacent::DECL, expected);
}

#[test]
fn test_untagged_enum() {
    #[derive(Tsify)]
    #[serde(untagged)]
    enum Untagged {
        Struct { x: String, y: i32 },
        EmptyStruct {},
        Tuple(i32, String),
        EmptyTuple(),
        Newtype(Foo),
        Unit,
    }

    let expected = if cfg!(feature = "js") {
        indoc! {r#"
            export type Untagged = { x: string; y: number } | {} | [number, string] | [] | Foo | undefined;"#
        }
    } else {
        indoc! {r#"
            export type Untagged = { x: string; y: number } | {} | [number, string] | [] | Foo | null;"#
        }
    };

    assert_eq!(Untagged::DECL, expected);
}

#[test]
fn test_untagged_enum_with_namespace() {
    #[derive(Tsify)]
    #[serde(untagged)]
    #[tsify(namespace)]
    enum Untagged {
        Struct { x: String, y: i32 },
        EmptyStruct {},
        Tuple(i32, String),
        EmptyTuple(),
        Newtype(Foo),
        Unit,
    }

    let expected = if cfg!(feature = "js") {
        indoc! {r#"
            type __UntaggedFoo = Foo;
            declare namespace Untagged {
                export type Struct = { x: string; y: number };
                export type EmptyStruct = {};
                export type Tuple = [number, string];
                export type EmptyTuple = [];
                export type Newtype = __UntaggedFoo;
                export type Unit = undefined;
            }
        
            export type Untagged = { x: string; y: number } | {} | [number, string] | [] | Foo | undefined;"#
        }
    } else {
        indoc! {r#"
            type __UntaggedFoo = Foo;
            declare namespace Untagged {
                export type Struct = { x: string; y: number };
                export type EmptyStruct = {};
                export type Tuple = [number, string];
                export type EmptyTuple = [];
                export type Newtype = __UntaggedFoo;
                export type Unit = null;
            }
        
            export type Untagged = { x: string; y: number } | {} | [number, string] | [] | Foo | null;"#
        }
    };

    assert_eq!(Untagged::DECL, expected);
}

#[test]
fn test_module_reimport_enum() {
    #[derive(Tsify)]
    #[tsify(namespace)]
    enum Internal {
        Struct { x: String, y: i32 },
        EmptyStruct {},
        Tuple(i32, String),
        EmptyTuple(),
        Newtype(Foo),
        Newtype2(Foo),
        Unit,
    }

    let expected = indoc! {r#"
        type __InternalFoo = Foo;
        declare namespace Internal {
            export type Struct = { Struct: { x: string; y: number } };
            export type EmptyStruct = { EmptyStruct: {} };
            export type Tuple = { Tuple: [number, string] };
            export type EmptyTuple = { EmptyTuple: [] };
            export type Newtype = { Newtype: __InternalFoo };
            export type Newtype2 = { Newtype2: __InternalFoo };
            export type Unit = "Unit";
        }

        export type Internal = { Struct: { x: string; y: number } } | { EmptyStruct: {} } | { Tuple: [number, string] } | { EmptyTuple: [] } | { Newtype: Foo } | { Newtype2: Foo } | "Unit";"#
    };

    assert_eq!(Internal::DECL, expected);
}

#[test]
fn test_module_template_enum() {
    struct Test<T> {
        inner: T,
    }

    #[derive(Tsify)]
    #[tsify(namespace)]
    enum Internal<T> {
        Newtype(Test<T>),
        NewtypeF(Test<Foo>),
        NewtypeL(Test<Foo>),
        Unit,
    }
    let expected = indoc! {r#"
        type __InternalFoo = Foo;
        type __InternalTest<A> = Test<A>;
        declare namespace Internal {
            export type Newtype<T> = { Newtype: __InternalTest<T> };
            export type NewtypeF = { NewtypeF: __InternalTest<__InternalFoo> };
            export type NewtypeL = { NewtypeL: __InternalTest<__InternalFoo> };
            export type Unit = "Unit";
        }

        export type Internal<T> = { Newtype: Test<T> } | { NewtypeF: Test<Foo> } | { NewtypeL: Test<Foo> } | "Unit";"#
    };

    assert_eq!(expected, Internal::<Foo>::DECL);
}

struct Test<T> {
    inner: T,
}

#[test]
fn test_module_template_enum_inner() {
    struct Test<T> {
        inner: T,
    }

    #[derive(Tsify)]
    #[tsify(namespace)]
    enum Internal {
        Newtype(Test<Foo>),
        Unit,
    }

    let expected = indoc! {r#"
        type __InternalFoo = Foo;
        type __InternalTest<A> = Test<A>;
        declare namespace Internal {
            export type Newtype = { Newtype: __InternalTest<__InternalFoo> };
            export type Unit = "Unit";
        }
    
        export type Internal = { Newtype: Test<Foo> } | "Unit";"#
    };

    assert_eq!(Internal::DECL, expected);
}
