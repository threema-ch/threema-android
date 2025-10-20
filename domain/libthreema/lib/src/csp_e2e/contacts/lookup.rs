//! Task for looking up identities.
use core::mem;
use std::collections::{HashMap, HashSet, hash_map::Entry};

use const_format::formatcp;
use libthreema_macros::{DebugVariantNames, Name, VariantNames};
use tracing::{debug, error, info, warn};

use crate::{
    common::{ThreemaId, task::TaskLoop},
    csp_e2e::{CspE2eProtocolContext, CspE2eProtocolError, Flavor},
    https::{HttpsRequest, HttpsResult, directory, work_directory},
    model::contact::{Contact, ContactInit, ContactUpdateError},
    utils::cache::TimedCache,
};

/// Contact that was looked up.
#[derive(Clone)]
pub(crate) enum ContactResult {
    /// The contact is the user itself.
    User,
    /// The contact already exists.
    ExistingContact(Contact),
    /// The contact does not yet exist.
    NewContact(ContactInit),
    /// The contact is invalid (has been revoked or never existed).
    Invalid(ThreemaId),
}
impl ContactResult {
    pub(crate) fn update_from_work_contact(
        &mut self,
        work_contact: work_directory::WorkContact,
    ) -> Result<(), ContactUpdateError> {
        // Apply update (if possible)
        match self {
            ContactResult::User | ContactResult::Invalid(_) => Ok(()),
            ContactResult::ExistingContact(contact) | ContactResult::NewContact(contact) => {
                work_contact.update(contact)
            },
        }
    }
}

/// 1. If `work_directory_request` is present in a non-Work flavored context, log an error, discard the task
///    and abort these steps.
/// 2. Run the HTTPS requests as defined by [`HttpsRequest`] in parallel and let `responses` be the result.
///    The requests should be independent from one another (i.e. if one request fails, it should not impact
///    the other).
/// 3. Provide `responses` to the associated task as a [`ContactsLookupResponse`] and poll again.
pub struct ContactsLookupInstruction {
    /// The directory request to be made.
    pub directory_request: HttpsRequest,

    /// The work directory request to be made, if any.
    pub work_directory_request: Option<HttpsRequest>,
}

/// Possible responses to an [`ContactsLookupInstruction`].
#[derive(Name)]
pub struct ContactsLookupResponse {
    /// Result for the directory request.
    pub directory_result: HttpsResult,

    /// Result for the work directory request, if one was needed.
    pub work_directory_result: Option<HttpsResult>,
}

pub(crate) type ContactsLookupLoop = TaskLoop<ContactsLookupInstruction, HashMap<ThreemaId, ContactResult>>;

/// Cache for contact lookups at the directory. Entries expire after 10 minutes.
pub(crate) type ContactLookupCache = TimedCache<ThreemaId, CachedContactResult, 600>;

#[derive(PartialEq, Eq)]
pub(crate) enum CacheLookupPolicy {
    Allow,
    #[expect(dead_code, reason = "Will use later")]
    Deny,
}

/// Contact that was looked up.
#[derive(Clone)]
pub(crate) enum CachedContactResult {
    /// The contact does not yet exist.
    NewContact(ContactInit),
    /// The contact is invalid (has been revoked or never existed).
    Invalid(ThreemaId),
}
impl From<ContactResult> for Option<(ThreemaId, CachedContactResult)> {
    fn from(contact: ContactResult) -> Self {
        match contact {
            ContactResult::User => None,
            ContactResult::ExistingContact(contact) | ContactResult::NewContact(contact) => {
                Some((contact.identity, CachedContactResult::NewContact(contact)))
            },
            ContactResult::Invalid(identity) => Some((identity, CachedContactResult::Invalid(identity))),
        }
    }
}
impl From<CachedContactResult> for ContactResult {
    fn from(contact: CachedContactResult) -> Self {
        match contact {
            CachedContactResult::NewContact(contact) => Self::NewContact(contact),
            CachedContactResult::Invalid(identity) => Self::Invalid(identity),
        }
    }
}

struct LookupResult {
    known: HashMap<ThreemaId, ContactResult>,
    unknown: Vec<ThreemaId>,
}
impl LookupResult {
    fn new(capacity: usize) -> Self {
        Self {
            known: HashMap::with_capacity(capacity),
            unknown: Vec::with_capacity(capacity),
        }
    }
}

struct InitState {
    identities: Vec<ThreemaId>,
    cache_policy: CacheLookupPolicy,
}

struct RequestIdentitiesState {
    contacts: LookupResult,
    response: Option<ContactsLookupResponse>,
}

#[derive(DebugVariantNames, VariantNames)]
enum State {
    Error(CspE2eProtocolError),
    Init(InitState),
    RequestIdentities(RequestIdentitiesState),
    Done,
}
impl State {
    fn poll_init(
        context: &mut CspE2eProtocolContext,
        state: InitState,
    ) -> Result<(Self, ContactsLookupLoop), CspE2eProtocolError> {
        let mut contacts = LookupResult::new(state.identities.len());
        for identity in state.identities {
            // Check if identity is the user itself
            if identity == context.csp_e2e.user_identity {
                let _ = contacts.known.insert(identity, ContactResult::User);
                continue;
            }

            // Skip lookup if identity is a _Special Contact_
            //
            // Note: Unlike other _Predefined Contact_s, _Special Contact_s will not be added to the
            // set of contacts and therefore would create a lookup on the directory server every
            // time which we avoid this way.
            if let Some(predefined_contact) = context.config.predefined_contacts.get(&identity)
                && predefined_contact.special
            {
                let _ = contacts.known.insert(
                    identity,
                    ContactResult::NewContact(ContactInit::from(predefined_contact)),
                );
                continue;
            }

            // Lookup existing contact
            if let Some(existing_contact) = context.contacts.borrow().get(identity)? {
                let _ = contacts
                    .known
                    .insert(identity, ContactResult::ExistingContact(existing_contact));
                continue;
            }

            // Lookup from cache
            if state.cache_policy == CacheLookupPolicy::Allow
                && let Some(cached_contact) = context.contact_lookup_cache.get_mut(identity)
            {
                let _ = contacts
                    .known
                    .insert(identity, ContactResult::from(cached_contact.clone()));
                continue;
            }

            // Couldn't find the contact
            contacts.unknown.push(identity);
        }

        // Check if we have found all contacts already
        if contacts.unknown.is_empty() {
            return Ok((Self::Done, ContactsLookupLoop::Done(contacts.known)));
        }

        // Request the unknown identities from the directory (and the work directory, if needed).
        info!(identities = ?contacts.unknown, "Fetching identities");
        let instruction = ContactsLookupInstruction {
            directory_request: directory::request_identities(
                &context.client_info,
                &context.config.directory_server_url,
                &context.csp_e2e.flavor,
                &contacts.unknown,
            ),
            work_directory_request: match &context.csp_e2e.flavor {
                Flavor::Consumer => None,
                Flavor::Work(work_context) => Some(work_directory::request_contacts(
                    &context.client_info,
                    &context.config.work_server_url,
                    work_context,
                    &contacts.unknown,
                )),
            },
        };
        Ok((
            Self::RequestIdentities(RequestIdentitiesState {
                contacts,
                response: None,
            }),
            ContactsLookupLoop::Instruction(instruction),
        ))
    }

    fn poll_request(
        context: &mut CspE2eProtocolContext,
        mut state: RequestIdentitiesState,
    ) -> Result<(Self, ContactsLookupLoop), CspE2eProtocolError> {
        // Ensure the caller provided the response
        let Some(response) = state.response else {
            return Err(CspE2eProtocolError::InvalidState(formatcp!(
                "{} result was not provided for '{}' state",
                ContactsLookupResponse::NAME,
                State::REQUEST_IDENTITIES,
            )));
        };

        {
            let mut unknown: HashSet<ThreemaId> = state.contacts.unknown.into_iter().collect();

            // Add all determined contact inits
            let directory_result = directory::handle_identities_result(response.directory_result)?;
            for mut contact in directory_result {
                // Ensure the contact was requested
                if !unknown.remove(&contact.identity) {
                    warn!(identity = ?contact.identity, "Discarding identity not requested");
                    continue;
                }

                // If the contact is a _Predefined Contact_, update it
                if let Some(predefined_contact) = context.config.predefined_contacts.get(&contact.identity)
                    && let Err(error) = predefined_contact.update(&mut contact)
                {
                    error!(identity = ?contact.identity, ?error,
                            "Unable to update contact from predefined contact");
                    return Err(CspE2eProtocolError::InternalError(format!(
                        "Unable to update contact from predefined contact: {error}"
                    )));
                }

                // Add the contact
                let Entry::Vacant(entry) = state.contacts.known.entry(contact.identity) else {
                    error!(identity = ?contact.identity,
                        "Accounting error, identity already known");
                    continue;
                };
                let _ = entry.insert(ContactResult::NewContact(contact.clone()));
            }

            // The directory server only yields us known and unrevoked identities, so we'll need to
            // add the remaining ones as invalid
            for identity in unknown {
                match state.contacts.known.entry(identity) {
                    Entry::Occupied(_) => {
                        error!(?identity, "Accounting error, identity already known");
                    },
                    Entry::Vacant(entry) => {
                        let _ = entry.insert(ContactResult::Invalid(identity));
                    },
                }
            }
        }

        // Update all contacts that could be identified as work contacts of the same subscription
        match (&context.csp_e2e.flavor, response.work_directory_result) {
            (Flavor::Consumer, None) => {},
            (Flavor::Work(_), Some(work_directory_result)) => {
                let work_directory_result = work_directory::handle_contacts_result(work_directory_result)?;
                for work_contact in work_directory_result {
                    let identity = work_contact.identity;

                    // Update the contact
                    let Entry::Occupied(mut entry) = state.contacts.known.entry(work_contact.identity) else {
                        warn!(?identity, "Discarding work contact not requested");
                        continue;
                    };
                    if let Err(error) = entry.get_mut().update_from_work_contact(work_contact) {
                        error!(
                            ?identity,
                            ?error,
                            "Unable to update contact result from work contact"
                        );
                        return Err(CspE2eProtocolError::ServerError(format!(
                            "Unable to update contact result from work contact: {error}"
                        )));
                    }
                }
            },
            (Flavor::Consumer, Some(_)) => {
                error!("Discarding unexpected work directory result for consumer flavor");
            },
            (Flavor::Work(_), None) => {
                let message = "Missing work directory result for work flavor";
                error!(message);
                return Err(CspE2eProtocolError::InternalError(message.to_owned()));
            },
        }

        // TODO(LIB-41): Run the contact import flow that is still TBD to update the verification
        // level if a matching phone number / email was found and set sync state accordingly.

        // Add all determined contacts to the cache
        for contact in state.contacts.known.values() {
            if let Some((identity, cached_contact)) = contact.clone().into() {
                let _ = context.contact_lookup_cache.insert(identity, cached_contact);
            }
        }

        // Done
        Ok((Self::Done, ContactsLookupLoop::Done(state.contacts.known)))
    }
}

/// Subtask for looking up (valid) contacts.
#[derive(Debug, Name)]
pub(crate) struct ContactsLookupSubtask {
    state: State,
}
impl ContactsLookupSubtask {
    pub(crate) fn new(identities: Vec<ThreemaId>, cache_policy: CacheLookupPolicy) -> ContactsLookupSubtask {
        Self {
            state: State::Init(InitState {
                identities,
                cache_policy,
            }),
        }
    }

    #[tracing::instrument(skip_all, fields(?self))]
    pub(crate) fn poll(
        &mut self,
        context: &mut CspE2eProtocolContext,
    ) -> Result<ContactsLookupLoop, CspE2eProtocolError> {
        let result = match mem::replace(
            &mut self.state,
            State::Error(CspE2eProtocolError::InvalidState(formatcp!(
                "{} in a transitional state",
                ContactsLookupSubtask::NAME
            ))),
        ) {
            State::Error(error) => Err(error),
            State::Init(state) => State::poll_init(context, state),
            State::RequestIdentities(state) => State::poll_request(context, state),
            State::Done => Err(CspE2eProtocolError::InvalidState(formatcp!(
                "{} already done",
                ContactsLookupSubtask::NAME
            ))),
        };
        match result {
            Ok((state, instruction)) => {
                self.state = state;
                debug!(state = ?self.state, "Changed state");
                Ok(instruction)
            },
            Err(error) => {
                self.state = State::Error(error.clone());
                debug!(state = ?self.state, "Changed state to error");
                Err(error)
            },
        }
    }

    #[tracing::instrument(skip_all, fields(?self))]
    pub(crate) fn response(&mut self, response: ContactsLookupResponse) -> Result<(), CspE2eProtocolError> {
        let State::RequestIdentities(state) = &mut self.state else {
            return Err(CspE2eProtocolError::InvalidState(formatcp!(
                "Must be in '{}' state",
                State::REQUEST_IDENTITIES
            )));
        };
        let _ = state.response.insert(response);
        Ok(())
    }
}
