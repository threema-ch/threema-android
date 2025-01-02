//! Commonly used macros of libthreema.

use convert_case::{Case, Casing as _};
use proc_macro::TokenStream;
use quote::{format_ident, quote};
use syn::{
    self,
    parse::{Parse, ParseStream},
    parse_macro_input,
    punctuated::Punctuated,
    Data, DeriveInput, Expr, Fields, Ident, Variant,
};

/// Provides variant names for an `enum`.
///
/// # Examples
///
/// Given the following:
///
/// ```
/// use libthreema_macros::VariantNames;
///
/// #[derive(VariantNames)]
/// enum Something {
///     SomeItem,
///     SomeOtherItem(u64),
/// }
/// ```
///
/// the derive macro expands it to:
///
/// ```
/// enum Something {
///     SomeItem,
///     SomeOtherItem(u64),
/// }
///
/// impl Something {
///     pub const SOME_ITEM: &'static str = "SomeItem";
///     pub const SOME_OTHER_ITEM: &'static str = "SomeOtherItem";
///
///     pub const fn variant_name(&self) -> &'static str {
///         match self {
///             Self::SomeItem => Self::SOME_ITEM,
///             Self::SomeOtherItem(..) => Self::SOME_OTHER_ITEM,
///         }
///     }
/// }
/// ```
#[proc_macro_derive(VariantNames)]
pub fn derive_variant_names(input: TokenStream) -> TokenStream {
    fn get_const_name(variant: &Variant) -> Ident {
        format_ident!(
            "{}",
            variant
                .ident
                .to_string()
                .from_case(Case::Pascal)
                .to_case(Case::UpperSnake)
        )
    }

    // Parse the input tokens into a syntax tree.
    let input = parse_macro_input!(input as DeriveInput);
    let enum_name = input.ident;

    // Map each variant to its literal identifier name
    let const_variants = match &input.data {
        Data::Enum(data) => data.variants.iter().map(|variant| {
            let docstring = format!(" Variant name of [`{}::{}`].", enum_name, variant.ident);
            let const_name = get_const_name(variant);
            let literal_name = variant.ident.to_string();
            quote! {
                #[doc = #docstring]
                pub const #const_name: &'static str = #literal_name;
            }
        }),
        #[allow(clippy::unimplemented)]
        _ => unimplemented!(),
    };
    let mapped_variants = match &input.data {
        Data::Enum(data) => data.variants.iter().map(|variant| {
            let variant_name = &variant.ident;
            let parameters = match variant.fields {
                Fields::Unit => quote! {},
                Fields::Unnamed(..) => quote! { (..) },
                Fields::Named(..) => quote! { {..} },
            };
            let const_name = get_const_name(variant);
            quote! {
                Self::#variant_name #parameters => Self::#const_name
            }
        }),
        #[allow(clippy::unimplemented)]
        _ => unimplemented!(),
    };

    // Implement for the enum
    let expanded = quote! {
        impl #enum_name {
            #(#const_variants)*

            /// Get the variant name of `self`.
            pub const fn variant_name(&self) -> &'static str {
                match self {
                    #(#mapped_variants),*
                }
            }
        }
    };

    // Generate code
    TokenStream::from(expanded)
}

/// Implements [`Debug`] for the provided `enum`. Depends on [`VariantNames`].
///
/// # Examples
///
/// Given the following:
///
/// ```
/// use libthreema_macros::{DebugVariantNames, VariantNames};
///
/// #[derive(DebugVariantNames, VariantNames)]
/// enum Something {
///     SomeItem,
///     SomeOtherItem(u64),
/// }
/// ```
///
/// the derive macro expands it to:
///
/// ```
/// # use libthreema_macros::VariantNames;
/// #
/// # #[derive(VariantNames)]
/// enum Something {
///     SomeItem,
///     SomeOtherItem(u64),
/// }
///
/// // Omitting expansion of `VariantNames` here.
///
/// impl std::fmt::Debug for Something {
///     fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
///         f.write_fmt(format_args!("{}::{}", "Something", self.variant_name()))
///     }
/// }
/// ```
#[proc_macro_derive(DebugVariantNames)]
pub fn derive_debug_variant_names(input: TokenStream) -> TokenStream {
    // Parse the input tokens into a syntax tree.
    let input = parse_macro_input!(input as DeriveInput);

    // Ensure its an enum
    #[allow(clippy::unimplemented)]
    if !matches!(input.data, Data::Enum(..)) {
        unimplemented!()
    }

    // Implement `Debug` for the enum
    let name = input.ident;
    let literal_name = name.to_string();
    let expanded = quote! {
        impl std::fmt::Debug for #name {
            fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
                f.write_fmt(format_args!("{}::{}", #literal_name, self.variant_name()))
            }
        }
    };

    // Generate code
    TokenStream::from(expanded)
}

struct Arrays(Punctuated<Expr, syn::Token![,]>);

impl Parse for Arrays {
    fn parse(input: ParseStream) -> syn::parse::Result<Arrays> {
        let punctuated = Punctuated::parse_terminated(input)?;
        Ok(Arrays(punctuated))
    }
}

/// Concatenates fixed-size byte arrays into a single large byte array.
///
/// # Examples
///
/// ```
/// use libthreema_macros::concat_fixed_bytes;
///
/// let a = [1u8; 4];
/// let b = [2u8; 3];
/// let c = [3u8; 3];
///
/// let concatenated: [u8; 10] = concat_fixed_bytes!(a, b, c);
/// assert_eq!(concatenated, [1, 1, 1, 1, 2, 2, 2, 3, 3, 3]);
/// ```
#[proc_macro]
pub fn concat_fixed_bytes(tokens: TokenStream) -> TokenStream {
    let input = syn::parse_macro_input!(tokens as Arrays).0.into_iter();
    let indices = input.clone().enumerate();
    let arrays: Vec<Expr> = input.collect();
    let field_length_parameters: Vec<Ident> = indices
        .clone()
        .map(|(index, _)| format_ident!("T{index}"))
        .collect();
    let field_names: Vec<Ident> = indices
        .map(|(index, _)| format_ident!("t{index}"))
        .collect();

    let expanded = quote! {{
        #[repr(C)]
        struct ConcatenatedArrays<#(const #field_length_parameters: usize,)*> {
            #(#field_names: [u8; #field_length_parameters],)*
        }
        let concatenated_arrays = ConcatenatedArrays {
            #(#field_names: #arrays,)*
        };
        unsafe {
            core::mem::transmute(concatenated_arrays)
        }
    }};

    // Generate code
    TokenStream::from(expanded)
}

// Avoids test dependencies to be picked up by the linter.
#[cfg(test)]
mod external_crate_false_positives {
    use trybuild as _;
}
