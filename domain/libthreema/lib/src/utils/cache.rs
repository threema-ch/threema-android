//! Cache-related utilities.
use core::hash::Hash;
use std::collections::{HashMap, hash_map::Entry};

use crate::utils::time::Instant;

/// Cache store bound by time
///
/// Values are timestamped when inserted and are evicted if expired at time of retrieval.
///
/// Note: This cache is in-memory only
#[derive(Debug)]
pub(crate) struct TimedCache<K, V, const EXPIRES_AT_S: u64>(HashMap<K, (Instant, V)>);
impl<K: Hash + Eq, V, const EXPIRES_AT_S: u64> TimedCache<K, V, EXPIRES_AT_S> {
    /// Creates a new `TimedCache` with the specified lifespan.
    pub(crate) fn new(inner: HashMap<K, (Instant, V)>) -> Self {
        Self(inner)
    }

    /// Refresh the cache by removing any expired values from the cache.
    #[expect(clippy::allow_attributes, reason = "Test uses it...")]
    #[allow(dead_code, reason = "Will use later")]
    pub(crate) fn refresh(&mut self) {
        self.0.retain(|_, (inserted_at, _)| Self::valid(inserted_at));
    }

    /// Get a mutable reference to a value in the cache.
    pub(crate) fn get_mut(&mut self, key: K) -> Option<&mut V> {
        // Lookup the value
        let Entry::Occupied(entry) = self.0.entry(key) else {
            return None;
        };

        // Ensure the value is still valid or purge it
        let (inserted_at, _) = entry.get();
        if !Self::valid(inserted_at) {
            let _ = entry.remove();
            return None;
        }

        // Return the value
        let (_, value) = entry.into_mut();
        Some(value)
    }

    /// Insert a value pair into the cache.
    ///
    /// Returns the previous value if present and still valid.
    pub(crate) fn insert(&mut self, key: K, value: V) -> Option<V> {
        self.0
            .insert(key, (Instant::now(), value))
            .take_if(|(inserted_at, _)| Self::valid(inserted_at))
            .map(|(_, value)| value)
    }

    fn valid(inserted_at: &Instant) -> bool {
        inserted_at.elapsed().as_secs() < EXPIRES_AT_S
    }
}

#[expect(clippy::unwrap_used, reason = "Test code")]
#[cfg(test)]
mod tests {
    use super::*;
    use crate::utils::time::Duration;

    #[test]
    fn timed_cache() {
        let mut cache = TimedCache::<u64, &'static str, 60>::new(
            [(
                1,
                (
                    Instant::now().checked_sub(Duration::from_mins(1)).unwrap(),
                    "gone",
                ),
            )]
            .into_iter()
            .collect(),
        );

        assert_eq!(cache.0.len(), 1, "Should initially contain one value");

        assert_eq!(cache.insert(2, "kept"), None);
        assert_eq!(cache.0.len(), 2, "Should contain two values after insertion");

        assert_eq!(
            cache.get_mut(2),
            Some(&mut "kept"),
            "Should maintain two values when getting one that did not expire"
        );

        assert_eq!(
            cache.get_mut(1),
            None,
            "Should remove the value that expired when getting it"
        );
        assert_eq!(cache.0.len(), 1, "Should only have the value that didn't expire");
    }

    #[test]
    fn refresh() {
        let mut cache = TimedCache::<u64, &'static str, 60>::new(
            [(
                1,
                (
                    Instant::now().checked_sub(Duration::from_mins(1)).unwrap(),
                    "gone",
                ),
            )]
            .into_iter()
            .collect(),
        );

        cache.refresh();
        assert_eq!(
            cache.0.len(),
            0,
            "Should purge all expired values after a refresh"
        );
    }
}
