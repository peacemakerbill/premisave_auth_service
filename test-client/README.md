# Auth console

A single HTML page for trying the auth-service by hand. There is nothing to install and no build step. It talks to a running service with `fetch`, and keeps what you type, such as the address and tokens, in your browser only.

```bash
# start the service first, see ../docs/SETUP.md
cd test-client
python3 -m http.server 5500
# open http://localhost:5500/auth-console.html
```

Opening the file directly also works for everything except social sign in, which needs a real origin (the page tells you when it does).

The service allows every origin in development, so any local address works. The page's address box defaults to `http://localhost:8080`. Change it to point at another server.

## What is in it

| Group | Page | What you can do |
| --- | --- | --- |
| Account | Overview | The active account, the time left on its access token, refresh now, copy the token, sign out. |
| | Sign up and recovery | Sign up, activate, resend the activation mail, sign in, forgot and reset password, change password. |
| | Social sign in | Google, Facebook and GitHub, by token or by authorization code. See [SOCIAL_SIGN_IN.md](../docs/SOCIAL_SIGN_IN.md). |
| | Sessions and devices | Active sessions, devices, trust, forget a device, sign in history, security events. |
| | Profile | Read and update the profile, upload a picture, search people. |
| Community | Likes, follows, reviews | The social endpoints. |
| | Location, Address search | Save a location, and try the Places proxy. |
| Home owners | My application | The applicant's side of [the workflow](../docs/HOME_OWNER_APPLICATIONS.md). |
| | Staff review | The review queue and actions. Needs a staff account. |
| | Administration | Promote, and the user management endpoints. Needs an administrator. |
| Services | Internal API | The `/internal/**` calls, with an `X-API-Key` you type in. |
| | Health | `GET /health`. |
| Tools | All endpoints, Request builder, Error shapes | Every endpoint in a list, a free form request, and the error formats. |

A request log at the bottom shows each call, its status and its time.

## Notes

- **Tokens stay in this browser.** The page stores the tokens so you can switch between accounts. Use it with test accounts, and do not leave real credentials in it on a shared computer.
- **Devices.** A browser gets the remembered device cookie. A native app sends `X-Device-Token` and `X-Client-Platform: mobile`. The page can act either way (the "device" setting), so you can see both.
- **Auto refresh.** The page can refresh the access token before it ends, the way a real client should. Turn it off to see what a `401` looks like.
- On plain `http` set `SECURITY_DEVICE_COOKIE_SECURE=false` in the service's `.env`, or every sign in looks like a new device.

The same endpoints are in [`../postman`](../postman), and described in [../docs/API.md](../docs/API.md).
