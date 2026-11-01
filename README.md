# chat-commerce-bot

Spring Boot middleware that connects a Magento 2 store to WhatsApp through Infobip.

Customers message the store's WhatsApp number; this service receives the message from Infobip, decides what it
means, talks to Magento over REST, and replies. Magento stays the single source of truth for customers, products
and orders. This service only holds short-lived conversation state in Redis.

**Today:** customers prove they own their phone number by sending a one-time code, and the bot recognises verified
customers. A verified customer gets an interactive **menu** (a WhatsApp list), taps are understood, and each customer
has a short-lived **session** in Redis (state and cart). **Next:** filling the cart (browse, search, SKU entry) and
cash-on-delivery checkout.

## How it fits together

```
Customer ──WhatsApp──> Infobip ──webhook──> chat-commerce-bot ──REST──> Magento 2
                                                │   ▲                  (Vendor_PhoneVerification module)
                                                │   └── Redis: duplicate markers, rate limits, customer sessions
                                                └── reply via Infobip API ──> Customer
```

### Phone verification flow

1. The customer enters their number under **My Account > Phone & WhatsApp** on the store. Magento shows `VERIFY 482913`.
2. The customer sends that exact message from the same number to the store's WhatsApp number.
3. Infobip forwards it to `POST /webhooks/whatsapp/{secret}`. The service answers `200` immediately.
4. A virtual thread drops duplicates, applies the rate limit, recognises the `VERIFY ######` pattern and calls
   Magento's `POST /rest/V1/phone/verify`.
5. Magento marks the number verified; the service replies on WhatsApp; the website page flips to *Verified*.

Any other message triggers `GET /rest/V1/phone/lookup`: verified customers get the main menu, everyone else gets a
"please verify your number" reply with a link.

### Menu and taps

A verified customer who sends anything receives a WhatsApp **list message** with a button (**Open menu**) and six rows:
Order again, Browse categories, Search products, Enter SKUs, View cart, Help.

When the customer taps a row, Infobip sends a webhook whose message type is `INTERACTIVE_LIST_REPLY` carrying the
row's id (for example `menu_help`). The bot looks the id up in the `MenuAction` enum and replies. Today **Help** and
**View cart** are real; the other four say they are not ready yet. A tap from a number that is not verified is treated
like any message from a stranger, and an id the bot does not know simply shows the menu again.

### Sessions

Each verified customer has a session stored in Redis as JSON under `wa:session:<number>` (state and cart). Every
interaction restarts a **2-hour idle timer**; after that the customer starts again from the menu. The cart lives in the
session, not in Magento: nothing is written to Magento until the customer confirms an order. Strangers never get a
session.

## Status

| Part | State |
|---|---|
| Webhook, secret check, dedupe, rate limits | Working |
| `VERIFY` handling and Magento calls over HTTPS | Working (tested end to end, with simulated webhooks and with real WhatsApp messages) |
| Replies through Infobip | Working (a real WhatsApp reply was received) |
| Real inbound messages from Infobip to this app | Working (tested on the Infobip sandbox through a Cloudflare tunnel; see below) |
| Interactive menu (list message) and tap handling | Working (tested with real WhatsApp messages) |
| Per-customer session in Redis (state, cart, 2 h idle expiry) | Working. The cart is always empty until the next steps |
| Help and View cart menu rows | Working |
| Order again, browse, search, SKU entry | Placeholder replies ("not ready yet") |
| Cart filling, checkout | Not built |

## Requirements

- **Java 25** (the build targets `--release 25`; developed on JDK 26) and the included Maven wrapper (`./mvnw`)
- **Spring Boot 4.1.x**
- **Docker**, for the local Redis container
- **Magento 2.4.4+** with the `Vendor_PhoneVerification` module installed, an Integration token, and integration
  tokens allowed as bearer tokens (see the module's README)
- An **Infobip** account with a WhatsApp sender. On a trial, only your verified test numbers can message you.

## Quick start

### 1. Start Redis

```bash
docker compose up -d
docker compose exec redis redis-cli ping    # expect PONG
```

Redis here has no password, so keep it reachable from your own machine only. If you are on shared Wi-Fi, change the
port mapping in `compose.yaml` to `"127.0.0.1:6379:6379"`. Nothing is persisted on purpose: it only holds duplicate
markers and counters that expire within minutes, and sessions that expire after 2 idle hours. A restart of Redis
simply sends customers back to the menu.

### 2. Prepare Magento

Install the module, set the WhatsApp sender number, create the Integration, and enable bearer tokens. All of this is
in the Magento module's README. You end up with an **Access Token** (not the Access Token Secret).

### 3. Set the environment variables

The app refuses to start until the required ones are set, and says which are missing.

| Variable | Required | Description |
|---|---|---|
| `WEBHOOK_SECRET` | yes | Random string that protects the webhook URL. Generate one with `openssl rand -hex 24`. |
| `MAGENTO_BASE_URL` | yes* | Store URL, e.g. `https://magento.test` (default `https://app.magento.test`) |
| `STOREFRONT_URL` | yes* | Public store URL, used in the "please verify" reply (default `https://app.magento.test`) |
| `MAGENTO_TOKEN` | yes | Magento Integration **Access Token** |
| `INFOBIP_BASE_URL` | yes | Your Infobip base URL, e.g. `https://xxxxx.api.infobip.com` (from the Infobip portal) |
| `INFOBIP_API_KEY` | yes | Infobip API key |
| `INFOBIP_SENDER` | yes | Your WhatsApp sender number, digits only (no `+`) |
| `REDIS_HOST` / `REDIS_PORT` | no | Default `localhost` / `6379` |

\* Set these explicitly: the defaults assume a different domain than most projects use.

In IntelliJ: **Run > Edit Configurations > Modify options > Environment variables**, one line separated by semicolons:

```
WEBHOOK_SECRET=...;MAGENTO_BASE_URL=https://magento.test;STOREFRONT_URL=https://magento.test;MAGENTO_TOKEN=...;INFOBIP_BASE_URL=https://xxxxx.api.infobip.com;INFOBIP_API_KEY=...;INFOBIP_SENDER=...
```

Leave **Store as project file** unticked so the secrets never end up in git. Never commit real values anywhere
in this repository.

### 4. Make Java trust Warden's certificate (Windows + Warden)

Warden signs local sites with its own root certificate. Windows and Java do not trust it until you tell them to;
without this the app logs `unable to find valid certification path` when it calls Magento.

1. Copy the certificate out of WSL (in a WSL terminal):
   ```bash
   cp ~/.warden/ssl/rootca/certs/ca.cert.pem /mnt/c/Users/<you>/warden-rootca.crt
   ```
2. On Windows, double-click it, **Install Certificate > Current User > Place all certificates in the following
   store > Trusted Root Certification Authorities**, and accept the warning. `https://magento.test` should now show
   a padlock in Chrome or Edge.
3. Tell the JVM to use the Windows trust store: add these **VM options** to the IntelliJ run configuration
   (**Modify options > Add VM options**) and restart the app:
   ```
   -Djavax.net.ssl.trustStoreType=Windows-ROOT -Djavax.net.ssl.trustStore=NONE
   ```
   The Windows list also contains the public authorities Infobip uses, so replies keep working.

This is for local development only. Do not install the certificate or use these options in production, where the
store has a normal public certificate.

### 5. Run it

Run `ChatCommerceBotApplication` from IntelliJ, or from a terminal with the variables exported:

```bash
./mvnw spring-boot:run -Dspring-boot.run.jvmArguments="-Djavax.net.ssl.trustStoreType=Windows-ROOT -Djavax.net.ssl.trustStore=NONE"
```

Check `http://localhost:8080/actuator/health`. It should report `UP`, which includes the Redis connection.

## Testing

```bash
./mvnw clean test
```

38 tests run:

- 26 unit tests for the conversation logic (verification, dedupe, rate limits, menu, taps, cart, sessions) using
  hand-written fakes: no Spring, Redis or network;
- 5 tests that parse Infobip-shaped JSON (text and list-tap payloads) into our own message type;
- 1 test that the list message is serialised in the shape Infobip expects;
- 5 tests for the session JSON (round trip, unknown fields, damaged data);
- a context-load test that starts the application with dummy property values, so it does not need your real secrets.

The Redis calls themselves are checked by hand (see "Inspect a session" below).

### Simulate an Infobip webhook

Request a code on the store's My Account page first (it lasts 10 minutes). Then, **from a Windows terminal** (Git Bash
or IntelliJ's Terminal, not WSL; the app runs on Windows, so `localhost:8080` does not exist inside WSL):

```bash
curl -i -X POST "http://localhost:8080/webhooks/whatsapp/<YOUR_WEBHOOK_SECRET>" \
  -H "Content-Type: application/json" \
  -d '{"results":[{"from":"<YOUR_NUMBER>","messageId":"test-verify-1","message":{"type":"TEXT","text":"VERIFY <CODE>"}}]}'
```

- `<YOUR_NUMBER>`: digits only with country code, e.g. `27821234567`.
- Use a **new `messageId` every time**; repeating one is ignored on purpose (that is the duplicate protection).
- Expect `200` at once, `Number *******1234 verified` in the console, a WhatsApp reply on your phone, and the website
  page flipping to *Verified*.
- A wrong secret returns `401`.

### Inspect a session

After a verified customer has used the menu (add the Redis password option if your compose file sets one):

```bash
docker compose exec redis redis-cli --scan --pattern 'wa:session:*'
docker compose exec redis redis-cli TTL wa:session:<digits>     # about 7200 right after an interaction
docker compose exec redis redis-cli GET wa:session:<digits>     # {"state":"IDLE","cart":[]}
```

Session keys contain a phone number and the value will contain what the customer ordered, so do not paste them into
tickets or chats.

## Receiving real WhatsApp messages

Infobip has to reach your app over the internet, so on a laptop you need a tunnel (ngrok, Cloudflare Tunnel, etc.)
forwarding to port 8080. In the Infobip portal, set the inbound message forwarding for your sender to:

```
https://<tunnel-host>/webhooks/whatsapp/<WEBHOOK_SECRET>
```

Menu names change, so follow Infobip's current WhatsApp "receive messages" documentation. The payload this service
reads is the `results[]` array with `from`, `messageId` and `message`, of type `TEXT` (`message.text`) or
`INTERACTIVE_LIST_REPLY` (`message.id` is the tapped row's id, `message.title` its title). Other types are ignored. If
Infobip's real payload differs, `InfobipInbound` is the only class to adjust. In testing, the real payloads matched.
The console line `Webhook received: N result(s), M message(s)` counts what arrived and what was understood.

A quick tunnel is the easiest way to test:

```bash
cloudflared tunnel --url http://localhost:8080
```

It prints a public `https://...trycloudflare.com` address. It changes every time the tunnel starts, so update the
Infobip subscription each session, and close the tunnel when you are done.

**Subscription settings that worked:** WhatsApp *inbound messages*, forwarding action **Apply Subscription**, JSON,
filters left empty, no authentication (the secret in the URL does that job). Do not filter by keyword: the bot has to
see every message.

**Sandbox quirk, link your phone once:** on the shared Infobip sandbox number, send your account keyword (your Infobip
username, shown locked in the sender's inbound settings) from your phone first. Infobip replies that your phone is
connected. Until then nothing reaches the app. Afterwards, normal messages arrive without the keyword. Infobip's docs do
not describe this; a registered production sender is expected not to need it.

## What the bot says

| Customer sends | Bot does |
|---|---|
| `VERIFY 482913` (case, a colon, spaces and a trailing full stop are tolerated; exactly 6 digits) | Calls Magento. Success: "Thank you ..., your number is verified." Anything else: one generic "invalid or expired" message. |
| Anything else, number not verified or unknown | One "please verify your number" reply per minute per number, with a link to the store |
| Anything else, number verified | The main menu (list message), with the customer's first name |
| Tap on **Help** | A short explanation of how ordering works |
| Tap on **View cart** | The cart, or "Your cart is empty" |
| Tap on any other row | "<Option> is not ready yet, we are still building it. Send any message to see the menu again." |
| Tap on an unknown row id | The menu again |
| Tap from a number that is not verified | The "please verify your number" reply (once a minute) |
| Anything while Magento is unreachable | "We are having trouble right now, please try again in a few minutes" |
| More than 20 messages a minute from one number | Ignored |

## Project layout

```
src/main/java/com/alex/chatcommercebot
├── ChatCommerceBotApplication.java
├── config/          AppProperties (validated settings), AppConfig, REST client setup
├── conversation/    Business logic. No Spring imports, so it is unit tested on its own:
│                    ConversationService, Messages, Redact, Menu / MenuAction / ListMessage,
│                    Session / CartLine, and the interfaces it depends on
│                    (MagentoGateway, MessageSender, KeyValueStore, SessionStore)
├── infobip/         WebhookController (secret check, ack, hand-off), inbound payload, message sender
├── magento/         MagentoRestGateway (verify and lookup calls)
└── store/           RedisKeyValueStore (atomic dedupe marker and counter), RedisSessionStore, SessionCodec
```

## Design notes

- **Acknowledge first.** The webhook returns `200` immediately and the work runs on a virtual thread, so Infobip never
  waits on Magento and never retries because of a slow response.
- **Idempotent.** The provider message id is recorded with an atomic `SET NX` for 48 hours, so a redelivered webhook
  cannot trigger a second verification or a second reply.
- **Secrets.** The webhook secret is compared in constant time. Settings are validated at startup, so a missing
  secret stops the app instead of silently running open.
- **Privacy.** Logs show only the last four digits of a number and never the verification code. Infobip error bodies
  are not logged because they can echo customer data.
- **Failure behaviour.** Any Magento failure (network, TLS, HTTP error) becomes one exception type and one polite
  message to the customer; replies to Infobip failing are logged and never crash the worker.
- **Unexpected errors are logged, not lost.** The work is handed to an executor with `submit`, which keeps exceptions
  inside a `Future` nobody reads. `WebhookController` therefore catches them and logs them with the message id.
- **Menu limits are checked in code.** `ListMessage` rejects values WhatsApp would refuse (button 20 characters, row title
  24, description 72, at most 10 rows), so a mistake fails in a test instead of as a rejected message.
- **Sessions are disposable.** Unreadable or expired session data is replaced by an empty session. Losing a session
  costs the customer only a trip back to the menu.

## Troubleshooting

| Symptom | Cause and fix |
|---|---|
| App fails at startup with `Binding to target ... AppProperties failed` | A required environment variable is empty. The message lists which. |
| `curl: (7) Failed to connect to localhost:8080` | You ran curl inside WSL. Run it from a Windows terminal. |
| `Could not reach Magento: ... unable to find valid certification path` | Java does not trust Warden's certificate. Follow step 4. |
| `Could not reach Magento: UnknownHostException` | Windows cannot resolve the store domain. Add it to the Windows `hosts` file pointing at `127.0.0.1`. |
| `Magento answered HTTP 401` | Wrong token, Integration not activated, its two Phone Verification permissions not ticked, or integration tokens not allowed as bearer tokens in Magento (see the module README). |
| `Magento answered HTTP 404` | Wrong `MAGENTO_BASE_URL`, or the module is not enabled. |
| Webhook returns `200` but nothing happens | The `messageId` was already used. Use a new one. |
| The menu arrives but tapping a row does nothing, console says `0 text message(s)` | The app is still an older build that only reads text. Rebuild and restart. |
| `Infobip rejected a message (HTTP 400)` right after sending the menu | The list was refused. Check the row titles and descriptions against the limits in `ListMessage`. |
| App fails at startup with a missing `SessionStore` bean | `AppConfig` was not updated to pass the `SessionStore` to `ConversationService`. |
| Messages from your phone never arrive, nothing in the console | The sandbox has not linked your phone yet. Send your Infobip account keyword once. |
| `HttpTimeoutException: Request cancelled` right after a Magento cache flush or module update | Magento was cold and took longer than 10 s. Retry; if it persists, time the call with curl and raise `app.magento.timeout`. |
| Verification always "invalid or expired" | The code expired (10 minutes), was already used, or the `from` number differs from the one entered on the website. |
| `Infobip rejected a message (HTTP n)` | Check the API key, base URL and sender. On a trial, the recipient must be a verified test number. |
| `Could not reach Infobip` | Network or TLS problem towards Infobip. If you set the Windows trust-store options, make sure the Windows certificate list is intact. |

## Known limits

- Understood: typed text and taps on list rows. Images, locations, reactions and reply-button taps are ignored.
- Two messages from the same number processed at the same moment are not yet serialised, so they could overwrite each
  other's session changes. This does not matter while sessions are never modified; a per-customer lock comes before the
  cart is filled.
- Most menu rows are placeholders; the cart cannot be filled yet.
- Processing is in-process: a message accepted while the JVM crashes is lost (Infobip already got its `200`).
  A durable queue (RabbitMQ) is the fix when that matters.
- The webhook secret lives in the URL path, so keep that URL out of shared logs and screenshots. Prefer a header or
  signature if your Infobip setup supports it.
- Local Redis has no password; see Quick start, step 1.

## Roadmap

1. ~~Interactive menu (list message) and session~~ **Done.**
2. A per-customer lock, then the conversation state machine (entering items, awaiting confirmation).
3. Magento endpoints for categories, product search and frequent products; wire up browse, search, SKU entry and
   order again.
4. Cash-on-delivery checkout through a single transactional Magento endpoint, with a maximum order value and an
   explicit confirm step.
5. Order status messages (approved templates are needed outside the 24-hour window).
6. RabbitMQ for durable processing, Docker image next to Warden, and a provider interface so Twilio can be added.