# Step 3: webhook, VERIFY handler, Infobip reply

Package: `com.alex.chat_commerce_bot` (the default Initializr package for `chat-commerce-bot`).
If yours differs, move the folders and fix the `package` / `import` lines.

## Add to your project

1. Copy `src/` and `compose.yaml` into your project root (merge folders).
   Keep your existing `ChatCommerceBotApplication.java` and its test.
2. **Delete** `src/main/resources/application.properties` (Initializr's empty one); this uses `application.yml`.
3. `docker compose up -d` starts Redis on localhost:6379.
4. Set environment variables in IntelliJ (Run > Edit Configurations > Environment variables):

| Variable | Value |
|---|---|
| `WEBHOOK_SECRET` | any long random string (e.g. `openssl rand -hex 24`) |
| `MAGENTO_TOKEN` | Access Token of your Magento Integration (only the two Phone Verification permissions) |
| `MAGENTO_BASE_URL` | your Warden URL, e.g. `https://app.magento.test` (check `WARDEN_ENV_NAME` in your Magento `.env`) |
| `STOREFRONT_URL` | same public store URL |
| `INFOBIP_BASE_URL` | the base URL shown in your Infobip portal, e.g. `https://xxxxx.api.infobip.com` |
| `INFOBIP_API_KEY` | your Infobip API key |
| `INFOBIP_SENDER` | your WhatsApp sender number, digits only |

The app refuses to start until the required ones are set.

## Test without Infobip

Simulate Infobip's webhook with curl (replace the secret and the code from My Account):

```bash
curl -i -X POST "http://localhost:8080/webhooks/whatsapp/$WEBHOOK_SECRET" \
  -H "Content-Type: application/json" \
  -d '{"results":[{"from":"27821234567","to":"27000000000","messageId":"test-1","message":{"type":"TEXT","text":"VERIFY 482913"}}]}'
```

- Expect `200` immediately. A wrong secret gives `401`.
- Watch the app log: it prints the masked number and the outcome, never the code.
- Magento's `customer_entity.phone_verified` flips to 1 on a correct code.
- Use a new `messageId` for each test: repeating one is deliberately ignored (dedupe).
- The reply goes through Infobip, so with a fake sender you will see an Infobip error in the log; that is expected.

## Receiving real WhatsApp messages

Infobip must reach your app from the internet, so on a laptop you need a tunnel (ngrok, cloudflared, etc.)
pointing at port 8080. In the Infobip portal, set the inbound-message forwarding for your sender to
`https://<tunnel-host>/webhooks/whatsapp/<WEBHOOK_SECRET>`. The exact menu names change, so follow
Infobip's current WhatsApp "receive messages" docs. On a trial account only your verified test numbers can message you.

## Likely first problems

- **"Could not reach Magento: SSLHandshakeException ..."** Warden uses its own root CA, which Java does not trust.
  Import Warden's CA into the JDK you run the app with (typically `~/.warden/ssl/rootca/certs/ca.cert.pem` inside WSL):
  `keytool -importcert -alias warden -file ca.cert.pem -cacerts -storepass changeit`.
- **"UnknownHostException app.magento.test"**: your Windows hosts file needs an entry for the Warden domain
  (WSL has its own). Point it at 127.0.0.1.
- **"Magento answered HTTP 401"**: wrong or missing `MAGENTO_TOKEN`, or the Integration is not activated.
- **"Magento answered HTTP 404"**: wrong `MAGENTO_BASE_URL`, or the Vendor_PhoneVerification module is not enabled.

## What was verified

`conversation/` has no Spring imports and its 12 unit tests were compiled and run (Java 21, against minimal stand-ins
for SLF4J and JUnit): verify variants, dedupe, rate limit, stranger throttle, Magento-down message, log redaction.

**Not compiled or run:** the Spring wiring (`config/`, `store/`, `magento/`, `infobip/`). No Maven repository
was reachable where this was written, so it has not been built against Boot 4.1.2. Expect to fix an import or two on first build.
Infobip's payload and endpoint shapes are from memory of their docs; check them against the current docs.

## Known limits (by design, for now)

- Only plain text messages are read; list / button taps arrive with the menu in the next step.
- A message that arrives while Redis or the JVM is down is lost (Infobip already got its 200).
  A durable queue (RabbitMQ) is the fix when it matters.
- The webhook secret is in the URL path, so keep it out of shared logs.
