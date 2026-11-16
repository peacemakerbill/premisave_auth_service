# Troubleshooting

Find the symptom, read the likely cause, apply the fix. The service logs a plain reason for most failures, so look at the log first. For setup steps see [SETUP.md](SETUP.md).

## Contents

- [The service will not start](#the-service-will-not-start)
- [Email does not arrive](#email-does-not-arrive)
- [Sign in fails](#sign-in-fails)
- [Tokens and sessions](#tokens-and-sessions)
- [Social sign in](#social-sign-in)
- [Browsers and CORS](#browsers-and-cors)
- [Home Owner uploads](#home-owner-uploads)
- [Other services cannot connect](#other-services-cannot-connect)
- [Places and maps](#places-and-maps)
- [Still stuck](#still-stuck)

## The service will not start

| What you see | Cause and fix |
| --- | --- |
| `Could not resolve placeholder 'JWT_SECRET'` (or `API_KEY`, `GMAIL_PASSWORD`, `CLOUDINARY_*`) | A [required setting](CONFIGURATION.md#required) is missing. The placeholder in the message is the one. Add it to `.env`, **in the folder you start from**, or export it. |
| It starts, but ignores your `.env` | You started it from another folder. `.env` is read from the current working directory. `cd` into the project, or use real environment variables. |
| Values look right but do not work | Quotes or trailing spaces in `.env`. Write `KEY=value`, nothing else on the line. |
| `Address already in use` / `Port 8080 was already in use` | Something else holds 8080. Stop it, or `ss -ltnp | grep 8080` to find it. The port is fixed in `application.yml`. |
| `MongoTimeoutException` or `Timed out while waiting for a server` | MongoDB is not running or `MONGODB_URI` is wrong. `docker ps` and try `mongosh "$MONGODB_URI"`. In Docker, `localhost` is the container. |
| It starts, but the log shows Redis connection errors | Redis is not running, or `REDIS_HOST` and `REDIS_PORT` are wrong. Redis is optional for starting. Without it, sign in throttling stops limiting and logged out tokens are not checked, so fix it before you trust the service. |
| `WeakKeyException` or `The signing key's size is ... bits` | `JWT_SECRET` is too short. Use `openssl rand -base64 32`. |
| `Unsupported class file major version` | The JDK is older than 21. `java -version`. |
| `Storage configuration is invalid` | Only a warning unless `STORAGE_FAIL_ON_UNAVAILABLE=true`. See [STORAGE.md](STORAGE.md#when-storage-is-not-working). |
| `contextLoads` fails in `mvnw test` | The tests do not read `.env`. Export the six required settings in your shell and start MongoDB. See [DEVELOPMENT.md](DEVELOPMENT.md#tests). |

## Email does not arrive

Mail is sent in the background, so a request succeeds even when the mail fails. Look for `NOTIFICATION_FAILED` in the log or in `GET /admin/security/events`. The event names the template and the error class, never the address.

| Symptom | Cause and fix |
| --- | --- |
| `535 Username and Password not accepted` | `GMAIL_PASSWORD` is not an App Password, or `GMAIL_USERNAME` is not the account that made it. Turn on 2-step verification and create an App Password. |
| `AuthenticationFailedException` straight after changing the password | Restart. Settings are read once, at startup. |
| No error, but nothing in the inbox | Check spam. Gmail limits how many messages an account can send each day, and a new sender is often filtered. |
| The activation link opens a page that does not exist | `FRONTEND_URL` points at the wrong place. Links are built from it. Test the link by hand with `GET /auth/verify/<token>`. |
| The "this was not me" link opens nothing | The app needs a `/security-report` page. The Flutter app has one. A different front end must add it. |
| People are told to write to the wrong address | `SUPPORT_EMAIL` is still the default. Set it. |
| The mail says the time is wrong | `SECURITY_DISPLAY_ZONE` is `UTC`. Set `Africa/Nairobi` or your zone. |

To get past an unactivated account while developing, verify it directly: `PUT /admin/users/verify/{id}` as an administrator.

## Sign in fails

| Message or status | Meaning |
| --- | --- |
| `401` "Invalid email or password." | The password is wrong, **or there is no such account**. The answer is the same on purpose. |
| `403` "Account not verified. Please check your email." | The password is right, and the email is not yet verified. Open the activation link, or call `POST /auth/resend-activation?email=`. |
| `403` "Account is deactivated. Please contact support." | An administrator deactivated it. Reactivate with `PUT /admin/users/activate/{id}`. |
| `429` "Too many sign-in attempts. Please try again in N minutes." | The account or the address is throttled after repeated failures. It clears by itself. The lock starts at 15 minutes and doubles, up to 240. During development you can clear it by deleting the `sec:*` keys from Redis. |
| `429` with no body | The request rate limiter, shared by sign up, sign in and a few more paths. Wait a minute. See [Throttling](CONFIGURATION.md#throttling). |
| `400` "Email already exists" | Sign up with a taken address. With `SECURITY_HIDE_EXISTING_ACCOUNTS=true` the answer is the neutral `202` instead. |
| `400` with a map under `errors` | A field failed validation. The map says which one. |
| Every sign in looks like a "new device" | The device cookie is not coming back. On plain `http` set `SECURITY_DEVICE_COOKIE_SECURE=false`. A front end on another site needs `SAME_SITE=None` with `Secure` and HTTPS. A native app must send `X-Device-Token`. |

## Tokens and sessions

| Symptom | Cause and fix |
| --- | --- |
| `401` "Your session has ended. Please sign in again." | The session behind the token was ended, by sign out, by the owner, by a password reset or change, or by reuse of a refresh token. Sign in again. |
| `401` after a refresh that worked once | A refresh token works **once**. The client must store the new refresh token every time, and must not refresh twice in parallel. Using an old one ends the whole session and emails the owner. |
| `401` "Invalid or expired refresh token" | The token is old, malformed, or an access token sent as a refresh token. |
| A token is rejected by another service | The other service does not derive the key the same way. See [INTEGRATION.md](INTEGRATION.md#checking-a-users-token): Base64 decode, then exactly 32 bytes. Check `JWT_SECRET` is identical. |
| Everyone was signed out after a deploy | `JWT_SECRET` changed. Keep it the same across deploys. |
| A signed out token still works in another service | That service only checks the signature. Shorten tokens with `SECURITY_ACCESS_TOKEN_MINUTES` or call `/internal/sessions/{sid}`. |
| The new role does not appear after a promotion | The role is inside the token. It applies at the next sign in or refresh. |
| `403` for an administrator that was just created | An administrator must be verified **and** active. An invited administrator must accept the invitation first. |

## Social sign in

| Message | Cause and fix |
| --- | --- |
| "Google sign-in is not configured on this server" | `GOOGLE_CLIENT_ID` is empty. Facebook and GitHub say the same for their own variables. |
| "This redirect address is not allowed for sign-in" | `redirectUri` is not in `OAUTH_ALLOWED_REDIRECT_URIS`, exactly. Add it, and register the same address with the provider. |
| A code is refused as already used or invalid | Codes work once and last minutes. Do not retry the same code. Start the flow again. |
| "Facebook token was not issued for this app" | The token came from another Facebook app. Check `FACEBOOK_APP_ID`. |
| "GitHub did not return a verified email" | The GitHub account has no verified email, or the app did not ask for the `user:email` scope. |
| "This email is already linked to a different Google account" | The email's account is tied to another Google identity. The person must use that one. |
| The Google audience does not match | The token was issued to a client ID that is not in `GOOGLE_CLIENT_ID`. Add the Android, iOS or Web ID to the comma separated list. |
| Works on `localhost`, fails on the real site | The production origin and redirect address are not registered with the provider, or not in the allow list. |

More in [SOCIAL_SIGN_IN.md](SOCIAL_SIGN_IN.md).

## Browsers and CORS

| Symptom | Cause and fix |
| --- | --- |
| "blocked by CORS policy" for a header | Only `Authorization`, `Content-Type`, `Accept`, `X-Requested-With`, `X-CSRF-Token`, `X-Device-Token` and `X-Client-Platform` are allowed from a browser. Other headers need adding in `SecurityConfig`. |
| `X-API-Key` from a browser fails | By design. That header is for services. Never put the key in a browser. |
| Opening a test page from `file://` fails | The social flows need a real origin. Serve the folder, for example `python3 -m http.server 3000`. |
| Cookie not stored | See "Every sign in looks like a new device" above. |

## Home Owner uploads

| Status and message | Cause and fix |
| --- | --- |
| `503` on upload | Storage is not usable. The log says why, see [STORAGE.md](STORAGE.md#when-storage-is-not-working). |
| `400` "Only PDF, JPG, PNG or WEBP files are accepted." | The file's content is not one of those, whatever its name says. Re-export it. |
| `400` "That file is larger than 8 MB" | Over `HOME_OWNER_MAX_FILE_SIZE_MB`. Above 10 MB, the server's own limit stops it first. |
| `409` "This exact file is already attached" | The same file was uploaded under another type. |
| `409` "...cannot be edited while it is..." | Only `DRAFT` and `INFO_REQUESTED` applications can change. |
| `422` on submit | The form or the documents are incomplete. The `details` list says what to add. |
| `409` "You already have an application in progress." | One open application at a time. |
| `403` on a review action | The caller is not staff, or the action needs an administrator, like promote and assign to someone else. |
| A file shows in the app but `404` on download | The file was removed from the disk, or the share is not mounted. Restore it, or have the applicant upload it again. |

## Other services cannot connect

| Symptom | Cause and fix |
| --- | --- |
| `401` from `/internal/**` | `X-API-Key` is missing or does not equal `API_KEY`. The wallet calls it `INTERNAL_API_KEY`. |
| `404` from `/internal/users/{email}/details` | There is no account with that address. That is the intended answer. |
| `valid: false` from `validate-email` | Read `reason`: `NOT_FOUND`, `INACTIVE`, `UNVERIFIED`, `ARCHIVED` or `INVALID_FORMAT`. |
| Connection refused between containers | `localhost` is the container. Use the service name or the host address. |

## Places and maps

| Symptom | Cause and fix |
| --- | --- |
| `GET /places/status` says `enabled: false` | `GOOGLE_MAPS_API_KEY` is empty. The apps fall back to OpenStreetMap. |
| Google returns an error | The key is not enabled for **Places API (New)** and **Geocoding API**, billing is off, or the key is restricted to an address that is not this server's. |
| `429` "Too many address searches" | One account passed `PLACES_REQUESTS_PER_USER_PER_MINUTE`. |

## Still stuck

1. Raise the log level for one package: `LOGGING_LEVEL_COM_PREMISAVE_AUTH=DEBUG` is already the default, so read the lines just before the failure.
2. Reproduce it with `curl` or the [auth console](../test-client/README.md), to separate a server problem from a client problem.
3. Look for events at `GET /admin/security/events`, filtered by `userId` or `type`.
4. [Open an issue](https://github.com/peacemakerbill/premisave_auth_service/issues) with the status, the message, what you did, and the log lines around it. **Remove every secret, token and email address first.**
