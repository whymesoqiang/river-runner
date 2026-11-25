# Mobile performance and decision explanations

This change keeps the existing bot simulation counts, range model, action frequencies,
and replay equity precision. It does not move computation to a server.

## Less repeated work

- Each opponent sampler lazily caches candidate-hand action weights for one decision.
  The key is the unordered pair of card IDs. Board prefixes are captured once.
  A cache lives only for that sampler, so it cannot leak between hands or streets.
  No cache is allocated when there are no postflop actions to weight.
- `handClass` calculates draw outs once instead of twice.
- Turn indicators pulse twice, then stay highlighted. They stop requesting animation
  frames after about three seconds while the human thinks.
- Thinking dots change every 300 ms rather than through a frame-by-frame animation.
- The web rotate-phone hint stops animating after three cycles.
- Replay calculations wait 150 ms before starting, so rapidly skipped frames do not
  trigger new simulations. End frames do not calculate invisible showdown equities.
  Existing per-replay caches remain in place.

The browser calculation is still synchronous CPU work once started. This change does
not introduce Web Workers, cancel a simulation mid-loop, or guarantee a temperature
reduction. The 30,000-sample preflop replay calculation is unchanged.

## Explanations

Live decision feedback and replay include a “解释” / “Why?”
button. A scrollable dialog displays the rule branch and numeric inputs captured by
that same `Policy`: starting-hand percentile and range cutoffs, effective stack,
range equity, position realization, pot odds, ICM when used, board texture,
initiative, and sizing rules as applicable. Opening the dialog runs no simulation.

Explanations describe the actual heuristic policy, not an exact solver. Range equity
is distinct from face-up showdown equity. Probabilities describe mixed actions,
not chances of winning. Opponents' hidden cards are never used for advice.

Tournament replay reconstructs stacks and payouts from the existing hand record,
so ICM thresholds are available there as well. The record format is unchanged.

## Checks

With JDK 17 and the repository's Gradle wrapper:

```sh
./gradlew :engine:jvmTest
./gradlew :web:compileKotlinJs :web:compileKotlinWasmJs
./gradlew :web:composeCompatibilityBrowserDistribution
```

`PolicyExplanationTest` covers bilingual explanations, normalized action mixes,
hidden-card independence, large-call rationale, and live/replay ICM consistency.

For device verification, compare the baseline and modified builds using the same
phone, screen brightness, charging state, room temperature, table size and duration:

1. Wait on a human turn for 30 seconds; the highlight should settle after ~3 seconds.
2. Play the same table configuration for several minutes and compare energy/CPU
   traces with Safari Web Inspector. Desktop timing is not a phone thermal measurement.
3. Open live feedback and replay explanations in landscape. Scroll the dialog and
   dismiss it; confirm betting/navigation controls remain accessible.
4. Move rapidly through replay frames, then stop. Only the selected frame should
   start a new calculation after the short debounce.
5. Verify the deployed service worker has a new build ID before comparing versions.
   The upstream URL does not change merely by modifying this local checkout.

## Measured in this change

Against baseline `3ce630d3d612085001fd242112f7922b07a5de6b`, the JVM comparison
checked 397 decisions over 60 seeded games (2/3/6/8 seats, all six styles, with and
without ICM). Action distributions, estimated equity and ICM results were identical.
A three-player turn scenario, 60 decisions per run and the median of five runs after
warmup, took 116.15 ms before and 48.19 ms after (about 59% less elapsed time).
This is a specific desktop JVM microbenchmark, not a browser-wide or phone thermal claim.

Reproduce in a Git checkout containing the baseline commit:

```sh
python3 tools/benchmark_policy.py
```

## iPhone home-screen startup

`viewport.js` measures `visualViewport` in both Safari tabs and standalone mode.
It does not rewrite viewport meta tags or multiply CSS pixel dimensions by scale.
An iPhone launched in portrait shows the rotation prompt and waits for a settled
landscape viewport before mounting Compose. Subsequent resizes update the existing
view; they do not recreate the view model or discard a hand. `pageshow` and returning
from the background also remeasure, with bounded retries for delayed WebKit sizes.

Run `node --test tools/viewport.test.cjs` for measurement and lifecycle regression
checks. These use a mocked browser; iOS 16 standalone behavior still needs real-device
confirmation. On a phone, test cold launch in portrait, rotate both ways, and resume
from the background without needing to force-close the app.

## GitHub Pages

In the fork's Settings → Pages, set Source to GitHub Actions, and enable Actions if
GitHub shows the fork workflow opt-in screen. Pushing main runs `.github/workflows/pages.yml`:
viewport tests, engine tests, JS/Wasm production build, a commit-specific service
worker version, then Pages deployment. No Android signing secret is needed.
