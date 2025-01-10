use proc_macro2::TokenStream;
use quote::quote;

use crate::{ctxt::Ctxt, decl::TsTypeAliasDecl, typescript::TsType};

pub fn expend(item: syn::ItemType) -> syn::Result<TokenStream> {
    let ctxt = Ctxt::new();

    let type_ann = TsType::from(item.ty.as_ref());

    let decl = TsTypeAliasDecl {
        id: item.ident.to_string(),
        export: true,
        type_params: item
            .generics
            .type_params()
            .map(|ty| ty.ident.to_string())
            .collect(),
        type_ann,
    };

    let decl_str = decl.to_string();

    let typescript_custom_section = quote! {
        #[automatically_derived]
        const _: () = {
            use wasm_bindgen::prelude::*;
            #[wasm_bindgen(typescript_custom_section)]
            const TS_APPEND_CONTENT: &'static str = #decl_str;
        };
    };

    ctxt.check()?;

    let tokens = quote! {
      #item
      #typescript_custom_section
    };

    Ok(tokens)
}
