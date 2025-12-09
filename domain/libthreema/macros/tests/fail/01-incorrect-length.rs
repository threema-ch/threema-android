use libthreema_macros::concat_fixed_bytes;

fn main() {
    let a = [1u8; 4];
    let b = [2u8; 3];
    let c = [3u8; 3];

    let _result: [u8; 8] = concat_fixed_bytes!(a, b); // yields 7 bytes, not 8
    let _result: [u8; 8] = concat_fixed_bytes!(a, b, c); // yields 10 bytes, not 8
}
