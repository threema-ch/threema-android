//! Test that the [`libthreema_macros::ConstantTimeEq`] works as expected.
#![expect(unused_crate_dependencies, reason = "False positive")]
#![expect(
    clippy::single_char_lifetime_names,
    single_use_lifetimes,
    reason = "Test lifetimes"
)]

use libthreema_macros::ConstantTimeEq;

#[derive(Clone, Copy, ConstantTimeEq, Debug)]
struct NamedField {
    data: [u8; 3],
}

#[derive(ConstantTimeEq, Debug)]
struct NamedFields {
    data1: [u8; 3],
    data2: u64,
}

#[derive(ConstantTimeEq, Debug)]
struct NamedFieldsBorrowed<'a> {
    data1: &'a [u8],
    data2: u64,
}

#[derive(ConstantTimeEq, Debug)]
struct NamedFieldsMultipleBorrowed<'a, 'b> {
    data1: &'a [u8],
    data2: &'b [u8],
}

#[derive(Clone, Copy, ConstantTimeEq, Debug)]
struct UnnamedField(NamedField);

#[derive(Clone, Copy, ConstantTimeEq, Debug)]
struct UnnamedArrayField([u8; 3]);

#[derive(Clone, Copy, ConstantTimeEq, Debug)]
struct UnnamedFields(u32, u64, u32);

#[derive(ConstantTimeEq, Debug)]
struct NamedFieldWithInnerUnnamedField {
    data: UnnamedField,
}

#[derive(ConstantTimeEq, Debug)]
struct NamedFieldWithInnerUnnamedFields {
    data: UnnamedFields,
}

#[derive(ConstantTimeEq, Debug)]
struct NamedFieldWithInnerUnnamedArrayField {
    data: UnnamedArrayField,
}

#[derive(ConstantTimeEq, Debug)]
struct NamedFieldsWithInnerUnnamedField {
    data1: UnnamedField,
    data2: u64,
}

#[derive(ConstantTimeEq, Debug)]
struct NamedFieldsWithInnerUnnamedFields {
    data1: UnnamedFields,
    data2: u64,
}

#[derive(ConstantTimeEq, Debug)]
struct NamedFieldsWithInnerUnnamedArrayField {
    data1: UnnamedArrayField,
    data2: u64,
}

#[cfg(test)]
mod test {
    use rstest::rstest;
    use subtle::ConstantTimeEq as _;

    use crate::*;

    #[test]
    fn ct_eq_named_field() {
        let left = NamedField { data: [3; 3] };
        let right = NamedField { data: [3; 3] };
        assert!(bool::from(left.ct_eq(&right)));
        assert_eq!(left, right);
    }

    #[rstest]
    #[case([1, 0, 0])]
    #[case([0, 1, 1])]
    #[case([1, 1, 1])]
    fn ct_not_eq_named_field(#[case] right: [u8; 3]) {
        let left = NamedField { data: [0; 3] };
        let right = NamedField { data: right };
        assert!(bool::from(!left.ct_eq(&right)));
        assert_ne!(left, right);
    }

    #[test]
    fn ct_eq_named_fields() {
        let left = NamedFields {
            data1: [45; 3],
            data2: 3,
        };
        let right = NamedFields {
            data1: [45; 3],
            data2: 3,
        };
        assert!(bool::from(left.ct_eq(&right)));
        assert_eq!(left, right);
    }

    #[rstest]
    #[case(([87; 3], 3))]
    #[case(([45; 3], 2))]
    #[case(([34; 3], 5))]
    fn ct_not_eq_named_fields(#[case] right: ([u8; 3], u64)) {
        let left = NamedFields {
            data1: [45; 3],
            data2: 3,
        };
        let right = NamedFields {
            data1: right.0,
            data2: right.1,
        };
        assert!(bool::from(!left.ct_eq(&right)));
        assert_ne!(left, right);
    }

    #[test]
    fn ct_eq_unnamed_array_field() {
        let left = UnnamedArrayField([3; 3]);
        let right = UnnamedArrayField([3; 3]);
        assert!(bool::from(left.ct_eq(&right)));
        assert_eq!(left, right);
    }

    #[rstest]
    #[case([0, 0, 1])]
    #[case([1, 1, 0])]
    #[case([1, 1, 1])]
    fn ct_not_eq_unnamed_array_field(#[case] right: [u8; 3]) {
        let left = UnnamedArrayField([0, 0, 0]);
        let right = UnnamedArrayField(right);
        assert!(bool::from(!left.ct_eq(&right)));
        assert_ne!(left, right);
    }

    #[test]
    fn ct_eq_unnamed_field() {
        let left = UnnamedField(NamedField { data: [23; 3] });
        let right = UnnamedField(NamedField { data: [23; 3] });
        assert!(bool::from(left.ct_eq(&right)));
        assert_eq!(left, right);
    }

    #[rstest]
    #[case([0, 1, 0])]
    #[case([0, 1, 1])]
    #[case([1, 1, 1])]
    fn ct_not_eq_unnamed_field(#[case] right: [u8; 3]) {
        let left = UnnamedField(NamedField { data: [0; 3] });
        let right = UnnamedField(NamedField { data: right });
        assert!(bool::from(!left.ct_eq(&right)));
        assert_ne!(left, right);
    }

    #[test]
    fn ct_eq_unnamed_fields() {
        let left = UnnamedFields(109, 2, 35);
        let right = UnnamedFields(109, 2, 35);
        assert!(bool::from(left.ct_eq(&right)));
        assert_eq!(left, right);
    }

    #[rstest]
    #[case((0, 0, 1))]
    #[case((0, 1, 0))]
    #[case((0, 1, 1))]
    #[case((1, 0, 0))]
    #[case((1, 0, 1))]
    #[case((1, 1, 0))]
    #[case((1, 1, 1))]
    fn ct_not_eq_unnamed_fields(#[case] right: (u32, u64, u32)) {
        let left = UnnamedFields(0, 0, 0);
        let right = UnnamedFields(right.0, right.1, right.2);
        assert!(bool::from(!left.ct_eq(&right)));
        assert_ne!(left, right);
    }

    #[test]
    fn ct_eq_named_field_with_inner_unnamed_field() {
        let left = NamedFieldWithInnerUnnamedField {
            data: UnnamedField(NamedField { data: [23; 3] }),
        };
        let right = NamedFieldWithInnerUnnamedField {
            data: UnnamedField(NamedField { data: [23; 3] }),
        };
        assert!(bool::from(left.ct_eq(&right)));
        assert_eq!(left, right);
    }

    #[rstest]
    #[case([0, 0, 1])]
    #[case([0, 1, 1])]
    #[case([1, 1, 1])]
    fn ct_not_eq_named_field_with_inner_unnamed_field(#[case] right: [u8; 3]) {
        let left = NamedFieldWithInnerUnnamedField {
            data: UnnamedField(NamedField { data: [0; 3] }),
        };
        let right = NamedFieldWithInnerUnnamedField {
            data: UnnamedField(NamedField { data: right }),
        };
        assert!(bool::from(!left.ct_eq(&right)));
        assert_ne!(left, right);
    }

    #[test]
    fn ct_eq_named_field_with_inner_unnamed_fields() {
        let left = NamedFieldWithInnerUnnamedFields {
            data: UnnamedFields(109, 2, 35),
        };
        let right = NamedFieldWithInnerUnnamedFields {
            data: UnnamedFields(109, 2, 35),
        };
        assert!(bool::from(left.ct_eq(&right)));
        assert_eq!(left, right);
    }

    #[rstest]
    #[case((0, 0, 1))]
    #[case((0, 1, 0))]
    #[case((0, 1, 1))]
    #[case((1, 0, 0))]
    #[case((1, 0, 1))]
    #[case((1, 1, 0))]
    #[case((1, 1, 1))]
    fn ct_not_eq_named_field_with_inner_unnamed_fields(#[case] right: (u32, u64, u32)) {
        let left = NamedFieldWithInnerUnnamedFields {
            data: UnnamedFields(0, 0, 0),
        };
        let right = NamedFieldWithInnerUnnamedFields {
            data: UnnamedFields(right.0, right.1, right.2),
        };
        assert!(bool::from(!left.ct_eq(&right)));
        assert_ne!(left, right);
    }

    #[test]
    fn ct_eq_named_field_with_inner_unnamed_array_field() {
        let left = NamedFieldWithInnerUnnamedArrayField {
            data: UnnamedArrayField([75; 3]),
        };
        let right = NamedFieldWithInnerUnnamedArrayField {
            data: UnnamedArrayField([75; 3]),
        };
        assert!(bool::from(left.ct_eq(&right)));
        assert_eq!(left, right);
    }

    #[rstest]
    #[case([0, 0, 1])]
    #[case([1, 1, 0])]
    #[case([1, 1, 1])]
    fn ct_not_eq_named_field_with_inner_unnamed_array_field(#[case] right: [u8; 3]) {
        let left = NamedFieldWithInnerUnnamedArrayField {
            data: UnnamedArrayField([0; 3]),
        };
        let right = NamedFieldWithInnerUnnamedArrayField {
            data: UnnamedArrayField(right),
        };
        assert!(bool::from(!left.ct_eq(&right)));
        assert_ne!(left, right);
    }

    #[test]
    fn ct_eq_named_fields_with_inner_unnamed_field() {
        let left = NamedFieldsWithInnerUnnamedField {
            data1: UnnamedField(NamedField { data: [23; 3] }),
            data2: 3,
        };
        let right = NamedFieldsWithInnerUnnamedField {
            data1: UnnamedField(NamedField { data: [23; 3] }),
            data2: 3,
        };
        assert!(bool::from(left.ct_eq(&right)));
        assert_eq!(left, right);
    }

    #[rstest]
    #[case(([0, 0, 0], 1))]
    #[case(([1, 0, 1], 0))]
    #[case(([1, 1, 0], 1))]
    fn ct_not_eq_named_fields_with_inner_unnamed_field(#[case] right: ([u8; 3], u64)) {
        let left = NamedFieldsWithInnerUnnamedField {
            data1: UnnamedField(NamedField { data: [0; 3] }),
            data2: 0,
        };
        let right = NamedFieldsWithInnerUnnamedField {
            data1: UnnamedField(NamedField { data: right.0 }),
            data2: right.1,
        };
        assert!(bool::from(!left.ct_eq(&right)));
        assert_ne!(left, right);
    }

    #[test]
    fn ct_eq_named_fields_with_inner_unnamed_fields() {
        let left = NamedFieldsWithInnerUnnamedFields {
            data1: UnnamedFields(109, 2, 35),
            data2: 8,
        };
        let right = NamedFieldsWithInnerUnnamedFields {
            data1: UnnamedFields(109, 2, 35),
            data2: 8,
        };
        assert!(bool::from(left.ct_eq(&right)));
        assert_eq!(left, right);
    }

    #[rstest]
    #[case((0, 0, 0, 1))]
    #[case((0, 1, 0, 0))]
    #[case((1, 1, 1, 0))]
    #[case((1, 1, 0, 1))]
    fn ct_not_eq_named_fields_with_inner_unnamed_fields(#[case] right: (u32, u64, u32, u64)) {
        let left = NamedFieldsWithInnerUnnamedFields {
            data1: UnnamedFields(0, 0, 0),
            data2: 0,
        };
        let right = NamedFieldsWithInnerUnnamedFields {
            data1: UnnamedFields(right.0, right.1, right.2),
            data2: right.3,
        };
        assert!(bool::from(!left.ct_eq(&right)));
        assert_ne!(left, right);
    }

    #[test]
    fn ct_eq_named_fields_with_inner_unnamed_array_field() {
        let left = NamedFieldsWithInnerUnnamedArrayField {
            data1: UnnamedArrayField([75; 3]),
            data2: 5,
        };
        let right = NamedFieldsWithInnerUnnamedArrayField {
            data1: UnnamedArrayField([75; 3]),
            data2: 5,
        };
        assert!(bool::from(left.ct_eq(&right)));
        assert_eq!(left, right);
    }

    #[rstest]
    #[case(([0, 0, 1], 0))]
    #[case(([0, 0, 0], 1))]
    #[case(([0, 1, 1], 1))]
    fn ct_not_eq_named_fields_with_inner_unnamed_array_field(#[case] right: ([u8; 3], u64)) {
        let left = NamedFieldsWithInnerUnnamedArrayField {
            data1: UnnamedArrayField([0; 3]),
            data2: 0,
        };
        let right = NamedFieldsWithInnerUnnamedArrayField {
            data1: UnnamedArrayField(right.0),
            data2: right.1,
        };
        assert!(bool::from(!left.ct_eq(&right)));
        assert_ne!(left, right);
    }

    #[test]
    fn ct_eq_named_fields_borrowed() {
        let left = NamedFieldsBorrowed {
            data1: &[75; 3],
            data2: 5,
        };
        let right = NamedFieldsBorrowed {
            data1: &[75; 3],
            data2: 5,
        };
        assert!(bool::from(left.ct_eq(&right)));
        assert_eq!(left, right);
    }

    #[rstest]
    #[case(([0, 0, 0], 1))]
    #[case(([0, 0, 1], 0))]
    #[case(([1, 1, 1], 1))]
    fn ct_not_eq_named_fields_borrowed(#[case] right: ([u8; 3], u64)) {
        let left = NamedFieldsBorrowed {
            data1: &[0; 3],
            data2: 0,
        };
        let right = NamedFieldsBorrowed {
            data1: &right.0,
            data2: right.1,
        };
        assert!(bool::from(!left.ct_eq(&right)));
        assert_ne!(left, right);
    }

    #[test]
    fn ct_eq_named_fields_multiple_borrowed() {
        let left = NamedFieldsMultipleBorrowed {
            data1: &[75; 3],
            data2: &[38; 3],
        };
        let right = NamedFieldsMultipleBorrowed {
            data1: &[75; 3],
            data2: &[38; 3],
        };
        assert!(bool::from(left.ct_eq(&right)));
        assert_eq!(left, right);
    }

    #[rstest]
    #[case(([0, 0, 1], [0, 0, 0]))]
    #[case(([0, 0, 0], [0, 1, 1]))]
    #[case(([1, 1, 1], [1, 0, 0]))]
    fn ct_not_eq_named_fields_multiple_borrowed(#[case] right: ([u8; 3], [u8; 3])) {
        let left = NamedFieldsMultipleBorrowed {
            data1: &[0; 3],
            data2: &[0; 3],
        };
        let right = NamedFieldsMultipleBorrowed {
            data1: &right.0,
            data2: &right.1,
        };
        assert!(bool::from(!left.ct_eq(&right)));
        assert_ne!(left, right);
    }
}
