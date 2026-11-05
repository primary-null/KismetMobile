# Security Policy

## Supported Versions

Security fixes target the latest source version. Release candidates and debug APKs are evaluation builds.

## Reporting a Vulnerability

If you discover a security vulnerability, please report it privately.

**Do not** create a public GitHub issue.

No public repository or dedicated security inbox is currently available. Contact the maintainer through an established private channel. A future public repository must provide a verified private reporting route before accepting vulnerability reports.

Include the affected version, a minimal reproduction using synthetic data, and the impact. Never include credentials, private keys, real device identifiers, or captures from third parties. No response-time guarantee is offered.

## Current limitations

- Direct Kismet API connections use cleartext HTTP Basic authentication. Protect the connection with a trusted network or verified tunnel.
- Saved credentials are stored in private app preferences without encryption. Device compromise can expose them.
- SSH pins the first key seen per host and port, rejecting subsequent key changes. First use still requires a trusted network and independent server verification.
- PCAP and WiGLE exports may contain wireless identifiers and locations. Review files before sharing them.
