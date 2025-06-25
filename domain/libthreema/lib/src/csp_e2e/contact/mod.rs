//! Contact-related tasks and structures.
use duplicate::duplicate_item;
use predefined::PredefinedContact;

use super::CspE2eProtocolContext;
use crate::{
    common::{Delta, FeatureMask, PublicKey, ThreemaId},
    csp_e2e::endpoints::work_directory,
    protobuf::{self, d2d_sync::contact as protobuf_contact},
    utils::time::utc_now_ms,
};

pub mod create;
pub mod lookup;
pub(crate) mod predefined;
pub mod update;

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
pub(crate) enum ContactPermission {
    Allow,
    BlockExplicit,
    BlockUnknown,
}

/// All protocol relevant data associated to a contact with the following exclusions:
///
/// - _contact-defined_ profile picture
/// - _user-defined_ profile picture
#[derive(Clone)]
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

impl Contact {
    pub(crate) fn update_from_predefined_contact(
        &mut self,
        predefined_contact: &PredefinedContact,
    ) -> Result<(), ContactUpdateError> {
        // Ensure the identity and public key equal before updating
        if predefined_contact.identity != self.identity {
            return Err(ContactUpdateError::IdentityMismatch {
                expected: self.identity,
                actual: predefined_contact.identity,
            });
        }
        if predefined_contact.public_key != self.public_key {
            return Err(ContactUpdateError::PublicKeyMismatch);
        }

        // Bump verification level
        self.verification_level = protobuf_contact::VerificationLevel::FullyVerified;

        // Update nickname
        self.nickname = Some(predefined_contact.nickname.clone());

        // Done
        Ok(())
    }

    pub(crate) fn update_from_work_contact(
        &mut self,
        work_contact: work_directory::WorkContact,
    ) -> Result<(), ContactUpdateError> {
        // Ensure the identity and public key equal before updating
        if work_contact.identity != self.identity {
            return Err(ContactUpdateError::IdentityMismatch {
                expected: self.identity,
                actual: work_contact.identity,
            });
        }
        if &work_contact.public_key != self.public_key.0.as_bytes() {
            return Err(ContactUpdateError::PublicKeyMismatch);
        }

        // Bump verification levels (if needed)
        if self.verification_level == protobuf_contact::VerificationLevel::Unverified {
            self.verification_level = protobuf_contact::VerificationLevel::ServerVerified;
        }
        if self.work_verification_level == protobuf_contact::WorkVerificationLevel::None {
            self.work_verification_level = protobuf_contact::WorkVerificationLevel::WorkSubscriptionVerified;
        }

        // Update first and last name if provided
        if let Some(first_name) = work_contact.first_name {
            self.first_name = Some(first_name);
        }
        if let Some(last_name) = work_contact.last_name {
            self.last_name = Some(last_name);
        }

        // Done
        Ok(())
    }

    pub(crate) fn contact_permission(&self, context: &CspE2eProtocolContext) -> ContactPermission {
        let predefined_contact = context.config.predefined_contacts.get(&self.identity);

        // Special contacts cannot be blocked
        if predefined_contact.is_some_and(|contact| contact.special) {
            return ContactPermission::Allow;
        }

        // Check if the user explicitly blocked the contact
        if context.contacts.is_explicitly_blocked(self.identity) {
            return ContactPermission::BlockExplicit;
        }

        // Check if the user wants to block _unknown_ identities.
        if !context.settings.block_unknown_identities() {
            return ContactPermission::Allow;
        }

        // Predefined contacts should not be implicitly blocked
        if predefined_contact.is_some() {
            return ContactPermission::Allow;
        }

        // A contact is not _unknown_ if the user and the identity are in at least one common active
        // group.
        if self.acquaintance_level == protobuf_contact::AcquaintanceLevel::Direct
            || context.contacts.is_member_of_active_group(self.identity)
        {
            ContactPermission::Allow
        } else {
            ContactPermission::BlockUnknown
        }
    }
}

/// All protocol relevant data associated to create a contact with the following exclusions:
///
/// - _contact-defined_ profile picture
/// - _user-defined_ profile picture
pub type ContactInit = Contact;

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
#[derive(Clone, PartialEq)]
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
        self == &Self::default(self.identity)
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

pub(crate) enum ContactOrInit {
    ExistingContact(Contact),
    NewContact(ContactInit),
}
impl ContactOrInit {
    #[inline]
    pub(crate) fn inner(&self) -> &Contact {
        match self {
            ContactOrInit::NewContact(contact) | ContactOrInit::ExistingContact(contact) => contact,
        }
    }
}
