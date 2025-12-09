use libthreema_macros::concat_fixed_bytes;

fn main() {
    let a = [1u8; 4];
    let b = [4u16; 2];

    let _result: [u8; 4] = concat_fixed_bytes!(b); // b is a u16 array
    let _result: [u8; 8] = concat_fixed_bytes!(a, b); // b is a u16 array
}
