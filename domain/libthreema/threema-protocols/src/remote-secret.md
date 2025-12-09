# Remote Secret Subprotocol

This protocol provides an optional protection layer for customers using Threema
Work, cryptographically guarding access to application storage with the help of
a Remote Secret stored on the Work server.

With this mechanism it is possible to remotely block access to the application
storage, temporarily or permanently. Given a timely response, it allows to
prevent data theft, e.g. when a device has been stolen.

## Terminology

- `RS`: Remote Secret (Key)
- `RSH`: Remote Secret Hash
- `RSHID`: Remote Secret Hash tied to an Identity
- `RSAT`: Remote Secret Authentication Token

## Storage Protection

Generally, all stored data that can be protected by the Remote Secret feature
should be protected by it. With the following exceptions:

- RSAT
- RSH/RSHID
- Identity/Safe/Data backups¹
- When using RSHID: The user's Threema ID
- For OnPrem: Cached OPPF file²

Due to significant platform differences, the concrete implementation depends on
the respective platform.

¹: Customers that want to prevent access to backups without RS are advised to
disable backups via MDM.

²: The domain rules and Work server URL from the OPPF file are required to
access the Remote Secret endpoint securely.

## Activation

The following steps are defined as the _Remote Secret Activate Steps_:

1. If storage is already protected by the Remote Secret feature, abort these
   steps.
2. Let RS be a random key.
3. In a loop:
   1. Call the _Remote Secret Create_ endpoint with RS.
   2. If the received status code is `401` and indicates invalid credentials,
      prompt the user to enter their Work credentials and continue with the next
      loop iteration.
   3. If the received status code is not `200` or could not be decoded,
      exceptionally abort these steps.
   4. Extract RSAT and RSH/RSHID from the response.
4. Apply RS to protect inner application storage and store RSAT and RSH/RSHID.

## Deactivation

The following steps are defined as the _Remote Secret Deactivate Steps_:

1. If storage is not protected by the Remote Secret feature, abort these steps.
2. Stop the task that is continuously running the _Remote Secret Monitor Steps_.
3. Run the _Remote Secret Monitor Steps_ until it yields RS.
4. Remove the protection of inner storage by RS and purge RSAT and RSH/RSHID
   from storage.
5. Schedule a persistent task bound to the application to run the _Remote Secret
   Delete Steps_ with RSAT.

The following steps are defined as the _Remote Secret Delete Steps_:

1. Let RSAT be the RSAT associated to RS the storage was previously protected
   with.
2. Call the _Remote Secret Delete_ endpoint with RSAT.
3. In a loop:
   1. If the received status code is `401` and indicates invalid credentials,
      prompt the user to enter their Work credentials and continue with the next
      loop iteration.
   2. If the received status code is not `200` or `204`, log an error and abort
      these steps.

## Monitoring

The following steps are defined as the _Remote Secret Monitor Steps_:

1. Let `interval` be `10s`, `max-failed-attempts` be `5` and `failed-attempts`
   be `0`.
2. In a loop:
   1. Call the _Remote Secret Monitor_ endpoint with RSAT.
   2. If the received status code is `403`, run the _Remote Secret Lock Steps_
      with `reason` _locked_ and abort these steps.
   3. If the received status code is `404`, run the _Remote Secret Lock Steps_
      with `reason` _not found_ and abort these steps.
   4. If the received status code is not `200` or the response could not be
      decoded:
      1. If `failed-attempts` is greater than or equal to `max-failed-attempts`,
         run the _Remote Secret Lock Steps_ with `reason` _server error_ and
         abort these steps.
      2. Increase `failed-attempts` by `1`.
   5. If the status code is `200` and the response could be decoded:
      1. If the derived RSH/RSHID from RS of the response does not match the
         stored RSH/RSHID, run the _Remote Secret Lock Steps_ with `reason`
         _mismatch_ and abort these steps.
      2. Reset `failed-attempts` to `0`.
      3. Update `interval` and `max-failed-attempts` from the response.
      4. If the RS was not yielded before, yield the retrieved RS.
   6. Wait for `interval` before continuing in the next iteration.

The following steps are defined as the _Remote Secret Lock Steps_:

1. Let `reason` be any of the following lock reasons: _locked_, _not found_,
   _server error_, _mismatch_.
2. Immediately lock access to the storage and purge any keys from memory.
3. Notify the user according to `reason` with the option to manually retry.
