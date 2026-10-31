# Source fixture

`source-494130c.zip` contains all 29 Markdown files (27 recipes and the root
README/AGENTS documents) from public `miguellobato84/recipes` commit
`494130cd4dc66f0ef3095040830426e2a839d898`, verified against `main` on 2026-10-31.
Non-Markdown source assets are omitted. These files are test-only resources and
are never bundled in the APK. Do not edit the source repository to run tests.

The opt-in `LiveSourceTest` downloads the real public snapshot using the app's
downloader when `VERIFY_LIVE_RECIPES=1`. Ordinary tests remain offline.
