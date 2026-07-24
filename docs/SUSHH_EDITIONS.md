# Sushh editions and broadcast protocol

Sushh is built as two separately installable Android editions sharing one
encrypted transport and protocol core.

## Editions

### Sushh Hub

- Owner/admin user interface
- Broadcast drafting and approval
- Shared participant inbox (next integration stage)
- Channel and location-scope administration
- Urgent-alert publishing
- Participant credentials and revocation

Package: `in.sushh.hub`

### Sushh Participant

- Verified broadcast feed
- Private messages to Hub
- Location-channel subscriptions
- Urgent-alert acknowledgement
- Opaque encrypted message relay
- No channel publishing authority

Package: `in.sushh.participant`

## Security invariants implemented in the foundation

- A broadcast is accepted only with three distinct valid Owner signatures.
- Signatures use ECDSA and registered Owner public keys.
- Revoked, unknown and duplicate Owners do not count.
- The signed payload binds the broadcast ID, channel, author device, exact body,
  creation and expiry times, alert severity, location scope and attachment
  digest.
- Editing any signed field invalidates all previous approvals.
- Expired broadcasts are rejected.
- Location scopes support global, country, state, district and local-zone
  targeting without continuous GPS tracking.
- APK edition flags control available UI surfaces, but authority is never
  granted by an APK flag. It comes only from cryptographic credentials.

## Delivery

Both branches run `.github/workflows/build-apk.yml`. Each successful workflow
uploads an installable debug APK under the run's **Artifacts** section for 30
days.

## Integration stages still required

The security/domain foundation must next be connected to persistent storage,
Briar synchronization records, Hub approval screens, the shared Hub inbox,
Participant broadcast feed, Android urgent notifications, acknowledgements and
device credential provisioning. Those are protocol and UI features, not enabled
merely by the edition flag.
