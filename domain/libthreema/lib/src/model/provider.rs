//! A set of providers that must be implemented by the app using the CSP E2EE protocol.
use super::{
    contact::{Contact, ContactUpdate},
    message::{IncomingMessage, WebSessionResumeMessage},
};
use crate::common::{MessageId, Nonce, ThreemaId};

/// Provider errors.
#[derive(Debug, thiserror::Error)]
#[cfg_attr(feature = "uniffi", derive(uniffi::Error))]
#[cfg_attr(
    feature = "wasm",
    derive(tsify::Tsify, serde::Deserialize),
    serde(
        tag = "type",
        content = "details",
        rename_all = "kebab-case",
        rename_all_fields = "camelCase"
    ),
    tsify(from_wasm_abi)
)]
pub enum ProviderError {
    /// The provided parameter was invalid. This may only be used for cases where the API contract has been
    /// violated and it indicates a bug in libthreema.
    #[error("Invalid parameter: {0}")]
    InvalidParameter(String),

    /// The app's internal state conflicts with the requested action. This can either indicate a bug in the
    /// app or a bug in libthreema.
    #[error("Invalid state: {0}")]
    InvalidState(String),

    /// A foreign function considered infallible returned an error.
    #[cfg(any(test, feature = "uniffi", feature = "cli"))]
    #[error("Infallible function failed in foreign code: {0}")]
    Foreign(String),
}
#[cfg(feature = "uniffi")]
impl From<uniffi::UnexpectedUniFFICallbackError> for ProviderError {
    fn from(error: uniffi::UnexpectedUniFFICallbackError) -> Self {
        Self::Foreign(error.reason)
    }
}

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

/// Nonce storage interface.
pub trait NonceStorage {
    /// 1. Lookup `nonce` in the associated context (do any necessary conversion to convert the bytes of
    ///    `nonce` for comparison).
    /// 2. Return whether `nonce` already exists in the storage.
    ///
    /// # Errors
    ///
    /// This function is considered infallible and should not return an error.
    fn has(&self, nonce: &Nonce) -> Result<bool, ProviderError>;

    /// 1. Transform each nonce bytes of `nonces` to the necessary format.
    /// 2. Store the resulting nonces in the storage, disregarding whether a nonce was already present before.
    ///
    /// # Errors
    ///
    /// This function is considered infallible and should not return an error.
    fn add_many(&mut self, nonces: Vec<Nonce>) -> Result<(), ProviderError>;
}

/// Makes dirty shortcuts to get things done faster initially. Contains the pure essence of tech debt. Expect
/// things to be changed or removed quickly.
pub trait ShortcutProvider {
    /// Handle a `web-session-resume` sent from `*3MAPUSH`.
    ///
    /// # Errors
    ///
    /// This function is considered infallible and should not return an error.
    fn handle_web_session_resume(&mut self, message: WebSessionResumeMessage) -> Result<(), ProviderError>;
}

/// Settings storage interface.
pub trait SettingsProvider {
    /// Return whether unknown identities should be blocked.
    ///
    /// # Errors
    ///
    /// This function is considered infallible and should not return an error.
    fn block_unknown_identities(&self) -> Result<bool, ProviderError>;
}

/// Contact storage interface.
pub trait ContactProvider {
    /// 1. If `identity` is called with the user's Threema ID, return [`ProviderError::InvalidParameter`].
    /// 2. Return whether `identity` is present in the identity block list.
    ///
    /// # Errors
    ///
    /// In case libthreema provides invalid parameters. Otherwise, this function is considered infallible and
    /// should not return an error.
    fn is_explicitly_blocked(&self, identity: ThreemaId) -> Result<bool, ProviderError>;

    /// 1. If `identity` is the user's Threema ID, return [`ProviderError::InvalidParameter`].
    /// 2. Return whether `identity` is part of an active group (i.e. a group that is not marked as _left_).
    ///
    /// # Errors
    ///
    /// In case libthreema provides invalid parameters. Otherwise, this function is considered infallible and
    /// should not return an error.
    fn is_member_of_active_group(&self, identity: ThreemaId) -> Result<bool, ProviderError>;

    /// 1. If `identity` is the user's Threema ID, return [`ProviderError::InvalidParameter`].
    /// 2. Return whether a contact for `identity` exists.¹
    ///
    /// ¹: This must consider contacts existing in storage with any acquaintance level, including _deleted_.
    ///
    /// # Errors
    ///
    /// In case libthreema provides invalid parameters. Otherwise, this function is considered infallible and
    /// should not return an error.
    fn has(&self, identity: ThreemaId) -> Result<bool, ProviderError>;

    /// 1. If any identity of `identities` is the user's Threema ID, return
    ///    [`ProviderError::InvalidParameter`].
    /// 2. Return the amount of `identities` a contact exists for.¹
    ///
    /// ¹: This must count contacts existing in storage with any acquaintance level, including _deleted_.
    ///
    /// # Errors
    ///
    /// In case libthreema provides invalid parameters. Otherwise, this function is considered infallible and
    /// should not return an error.
    fn has_many(&self, identities: &[ThreemaId]) -> Result<usize, ProviderError>;

    /// 1. If `identity` is the user's Threema ID, return [`ProviderError::InvalidParameter`].
    /// 2. Return the contact stored for `identity`, if any.¹
    ///
    /// ¹: This must return contacts existing in storage with any acquaintance level, including _deleted_.
    ///
    /// # Errors
    ///
    /// In case libthreema provides invalid parameters. Otherwise, this function is considered infallible and
    /// should not return an error.
    fn get(&self, identity: ThreemaId) -> Result<Option<Contact>, ProviderError>;

    /// 1. If any identity of `contacts` is the user's Threema ID, return [`ProviderError::InvalidParameter`].
    /// 2. If any contact for `contacts` exists, return [`ProviderError::InvalidState`].¹
    /// 3. Add each contact of `contacts`.
    ///
    /// ¹: This must consider contacts existing in storage with any acquaintance level, including _deleted_.
    ///
    /// # Errors
    ///
    /// In case libthreema provides invalid parameters or an invalid state. Otherwise, this function is
    /// considered infallible and should not return an error.
    fn add(&mut self, contacts: Vec<Contact>) -> Result<(), ProviderError>;

    /// 1. If `contact.identity` is the user's Threema ID, return [`ProviderError::InvalidParameter`].
    /// 2. If no contact for `identity` exists, return [`ProviderError::InvalidState`].¹
    /// 3. Update the `contact`.
    ///
    /// ¹: This must consider contacts existing in storage with any acquaintance level, including _deleted_.
    ///
    /// # Errors
    ///
    /// In case libthreema provides invalid parameters or an invalid state. Otherwise, this function is
    /// considered infallible and should not return an error.
    fn update(&mut self, contacts: Vec<ContactUpdate>) -> Result<(), ProviderError>;

    /// 1. If `identity` is the user's Threema ID, return [`ProviderError::InvalidParameter`].
    /// 2. Look up the contact associated to `identity` and let `contact` be the result.¹
    /// 3. If `contact` is not defined, return [`ProviderError::InvalidState`].
    /// 4. Return the contact-defined profile picture associated to `contact` (if any).
    ///
    /// ¹: This must consider contacts existing in storage with any acquaintance level, including
    /// _deleted_.
    ///
    /// # Errors
    ///
    /// In case libthreema provides invalid parameters. Otherwise, this function is considered infallible and
    /// should not return an error.
    fn get_contact_defined_profile_picture(
        &self,
        identity: ThreemaId,
    ) -> Result<Option<ProfilePicture>, ProviderError>;

    /// 1. If `identity` is the user's Threema ID, return [`ProviderError::InvalidParameter`].
    /// 2. Look up the contact associated to `identity` and let `contact` be the result.¹
    /// 3. If `contact` is not defined, return [`ProviderError::InvalidState`].
    /// 4. Return the user-defined profile picture associated to `contact` (if any).
    ///
    /// ¹: This must consider contacts existing in storage with any acquaintance level, including _deleted_.
    ///
    /// # Errors
    ///
    /// In case libthreema provides invalid parameters. Otherwise, this function is considered infallible and
    /// should not return an error.
    fn get_user_defined_profile_picture(
        &self,
        identity: ThreemaId,
    ) -> Result<Option<ProfilePicture>, ProviderError>;
}

/// Conversation storage interface.
pub trait ConversationProvider {
    /// TODO(LIB-16): Known problematic because checking the message ID early and bailing may prevent
    /// reflection.
    ///
    /// Return whether a message with message `id` from `sender_identity` has been marked as used (i.e. points
    /// to an existing message or was explicitly marked used at some point).
    ///
    /// # Errors
    ///
    /// This function is considered infallible and should not return an error.
    fn message_is_marked_used(
        &self,
        sender_identity: ThreemaId,
        id: MessageId,
    ) -> Result<bool, ProviderError>;

    /// Set the typing indicator for a specific 1:1 conversation.
    ///
    /// 1. If the referred contact does not exist, return [`ProviderError::InvalidState`].¹
    /// 2. If `is_typing` is `true`, start a timer to display that the sender is typing in the associated
    ///    conversation for the next 15s.
    /// 3. If `is_typing` is `false`, cancel any running timer displaying that the sender is typing in the
    ///    associated conversation.
    ///
    /// ¹: This must consider contacts existing in storage with any acquaintance level, including _deleted_.
    ///
    /// # Errors
    ///
    /// In case libthreema provides invalid parameters or an invalid state. Otherwise, this function is
    /// considered infallible and should not return an error.
    fn set_typing_indicator(
        &mut self,
        sender_identity: ThreemaId,
        is_typing: bool,
    ) -> Result<(), ProviderError>;

    /// Add a new visible incoming message or update an existing message's state from an incoming message for
    /// a specific conversation.
    ///
    /// 1. If `message.body` refers to a 1:1 contact conversation:
    ///    1. If the referred contact does not exist, return [`ProviderError::InvalidState`].¹
    ///    2. If the referred contact does not have acquaintance level _direct_, log a notice, discard
    ///       `message` and return.²
    /// 2. If `message.body` refers to a group conversation and the referred group does not exist, return
    ///    [`ProviderError::InvalidState`].
    /// 3. If the `message.sender_identity`, `message.id` tuple is already present in the referred
    ///    conversation (i.e. the message has already been added at some point), log a notice, discard
    ///    `message` and return.³
    /// 4. Match over the `message.body`:
    ///    1. If `message` is a 1:1 text message, add it to the conversation.
    ///    2. (Unreachable)
    ///
    /// ¹: This must consider contacts existing in storage with any acquaintance level, including _deleted_.
    ///
    /// ²: Not all messages update the acquaintance level, e.g. typing indicators. But considering that
    /// contacts with an acquaintance level other than _direct_ are invisible and by definition should not
    /// have an associated conversation with visible messages, there is no reason to process such messages
    /// further.
    ///
    /// ³: Messages are not acknowledged from the server until they are fully processed, so this can happen
    /// even under normal conditions.
    ///
    /// # Errors
    ///
    /// In case libthreema provides invalid parameters or an invalid state. Otherwise, this function is
    /// considered infallible and should not return an error.
    fn add_or_update_incoming_message(&mut self, message: IncomingMessage) -> Result<(), ProviderError>;
}

/// In memory provider implementations for testing / the CLI
#[cfg(any(test, feature = "cli"))]
pub mod in_memory {
    use core::cell::RefCell;
    use std::{
        collections::{HashMap, HashSet, hash_map},
        rc::Rc,
    };

    use tracing::{info, warn};

    use super::{
        ContactProvider, ConversationProvider, NonceStorage, ProfilePicture, ProviderError, SettingsProvider,
        ShortcutProvider,
    };
    use crate::{
        common::{ConversationId, GroupIdentity, MessageId, Nonce, ThreemaId},
        model::{
            contact::{Contact, ContactUpdate},
            message::{IncomingMessage, IncomingMessageBody, WebSessionResumeMessage},
        },
        utils::apply::TryApply as _,
    };

    /// Default shortcut provider, applying reasonable shortcut defaults.
    pub struct DefaultShortcutProvider;
    impl ShortcutProvider for DefaultShortcutProvider {
        fn handle_web_session_resume(
            &mut self,
            _message: WebSessionResumeMessage,
        ) -> Result<(), ProviderError> {
            warn!("Discarding web-session-resume");
            Ok(())
        }
    }

    /// In-memory settings.
    #[derive(Default)]
    pub struct InMemoryDbSettings {
        /// Whether unknown identities should be blocked.
        pub block_unknown_identities: bool,
    }

    /// In-memory nonce storage provider.
    pub struct InMemoryDbNonceProvider(Rc<RefCell<HashSet<Nonce>>>);
    impl NonceStorage for InMemoryDbNonceProvider {
        fn has(&self, nonce: &Nonce) -> Result<bool, ProviderError> {
            Ok(self.0.borrow().contains(nonce))
        }

        fn add_many(&mut self, nonces: Vec<Nonce>) -> Result<(), ProviderError> {
            self.0.borrow_mut().extend(nonces);
            Ok(())
        }
    }

    /// In-memory settings provider.
    pub struct InMemoryDbSettingsProvider(Rc<RefCell<InMemoryDbSettings>>);
    impl SettingsProvider for InMemoryDbSettingsProvider {
        fn block_unknown_identities(&self) -> Result<bool, ProviderError> {
            Ok(self.0.borrow().block_unknown_identities)
        }
    }

    type InMemoryDbContacts = HashMap<ThreemaId, (Contact, Option<ProfilePicture>)>;

    /// In-memory contacts provider.
    pub struct InMemoryDbContactProvider {
        pub(crate) user_identity: ThreemaId,
        pub(crate) blocked_identities: Rc<RefCell<HashSet<ThreemaId>>>,
        pub(crate) contacts: Rc<RefCell<InMemoryDbContacts>>,
        pub(crate) _groups: Rc<RefCell<HashMap<GroupIdentity, bool>>>, // TODO(LIB-16): Implement groups
    }
    impl ContactProvider for InMemoryDbContactProvider {
        fn is_explicitly_blocked(&self, identity: ThreemaId) -> Result<bool, ProviderError> {
            // Bail if identity is the user's identity
            if identity == self.user_identity {
                return Err(ProviderError::InvalidParameter(
                    "Unexpected encounter of user's identity in contact provider".to_owned(),
                ));
            }

            // Check if the identity is explicitly blocked
            Ok(self.blocked_identities.borrow().contains(&identity))
        }

        fn is_member_of_active_group(&self, identity: ThreemaId) -> Result<bool, ProviderError> {
            // Bail if identity is the user's identity
            if identity == self.user_identity {
                return Err(ProviderError::InvalidParameter(
                    "Unexpected encounter of user's identity in contact provider".to_owned(),
                ));
            }

            // Check if the identity is a member of an active group
            // TODO(LIB-16): Implement groups
            Ok(false)
        }

        fn has(&self, identity: ThreemaId) -> Result<bool, ProviderError> {
            // Bail if identity is the user's identity
            if identity == self.user_identity {
                return Err(ProviderError::InvalidParameter(
                    "Unexpected encounter of user's identity in contact provider".to_owned(),
                ));
            }

            // Check if a contact for identity exists
            Ok(self.contacts.borrow().contains_key(&identity))
        }

        fn has_many(&self, identities: &[ThreemaId]) -> Result<usize, ProviderError> {
            identities.iter().try_fold(0_usize, |n_identities, identity| {
                // Bail if identity is the user's identity
                if *identity == self.user_identity {
                    return Err(ProviderError::InvalidParameter(
                        "Unexpected encounter of user's identity in contact provider".to_owned(),
                    ));
                }

                // Increase count if a contact for identity exists
                if self.contacts.borrow().contains_key(identity) {
                    Ok(n_identities.saturating_add(1))
                } else {
                    Ok(n_identities)
                }
            })
        }

        fn get(&self, identity: ThreemaId) -> Result<Option<Contact>, ProviderError> {
            // Bail if identity is the user's identity
            if identity == self.user_identity {
                return Err(ProviderError::InvalidParameter(
                    "Unexpected encounter of user's identity in contact provider".to_owned(),
                ));
            }

            // Get a snapshot of the contact (if it exists)
            let contacts = self.contacts.borrow();
            let Some((contact, _)) = contacts.get(&identity) else {
                return Ok(None);
            };
            Ok(Some(contact.clone()))
        }

        fn add(&mut self, contacts: Vec<Contact>) -> Result<(), ProviderError> {
            for contact in contacts {
                // Bail if the contact's identity is the user's identity or the contact already exists
                if self.has(contact.identity)? {
                    return Err(ProviderError::InvalidState(
                        "Contact to be added already exists".to_owned(),
                    ));
                }

                // Add the contact
                let _ = self
                    .contacts
                    .borrow_mut()
                    .insert(contact.identity, (contact, None));
            }

            Ok(())
        }

        fn update(&mut self, contacts: Vec<ContactUpdate>) -> Result<(), ProviderError> {
            for update in contacts {
                // Bail if identity is the user's identity
                if update.identity == self.user_identity {
                    return Err(ProviderError::InvalidParameter(
                        "Unexpected encounter of user's identity in contact provider".to_owned(),
                    ));
                }

                // Ensure the contact exists
                let mut contacts = self.contacts.borrow_mut();
                let Some((contact, _)) = contacts.get_mut(&update.identity) else {
                    return Err(ProviderError::InvalidState(
                        "Contact to be updated does not exist".to_owned(),
                    ));
                };

                // Update the contact
                contact
                    .try_apply(update)
                    .map_err(|error| ProviderError::Foreign(error.to_string()))?;
            }

            Ok(())
        }

        fn get_contact_defined_profile_picture(
            &self,
            identity: ThreemaId,
        ) -> Result<Option<ProfilePicture>, ProviderError> {
            // Bail if identity is the user's identity
            if identity == self.user_identity {
                return Err(ProviderError::InvalidParameter(
                    "Unexpected encounter of user's identity in contact provider".to_owned(),
                ));
            }

            // Ensure the contact exists
            let contacts = self.contacts.borrow();
            let Some((_, profile_picture)) = contacts.get(&identity) else {
                return Err(ProviderError::InvalidState(
                    "Contact to retrieve the contact-defined profile picture from does not exist".to_owned(),
                ));
            };

            // Hand out the contact-defined profile picture
            Ok(profile_picture.clone())
        }

        fn get_user_defined_profile_picture(
            &self,
            identity: ThreemaId,
        ) -> Result<Option<ProfilePicture>, ProviderError> {
            // Bail if identity is the user's identity
            if identity == self.user_identity {
                return Err(ProviderError::InvalidParameter(
                    "Unexpected encounter of user's identity in contact provider".to_owned(),
                ));
            }

            // Ensure the contact exists
            if !self.contacts.borrow().contains_key(&identity) {
                return Err(ProviderError::InvalidState(
                    "Contact to retrieve the user-defined profile picture from does not exist".to_owned(),
                ));
            }

            // This implementation does not have a user-defined profile picture
            Ok(None)
        }
    }

    type InMemoryDbConversations = HashMap<ConversationId, HashMap<(ThreemaId, MessageId), IncomingMessage>>;

    /// In-memory message provider.
    pub struct InMemoryDbMessageProvider {
        pub(crate) contacts: Rc<RefCell<InMemoryDbContacts>>,
        pub(crate) conversations: Rc<RefCell<InMemoryDbConversations>>,
    }
    impl ConversationProvider for InMemoryDbMessageProvider {
        fn message_is_marked_used(
            &self,
            sender_identity: ThreemaId,
            id: MessageId,
        ) -> Result<bool, ProviderError> {
            // TODO(LIB-16): This is pretty expensive. We'll have to figure this out.
            for conversation in self.conversations.borrow().values() {
                if conversation.contains_key(&(sender_identity, id)) {
                    return Ok(true);
                }
            }
            Ok(false)
        }

        fn set_typing_indicator(
            &mut self,
            _sender_identity: ThreemaId,
            _is_typing: bool,
        ) -> Result<(), ProviderError> {
            // Ignore
            Ok(())
        }

        fn add_or_update_incoming_message(&mut self, message: IncomingMessage) -> Result<(), ProviderError> {
            let mut conversations = self.conversations.borrow_mut();

            // Get or create the conversation
            let conversation = conversations
                .entry(match &message.body {
                    IncomingMessageBody::Contact { .. } => {
                        // Ensure the referred contact exists
                        if !self.contacts.borrow().contains_key(&message.sender_identity) {
                            return Err(ProviderError::InvalidState(
                                "Contact the incoming message refers to does not exist".to_owned(),
                            ));
                        }

                        ConversationId::Contact(message.sender_identity)
                    },

                    IncomingMessageBody::Group(body) => {
                        // TODO(LIB-16): Ensure the referred group exists
                        ConversationId::Group(body.group_identity)
                    },
                })
                .or_default();

            // Add, if possible
            match conversation.entry((message.sender_identity, message.id)) {
                hash_map::Entry::Occupied(_) => {
                    // A message from the sender with the same message ID already existss
                    warn!(
                        sender_identity = ?message.sender_identity,
                        nessage_id = ?message.id,
                        "Discarding message that already exists"
                    );
                },
                hash_map::Entry::Vacant(vacant_entry) => {
                    info!(?message, "Added message");
                    let _ = vacant_entry.insert(message);
                },
            }
            Ok(())
        }
    }

    /// Initializer for an [`InMemoryDb`].
    pub struct InMemoryDbInit {
        /// The user's identity.
        pub user_identity: ThreemaId,
        /// Initial settings.
        pub settings: InMemoryDbSettings,
        /// Initial blocked identities.
        pub blocked_identities: Vec<ThreemaId>,
        /// Initial contacts.
        pub contacts: Vec<Contact>,
    }

    /// In-memory database.
    pub struct InMemoryDb {
        pub(crate) user_identity: ThreemaId,
        pub(crate) csp_e2e_nonce_storage: Rc<RefCell<HashSet<Nonce>>>,
        pub(crate) d2x_nonce_storage: Rc<RefCell<HashSet<Nonce>>>,
        pub(crate) settings: Rc<RefCell<InMemoryDbSettings>>,
        pub(crate) blocked_identities: Rc<RefCell<HashSet<ThreemaId>>>,
        pub(crate) contacts: Rc<RefCell<InMemoryDbContacts>>,
        pub(crate) groups: Rc<RefCell<HashMap<GroupIdentity, bool>>>, // TODO(LIB-16): Implement groups
        pub(crate) conversations: Rc<RefCell<InMemoryDbConversations>>,
    }

    impl From<InMemoryDbInit> for InMemoryDb {
        fn from(init: InMemoryDbInit) -> Self {
            Self {
                user_identity: init.user_identity,
                csp_e2e_nonce_storage: Rc::new(RefCell::new(HashSet::new())),
                d2x_nonce_storage: Rc::new(RefCell::new(HashSet::new())),
                settings: Rc::new(RefCell::new(init.settings)),
                blocked_identities: Rc::new(RefCell::new(init.blocked_identities.into_iter().collect())),
                contacts: Rc::new(RefCell::new(
                    init.contacts
                        .into_iter()
                        .map(|contact| (contact.identity, (contact, None)))
                        .collect(),
                )),
                groups: Rc::new(RefCell::new(HashMap::new())), // TODO(LIB-16): Implement groups
                conversations: Rc::new(RefCell::new(HashMap::new())),
            }
        }
    }

    impl InMemoryDb {
        /// CSP nonce provider.
        #[must_use]
        pub fn csp_e2e_nonce_provider(&mut self) -> InMemoryDbNonceProvider {
            InMemoryDbNonceProvider(Rc::clone(&self.csp_e2e_nonce_storage))
        }

        /// D2D nonce provider.
        #[must_use]
        pub fn d2d_nonce_provider(&mut self) -> InMemoryDbNonceProvider {
            InMemoryDbNonceProvider(Rc::clone(&self.d2x_nonce_storage))
        }

        /// Settings provider.
        #[must_use]
        pub fn settings_provider(&self) -> InMemoryDbSettingsProvider {
            InMemoryDbSettingsProvider(Rc::clone(&self.settings))
        }

        /// Contact provider.
        #[must_use]
        pub fn contact_provider(&mut self) -> InMemoryDbContactProvider {
            InMemoryDbContactProvider {
                user_identity: self.user_identity,
                blocked_identities: Rc::clone(&self.blocked_identities),
                contacts: Rc::clone(&self.contacts),
                _groups: Rc::clone(&self.groups),
            }
        }

        /// Message provider.
        #[must_use]
        pub fn message_provider(&mut self) -> InMemoryDbMessageProvider {
            InMemoryDbMessageProvider {
                contacts: Rc::clone(&self.contacts),
                conversations: Rc::clone(&self.conversations),
            }
        }
    }
}
