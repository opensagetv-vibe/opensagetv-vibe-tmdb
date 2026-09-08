# Security

Report security issues privately through the GitHub repository's security
advisory interface. Do not include API keys, access tokens, private media,
SageTV appdata, or unredacted request URLs in an issue or diagnostic archive.

The project treats credential redaction, bounded HTTP responses, cache-file
permissions, dependency checksums, and fail-safe plugin startup as security
boundaries. A TMDB credential accidentally exposed in any log or artifact
should be revoked and replaced immediately.
