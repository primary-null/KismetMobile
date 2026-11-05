# Security Policy

## Supported Versions

Security fixes target the latest version on the `main` branch.

## Reporting a Vulnerability

Please report vulnerabilities privately through GitHub: open the repository's **Security** tab and choose **Report a vulnerability**. Do not open a public issue for security problems.

Include the affected version, a minimal reproduction using synthetic data, and the impact. Never include credentials, private keys, real device identifiers, or captures of third-party traffic.

## Known limitations

- Direct Kismet API connections use cleartext HTTP Basic authentication. Protect the connection with a trusted network or a verified tunnel.
- Saved credentials are stored in private app preferences without encryption. A compromised device can expose them.
- SSH pins the first key seen per host and port and rejects later key changes. The first connection still needs a trusted network and independent server verification.
- PCAP and WiGLE exports may contain wireless identifiers and locations. Review files before sharing them.
