use tsify::Tsify;

#[derive(Tsify)]
#[tsify(into_wasm_abi, from_wasm_abi)]
pub enum GenericEnum<T, U> {
    Unit,
    NewType(T),
    Seq(T, U),
    Map { x: T, y: U },
}
