<!-- band -->
<div align="center">

<img src="../docs/assets/head-oauth-test.svg" alt="oauth-test/README.md. Test Google, Facebook and GitHub sign in against your own service." width="100%"/>

[![Google](https://img.shields.io/badge/Google-4285F4?style=flat-square&logo=google&logoColor=white)](../docs/SOCIAL_SIGN_IN.md#google)
[![Facebook](https://img.shields.io/badge/Facebook-1877F2?style=flat-square&logo=facebook&logoColor=white)](../docs/SOCIAL_SIGN_IN.md#facebook)
[![GitHub](https://img.shields.io/badge/GitHub-181717?style=flat-square&logo=github&logoColor=white)](../docs/SOCIAL_SIGN_IN.md#github)
[![HTML single_page](https://img.shields.io/badge/HTML-single_page-E34F26?style=flat-square&logo=html5&logoColor=white)](oauth-test.html)
[![Updated](https://img.shields.io/github/last-commit/peacemakerbill/premisave_auth_service?path=oauth-test/README.md&style=flat-square&color=14493B&label=updated)](https://github.com/peacemakerbill/premisave_auth_service/commits/main/oauth-test/README.md)

</div>

# OAuth test console

A single HTML page for trying Google, Facebook and GitHub sign in against your own auth-service. It can also show you the raw provider token, so you can paste it into Postman and call the endpoint yourself.

```bash
cd oauth-test
python3 -m http.server 3000
# open http://localhost:3000/oauth-test.html
```

The providers need a real **origin**, so serve the page as above. Opening the file directly does not work, and the page says so.

## Using it

1. Start the auth-service with the provider's settings in `.env`. See [SOCIAL_SIGN_IN.md](../docs/SOCIAL_SIGN_IN.md) for how to create them.
2. Open the page. The pill at the top shows whether the backend answers. The backend address defaults to `http://localhost:8080`, under **Configuration**.
3. Under **Providers**, enter the client ID for each provider you want to try, and choose what a login should do: sign in to your backend, or only produce a token.
4. Press the provider's button. A token appears under **Generated provider tokens**, and a successful sign in shows the account under **Signed in**.
5. The **Request log** shows each call and its answer.

## What to register

The page shows the exact values to copy.

| Provider | Register |
| --- | --- |
| Google | The page's origin (for `http://localhost:3000` that is exactly that) under **Authorized JavaScript origins**. |
| GitHub | The page's address, for example `http://localhost:3000/oauth-test.html`, as the **callback URL** of the OAuth App. |
| Facebook | The origin as a valid OAuth redirect or site URL, as the app's settings require. |

If a flow sends an authorization code together with a redirect address, that exact address must also be in the service's `OAUTH_ALLOWED_REDIRECT_URIS`.

Never put a client **secret** in this page. Only client IDs belong in a browser. The secrets stay in the service's `.env`.
