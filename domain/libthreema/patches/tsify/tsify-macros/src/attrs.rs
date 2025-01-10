use serde_derive_internals::ast::Field;

#[derive(Debug, Default)]
pub struct TsifyContainerAttars {
    pub into_wasm_abi: bool,
    pub from_wasm_abi: bool,
    pub namespace: bool,
}

impl TsifyContainerAttars {
    pub fn from_derive_input(input: &syn::DeriveInput) -> syn::Result<Self> {
        let mut attrs = Self {
            into_wasm_abi: false,
            from_wasm_abi: false,
            namespace: false,
        };

        for attr in &input.attrs {
            if !attr.path().is_ident("tsify") {
                continue;
            }

            attr.parse_nested_meta(|meta| {
                if meta.path.is_ident("into_wasm_abi") {
                    if attrs.into_wasm_abi {
                        return Err(meta.error("duplicate attribute"));
                    }
                    attrs.into_wasm_abi = true;
                    return Ok(());
                }

                if meta.path.is_ident("from_wasm_abi") {
                    if attrs.from_wasm_abi {
                        return Err(meta.error("duplicate attribute"));
                    }
                    attrs.from_wasm_abi = true;
                    return Ok(());
                }

                if meta.path.is_ident("namespace") {
                    if !matches!(input.data, syn::Data::Enum(_)) {
                        return Err(meta.error("#[tsify(namespace)] can only be used on enums"));
                    }
                    if attrs.namespace {
                        return Err(meta.error("duplicate attribute"));
                    }
                    attrs.namespace = true;
                    return Ok(());
                }

                Err(meta.error("unsupported tsify attribute, expected one of `into_wasm_abi`, `from_wasm_abi`, `namespace`"))
            })?;
        }

        Ok(attrs)
    }
}

#[derive(Debug, Default)]
pub struct TsifyFieldAttrs {
    pub type_override: Option<String>,
    pub optional: bool,
}

impl TsifyFieldAttrs {
    pub fn from_serde_field(field: &Field) -> syn::Result<Self> {
        let mut attrs = Self {
            type_override: None,
            optional: false,
        };

        for attr in &field.original.attrs {
            if !attr.path().is_ident("tsify") {
                continue;
            }

            attr.parse_nested_meta(|meta| {
                if meta.path.is_ident("type") {
                    if attrs.type_override.is_some() {
                        return Err(meta.error("duplicate attribute"));
                    }
                    let lit = meta.value()?.parse::<syn::LitStr>()?;
                    attrs.type_override = Some(lit.value());
                    return Ok(());
                }

                if meta.path.is_ident("optional") {
                    if attrs.optional {
                        return Err(meta.error("duplicate attribute"));
                    }
                    attrs.optional = true;
                    return Ok(());
                }

                Err(meta.error("unsupported tsify attribute, expected one of `type` or `optional`"))
            })?;
        }

        if let Some(expr) = field.attrs.skip_serializing_if() {
            let path = expr
                .path
                .segments
                .iter()
                .map(|segment| segment.ident.to_string())
                .collect::<Vec<_>>()
                .join("::");

            attrs.optional |= &path == "Option::is_none";
        }

        Ok(attrs)
    }
}
