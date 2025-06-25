//! A set of providers that must be implemented by the app using the CSP E2EE protocol.
//!
//! TODO(LIB-16):
//! - All functions must return a `Result`
//! - Consistently use `Vec` or `&[]`

use super::{
    contact::{Contact, ContactUpdate},
    message::{IncomingMessage, WebSessionResume},
};
use crate::common::{Conversation, MessageId, Nonce, ThreemaId};

/// A set of supported media types for profile pictures.
#[derive(Clone, Copy)]
pub enum ProfilePictureMediaType {
    /// JPEG (not JPEG XL)
    Jpeg,
}

/// A profile picture.
#[derive(Clone)]
pub struct ProfilePicture {
    /// Media type of the profile picture
    pub media_type: ProfilePictureMediaType,
    /// Bytes of the profile picture
    pub data: Vec<u8>,
}

/// Makes dirty shortcuts to get things done faster initially. Contains the pure essence of tech
/// debt. Expect things to be changed or removed quickly.
pub trait ShortcutProvider {
    /// Handle a `web-session-resume` sent from `*3MAPUSH`.
    fn handle_web_session_resume(&self, message: WebSessionResume);
}

/// Nonce storage interface.
pub trait NonceStorage {
    /// 1. Lookup `nonce` in the associated context (do any necessary conversion to convert the bytes of
    ///    `nonce` for comparison).
    /// 2. Return whether `nonce` already exists in the storage.
    fn has(&self, nonce: &Nonce) -> bool;

    /// 1. Transform each nonce bytes of `nonces` to the necessary format.
    /// 2. Store the resulting nonces in the storage, disregarding whether a nonce was already present before.
    fn add_many(&self, nonces: Vec<Nonce>);
}

/// Settings storage interface.
pub trait SettingsProvider {
    /// Return whether unknown identities should be blocked.
    fn block_unknown_identities(&self) -> bool;
}

/// Contact storage interface.
pub trait ContactProvider {
    /// 1. If `identity` is called with the user's Threema ID, return an error.
    /// 2. Return whether `identity` is present in the identity block list.
    fn is_explicitly_blocked(&self, identity: ThreemaId) -> bool;

    /// 1. If `identity` is the user's Threema ID, return an error.
    /// 2. Return whether `identity` is part of an active group (i.e. a group that is not marked as _left_).
    fn is_member_of_active_group(&self, identity: ThreemaId) -> bool;

    /// 1. If `identity` is the user's Threema ID, return an error.
    /// 2. Return whether a contact for `identity` exists.¹
    ///
    /// ¹: This must consider contacts existing in storage with any acquaintance level, including
    /// _deleted_.
    fn has(&self, identity: ThreemaId) -> bool;

    /// 1. If any identity of `identities` is the user's Threema ID, return an error.
    /// 2. Return the amount of `identities` a contact exists for.¹
    ///
    /// ¹: This must count contacts existing in storage with any acquaintance level, including
    /// _deleted_.
    fn has_many(&self, identities: &[ThreemaId]) -> usize;

    /// 1. If `identity` is the user's Threema ID, return an error.
    /// 2. Return the contact stored for `identity`, if any.¹
    ///
    /// ¹: This must return contacts existing in storage with any acquaintance level, including
    /// _deleted_.
    fn get(&self, identity: ThreemaId) -> Option<Contact>;

    /// 1. If any identity of `contacts` is the user's Threema ID, return an error.
    /// 2. If any contact for `contacts` exists, return an error.¹
    /// 3. Add each contact of `contacts`.
    ///
    /// ¹: This must consider contacts existing in storage with any acquaintance level, including
    /// _deleted_.
    fn add(&self, contacts: &[Contact]);

    /// 1. If `contact.identity` is the user's Threema ID, return an error.
    /// 2. If no contact for `identity` exists, return an error.¹
    /// 3. Update the `contact`.
    ///
    /// ¹: This must consider contacts existing in storage with any acquaintance level, including
    /// _deleted_.
    fn update(&self, contacts: &[ContactUpdate]);

    /// 1. If `identity` is the user's Threema ID, return an error.
    /// 2. Look up the contact associated to `identity` and let `contact` be the result.¹
    /// 3. If `contact` is defined, return its contact defined profile picture (if any).
    ///
    /// ¹: This must consider contacts existing in storage with any acquaintance level, including
    /// _deleted_.
    fn get_contact_defined_profile_picture(&self, identity: ThreemaId) -> Option<ProfilePicture>;

    /// 1. If `identity` is the user's Threema ID, return an error.
    /// 2. Look up the contact associated to `identity` and let `contact` be the result.¹
    /// 3. If `contact` is defined, return its user defined profile picture (if any).
    ///
    /// ¹: This must consider contacts existing in storage with any acquaintance level, including
    /// _deleted_.
    fn get_user_defined_profile_picture(&self, identity: ThreemaId) -> Option<ProfilePicture>;
}

/// Message storage interface.
pub trait MessageProvider {
    /// TODO(LIB-16): Known problematic because checking the message ID early and bailing may
    /// prevent reflection.
    ///
    /// Return whether a message with message `id` from `sender_identity` has been marked as used
    /// (i.e. points to an existing message or was explicitly marked used at some point).
    fn is_marked_used(&self, sender_identity: ThreemaId, id: MessageId) -> bool;

    /// Add an incoming message to a specific conversation.
    ///
    /// 1. If `conversation` refers to a distribution list, return an error.
    /// 2. If `conversation` refers to a contact:
    ///    1. If the referred contact does not equal the `message`'s sender identity, return an error.
    ///    2. If the referred contact does not exist, return an error.¹
    /// 3. If `conversation` refers to a group and the referred group does not exist, return an error.
    /// 4. Match over the `message`:
    ///    1. If `message` is a text message, add it to the conversation.
    ///    2. (Unreachable)
    ///
    /// ¹: This must consider contacts existing in storage with any acquaintance level, including
    /// _deleted_.
    fn add(&self, conversation: Conversation, message: IncomingMessage);
}
