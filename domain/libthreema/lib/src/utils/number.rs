//! Number utilities and extensions

use duplicate::duplicate_item;

/// Port of <https://doc.rust-lang.org/nightly/std/primitive.u64.html#method.checked_exact_div>
pub(crate) trait CheckedExactDiv: Sized {
    /// Checked integer division without remainder. Computes `self / rhs`, returning `None` if `rhs == 0` or
    /// if `self % rhs != 0`.
    fn checked_exact_div_(self, rhs: Self) -> Option<Self>;
}

#[duplicate_item(
    integer_type;
    [ usize ];
    [ u128 ];
    [ u64 ];
    [ u32 ];
    [ u16 ];
    [ u8 ];
)]
impl CheckedExactDiv for integer_type {
    fn checked_exact_div_(self, rhs: Self) -> Option<Self> {
        if rhs == 0 {
            return None;
        }
        if !self.is_multiple_of(rhs) {
            return None;
        }
        Some(self.div_euclid(rhs))
    }
}
