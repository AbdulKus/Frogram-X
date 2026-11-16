# Frogram X UI work

Read `docs/GLASS_DESIGN.md` before changing the floating interface. Reuse the
shared geometry and semantic theme colours. Preserve capsule folder feedback,
circular icon feedback and the continuous expanded-avatar/header transition.
Verify animation intermediates, not only settled screenshots, on light, black
and Night Blue themes. Update the design reference when user-approved design
requirements change.

Preserve authenticated emulator/app sessions. Do not log out, uninstall or clear
app data for testing. Use replace installs signed with the existing test key.
Do not commit local API credentials, signing material, account data or private
emulator test artifacts.
