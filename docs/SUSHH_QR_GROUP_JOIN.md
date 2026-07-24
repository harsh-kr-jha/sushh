# QR group joining

Sushh supports secure, nearby group onboarding by QR code while preserving
Briar's recipient-bound private-group invitation protocol.

## User flow

1. A Hub group creator opens the group menu and chooses **Invite member by QR**.
2. A Participant opens **Private Groups** and chooses **Join group by QR**.
3. The two devices scan each other's QR codes.
4. Briar authenticates both devices and establishes or confirms their contact
   relationship over Bluetooth or local Wi-Fi.
5. The Hub signs and sends a private-group invitation specifically for that
   Participant.
6. The Participant receives the normal group invitation and accepts it to
   complete membership.

## Security properties

- Both devices must be physically present and scan each other.
- A photo of one QR code is not a reusable group membership credential.
- The QR exchange authenticates the contact before an invitation is issued.
- Group invitations remain signed by the group creator and bound to the
  intended Participant.
- Existing contacts can repeat the QR flow; Sushh resolves the verified contact
  and sends the invitation without creating a duplicate.
- The flow works without a central Sushh server. Nearby transport uses Briar's
  Bluetooth or local Wi-Fi support.

This feature deliberately does not encode a group's secret material in a
static QR code. Doing so would bypass Briar's recipient-specific signature
checks and make revocation and access control substantially weaker.
