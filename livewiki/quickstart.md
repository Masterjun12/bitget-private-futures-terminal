# Quickstart

## What this repository is

A compact, always-on-top Java 11 Swing dashboard for monitoring Bitget USDT futures. It displays public BTC, ETH, and SOL prices, the top five futures gainers, and—when you provide read-only API credentials—account equity and open positions.

*(Purpose excerpt from the repository README: `README.md`.)*

Use this wiki to choose a task, inspect the repository architecture, query focused pages from an agent, and keep the documentation up to date as the code changes.

## Work by intent

- **Change product behavior:** start with [Tasks](tasks.md).
- **Inspect implementation relationships:** open the [Architecture overview](architecture/overview.md).
- **Maintain tests, fixtures, tooling, benchmarks, or repository documentation:** open the [Auxiliary areas](auxiliary/index.md) inventory.

## Document a repo

1. Run `livewiki init` to index the repository and create deterministic navigation.
2. Run `livewiki init --batch` when you also want the generated folder and file pages.
3. Run `livewiki verify` before relying on or publishing the wiki.

## Query the wiki from an agent

1. Read `livewiki_quickstart` for orientation.
2. Use `livewiki_search` to find relevant pages.
3. Use `livewiki_read` to inspect the selected page in full.

## Keep the documentation up to date (for agents)

1. Inspect open debt with `livewiki_debt` or `livewiki status --json`.
2. Update a page with `livewiki_write_doc`, or edit it directly while preserving its ownership rules.
3. Run `livewiki verify`, then close resolved items with `livewiki_resolve_debt`.

## Repository facts

- **23 files** documented
- **7 folders** covered
- **181 code symbols** indexed
