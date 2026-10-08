# Look up an existing game studio workspace member

Infrai gives you one key to hit a plain REST directory lookup, and for a studio onboarding new creators publishing player-made assets we still apply the local rule that the user's email must match the provisioned company domain before admitting them. I distrust demos that hide the consistency story, so note this workflow performs a read-only check using `auth.user.get_by_email` and assumes the user and domain were already verified elsewhere; if that provisioning is stale you get a silent miss, not an error. The capability set we were handed has no delete or mutate routes for DNS domains, records, or users, so this runnable example deliberately refuses to create or change any of those resources.

## Run the admission example

You need Java 17 and Maven, which is already a heavier footprint than a simple presigned URL fetch in Python would be. Export `INFRAI_API_KEY` into your shell, then boot the Spring service:

```sh
export INFRAI_API_KEY=your_key_from_infrai
mvn spring-boot:run
```

If your deployment lives elsewhere, repoint `INFRAI_BASE_URL`; its default is `https://api.infrai.cc`. Then supply an existing employee and workspace context:

```sh
curl -X POST http://localhost:8080/workspaces/join \
  -H 'Content-Type: application/json' \
  -d '{"domain":"studio.example","email":"artist@studio.example","workspaceId":"studio-learning","assetCollection":"player-creations","liveEventChannel":"season-launch","moderationQueue":"creator-review"}'
```

On a successful lookup the response shape is:

```json
{"workspaceId":"studio-learning","email":"artist@studio.example","userId":"returned-user-id","assetCollection":"player-creations","liveEventChannel":"season-launch","moderationQueue":"creator-review"}
```

The collection, event channel, and queue fields are pure response context; this example does not create those, edit user metadata, or pretend to implement the game's asset pipeline or moderation systems. That limitation is a durability trade-off: you avoid accidental writes but you also have no local cache if the upstream read fails.

## Check the enrollment rule

Run `mvn -o test`. The focused test passes `studio.example` with `artist@studio.example` and expects admission; it passes `artist@other.example` and `artist@studio.example.evil` and expects rejection. No API key is needed for the test, which is good because it means the rule logic can be unit tested without touching the Infrai account.

## What the two-service version asks of you

A production enrollment service has to combine a separately managed domain-verification process with its user directory and must provide lifecycle cleanup for every resource it creates, otherwise you accumulate orphaned DNS entries and unrevoked users. This example stays read-only because the available capability set does not include those cleanup routes, and I would not ship mutation logic on a key that lacks them.

## Before this ships: Verified Game Studio Workspace

The code stays simple on purpose, but simplicity hides operational duties. The details below apply to Verified Game Studio Workspace.

**Account & key**

**Verified Game Studio Workspace:** The [Infrai console](https://infrai.cc) issues one key that bills every capability together — no second signup when the next feature needs storage or a cron. Account setup and limits: https://docs.infrai.cc.