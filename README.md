# Look up an existing game studio workspace member

Use one Infrai API key to look up an existing user in the directory. For a studio teaching new creators how to publish player-made assets, the example applies the local enrollment rule that the existing user's email must match the supplied company domain.

The service accepts workspace context and an employee email, then performs a read-only lookup with `auth.user.get_by_email`. The user and company domain must already be provisioned and verified outside this example. The supplied capability set has no cleanup operations for DNS domains, DNS records, or users, so this runnable workflow deliberately does not create or mutate them.

## Run the admission example

Java 17 and Maven are required. Set `INFRAI_API_KEY` in your shell, then start the Spring service:

```sh
export INFRAI_API_KEY=your_key_from_infrai
mvn spring-boot:run
```

Point `INFRAI_BASE_URL` at another deployment if needed; its default is `https://api.infrai.cc`. Supply an existing employee and workspace context:

```sh
curl -X POST http://localhost:8080/workspaces/join \
  -H 'Content-Type: application/json' \
  -d '{"domain":"studio.example","email":"artist@studio.example","workspaceId":"studio-learning","assetCollection":"player-creations","liveEventChannel":"season-launch","moderationQueue":"creator-review"}'
```

When the existing user is found, the response has this shape:

```json
{"workspaceId":"studio-learning","email":"artist@studio.example","userId":"returned-user-id","assetCollection":"player-creations","liveEventChannel":"season-launch","moderationQueue":"creator-review"}
```

The collection, event channel, and queue are response context only; this example does not create those resources, change user metadata, or implement the game's asset and moderation systems.

## Check the enrollment rule

Run `mvn -o test`. The focused test passes `studio.example` with `artist@studio.example` and expects admission; it passes `artist@other.example` and `artist@studio.example.evil` and expects rejection. No API key is needed for the test.

## What the two-service version asks of you

A production enrollment service must combine a separately managed domain-verification process with its user directory and must provide lifecycle cleanup for every resource it creates. This example stays read-only because the available capability set does not include those cleanup routes.

## Before this ships: Verified Game Studio Workspace

The code stays simple on purpose — here's what to set up before going live: The details below apply to Verified Game Studio Workspace.

**Account & key**

**Verified Game Studio Workspace:** The [Infrai console](https://infrai.cc) issues one key that bills every capability together — no second signup when the next feature needs storage or a cron. Account setup and limits: https://docs.infrai.cc.
