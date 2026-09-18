# Phase 17 — Webhooks

## Why

Phase 6 set up periodic polling (Jenkins asks GitHub "anything new?" every minute) and explicitly deferred webhooks as out of scope, since GitHub can't reach a Jenkins running on your laptop's `localhost`. This phase solves that specific local-dev problem and switches to the real-world-standard trigger mechanism: GitHub telling Jenkins the instant something happens, instead of Jenkins asking repeatedly.

## Concepts to understand first

### Polling vs. webhooks

- **Polling** (what Phase 6 set up): Jenkins periodically calls GitHub's API asking "what's changed?" Simple, no network exposure required, but has real costs at scale: **latency** (a push waits up to one poll interval before a build starts), and **API rate-limit pressure** (many jobs polling many repos frequently is a genuine, commonly-hit real-world GitHub API rate-limiting problem).
- **Webhooks**: GitHub sends Jenkins an HTTP POST the instant a push/PR event happens. Near-zero latency, no wasted "nothing changed" API calls. This is the real-world default for any Jenkins instance that's actually reachable from GitHub.

### The local-dev problem: GitHub can't reach `localhost:8080`

Your Jenkins is only reachable from your own machine — there is no public URL GitHub's servers can send a webhook POST to. In a real company, Jenkins is usually already reachable from GitHub (public IP, or GitHub Enterprise inside the same corporate network) — this obstacle is specifically a local-laptop-setup artifact, not something you'll normally fight in production. The standard fix for local development is a **tunnel**: a small client process that opens an outbound connection from your laptop to a public relay, giving GitHub something reachable that forwards straight back to your `localhost`.

`smee.io` is the tool GitHub's own webhook documentation recommends specifically for this local-development scenario (as opposed to general-purpose tunnels like ngrok, which work too but aren't webhook-specific).

### Webhook payload security

Anyone who discovers your (tunnel-exposed) webhook URL could POST a fake "push" event and trigger a build — unless the endpoint verifies the request actually came from GitHub. GitHub signs every webhook payload with HMAC-SHA256 using a shared secret you configure on both ends; Jenkins' GitHub plugin validates this signature and silently ignores anything that doesn't match. This is a real, necessary control the moment any webhook endpoint is reachable from the public internet at all — even via a "temporary" tunnel.

### Webhook delivery isn't guaranteed — and doesn't self-heal

GitHub retries a failed webhook delivery a few times, then gives up silently. Unlike polling (which just tries again next cycle regardless), a missed webhook delivery means that specific push **never** triggers a build unless something else notices — no automatic reconciliation. This is a genuine, real operational gotcha: "instant" triggering is only as reliable as the delivery, and outages (your tunnel dropping, Jenkins restarting at the wrong moment) create gaps polling wouldn't have.

**The pragmatic real-world pattern**: keep webhooks as the primary trigger, but leave a much-lower-frequency poll (e.g. every few hours) running as a safety net — "belt and suspenders" — so a missed webhook delivery is eventually caught anyway instead of silently never building.

## Practical scenarios

### Scenario A — set up the tunnel and register the webhook

1. Create a channel at `https://smee.io/new`, copy the generated URL.
2. Run the smee client locally, forwarding to your Jenkins webhook endpoint:
   ```bash
   npx smee-client --url <your-smee-url> --path /github-webhook/ --target http://localhost:8080/github-webhook/
   ```
3. In your GitHub repo → Settings → Webhooks → Add webhook: Payload URL = your smee.io URL, content type `application/json`, events = "Just the push event" (or "Send me everything" while learning).
4. Push a commit — confirm Jenkins starts a build within seconds, not within the old poll interval.

### Scenario B — belt and suspenders

1. Reduce (don't fully remove) the Multibranch job's periodic scan interval from Phase 6 to something much longer (e.g. every 4 hours) rather than every 1 minute.
2. Articulate why: this is now a safety net for missed webhook deliveries, not the primary trigger — it doesn't need to be fast anymore.

### Scenario C — verify signature validation actually protects you

1. In the GitHub webhook config, set a **Secret** value, and configure the matching secret in Jenkins (GitHub plugin's shared secret configuration).
2. Attempt to POST a forged "push" event directly to your smee/Jenkins endpoint via `curl`, without the correct signature header — confirm Jenkins rejects/ignores it.
3. Confirm a real push from GitHub (correctly signed) still triggers normally.

### Scenario D — witness a missed delivery, and recover from it

1. Stop the smee client (simulating your tunnel going down).
2. Push a commit — confirm no build triggers.
3. Check GitHub's webhook **Recent Deliveries** panel — find the failed delivery, and use the **Redeliver** button to manually resend it once your smee client is back up. Confirm this manually triggers the build after the fact.
4. Separately, confirm your Scenario B safety-net poll would have eventually caught this same missed push on its own, without any manual redelivery.

## Acceptance criteria

- [ ] smee tunnel running, GitHub webhook registered and pointed at it
- [ ] A push triggers a Jenkins build within seconds (Scenario A)
- [ ] Polling interval reduced to a safety-net cadence, with reasoning stated (Scenario B)
- [ ] Webhook secret configured, and an unsigned/forged request confirmed to be rejected (Scenario C)
- [ ] A missed delivery reproduced, observed in GitHub's delivery log, and manually redelivered (Scenario D)

## Out of scope

- Running smee (or any tunnel) as a permanent, always-on service — it's explicitly a local-development workaround. A real deployment would either have Jenkins genuinely reachable from GitHub, or use GitHub Enterprise Server inside the same network with no tunnel needed at all.
- GitHub App–based integration (vs. classic webhook + token) for richer PR status/check reporting — a reasonable next step once basic webhook triggering is solid.
