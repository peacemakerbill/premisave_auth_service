# Sign in with Google, Facebook and GitHub

The service verifies a credential from the provider on the server, creates or finds the account, and then does exactly what a password sign in does: a session, two tokens, device recognition, scoring and alerts. Every provider is optional and independent. With nothing configured the service starts normally and each provider answers with "not configured on this server".

## Contents

- [The endpoints](#the-endpoints)
- [Two ways to sign in](#two-ways-to-sign-in)
- [How an account is found or created](#how-an-account-is-found-or-created)
- [Google](#google)
- [Facebook](#facebook)
- [GitHub](#github)
- [Redirect addresses](#redirect-addresses)
- [Testing](#testing)
- [Security notes](#security-notes)

## The endpoints

All three take the same body and return the same answer as `POST /auth/signin`:

| Endpoint | `token` holds |
| --- | --- |
| `POST /auth/google` | A Google ID token, or a Google authorization code together with `redirectUri`. |
| `POST /auth/facebook` | A Facebook user access token, or a Facebook authorization code together with `redirectUri`. |
| `POST /auth/github` | A GitHub authorization code, or a GitHub access token. |

```json
{ "token": "<credential>" }
{ "token": "<authorization code>", "redirectUri": "https://app.example.com/auth.html" }
```

## Two ways to sign in

**With the provider's own sign in (web, Android, iOS).** The client runs Google Identity Services, `google_sign_in`, or the Facebook Login SDK, gets a token, and posts it. The service checks it with the provider and never needs a client secret for Google.

**With an authorization code (desktop, and GitHub everywhere).** An app that cannot run the provider's sign in, such as Linux and Windows desktop, sends the person to the provider's page in a browser and gets a code back on a redirect address. It posts the code, and for Google and Facebook the same `redirectUri`. The service exchanges the code with the provider using the **client secret**, which never leaves the server, and then runs the same checks as for a token. GitHub always works this way.

## How an account is found or created

In this order:

1. **A user already linked to this provider account.** The provider id on the user matches. The person signs in, and any missing name or picture is filled in.
2. **A user with the same email.** The provider has confirmed the email, so the account is linked on the spot and the person signs in. If the account was registered with a password but **never verified**, whoever registered it may not own the address. So the old password is discarded, its sessions are ended and the account is marked verified. A `SOCIAL_ACCOUNT_LINKED` event is audited. If the user is already linked to a **different** account at the same provider, the sign in is refused.
3. **Otherwise a new account.** It is a `CLIENT`, verified, active, with a generated username, a random password nobody knows, and the provider's picture copied to Cloudinary. The role is never taken from anywhere but the server.

An archived account or a deactivated account cannot sign in this way either. The provider must give an email address. If an address is on `AUTO_ADMIN_EMAILS`, the account becomes an administrator here, as on every other path.

## Google

1. In [Google Cloud Console](https://console.cloud.google.com), open **APIs and services**, then **Credentials**, and create an **OAuth client ID**. Make one for each kind of app: a Web application, an Android client and an iOS client if you ship them.
2. Set the IDs in `.env`, comma separated:

```text
GOOGLE_CLIENT_ID=web-id.apps.googleusercontent.com,android-id.apps.googleusercontent.com
```

A token issued to any of them is accepted. The service checks the signature, issuer, expiry, the audience and that `email_verified` is true.

For sign in by code (desktop):

```text
GOOGLE_CLIENT_SECRET=...            # the secret of the Web application client
GOOGLE_CODE_CLIENT_ID=...           # the Web client the code was issued to, defaults to the first GOOGLE_CLIENT_ID
OAUTH_ALLOWED_REDIRECT_URIS=...     # see Redirect addresses
```

The Web application client must list the same redirect address in the Cloud Console.

## Facebook

1. In [Meta for Developers](https://developers.facebook.com), create an app, add **Facebook Login** and request the `public_profile` and `email` permissions.
2. Set both values:

```text
FACEBOOK_APP_ID=...
FACEBOOK_APP_SECRET=...
```

The service first asks Facebook's `/debug_token` whether the token is valid, is a user token and **was issued to this app**. Without that, a token from any other Facebook app could be replayed here. It then reads the profile with an `appsecret_proof`. If Facebook returns no email, the sign in fails with a message that says so, because an account needs one.

## GitHub

1. In [GitHub settings](https://github.com/settings/developers), create an **OAuth App**. Set its callback URL to a page you control.
2. Set both values:

```text
GITHUB_CLIENT_ID=...
GITHUB_CLIENT_SECRET=...
```

The client sends the person to `https://github.com/login/oauth/authorize?client_id=...&redirect_uri=...&scope=user:email&state=...`, and posts the `code` it gets back as `token`. A code is exactly 20 hexadecimal characters. A GitHub access token is also accepted, but only after GitHub's check-token API confirms it was issued to this app. The email used is the primary verified one, else any verified one, which needs the `user:email` scope. GitHub rejects a code exchange whose redirect does not exactly match the one registered on the app.

## Redirect addresses

```text
OAUTH_ALLOWED_REDIRECT_URIS=https://app.example.com/auth.html,http://localhost:3000/auth.html
```

A client that posts a code with a `redirectUri` must use an address on this comma separated list, exactly. Anything else is refused, which stops anyone using this service to exchange a code for a redirect they control. The same address must be registered with the provider. The default is `http://localhost:3000/auth.html`, so add your real addresses in production and remove the localhost one.

## Testing

[`oauth-test/oauth-test.html`](../oauth-test/README.md) is a single page that runs each flow against your service and shows the answer. Serve it from a real address, not a file, because the providers need an origin:

```bash
cd oauth-test
python3 -m http.server 3000
# open http://localhost:3000/oauth-test.html
```

## Security notes

- The provider secrets stay on the server. Only Google's client ID and GitHub's client ID are ever sent to a front end.
- A token is accepted only if the provider confirms it was issued to **this** app.
- Google needs `email_verified`. Facebook and GitHub emails come from the provider's own profile and verified email data.
- A social account has no usable password. A person who wants one can use the password reset flow.
- Sensitive security changes ask a social session to be fresh, see [AUTHENTICATION.md](AUTHENTICATION.md#step-up-checks).
- Native mobile sign in is the Flutter app's concern. It needs the provider's own SDK, which posts a token to the same endpoints.
