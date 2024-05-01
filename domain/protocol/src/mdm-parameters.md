# MDM Parameters

## Introduction

This document describes the behavior of MDM (Mobile Device Management)
parameters in the context of a Threema app.

MDM parameters always override a user setting. The wording "this parameter
overrides user setting X" implies that the affected user setting is forcefully
set to the parameter value and cannot be modified by the user until the
parameter value has been unset.

## External MDM vs Threema App Configuration

There are two ways how MDM parameters can be defined and updated:

- **External MDM** systems can be used. These systems make use of the operating
  system's app configuration system (e.g. Android's [app
  restrictions][mdm-android] or iOS' [NSUserDefaults][mdm-ios]), to allow
  passing custom parameters to an app. Most of these systems allow propagating
  configuration values immediately to the apps through a change listener inside
  the app, if the app is running. More information can be found on the website
  of the [AppConfig Community][appconfig].
- **Threema App Configuration** is an alternative mechanism, which allows
  setting [MDM parameters directly from the Threema Work cockpit][mdm-threema].
  These parameters are synchronized with the regular Threema Work background
  synchronization and may take up to 24 hours to propagate to the client.

[mdm-android]: https://developer.android.com/work/managed-configurations
[mdm-ios]:
  https://developer.apple.com/documentation/foundation/nsuserdefaults#2926901
[appconfig]: https://www.appconfig.org/
[mdm-threema]: https://threema.ch/en/work/app-configuration

If an external MDM and Threema App Configuration are enabled simultaneously,
then both parameter sets are merged. If the same parameter is defined by both
systems, then only one of them is selected. The precedence can be configured
through the Threema Work cockpit.

## Storage

A total set of three parameter lists need to be stored by the app:

- filtered parameters from the Threema App Configuration,
- filtered parameters from the external MDM, and
- merged and applied parameters.

## MDM Parameter Steps

The following steps are defined as _MDM Filter Steps_:

1. Let `parameters` be the MDM parameters that have been provided and `source`
   be the source of that provider (i.e. either _external_ for an external MDM or
   _threema_ for the Threema App Configuration).
2. For each MDM parameter in `parameters`:
   1. If the parameter is unknown, log a warning and remove it from
      `parameters`.
   2. If the parameter value is invalid, log a warning and remove it from
      `parameters`.
   3. If `source` is _threema_ and the parameter name is any of the following,
      log a warning and remove it from `parameters`:
      - `th_id_backup`
      - `th_id_backup_password`
      - `th_license_username`
      - `th_license_password`
      - `th_safe_password`
3. Return `parameters`.

The following steps are defined as _MDM Update Steps_ and apply whenever the
parameters of the external MDM or the Threema App Configuration have been
refreshed:

1. Let `parameters` be the MDM parameters that have been provided.
2. Run the _MDM Filter Steps_ for `parameters` and overwrite `parameters` with
   the result.
3. If `parameters` is identical to the currently stored filtered set of
   parameters of this source, abort these steps.
4. (MD) Begin a transaction (scope: `MDM_PARAMETER_SYNC`, precondition: MDM
   parameters have not been updated by another device in the meantime, otherwise
   restart these steps from the beginning).
5. (MD) Reflect a `MdmParameterSync` message and commit the transaction.
6. Run the _MDM Merge And Apply Steps_ with `parameters` and the current
   parameters of the respective other source.

The following steps are defined as _MDM Merge And Apply Steps_.

1. Let `threema-parameters` be the provided set of parameters source from
   Threema App Configuration.
2. Let `external-parameters` be the provided set of parameters sourced from the
   external MDM.
3. Let `precedence` define the source parameter precedence.¹
4. Run the _MDM Filter Steps_ for the `threema-parameters` and overwrite
   `threema-parameters` with the result.
5. Run the _MDM Filter Steps_ for the `external-parameters` and overwrite
   `external-parameters` with the result.
6. Let `merged-parameters` be the union of `threema-parameters` and
   `external-parameters`. If a parameter is defined in both sets, then
   `precedence` defines which source takes precedence.
7. If `merged-parameters` is identical to the currently stored set of merged and
   applied parameters, abort these steps.
8. Store `threema-parameters`, `external-parameters` and `merged-parameters`,
   overwriting the previous parameter sets.
9. For each `parameter` of `merged-parameters`, run the associated steps defined
   for the parameter.

¹: When running these steps as part of a Threema Work sync, the precedence is
defined by the most recently received `override` parameter with `true` mapping
to _threema_ and `false` mapping to _external_. For reflected
`md-d2d-sync.MdmParameters`, the precedence is defined as part of the message.

## Parameters

[//]: # TODO(SE-307): Document steps for all parameters.

### th_disable_add_contact (boolean)

When this parameter is set:

1. Ensure that entry points for manually adding contacts are disabled if `true`
   or enabled if `false`.¹

When this parameter is unset:

1. Ensure that entry points for manually adding contacts are enabled.

¹: Contacts can still be added implicitly, e.g. through contact import or when
receiving a message from an unknown contact.
