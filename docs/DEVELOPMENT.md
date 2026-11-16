# Development

For anyone changing the code. Read [ARCHITECTURE.md](ARCHITECTURE.md) first for the layers, and [SETUP.md](SETUP.md) to get it running.

## Contents

- [Everyday commands](#everyday-commands)
- [Where things live](#where-things-live)
- [House rules](#house-rules)
- [Adding an endpoint](#adding-an-endpoint)
- [Adding a social provider](#adding-a-social-provider)
- [Adding an email](#adding-an-email)
- [Adding a setting](#adding-a-setting)
- [Tests](#tests)
- [Trying changes by hand](#trying-changes-by-hand)
- [The README pictures](#the-readme-pictures)
- [Before you open a pull request](#before-you-open-a-pull-request)

## Everyday commands

```bash
sh ./mvnw spring-boot:run              # run with .env from this folder
sh ./mvnw -q -DskipTests package       # build the jar into target/
sh ./mvnw test                         # run the tests, see Tests below
python3 tool/readme_assets.py          # redraw the pictures in docs/assets
```

Java 21 is required. The Maven wrapper downloads Maven itself.

## Where things live

```text
src/main/java/com/premisave/auth/
  controller/          HTTP only: validate, call one service, return a DTO
  service/             the rules
    security/          sessions, devices, throttling, risk, alerts, audit, admins
    application/       Home Owner workflow
    oauth/             one client per social provider
    storage/           where application documents are kept
    places/            the Google Places proxy
  security/            the three filters, JwtService, user details
  config/              route rules, beans, startup runners, typed settings
  entity/, repository/ MongoDB documents and Spring Data repositories
  dto/, enums/         what goes in and out, and the fixed vocabularies
  exception/           ApiException and the one handler
  util/                the rate limiter interceptor
src/main/resources/
  application.yml      every setting, read from the environment
  templates/email/     the email HTML files
  META-INF/additional-spring-configuration-metadata.json   IDE help for the settings
docs/                  these guides, and docs/assets for the pictures
tool/                  the script that draws the pictures, and its fonts
test-client/, oauth-test/   browser pages for trying the API by hand
postman/               a collection and an environment
```

## House rules

These keep the code easy to follow and safe. They are what a reviewer looks for.

- **Layers stay in their lane.** Controllers do no business logic and never touch a repository. Services own the rules and the I/O.
- **Throw `ApiException`** for anything a person can cause, with a message that is safe to show. Use the helpers: `badRequest`, `notFound`, `conflict`, `forbidden`, `unavailable`, `unprocessable`. Avoid a bare `RuntimeException`, which answers 400 by accident.
- **Never leak whether an account exists.** Sign in, resend activation and forgot password answer the same either way. Keep it that way.
- **Never log or return a secret, a token, a password, a file path or a full email.** Log a user id. The audit trail masks the subject.
- **Everything that matters is audited.** A security relevant action writes a `SecurityEvent` through `SecurityAuditService`.
- **Helpers fail open, the person does not.** Redis, mail, geolocation and file lookups failing must not turn a good request into a bad one. Catch, log, carry on.
- **Roles come from the server.** Never read a role from a request body.
- **A new setting always has a default** (except a secret), so a missing variable never stops startup by surprise.
- **Plain language in messages.** They are shown to people, so write for them, with no stack traces and no jargon.
- **Comments say why.** The code already says what.

## Adding an endpoint

1. Put the logic in a **service** method. Throw `ApiException` for failures.
2. Add the **DTOs**, with Jakarta Validation (`@NotBlank`, `@Size`, `@Email`) on the request.
3. Add the method to the **controller**. Keep it to: validate, call, return.
4. Decide **who may call it** in `SecurityConfig`. Paths are matched in order, and the last rule is `anyRequest().authenticated()`. Add `@PreAuthorize` for role checks that the path rules do not cover.
5. If it is sensitive (sign in, reset, anything that sends mail), add the path to the rate limited list in `WebConfig`.
6. Write an **audit event** if it changes security state.
7. Add it to [API.md](API.md), and the Postman collection.
8. Run `python3 tool/readme_assets.py` so the endpoint counts in the pictures stay true.

## Adding a social provider

1. Add a class in `service/oauth` that implements `OAuthProviderClient`. Return a verified email, a provider id, a name and a picture, or throw a clear error.
2. Read its credentials from `oauth.<provider>.*` in `application.yml`, with blank defaults so it is optional.
3. Register the endpoint in `AuthController`, calling `OAuthService.handleOAuth("<provider>", ...)`.
4. Add a provider id field to `User` and handle it in `OAuthService`'s `getProviderId` and `setProviderId`.
5. Document it in [SOCIAL_SIGN_IN.md](SOCIAL_SIGN_IN.md).

## Adding an email

1. Add an HTML file to `src/main/resources/templates/email`. Use `layout.html` for the frame.
2. Fill it with the existing renderer. It escapes every value, so pass raw text and never pre-build HTML from user input.
3. Send it through the mail service, which sends in the background and records `NOTIFICATION_FAILED` on error.

## Adding a setting

1. Add `${NAME:default}` to `application.yml` with a comment saying what it does.
2. Bind it with `@Value` or add a field to the matching `@ConfigurationProperties` class (`SecurityProperties`, `StorageProperties`, `AdminsProperties`).
3. Add it to `.env.example` with a comment, and to [CONFIGURATION.md](CONFIGURATION.md).
4. Add it to `additional-spring-configuration-metadata.json` if it is a custom key, so the IDE can describe it.

## Tests

There is one test today: `PremisaveAuthServiceApplicationTests.contextLoads`. It starts the whole application, which proves the wiring and the settings bind, and nothing else.

To run it, the application needs what it needs to start:

- The six [required settings](CONFIGURATION.md#required) **exported in your shell**. The `.env` file is read by `main()`, which tests do not call, so a `.env` alone is not enough.
- MongoDB reachable, because the security indexes are created at startup.

```bash
export JWT_SECRET=$(openssl rand -base64 32) API_KEY=test-key GMAIL_PASSWORD=x \
       CLOUDINARY_CLOUD_NAME=x CLOUDINARY_API_KEY=x CLOUDINARY_API_SECRET=x
sh ./mvnw test
```

That is a thin safety net for a service that guards sign in. The most valuable tests to add first, in this order:

1. **Route rules**, with `spring-security-test` and `MockMvc`: every path in the table in the [README](../README.md#who-can-reach-what) answers 401 or 403 for the wrong caller.
2. **Token rotation**: a refresh token works once, and a second use revokes the session.
3. **Sign in throttling**: five failures lock, and the lock doubles.
4. **Application transitions**: every allowed and refused move in [HOME_OWNER_APPLICATIONS.md](HOME_OWNER_APPLICATIONS.md#the-life-of-an-application).
5. **`StorageLayout`**: it was written free of Spring so each rule can be tested alone, including the paths that try to escape the folder.
6. **Account linking** in `OAuthService`: the unverified account case above all.

## Trying changes by hand

- [`test-client/auth-console.html`](../test-client/README.md) is a single page for sign up, sign in, refresh, sessions and devices.
- [`oauth-test/oauth-test.html`](../oauth-test/README.md) runs the social flows.
- [`postman/`](../postman) has every endpoint, with an environment for local work.
- To read the emails, use a throwaway Gmail account with an App Password. The connection requires STARTTLS and a login, so a plain local mail catcher will not work without changing `application.yml`.
- Raise the log level of one package, not all of them: `LOGGING_LEVEL_COM_PREMISAVE_AUTH_SERVICE_SECURITY=TRACE`.

## The README pictures

The drawings in `docs/assets` are made by `tool/readme_assets.py`, using only the Python standard library. They are plain SVG with CSS animation, so GitHub shows them as they are. The type is embedded from `tool/fonts`, so they look the same on every computer. The numbers in them, the endpoint counts, the access table, the code statistics and the risk weights, are read from the source, so run the script again after a big change.

```bash
python3 tool/readme_assets.py
git status docs/assets
```

If the repository has the workflow `.github/workflows/readme-assets.yml`, GitHub runs the script for you when the source changes and commits the new pictures. Do not edit an SVG by hand. The next run replaces it.

## Before you open a pull request

- [ ] It builds: `sh ./mvnw -q -DskipTests package`.
- [ ] The application starts and `GET /health` answers.
- [ ] The paths you touched answer what [API.md](API.md) says, or the doc is updated.
- [ ] A new setting is in `application.yml`, `.env.example` and [CONFIGURATION.md](CONFIGURATION.md).
- [ ] No secret, token, password or real address is in the code, a test, a log line or a document.
- [ ] A security relevant change writes an audit event and has a test.
- [ ] `python3 tool/readme_assets.py` has been run if endpoints or rules changed.
