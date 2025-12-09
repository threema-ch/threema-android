//! Commonly used macros of libthreema.
use convert_case::{Case, Casing as _};
use proc_macro::TokenStream;
use quote::{ToTokens as _, format_ident, quote};
use syn::{
    self, Data, DeriveInput, Expr, Fields, Ident, ItemStruct, LitInt, LitStr, Variant,
    parse::{Parse, ParseStream},
    parse_macro_input, parse_quote,
    punctuated::Punctuated,
};

/// Provides the name of a `struct`, `enum` or `union`.
///
/// # Examples
///
/// Given the following:
///
/// ```nobuild
/// use libthreema_macros::Name;
/// use crate::utils::debug::Name;
///
/// #[derive(Name)]
/// struct Something;
/// ```
///
/// the derive macro expands it to:
///
/// ```nobuild
/// struct Something;
/// impl Name for Something {
///     const NAME: &'static str = "Something";
/// }
/// ```
#[proc_macro_derive(Name)]
pub fn derive_name(input: TokenStream) -> TokenStream {
    // Parse the input tokens into a syntax tree.
    let input = parse_macro_input!(input as DeriveInput);

    // Implement `NAME`
    let name = input.ident;
    let literal_name = name.to_string();
    let (impl_generics, type_generics, where_clause) = input.generics.split_for_impl();
    let expanded = quote! {
        impl #impl_generics crate::utils::debug::Name for #name #type_generics #where_clause {
            /// The name for debugging purposes
            const NAME: &'static str = #literal_name;
        }
    };

    // Generate code
    TokenStream::from(expanded)
}

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
    let (impl_generics, type_generics, where_clause) = input.generics.split_for_impl();

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
        #[expect(clippy::unimplemented, reason = "Only applicable to enums")]
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
        #[expect(clippy::unimplemented, reason = "Only applicable to enums")]
        _ => unimplemented!(),
    };

    // Implement for the enum
    let expanded = quote! {
        impl #impl_generics #enum_name #type_generics #where_clause {
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
///     fn fmt(&self, formatter: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
///         write!(formatter, "{}::{}", "Something", self.variant_name())
///     }
/// }
/// ```
#[proc_macro_derive(DebugVariantNames)]
pub fn derive_debug_variant_names(input: TokenStream) -> TokenStream {
    // Parse the input tokens into a syntax tree.
    let input = parse_macro_input!(input as DeriveInput);

    // Ensure it's an enum
    #[expect(clippy::unimplemented, reason = "Only applicable to enums")]
    if !matches!(input.data, Data::Enum(..)) {
        unimplemented!()
    }

    // Implement `Debug` for the enum
    let name = input.ident;
    let literal_name = name.to_string();
    let (impl_generics, type_generics, where_clause) = input.generics.split_for_impl();
    let expanded = quote! {
        impl #impl_generics std::fmt::Debug for #name #type_generics #where_clause {
            fn fmt(&self, formatter: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
                write!(formatter, "{}::{}", #literal_name, self.variant_name())
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
    let input = parse_macro_input!(tokens as Arrays).0.into_iter();
    let indices = input.clone().enumerate();
    let arrays: Vec<Expr> = input.collect();
    let field_length_parameters: Vec<Ident> = indices
        .clone()
        .map(|(index, _)| format_ident!("T{index}"))
        .collect();
    let field_names: Vec<Ident> = indices.map(|(index, _)| format_ident!("t{index}")).collect();

    let expanded = quote! {{
        #[repr(C)]
        struct ConcatenatedArrays<#(const #field_length_parameters: usize,)*> {
            #(#field_names: [u8; #field_length_parameters],)*
        }
        let concatenated_arrays = ConcatenatedArrays {
            #(#field_names: #arrays,)*
        };
        unsafe {
            let concatenated_bytes = core::mem::transmute(concatenated_arrays);
            concatenated_bytes
        }
    }};

    // Generate code
    TokenStream::from(expanded)
}

/// Annotates a `prost::Message` in the following way:
///
/// ## `padding` fields
///
/// These fields will be marked as deprecated to discourage direct usage of it. Furthermore, the padding tag
/// will be extracted and made available on the message as a `PADDING_TAG` const. See the
/// `ProtobufPaddedMessage` trait.
#[proc_macro_attribute]
pub fn protobuf_annotations(_attribute: TokenStream, input: TokenStream) -> TokenStream {
    fn annotate_protobuf_message(mut message: ItemStruct) -> syn::Result<TokenStream> {
        let mut padding_tag: Option<LitInt> = None;

        for field in &mut message.fields {
            let Some(name) = field.ident.as_ref() else {
                continue;
            };

            // Process `padding` fields so that we can use `ProtobufPaddedMessage` on them easily
            if name == "padding" {
                // Look for the tag value in `#[prost(..., tag = "<tag-value>")]`
                for attribute in &field.attrs {
                    if !attribute.path().is_ident("prost") {
                        continue;
                    }
                    attribute.parse_nested_meta(|meta| {
                        let value: LitStr = meta.value()?.parse()?;
                        if meta.path.is_ident("tag") {
                            padding_tag = Some(value.parse()?);
                        }
                        Ok(())
                    })?;
                }

                // Ensure nobody uses the field directly by deprecating it
                field.attrs.push(parse_quote! {
                    #[deprecated(note = "Use ProtobufPaddedMessage trait to generate padding")]
                });
            }
        }

        let message_name = message.ident.clone();
        let mut output = message.into_token_stream();

        // Add any padding tag value as a const to the message
        if let Some(padding_tag) = padding_tag {
            output.extend(quote! {
                impl #message_name {
                    /// Tag value of the padding of this message.
                    pub const PADDING_TAG: u32 = #padding_tag;
                }
            });
        }

        Ok(output.into())
    }
    annotate_protobuf_message(parse_macro_input!(input as ItemStruct))
        .unwrap_or_else(|error| error.into_compile_error().into())
}

/// Implements [`subtle::ConstantTimeEq`] for named and unnamed structs.
///
/// Moreover, this derives [`PartialEq`] and [`Eq`] using constant time comparison.
///
/// Note: All fields must implement [`subtle::ConstantTimeEq`].
///
/// The proc macro was adapted from <https://github.com/dalek-cryptography/subtle/pull/111>
///
/// # Examples
///
/// Given the following:
///
/// ```
/// use libthreema_macros::ConstantTimeEq;
///
/// #[derive(ConstantTimeEq)]
/// struct MyStruct {
///     first_field: [u8; 32],
///     second_field: u64,
/// }
/// ```
///
/// the derive macro expands it to:
///
/// ```
/// struct MyStruct {
///     first_field: [u8; 32],
///     second_field: u64,
/// }
///
/// impl ::subtle::ConstantTimeEq for MyStruct {
///     #[inline]
///     fn ct_eq(&self, other: &Self) -> ::subtle::Choice {
///         use ::subtle::ConstantTimeEq as _;
///         return { self.first_field }.ct_eq(&{ other.first_field })
///             & { self.second_field }.ct_eq(&{ other.second_field });
///     }
/// }
/// impl PartialEq<Self> for MyStruct {
///     #[inline]
///     fn eq(&self, other: &Self) -> bool {
///         use ::subtle::ConstantTimeEq as _;
///         bool::from(self.ct_eq(other))
///     }
/// }
/// impl Eq for MyStruct {}
/// ```
#[proc_macro_derive(ConstantTimeEq)]
pub fn constant_time_eq(input: TokenStream) -> TokenStream {
    let input = parse_macro_input!(input as DeriveInput);

    #[expect(
        clippy::unimplemented,
        reason = "Only applicable to named and unnamed structs"
    )]
    let Data::Struct(data_struct) = input.data else {
        unimplemented!()
    };

    let constant_time_eq_stream = match &data_struct.fields {
        Fields::Named(fields_named) => {
            let mut token_stream = quote! {};
            let mut fields = fields_named.named.iter().peekable();
            while let Some(field) = fields.next() {
                let ident = &field.ident;
                token_stream.extend(quote! { {self.#ident}.ct_eq(&{other.#ident}) });

                if fields.peek().is_some() {
                    token_stream.extend(quote! { & });
                }
            }
            token_stream
        },
        Fields::Unnamed(unnamed_fields) => {
            let mut token_stream = quote! {};
            let mut fields = unnamed_fields.unnamed.iter().enumerate().peekable();
            while let Some(field) = fields.next() {
                let index = syn::Index::from(field.0);
                token_stream.extend(quote! { {self.#index}.ct_eq(&{other.#index}) });

                if fields.peek().is_some() {
                    token_stream.extend(quote! { & });
                }
            }
            token_stream
        },
        #[expect(clippy::unimplemented, reason = "Not applicable to unit-like structs")]
        Fields::Unit => unimplemented!(),
    };

    let name = &input.ident;
    let (impl_generics, type_generics, where_clause) = input.generics.split_for_impl();
    let expanded = quote! {
        impl #impl_generics ::subtle::ConstantTimeEq for #name #type_generics #where_clause {
            #[inline]
            fn ct_eq(&self, other: &Self) -> ::subtle::Choice {
                use ::subtle::ConstantTimeEq as _;
                return #constant_time_eq_stream
            }
        }

        impl #impl_generics PartialEq<Self> for #name #type_generics #where_clause {
            #[inline]
            fn eq(&self, other: &Self) -> bool{
                use ::subtle::ConstantTimeEq as _;
                bool::from(self.ct_eq(other))
            }
        }
        impl #impl_generics Eq for #name #type_generics #where_clause {}
    };

    TokenStream::from(expanded)
}

// Avoids dependencies to be picked up by the linter.
mod external_crate_false_positives {
    use subtle as _;
}

// Avoids test dependencies to be picked up by the linter.
#[cfg(test)]
mod external_crate_false_positives_test_feature {
    use rstest as _;
    use trybuild as _;
}
