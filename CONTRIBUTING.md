# Contributing

Short conventions for branches, commits, and pull requests.

## Branch naming

Format: `type/short-task-description`

- `type` matches the commit type of the work: `feat`, `fix`, `chore`, `docs`, `refactor`, `test`, `ci`, `build`.
- `short-task-description` is kebab-case, short, imperative (e.g. `xxx` in `chore/xxx`, `feat/xxx`).

Examples:

```text
chore/contributing-conventions
feat/live-vehicle-markers
fix/departure-delay-offset
docs/architecture-diagram
```

`renovate/*` branches are reserved for the Renovate bot.

## Commits

This repo follows [Conventional Commits](https://www.conventionalcommits.org/):

```text
type(scope): subject
```

- `type`: `feat`, `fix`, `chore`, `docs`, `refactor`, `test`, `ci`, `build`.
- `scope` is optional (e.g. `refactor(di)`, `fix(deps)`).
- `subject` is short, imperative, no trailing period.

Examples:

```text
feat: add live vehicle markers
fix: correct departure delay offset
chore(deps): update okhttp to v5.5.0
docs: add AGENTS.md as agent-doc source of truth
refactor(di): migrate from Hilt to Koin
```

## Pull requests

- Keep PRs small and focused; one convention type per PR when possible.
- PR titles follow the same `type(scope): subject` format (squash-merge friendly).
- CI must pass before merge.
- Sign commits (SSH signing).

## Formatting and static analysis

- Formatting is enforced by Spotless with ktfmt (kotlinlang style, matching the
  `kotlin.code.style=official` IDE setting): run
  `./gradlew spotlessApply` before pushing, CI runs `spotlessCheck`.
- Static analysis runs via `./gradlew detekt` (config in `config/detekt/detekt.yml`,
  including Compose rules). Fix findings or suppress with a justifying comment;
  don't weaken the shared config for a single case.

## Dependency and schema checks

CI runs three checks beyond tests/lint/build/quality: an APK/AAB size report, dependency-guard
(transitive closure baselines in `*/dependencies/`), dependency-analysis (`buildHealth`:
unused deps and runtimeOnly/compileOnly mis-scoping fail; transitive/api advice is warn-only),
and a Room schema freshness check folded into the build job (`core/schemas/`).

- Intentional dependency change trips dependency-guard: run
  `./gradlew dependencyGuardBaseline` and commit the updated baselines.
- `buildHealth` failures list exact fixes; transitive/api suggestions are warn-only.
- Entity changes regenerate schemas via `./gradlew :core:assembleDebug`; commit them.
