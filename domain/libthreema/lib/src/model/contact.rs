//! Contact structs.
use std::collections::HashMap;

use duplicate::duplicate_item;

use super::provider::{ContactProvider, ProviderError, SettingsProvider};
use crate::{
    common::{Delta, FeatureMask, ThreemaId, config::Config, keys::PublicKey},
    protobuf::{self, d2d_sync::contact as protobuf_contact},
    utils::{
        apply::{Apply as _, TryApply},
        time::utc_now_ms,
    },
};

/// A contact could not be updated.
#[derive(Debug, thiserror::Error)]
pub(crate) enum ContactUpdateError {
    /// Contact could not be updated because the identity does not match.
    #[error("Identity mismatch: Expected {expected} but got {actual}")]
    IdentityMismatch { expected: ThreemaId, actual: ThreemaId },

    /// Contact could not be updated because the public key does not match.
    #[error("Public key mismatch")]
    PublicKeyMismatch,
}

#[derive(Debug)]
pub(crate) enum CommunicationPermission {
    /// Communication with this contact is allowed.
    Allow,

    /// Communication with this contact is disallowed due to it being explicitly blocked.
    BlockExplicit,

    /// Communication with this contact is disallowed due to it being implicitly blocked by the _block
    /// unknown_ setting.
    ///
    /// Note: This is purely informational, i.e. when encountering this, the behaviour must be the same as for
    /// [`CommunicationPermission::BlockExplicit`]. Communication would be allowed after the contact has been
    /// explicitly added or both the user and the contact are in a group not marked as _left_.
    BlockUnknown,
}

/// All protocol relevant data associated to a contact with the following exclusions:
///
/// - _contact-defined_ profile picture
/// - _user-defined_ profile picture
//-
// IMPORTANT: When touching these, always make sure to update [`ContactUpdate`] and
// `ContactUpdate::apply_to` accordingly.
#[derive(Debug, Clone)]
pub struct Contact {
    /// Threema ID of the contact.
    pub identity: ThreemaId,

    /// Public key of the contact.
    pub public_key: PublicKey,

    /// Unix-ish timestamp in milliseconds when the contact has been created (added) locally.
    pub created_at: u64,

    /// First name of the contact.
    pub first_name: Option<String>,

    /// Last name of the contact.
    pub last_name: Option<String>,

    /// Nickname of the contact (without `~` prefix).
    ///
    /// IMPORTANT: Do not provide the Threema ID as a fallback!
    pub nickname: Option<String>,

    /// Verification level of the contact.
    pub verification_level: protobuf_contact::VerificationLevel,

    /// Threema Work verification level of the contact.
    pub work_verification_level: protobuf_contact::WorkVerificationLevel,

    /// Identity type of the contact.
    pub identity_type: protobuf_contact::IdentityType,

    /// Acquaintance level of the contact.
    pub acquaintance_level: protobuf_contact::AcquaintanceLevel,

    /// Activity state of the contact.
    pub activity_state: protobuf_contact::ActivityState,

    /// Features available for the contact.
    pub feature_mask: FeatureMask,

    /// Contact synchronisation state.
    pub sync_state: protobuf_contact::SyncState,

    /// _Read_ receipt policy override for this contact.
    pub read_receipt_policy_override: Option<protobuf::d2d_sync::ReadReceiptPolicy>,

    /// Typing indicator policy override for this contact.
    pub typing_indicator_policy_override: Option<protobuf::d2d_sync::TypingIndicatorPolicy>,

    /// Notification trigger policy for the contact.
    pub notification_trigger_policy_override:
        Option<protobuf_contact::notification_trigger_policy_override::Policy>,

    /// Notification sound policy for the contact.
    pub notification_sound_policy_override: Option<protobuf::d2d_sync::NotificationSoundPolicy>,

    /// Conversation category of the contact.
    pub conversation_category: protobuf::d2d_sync::ConversationCategory,

    /// Conversation visibility of the contact.
    pub conversation_visibility: protobuf::d2d_sync::ConversationVisibility,
}

/// All protocol relevant data associated to create a contact with the following exclusions:
///
/// - _contact-defined_ profile picture
/// - _user-defined_ profile picture
pub type ContactInit = Contact;

#[derive(Debug)]
pub(crate) enum ContactOrInit {
    ExistingContact(Contact),
    NewContact(ContactInit),
}
impl ContactOrInit {
    #[inline]
    pub(crate) const fn inner(&self) -> &Contact {
        match self {
            ContactOrInit::NewContact(contact) | ContactOrInit::ExistingContact(contact) => contact,
        }
    }

    pub(crate) fn communication_permission(
        &self,
        config: &Config,
        settings_provider: &dyn SettingsProvider,
        contact_provider: &dyn ContactProvider,
    ) -> Result<CommunicationPermission, ProviderError> {
        let inner = self.inner();
        let predefined_contact = config.predefined_contacts.get(&inner.identity);

        // Special contacts cannot be blocked
        if predefined_contact.is_some_and(|contact| contact.special) {
            return Ok(CommunicationPermission::Allow);
        }

        // Check if the user explicitly blocked the contact
        if contact_provider.is_explicitly_blocked(inner.identity)? {
            return Ok(CommunicationPermission::BlockExplicit);
        }

        // Check if the user wants to block _unknown_ identities.
        if !settings_provider.block_unknown_identities()? {
            return Ok(CommunicationPermission::Allow);
        }

        // Predefined contacts should not be implicitly blocked
        if predefined_contact.is_some() {
            return Ok(CommunicationPermission::Allow);
        }

        Ok(match &self {
            ContactOrInit::NewContact(_) => {
                // The contact does not exist and block unknown is active
                CommunicationPermission::BlockUnknown
            },

            ContactOrInit::ExistingContact(inner) => {
                // Check if the contact has acquaintance level _direct_
                if inner.acquaintance_level == protobuf_contact::AcquaintanceLevel::Direct {
                    return Ok(CommunicationPermission::Allow);
                }

                // Check if the user and the contact are part of an active group
                if contact_provider.is_member_of_active_group(inner.identity)? {
                    CommunicationPermission::Allow
                } else {
                    CommunicationPermission::BlockUnknown
                }
            },
        })
    }
}

#[duplicate_item(
    [
        input_type [ protobuf::d2d_sync::ReadReceiptPolicy ]
        output_type [ protobuf_contact::ReadReceiptPolicyOverride ]
        override_type [ protobuf_contact::read_receipt_policy_override::Override ]
        policy_transform [ policy.into() ]
    ]
    [
        input_type [ protobuf::d2d_sync::TypingIndicatorPolicy ]
        output_type [ protobuf_contact::TypingIndicatorPolicyOverride ]
        override_type [ protobuf_contact::typing_indicator_policy_override::Override ]
        policy_transform [ policy.into() ]
    ]
    [
        input_type [ protobuf_contact::notification_trigger_policy_override::Policy ]
        output_type [ protobuf_contact::NotificationTriggerPolicyOverride ]
        override_type [ protobuf_contact::notification_trigger_policy_override::Override ]
        policy_transform [ policy ]
    ]
    [
        input_type [ protobuf::d2d_sync::NotificationSoundPolicy ]
        output_type [ protobuf_contact::NotificationSoundPolicyOverride ]
        override_type [ protobuf_contact::notification_sound_policy_override::Override ]
        policy_transform [ policy.into() ]
    ]
)]
impl From<Option<input_type>> for output_type {
    fn from(policy: Option<input_type>) -> Self {
        output_type {
            r#override: Some(match policy {
                Some(policy) => override_type::Policy(policy_transform),
                None => override_type::Default(protobuf::common::Unit {}),
            }),
        }
    }
}

impl From<&ContactInit> for protobuf::d2d_sync::Contact {
    fn from(contact: &ContactInit) -> Self {
        Self {
            identity: contact.identity.as_str().to_owned(),
            public_key: Some(contact.public_key.0.to_bytes().into()),
            created_at: Some(contact.created_at),
            first_name: contact.first_name.clone(),
            last_name: contact.last_name.clone(),
            nickname: contact.nickname.clone(),
            verification_level: Some(contact.verification_level.into()),
            work_verification_level: Some(contact.work_verification_level.into()),
            identity_type: Some(contact.identity_type.into()),
            acquaintance_level: Some(contact.acquaintance_level.into()),
            activity_state: Some(contact.activity_state.into()),
            feature_mask: Some(contact.feature_mask.0),
            sync_state: Some(contact.sync_state.into()),
            read_receipt_policy_override: Some(contact.read_receipt_policy_override.into()),
            typing_indicator_policy_override: Some(contact.typing_indicator_policy_override.into()),
            notification_trigger_policy_override: Some(contact.notification_trigger_policy_override.into()),
            notification_sound_policy_override: Some(contact.notification_sound_policy_override.into()),
            contact_defined_profile_picture: None,
            user_defined_profile_picture: None,
            conversation_category: Some(contact.conversation_category.into()),
            conversation_visibility: Some(contact.conversation_visibility.into()),
        }
    }
}

/// All protocol relevant data associated to update a contact with the following exclusions:
///
/// - _contact-defined_ profile picture
/// - _user-defined_ profile picture
///
/// Note: This may only include changes to a contact.
#[derive(Debug, Clone, PartialEq)]
pub struct ContactUpdate {
    /// Threema ID of the contact.
    pub identity: ThreemaId,

    /// First name of the contact.
    pub first_name: Delta<String>,

    /// Last name of the contact.
    pub last_name: Delta<String>,

    /// Nickname of the contact (without `~` prefix).
    ///
    /// IMPORTANT: Do not provide the Threema ID as a fallback!
    pub nickname: Delta<String>,

    /// Verification level of the contact.
    pub verification_level: Option<protobuf_contact::VerificationLevel>,

    /// Threema Work verification level of the contact.
    pub work_verification_level: Option<protobuf_contact::WorkVerificationLevel>,

    /// Identity type of the contact.
    pub identity_type: Option<protobuf_contact::IdentityType>,

    /// Acquaintance level of the contact.
    pub acquaintance_level: Option<protobuf_contact::AcquaintanceLevel>,

    /// Activity state of the contact.
    pub activity_state: Option<protobuf_contact::ActivityState>,

    /// Features available for the contact.
    pub feature_mask: Option<FeatureMask>,

    /// Contact synchronisation state.
    pub sync_state: Option<protobuf_contact::SyncState>,

    /// _Read_ receipt policy override for this contact.
    pub read_receipt_policy_override: Delta<protobuf::d2d_sync::ReadReceiptPolicy>,

    /// Typing indicator policy override for this contact.
    pub typing_indicator_policy_override: Delta<protobuf::d2d_sync::TypingIndicatorPolicy>,

    /// Notification trigger policy for the contact.
    pub notification_trigger_policy_override:
        Delta<protobuf_contact::notification_trigger_policy_override::Policy>,

    /// Notification sound policy for the contact.
    pub notification_sound_policy_override: Delta<protobuf::d2d_sync::NotificationSoundPolicy>,

    /// Conversation category of the contact.
    pub conversation_category: Option<protobuf::d2d_sync::ConversationCategory>,

    /// Conversation visibility of the contact.
    pub conversation_visibility: Option<protobuf::d2d_sync::ConversationVisibility>,
}

impl ContactUpdate {
    pub(crate) fn default(identity: ThreemaId) -> Self {
        Self {
            identity,
            first_name: Delta::Unchanged,
            last_name: Delta::Unchanged,
            nickname: Delta::Unchanged,
            verification_level: None,
            work_verification_level: None,
            identity_type: None,
            acquaintance_level: None,
            activity_state: None,
            feature_mask: None,
            sync_state: None,
            read_receipt_policy_override: Delta::Unchanged,
            typing_indicator_policy_override: Delta::Unchanged,
            notification_trigger_policy_override: Delta::Unchanged,
            notification_sound_policy_override: Delta::Unchanged,
            conversation_category: None,
            conversation_visibility: None,
        }
    }

    pub(crate) fn has_changes(&self) -> bool {
        self != &Self::default(self.identity)
    }
}
impl TryApply<ContactUpdate> for Contact {
    type Error = ContactUpdateError;

    /// Try applying the contact update to the contact.
    ///
    /// # Errors
    ///
    /// Returns [`ContactUpdateError`] if the identity mismatches.
    fn try_apply(&mut self, value: ContactUpdate) -> Result<(), Self::Error> {
        // Unpacking here, so we can't miss updating this function when a new field is being added
        let ContactUpdate {
            identity,
            first_name,
            last_name,
            nickname,
            verification_level,
            work_verification_level,
            identity_type,
            acquaintance_level,
            activity_state,
            feature_mask,
            sync_state,
            read_receipt_policy_override,
            typing_indicator_policy_override,
            notification_trigger_policy_override,
            notification_sound_policy_override,
            conversation_category,
            conversation_visibility,
        } = value;

        // Ensure the identity equals before updating
        if identity != self.identity {
            return Err(ContactUpdateError::IdentityMismatch {
                expected: self.identity,
                actual: identity,
            });
        }

        // Update all properties
        self.first_name.apply(first_name);
        self.last_name.apply(last_name);
        self.nickname.apply(nickname);
        self.verification_level = verification_level.unwrap_or(self.verification_level);
        self.work_verification_level = work_verification_level.unwrap_or(self.work_verification_level);
        self.identity_type = identity_type.unwrap_or(self.identity_type);
        self.acquaintance_level = acquaintance_level.unwrap_or(self.acquaintance_level);
        self.activity_state = activity_state.unwrap_or(self.activity_state);
        self.feature_mask = feature_mask.unwrap_or(self.feature_mask);
        self.sync_state = sync_state.unwrap_or(self.sync_state);
        self.read_receipt_policy_override
            .apply(read_receipt_policy_override);
        self.typing_indicator_policy_override
            .apply(typing_indicator_policy_override);
        self.notification_trigger_policy_override
            .apply(notification_trigger_policy_override);
        self.notification_sound_policy_override
            .apply(notification_sound_policy_override);
        self.conversation_category = conversation_category.unwrap_or(self.conversation_category);
        self.conversation_visibility = conversation_visibility.unwrap_or(self.conversation_visibility);

        // Done
        Ok(())
    }
}

#[duplicate_item(
    [
        input_type [ protobuf::d2d_sync::ReadReceiptPolicy ]
        output_type [ protobuf_contact::ReadReceiptPolicyOverride ]
        override_type [ protobuf_contact::read_receipt_policy_override::Override ]
        policy_transform [ (*policy).into() ]
    ]
    [
        input_type [ protobuf::d2d_sync::TypingIndicatorPolicy ]
        output_type [ protobuf_contact::TypingIndicatorPolicyOverride ]
        override_type [ protobuf_contact::typing_indicator_policy_override::Override ]
        policy_transform [ (*policy).into() ]
    ]
    [
        input_type [ protobuf_contact::notification_trigger_policy_override::Policy ]
        output_type [ protobuf_contact::NotificationTriggerPolicyOverride ]
        override_type [ protobuf_contact::notification_trigger_policy_override::Override ]
        policy_transform [ *policy ]
    ]
    [
        input_type [ protobuf::d2d_sync::NotificationSoundPolicy ]
        output_type [ protobuf_contact::NotificationSoundPolicyOverride ]
        override_type [ protobuf_contact::notification_sound_policy_override::Override ]
        policy_transform [ (*policy).into() ]
    ]
)]
impl From<&Delta<input_type>> for Option<output_type> {
    fn from(policy: &Delta<input_type>) -> Self {
        match policy {
            Delta::Unchanged => None,
            Delta::Update(policy) => Some(output_type {
                r#override: Some(override_type::Policy(policy_transform)),
            }),
            Delta::Remove => Some(output_type {
                r#override: Some(override_type::Default(protobuf::common::Unit {})),
            }),
        }
    }
}

impl From<&ContactUpdate> for protobuf::d2d_sync::Contact {
    fn from(contact: &ContactUpdate) -> Self {
        Self {
            identity: contact.identity.as_str().to_owned(),
            public_key: None,
            created_at: None,
            first_name: contact.first_name.clone().into_non_empty(),
            last_name: contact.last_name.clone().into_non_empty(),
            nickname: contact.nickname.clone().into_non_empty(),
            verification_level: contact.verification_level.map(Into::into),
            work_verification_level: contact.work_verification_level.map(Into::into),
            identity_type: contact.identity_type.map(Into::into),
            acquaintance_level: contact.acquaintance_level.map(Into::into),
            activity_state: contact.activity_state.map(Into::into),
            feature_mask: contact.feature_mask.map(|feature_mask| feature_mask.0),
            sync_state: contact.sync_state.map(Into::into),
            read_receipt_policy_override: (&contact.read_receipt_policy_override).into(),
            typing_indicator_policy_override: (&contact.typing_indicator_policy_override).into(),
            notification_trigger_policy_override: (&contact.notification_trigger_policy_override).into(),
            notification_sound_policy_override: (&contact.notification_sound_policy_override).into(),
            contact_defined_profile_picture: None,
            user_defined_profile_picture: None,
            conversation_category: contact.conversation_category.map(Into::into),
            conversation_visibility: contact.conversation_visibility.map(Into::into),
        }
    }
}

/// A predefined contact that does not need to be fetched from the directory.
///
/// It is automatically elevated to the highest verification level.
#[derive(Debug, Clone)]
pub struct PredefinedContact {
    /// Threema ID of the predefined contact.
    pub identity: ThreemaId,

    /// Whether the predefined contact is marked as a _special contact_.
    pub special: bool,

    /// Public key of the predefined contact.
    pub public_key: PublicKey,

    /// Nickname of the contact (without `~` prefix).
    pub nickname: String,
}
impl PredefinedContact {
    pub(crate) const _3MAPUSH_IDENTITY: ThreemaId = ThreemaId::predefined(*b"*3MAPUSH");

    pub(crate) fn production() -> HashMap<ThreemaId, Self> {
        [
            Self {
                identity: Self::_3MAPUSH_IDENTITY,
                special: true,
                #[rustfmt::skip]
                public_key: PublicKey::from([
                    0xfd, 0x71, 0x1e, 0x1a, 0x0d, 0xb0, 0xe2, 0xf0,
                    0x3f, 0xca, 0xab, 0x6c, 0x43, 0xda, 0x25, 0x75,
                    0xb9, 0x51, 0x36, 0x64, 0xa6, 0x2a, 0x12, 0xbd,
                    0x07, 0x28, 0xd8, 0x7f, 0x71, 0x25, 0xcc, 0x24,
                ]),
                nickname: "Threema Push".to_owned(),
            },
            Self {
                identity: ThreemaId::predefined(*b"*3MATOKN"),
                special: false,
                #[rustfmt::skip]
                public_key: PublicKey::from([
                    0x04, 0x88, 0x4d, 0x12, 0xd6, 0x68, 0xf8, 0x55,
                    0xd0, 0x0d, 0x71, 0xfb, 0x1d, 0x9d, 0x41, 0x3c,
                    0x95, 0xf2, 0x71, 0x31, 0x2f, 0x7e, 0x07, 0x78,
                    0x46, 0xaf, 0x67, 0x18, 0x75, 0xc4, 0x10, 0x1b,
                ]),
                nickname: "Threema Token".to_owned(),
            },
            Self {
                identity: ThreemaId::predefined(*b"*3MAWORK"),
                special: false,
                #[rustfmt::skip]
                public_key: PublicKey::from([
                    0x9a, 0xa0, 0xa7, 0x2a, 0x8f, 0xb6, 0xf0, 0xcc,
                    0x53, 0x72, 0x7f, 0xea, 0x60, 0x96, 0xf1, 0xb7,
                    0xb0, 0xeb, 0xef, 0xcc, 0x26, 0x50, 0xad, 0x39,
                    0xa1, 0xe5, 0x48, 0x37, 0xbb, 0xa0, 0xbc, 0x4b,
                ]),
                nickname: "Threema Work Channel".to_owned(),
            },
            Self {
                identity: ThreemaId::predefined(*b"*BETAFBK"),
                special: false,
                #[rustfmt::skip]
                public_key: PublicKey::from([
                    0x56, 0x84, 0xd6, 0xdc, 0xd3, 0x2a, 0x16, 0x48,
                    0x8d, 0xf8, 0x37, 0x10, 0x95, 0xfc, 0x9a, 0x1f,
                    0xc2, 0x5b, 0xae, 0xb6, 0xb9, 0x73, 0x66, 0xd9,
                    0x9f, 0xdf, 0x2a, 0xba, 0x00, 0xe2, 0xbc, 0x5c,
                ]),
                nickname: "Threema Beta Feedback".to_owned(),
            },
            Self {
                identity: ThreemaId::predefined(*b"*MY3DATA"),
                special: false,
                #[rustfmt::skip]
                public_key: PublicKey::from([
                    0x3b, 0x01, 0x85, 0x4f, 0x24, 0x73, 0x6e, 0x2d,
                    0x0d, 0x2d, 0xc3, 0x87, 0xea, 0xf2, 0xc0, 0x27,
                    0x3c, 0x50, 0x49, 0x05, 0x21, 0x47, 0x13, 0x23,
                    0x69, 0xbf, 0x39, 0x60, 0xd0, 0xa0, 0xbf, 0x02,
                ]),
                nickname: "My Threema Data".to_owned(),
            },
            Self {
                identity: ThreemaId::predefined(*b"*SUPPORT"),
                special: false,
                #[rustfmt::skip]
                public_key: PublicKey::from([
                    0x0f, 0x94, 0x4d, 0x18, 0x32, 0x4b, 0x21, 0x32,
                    0xc6, 0x1d, 0x8e, 0x40, 0xaf, 0xce, 0x60, 0xa0,
                    0xeb, 0xd7, 0x01, 0xbb, 0x11, 0xe8, 0x9b, 0xe9,
                    0x49, 0x72, 0xd4, 0x22, 0x9e, 0x94, 0x72, 0x2a,
                ]),
                nickname: "Threema Support".to_owned(),
            },
            Self {
                identity: ThreemaId::predefined(*b"*THREEMA"),
                special: false,
                #[rustfmt::skip]
                public_key: PublicKey::from([
                    0x3a, 0x38, 0x65, 0x0c, 0x68, 0x14, 0x35, 0xbd,
                    0x1f, 0xb8, 0x49, 0x8e, 0x21, 0x3a, 0x29, 0x19,
                    0xb0, 0x93, 0x88, 0xf5, 0x80, 0x3a, 0xa4, 0x46,
                    0x40, 0xe0, 0xf7, 0x06, 0x32, 0x6a, 0x86, 0x5c,
                ]),
                nickname: "Threema Channel".to_owned(),
            },
        ]
        .into_iter()
        .map(|contact| (contact.identity, contact))
        .collect()
    }

    pub(crate) fn sandbox() -> HashMap<ThreemaId, Self> {
        [
            Self {
                identity: Self::_3MAPUSH_IDENTITY,
                special: true,
                #[rustfmt::skip]
                public_key: PublicKey::from([
                    0xfd, 0x71, 0x1e, 0x1a, 0x0d, 0xb0, 0xe2, 0xf0,
                    0x3f, 0xca, 0xab, 0x6c, 0x43, 0xda, 0x25, 0x75,
                    0xb9, 0x51, 0x36, 0x64, 0xa6, 0x2a, 0x12, 0xbd,
                    0x07, 0x28, 0xd8, 0x7f, 0x71, 0x25, 0xcc, 0x24,
                ]),
                nickname: "Threema Push".to_owned(),
            },
            Self {
                identity: ThreemaId::predefined(*b"*3MATOKN"),
                special: false,
                #[rustfmt::skip]
                public_key: PublicKey::from([
                    0x04, 0x88, 0x4d, 0x12, 0xd6, 0x68, 0xf8, 0x55,
                    0xd0, 0x0d, 0x71, 0xfb, 0x1d, 0x9d, 0x41, 0x3c,
                    0x95, 0xf2, 0x71, 0x31, 0x2f, 0x7e, 0x07, 0x78,
                    0x46, 0xaf, 0x67, 0x18, 0x75, 0xc4, 0x10, 0x1b,
                ]),
                nickname: "Threema Token".to_owned(),
            },
            Self {
                identity: ThreemaId::predefined(*b"*3MAWORK"),
                special: false,
                #[rustfmt::skip]
                public_key: PublicKey::from([
                    0x9a, 0xa0, 0xa7, 0x2a, 0x8f, 0xb6, 0xf0, 0xcc,
                    0x53, 0x72, 0x7f, 0xea, 0x60, 0x96, 0xf1, 0xb7,
                    0xb0, 0xeb, 0xef, 0xcc, 0x26, 0x50, 0xad, 0x39,
                    0xa1, 0xe5, 0x48, 0x37, 0xbb, 0xa0, 0xbc, 0x4b,
                ]),
                nickname: "Threema Work Channel".to_owned(),
            },
            Self {
                identity: ThreemaId::predefined(*b"*MY3DATA"),
                special: false,
                #[rustfmt::skip]
                public_key: PublicKey::from([
                    0x83, 0xad, 0xfe, 0xe6, 0x55, 0x8b, 0x68, 0xae,
                    0x3c, 0xd6, 0xbb, 0xe2, 0xa3, 0x3f, 0x4e, 0x44,
                    0x09, 0xd5, 0x62, 0x4a, 0x7c, 0xea, 0x23, 0xa1,
                    0x89, 0x75, 0xae, 0xa6, 0x27, 0x2a, 0x00, 0x70,
                ]),
                nickname: "My Threema Data".to_owned(),
            },
            Self {
                identity: ThreemaId::predefined(*b"*SUPPORT"),
                special: false,
                #[rustfmt::skip]
                public_key: PublicKey::from([
                    0x0f, 0x94, 0x4d, 0x18, 0x32, 0x4b, 0x21, 0x32,
                    0xc6, 0x1d, 0x8e, 0x40, 0xaf, 0xce, 0x60, 0xa0,
                    0xeb, 0xd7, 0x01, 0xbb, 0x11, 0xe8, 0x9b, 0xe9,
                    0x49, 0x72, 0xd4, 0x22, 0x9e, 0x94, 0x72, 0x2a,
                ]),
                nickname: "Threema Support".to_owned(),
            },
            Self {
                identity: ThreemaId::predefined(*b"*THREEMA"),
                special: false,
                #[rustfmt::skip]
                public_key: PublicKey::from([
                    0x3a, 0x38, 0x65, 0x0c, 0x68, 0x14, 0x35, 0xbd,
                    0x1f, 0xb8, 0x49, 0x8e, 0x21, 0x3a, 0x29, 0x19,
                    0xb0, 0x93, 0x88, 0xf5, 0x80, 0x3a, 0xa4, 0x46,
                    0x40, 0xe0, 0xf7, 0x06, 0x32, 0x6a, 0x86, 0x5c,
                ]),
                nickname: "Threema Channel".to_owned(),
            },
        ]
        .into_iter()
        .map(|contact| (contact.identity, contact))
        .collect()
    }

    pub(crate) fn update(&self, contact: &mut Contact) -> Result<(), ContactUpdateError> {
        let Self {
            identity,
            special: _special,
            public_key,
            nickname,
        } = self;

        // Ensure the identity and public key equal before updating
        if *identity != contact.identity {
            return Err(ContactUpdateError::IdentityMismatch {
                expected: contact.identity,
                actual: *identity,
            });
        }
        if *public_key != contact.public_key {
            return Err(ContactUpdateError::PublicKeyMismatch);
        }

        // Bump verification level
        contact.verification_level = protobuf_contact::VerificationLevel::FullyVerified;

        // Update nickname
        contact.nickname = Some(nickname.clone());

        // Done
        Ok(())
    }
}

impl From<&PredefinedContact> for ContactInit {
    fn from(predefined_contact: &PredefinedContact) -> Self {
        Self {
            identity: predefined_contact.identity,
            public_key: predefined_contact.public_key,
            created_at: utc_now_ms(),
            first_name: None,
            last_name: None,
            nickname: Some(predefined_contact.nickname.clone()),
            verification_level: protobuf_contact::VerificationLevel::FullyVerified,
            work_verification_level: protobuf_contact::WorkVerificationLevel::None,
            // Gateway IDs are semantically neither identity type but, eh, whatever.
            identity_type: protobuf_contact::IdentityType::Regular,
            acquaintance_level: protobuf_contact::AcquaintanceLevel::GroupOrDeleted,
            activity_state: protobuf_contact::ActivityState::Active,
            feature_mask: FeatureMask(FeatureMask::NONE),
            sync_state: protobuf_contact::SyncState::Initial,
            read_receipt_policy_override: None,
            typing_indicator_policy_override: None,
            notification_trigger_policy_override: None,
            notification_sound_policy_override: None,
            conversation_category: protobuf::d2d_sync::ConversationCategory::Default,
            conversation_visibility: protobuf::d2d_sync::ConversationVisibility::Normal,
        }
    }
}
