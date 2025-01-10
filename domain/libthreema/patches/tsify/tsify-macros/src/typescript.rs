use std::{collections::HashSet, fmt::Display};

use serde_derive_internals::{ast::Style, attr::TagType};

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum TsKeywordTypeKind {
    Number,
    Bigint,
    Boolean,
    String,
    Void,
    Undefined,
    Null,
    Never,
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct TsTypeElement {
    pub key: String,
    pub type_ann: TsType,
    pub optional: bool,
}

impl From<TsTypeElement> for TsTypeLit {
    fn from(m: TsTypeElement) -> Self {
        TsTypeLit { members: vec![m] }
    }
}

impl From<TsTypeElement> for TsType {
    fn from(m: TsTypeElement) -> Self {
        TsType::TypeLit(m.into())
    }
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct TsTypeLit {
    pub members: Vec<TsTypeElement>,
}

impl From<TsTypeLit> for TsType {
    fn from(lit: TsTypeLit) -> Self {
        TsType::TypeLit(lit)
    }
}

impl TsTypeLit {
    fn get_mut(&mut self, key: &String) -> Option<&mut TsTypeElement> {
        self.members.iter_mut().find(|member| &member.key == key)
    }

    fn and(self, other: Self) -> Self {
        let init = TsTypeLit { members: vec![] };

        self.members
            .into_iter()
            .chain(other.members.into_iter())
            .fold(init, |mut acc, m| {
                if let Some(acc_m) = acc.get_mut(&m.key) {
                    let mut tmp = TsType::NULL;
                    std::mem::swap(&mut acc_m.type_ann, &mut tmp);
                    acc_m.type_ann = tmp.and(m.type_ann);
                } else {
                    acc.members.push(m)
                }

                acc
            })
    }
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub enum TsType {
    Keyword(TsKeywordTypeKind),
    Lit(String),
    Array(Box<Self>),
    Tuple(Vec<Self>),
    Option(Box<Self>),
    Ref {
        name: String,
        type_params: Vec<Self>,
    },
    Fn {
        params: Vec<Self>,
        type_ann: Box<Self>,
    },
    TypeLit(TsTypeLit),
    Intersection(Vec<Self>),
    Union(Vec<Self>),
    Override {
        type_override: String,
        type_params: Vec<String>,
    },
}

macro_rules! type_lit {
    ($($k: ident: $t: path);* $(;)?) => {
        TsType::TypeLit(TsTypeLit {
            members: vec![$(
                TsTypeElement {
                    key: stringify!($k).to_string(),
                    type_ann: $t,
                    optional: false,
                }
            ),*],
        })
    };
}

impl From<TsKeywordTypeKind> for TsType {
    fn from(kind: TsKeywordTypeKind) -> Self {
        Self::Keyword(kind)
    }
}

impl From<&syn::Type> for TsType {
    fn from(ty: &syn::Type) -> Self {
        Self::from_syn_type(ty)
    }
}

impl TsType {
    pub const NUMBER: TsType = TsType::Keyword(TsKeywordTypeKind::Number);
    pub const BIGINT: TsType = TsType::Keyword(TsKeywordTypeKind::Bigint);
    pub const BOOLEAN: TsType = TsType::Keyword(TsKeywordTypeKind::Boolean);
    pub const STRING: TsType = TsType::Keyword(TsKeywordTypeKind::String);
    pub const VOID: TsType = TsType::Keyword(TsKeywordTypeKind::Void);
    pub const UNDEFINED: TsType = TsType::Keyword(TsKeywordTypeKind::Undefined);
    pub const NULL: TsType = TsType::Keyword(TsKeywordTypeKind::Null);
    pub const NEVER: TsType = TsType::Keyword(TsKeywordTypeKind::Never);

    pub const fn nullish() -> Self {
        if cfg!(feature = "js") {
            Self::UNDEFINED
        } else {
            Self::NULL
        }
    }

    pub const fn empty_type_lit() -> Self {
        Self::TypeLit(TsTypeLit { members: vec![] })
    }

    pub fn is_ref(&self) -> bool {
        matches!(self, Self::Ref { .. })
    }

    pub fn and(self, other: Self) -> Self {
        match (self, other) {
            (TsType::TypeLit(x), TsType::TypeLit(y)) => x.and(y).into(),
            (TsType::Intersection(x), TsType::Intersection(y)) => {
                let mut vec = Vec::with_capacity(x.len() + y.len());
                vec.extend(x);
                vec.extend(y);
                TsType::Intersection(vec)
            }
            (TsType::Intersection(x), y) => {
                let mut vec = Vec::with_capacity(x.len() + 1);
                vec.extend(x);
                vec.push(y);
                TsType::Intersection(vec)
            }
            (x, TsType::Intersection(y)) => {
                let mut vec = Vec::with_capacity(y.len() + 1);
                vec.push(x);
                vec.extend(y);
                TsType::Intersection(vec)
            }
            (x, y) => TsType::Intersection(vec![x, y]),
        }
    }

    fn bytes_short_circuit(ty: &syn::Type) -> Option<Self> {
        use syn::Type::*;
        use syn::TypePath;

        let Path(TypePath { path, .. }) = ty else {
            return None
        };
        let Some(segment) = path.segments.last() else {
            return None
        };
        let name = segment.ident.to_string();
        match name.as_str() {
            "u8" => Some(Self::Ref {
                name: "Uint8Array".to_string(),
                type_params: vec![],
            }),
            _ => None,
        }
    }

    fn from_syn_type(ty: &syn::Type) -> Self {
        use syn::Type::*;
        use syn::{
            TypeArray, TypeBareFn, TypeGroup, TypeImplTrait, TypeParamBound, TypeParen, TypePath,
            TypeReference, TypeSlice, TypeTraitObject, TypeTuple,
        };

        match ty {
            Array(TypeArray { elem, len, .. }) => {
                Self::bytes_short_circuit(elem).unwrap_or_else(|| {
                    let elem = Self::from_syn_type(elem);
                    let len = parse_len(len);

                    match len {
                        Some(len) if len <= 16 => Self::Tuple(vec![elem; len]),
                        _ => Self::Array(Box::new(elem)),
                    }
                })
            }

            Slice(TypeSlice { elem, .. }) => Self::bytes_short_circuit(elem)
                .unwrap_or_else(|| Self::Array(Box::new(Self::from_syn_type(elem)))),

            Reference(TypeReference { elem, .. })
            | Paren(TypeParen { elem, .. })
            | Group(TypeGroup { elem, .. }) => Self::from_syn_type(elem),

            BareFn(TypeBareFn { inputs, output, .. }) => {
                let params = inputs
                    .iter()
                    .map(|arg| Self::from_syn_type(&arg.ty))
                    .collect();

                let type_ann = if let syn::ReturnType::Type(_, ty) = output {
                    Self::from_syn_type(ty)
                } else {
                    TsType::VOID
                };

                Self::Fn {
                    params,
                    type_ann: Box::new(type_ann),
                }
            }

            Tuple(TypeTuple { elems, .. }) => {
                if elems.is_empty() {
                    TsType::nullish()
                } else {
                    let elems = elems.iter().map(Self::from_syn_type).collect();
                    Self::Tuple(elems)
                }
            }

            Path(TypePath { path, .. }) => Self::from_path(path).unwrap_or(TsType::NEVER),

            TraitObject(TypeTraitObject { bounds, .. })
            | ImplTrait(TypeImplTrait { bounds, .. }) => {
                let elems = bounds
                    .iter()
                    .filter_map(|t| match t {
                        TypeParamBound::Trait(t) => Self::from_path(&t.path),
                        _ => None, // skip lifetime etc.
                    })
                    .collect();

                Self::Intersection(elems)
            }

            Ptr(_) | Infer(_) | Macro(_) | Never(_) | Verbatim(_) => TsType::NEVER,

            _ => TsType::NEVER,
        }
    }

    fn from_path(path: &syn::Path) -> Option<Self> {
        path.segments.last().map(Self::from_path_segment)
    }

    fn from_path_segment(segment: &syn::PathSegment) -> Self {
        let name = segment.ident.to_string();

        let (args, output) = match &segment.arguments {
            syn::PathArguments::AngleBracketed(path) => {
                let args = path
                    .args
                    .iter()
                    .filter_map(|p| match p {
                        syn::GenericArgument::Type(t) => Some(t),
                        syn::GenericArgument::AssocType(t) => Some(&t.ty),
                        _ => None,
                    })
                    .collect();

                (args, None)
            }

            syn::PathArguments::Parenthesized(path) => {
                let args = path.inputs.iter().collect();

                let output = match &path.output {
                    syn::ReturnType::Default => None,
                    syn::ReturnType::Type(_, tp) => Some(tp.as_ref()),
                };

                (args, output)
            }

            syn::PathArguments::None => (vec![], None),
        };

        match name.as_str() {
            "u8" | "u16" | "u32" | "u64" | "usize" | "i8" | "i16" | "i32" | "i64" | "isize"
            | "f64" | "f32" => Self::NUMBER,

            "u128" | "i128" => {
                if cfg!(feature = "js") {
                    Self::BIGINT
                } else {
                    Self::NUMBER
                }
            }

            "String" | "str" | "char" | "Path" | "PathBuf" => Self::STRING,

            "bool" => Self::BOOLEAN,

            "Box" | "Cow" | "Rc" | "Arc" | "Cell" | "RefCell" if args.len() == 1 => {
                Self::from_syn_type(args[0])
            }

            "Vec" if args.len() == 1 => Self::bytes_short_circuit(args[0]).unwrap_or_else(|| {
                let elem = Self::from_syn_type(args[0]);
                Self::Array(Box::new(elem))
            }),

            "VecDeque" | "LinkedList" if args.len() == 1 => {
                let elem = Self::from_syn_type(args[0]);
                Self::Array(Box::new(elem))
            }

            "HashMap" | "BTreeMap" if args.len() == 2 => {
                let type_params = args.iter().map(|arg| Self::from_syn_type(arg)).collect();

                let name = if cfg!(feature = "js") {
                    "Map"
                } else {
                    "Record"
                }
                .to_string();

                Self::Ref { name, type_params }
            }

            "HashSet" | "BTreeSet" if args.len() == 1 => {
                let elem = Self::from_syn_type(args[0]);
                Self::Array(Box::new(elem))
            }

            "Option" if args.len() == 1 => Self::Option(Box::new(Self::from_syn_type(args[0]))),

            "Result" if args.len() == 2 => {
                let arg0 = Self::from_syn_type(args[0]);
                let arg1 = Self::from_syn_type(args[1]);

                let ok = type_lit! { Ok: arg0 };
                let err = type_lit! { Err: arg1 };

                Self::Union(vec![ok, err])
            }

            "Duration" => type_lit! {
                secs: Self::NUMBER;
                nanos: Self::NUMBER;
            },

            "SystemTime" => type_lit! {
                secs_since_epoch: Self::NUMBER;
                nanos_since_epoch: Self::NUMBER;
            },

            "Range" | "RangeInclusive" => {
                let start = Self::from_syn_type(args[0]);
                let end = start.clone();

                type_lit! {
                    start: start;
                    end: end;
                }
            }

            "Fn" | "FnOnce" | "FnMut" => {
                let params = args.into_iter().map(Self::from_syn_type).collect();
                let type_ann = output
                    .map(Self::from_syn_type)
                    .unwrap_or_else(|| TsType::VOID);

                Self::Fn {
                    params,
                    type_ann: Box::new(type_ann),
                }
            }
            _ => {
                let type_params = args.into_iter().map(Self::from_syn_type).collect();
                Self::Ref { name, type_params }
            }
        }
    }

    pub fn with_tag_type(self, name: String, style: Style, tag_type: &TagType) -> Self {
        let type_ann = self;

        match tag_type {
            TagType::External => {
                if matches!(style, Style::Unit) {
                    TsType::Lit(name)
                } else {
                    TsTypeElement {
                        key: name,
                        type_ann,
                        optional: false,
                    }
                    .into()
                }
            }
            TagType::Internal { tag } => {
                if type_ann == TsType::nullish() {
                    let tag_field: TsType = TsTypeElement {
                        key: tag.clone(),
                        type_ann: TsType::Lit(name),
                        optional: false,
                    }
                    .into();

                    tag_field
                } else {
                    let tag_field: TsType = TsTypeElement {
                        key: tag.clone(),
                        type_ann: TsType::Lit(name),
                        optional: false,
                    }
                    .into();

                    tag_field.and(type_ann)
                }
            }
            TagType::Adjacent { tag, content } => {
                let tag_field = TsTypeElement {
                    key: tag.clone(),
                    type_ann: TsType::Lit(name),
                    optional: false,
                };

                if matches!(style, Style::Unit) {
                    tag_field.into()
                } else {
                    let content_field = TsTypeElement {
                        key: content.clone(),
                        type_ann,
                        optional: false,
                    };

                    TsTypeLit {
                        members: vec![tag_field, content_field],
                    }
                    .into()
                }
            }
            TagType::None => type_ann,
        }
    }

    pub fn visit<'a, F: FnMut(&'a TsType)>(&'a self, f: &mut F) {
        f(self);

        match self {
            TsType::Ref { type_params, .. } => {
                type_params.iter().for_each(|t| t.visit(f));
            }
            TsType::Array(elem) => elem.visit(f),
            TsType::Tuple(elems) => {
                elems.iter().for_each(|t| t.visit(f));
            }
            TsType::Option(t) => t.visit(f),
            TsType::Fn { params, type_ann } => {
                params
                    .iter()
                    .chain(Some(type_ann.as_ref()))
                    .for_each(|t| t.visit(f));
            }
            TsType::TypeLit(TsTypeLit { members }) => {
                members.iter().for_each(|m| m.type_ann.visit(f));
            }
            TsType::Intersection(tys) | TsType::Union(tys) => {
                tys.iter().for_each(|t| t.visit(f));
            }
            TsType::Keyword(_) | TsType::Lit(_) | TsType::Override { .. } => (),
        }
    }

    pub fn type_ref_names(&self) -> HashSet<&String> {
        let mut set: HashSet<&String> = HashSet::new();

        self.visit(&mut |ty: &TsType| match ty {
            TsType::Ref { name, .. } => {
                set.insert(name);
            }
            TsType::Override { type_params, .. } => set.extend(type_params),
            _ => (),
        });

        set
    }

    pub fn prefix_type_refs(self, prefix: &String, exceptions: &Vec<String>) -> Self {
        match self {
            TsType::Array(t) => TsType::Array(Box::new(t.prefix_type_refs(prefix, exceptions))),
            TsType::Tuple(tv) => TsType::Tuple(
                tv.iter()
                    .map(|t| t.clone().prefix_type_refs(prefix, exceptions))
                    .collect(),
            ),
            TsType::Option(t) => TsType::Option(Box::new(t.prefix_type_refs(prefix, exceptions))),
            TsType::Ref { name, type_params } => {
                if exceptions.contains(&name) {
                    TsType::Ref {
                        name,
                        type_params: type_params
                            .iter()
                            .map(|t| t.clone().prefix_type_refs(prefix, exceptions))
                            .collect(),
                    }
                } else {
                    TsType::Ref {
                        name: format!("{}{}", prefix, name),
                        type_params: type_params
                            .iter()
                            .map(|t| t.clone().prefix_type_refs(prefix, exceptions))
                            .collect(),
                    }
                }
            }
            TsType::Fn { params, type_ann } => TsType::Fn {
                params: params
                    .iter()
                    .map(|t| t.clone().prefix_type_refs(prefix, exceptions))
                    .collect(),
                type_ann: Box::new(type_ann.prefix_type_refs(prefix, exceptions)),
            },
            TsType::TypeLit(lit) => TsType::TypeLit(TsTypeLit {
                members: lit
                    .members
                    .iter()
                    .map(|t| TsTypeElement {
                        key: t.key.clone(),
                        optional: t.optional,
                        type_ann: t.type_ann.clone().prefix_type_refs(prefix, exceptions),
                    })
                    .collect(),
            }),
            TsType::Intersection(tv) => TsType::Intersection(
                tv.iter()
                    .map(|t| t.clone().prefix_type_refs(prefix, exceptions))
                    .collect(),
            ),
            TsType::Union(tv) => TsType::Union(
                tv.iter()
                    .map(|t| t.clone().prefix_type_refs(prefix, exceptions))
                    .collect(),
            ),
            _ => self,
        }
    }

    pub fn type_refs(&self, type_refs: &mut Vec<(String, Vec<TsType>)>) {
        match self {
            TsType::Array(t) | TsType::Option(t) => t.type_refs(type_refs),
            TsType::Tuple(tv) | TsType::Union(tv) | TsType::Intersection(tv) => {
                tv.iter().for_each(|t| t.type_refs(type_refs))
            }
            TsType::Ref { name, type_params } => {
                type_refs.push((name.clone(), type_params.clone()));
                type_params
                    .iter()
                    .for_each(|t| t.clone().type_refs(type_refs));
            }
            TsType::Fn { params, type_ann } => {
                params.iter().for_each(|t| t.clone().type_refs(type_refs));
                type_ann.type_refs(type_refs);
            }
            TsType::TypeLit(lit) => {
                lit.members.iter().for_each(|t| {
                    t.type_ann.type_refs(type_refs);
                });
            }
            _ => {}
        }
    }
}

fn parse_len(expr: &syn::Expr) -> Option<usize> {
    if let syn::Expr::Lit(syn::ExprLit {
        lit: syn::Lit::Int(lit_int),
        ..
    }) = expr
    {
        lit_int.base10_parse::<usize>().ok()
    } else {
        None
    }
}

fn is_js_ident(string: &str) -> bool {
    !string.contains('-')
}

impl Display for TsTypeElement {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        let key = &self.key;
        let type_ann = &self.type_ann;

        let optional_ann = if self.optional { "?" } else { "" };

        if is_js_ident(key) {
            write!(f, "{key}{optional_ann}: {type_ann}")
        } else {
            write!(f, "\"{key}\"{optional_ann}: {type_ann}")
        }
    }
}

impl Display for TsTypeLit {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        let members = self
            .members
            .iter()
            .map(|elem| elem.to_string())
            .collect::<Vec<_>>()
            .join("; ");

        if members.is_empty() {
            write!(f, "{{}}")
        } else {
            write!(f, "{{ {members} }}")
        }
    }
}

impl Display for TsType {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        match self {
            TsType::Keyword(kind) => {
                let ty = format!("{:?}", kind).to_lowercase();
                write!(f, "{ty}")
            }

            TsType::Lit(lit) => {
                write!(f, "\"{lit}\"")
            }

            TsType::Array(elem) => match elem.as_ref() {
                TsType::Union(_) | TsType::Intersection(_) | &TsType::Option(_) => {
                    write!(f, "({elem})[]")
                }
                _ => write!(f, "{elem}[]"),
            },

            TsType::Tuple(elems) => {
                let elems = elems
                    .iter()
                    .map(|elem| elem.to_string())
                    .collect::<Vec<_>>()
                    .join(", ");

                write!(f, "[{elems}]")
            }

            TsType::Ref { name, type_params } => {
                let params = type_params
                    .iter()
                    .map(|param| param.to_string())
                    .collect::<Vec<_>>()
                    .join(", ");

                if params.is_empty() {
                    write!(f, "{name}")
                } else {
                    write!(f, "{name}<{params}>")
                }
            }

            TsType::Fn { params, type_ann } => {
                let params = params
                    .iter()
                    .enumerate()
                    .map(|(i, param)| format!("arg{i}: {param}"))
                    .collect::<Vec<_>>()
                    .join(", ");

                write!(f, "({params}) => {type_ann}")
            }

            TsType::Option(elem) => {
                write!(f, "{elem} | {}", TsType::nullish())
            }

            TsType::TypeLit(type_lit) => {
                write!(f, "{type_lit}")
            }

            TsType::Intersection(types) => {
                if types.len() == 1 {
                    let ty = &types[0];
                    return write!(f, "{ty}");
                }

                let types = types
                    .iter()
                    .map(|ty| match ty {
                        TsType::Union(_) => format!("({ty})"),
                        _ => ty.to_string(),
                    })
                    .collect::<Vec<_>>()
                    .join(" & ");

                write!(f, "{types}")
            }

            TsType::Union(types) => {
                if types.len() == 1 {
                    let ty = &types[0];
                    return write!(f, "{ty}");
                }

                let types = types
                    .iter()
                    .map(|ty| match ty {
                        TsType::Intersection(_) => format!("({ty})"),
                        _ => ty.to_string(),
                    })
                    .collect::<Vec<_>>()
                    .join(" | ");

                write!(f, "{types}")
            }

            TsType::Override { type_override, .. } => f.write_str(type_override),
        }
    }
}

#[cfg(test)]
mod tests {
    use super::TsType;

    macro_rules! assert_ts {
        ( $( $t:ty )|* , $expected:expr) => {
          $({
            let ty: syn::Type = syn::parse_quote!($t);
            let ts_type = TsType::from_syn_type(&ty);
            assert_eq!(ts_type.to_string(), $expected);
          })*
        };
      }

    #[test]
    fn test_basic_types() {
        if cfg!(feature = "js") {
            assert_ts!((), "undefined");
            assert_ts!(u128 | i128, "bigint");
            assert_ts!(HashMap<String, i32> | BTreeMap<String, i32>, "Map<string, number>");
            assert_ts!(Option<i32>, "number | undefined");
            assert_ts!(Vec<Option<T>> | VecDeque<Option<T>> | LinkedList<Option<T>> | &'a [Option<T>], "(T | undefined)[]");
        } else {
            assert_ts!((), "null");
            assert_ts!(u128 | i128, "number");
            assert_ts!(HashMap<String, i32> | BTreeMap<String, i32>, "Record<string, number>");
            assert_ts!(Option<i32>, "number | null");
            assert_ts!(Vec<Option<T>> | VecDeque<Option<T>> | LinkedList<Option<T>> | &'a [Option<T>], "(T | null)[]");
        }

        assert_ts!(
            u8 | u16 | u32 | u64 | usize | i8 | i16 | i32 | i64 | isize | f32 | f64,
            "number"
        );
        assert_ts!(String | str | char | Path | PathBuf, "string");
        assert_ts!(bool, "boolean");
        assert_ts!(Box<i32> | Rc<i32> | Arc<i32> | Cell<i32> | RefCell<i32> | Cow<'a, i32>, "number");
        assert_ts!(Vec<i32> | VecDeque<i32> | LinkedList<i32> | &'a [i32], "number[]");
        assert_ts!(HashSet<i32> | BTreeSet<i32>, "number[]");

        assert_ts!(Result<i32, String>, "{ Ok: number } | { Err: string }");
        assert_ts!(dyn Fn(String, f64) | dyn FnOnce(String, f64) | dyn FnMut(String, f64), "(arg0: string, arg1: number) => void");
        assert_ts!(dyn Fn(String) -> i32 | dyn FnOnce(String) -> i32 | dyn FnMut(String) -> i32, "(arg0: string) => number");

        assert_ts!((i32), "number");
        assert_ts!((i32, String, bool), "[number, string, boolean]");

        assert_ts!([i32; 4], "[number, number, number, number]");
        assert_ts!([i32; 16], format!("[{}]", ["number"; 16].join(", ")));
        assert_ts!([i32; 17], "number[]");
        assert_ts!([i32; 1 + 1], "number[]");

        assert_ts!(Duration, "{ secs: number; nanos: number }");
        assert_ts!(
            SystemTime,
            "{ secs_since_epoch: number; nanos_since_epoch: number }"
        );

        assert_ts!(Range<i32>, "{ start: number; end: number }");
        assert_ts!(Range<&'static str>, "{ start: string; end: string }");
        assert_ts!(RangeInclusive<usize>, "{ start: number; end: number }");
    }
}
