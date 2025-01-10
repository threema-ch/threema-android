use proc_macro2::TokenStream;
use quote::quote;
use syn::{parse_quote, DeriveInput};

use crate::{container::Container, parser::Parser, wasm_bindgen};

pub fn expand(input: DeriveInput) -> syn::Result<TokenStream> {
    let cont = Container::from_derive_input(&input)?;

    let parser = Parser::new(&cont);
    let decl = parser.parse();

    let (impl_generics, ty_generics, where_clause) = cont.generics().split_for_impl();

    let ident = cont.ident();
    let decl_str = decl.to_string();

    let tokens = if cfg!(feature = "wasm-bindgen") {
        wasm_bindgen::expand(&cont, decl)
    } else {
        quote! {
            #[automatically_derived]
            const _: () = {
                use tsify::Tsify;
                impl #impl_generics Tsify for #ident #ty_generics #where_clause {
                    const DECL: &'static str = #decl_str;
                }
            };
        }
    };

    cont.check()?;

    Ok(tokens)
}

pub fn expand_by_attr(args: TokenStream, input: DeriveInput) -> syn::Result<TokenStream> {
    let mut cloned_input = input.clone();
    let attr: syn::Attribute = parse_quote!(#[tsify(#args)]);
    cloned_input.attrs.push(attr);

    let derived = expand(cloned_input)?;

    let tokens = quote! {
      #input
      #derived
    };

    Ok(tokens)
}
