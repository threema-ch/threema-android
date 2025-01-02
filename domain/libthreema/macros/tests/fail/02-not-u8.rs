use libthreema_macros::concat_fixed_bytes;

fn main() {
    let a = [1u8; 4];
    let b = [2u8; 3];
    let c = [3u8; 3];
    let d = [4u16; 2];

    let result: [u8; 4] = concat_fixed_bytes!(d); // d is a u16 array
    let result: [u8; 8] = concat_fixed_bytes!(a, d); // d is a u16 array
}
