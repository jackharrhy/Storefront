# Item catalog and browser audit

Start the Compose server, then generate the item catalog:

```sh
docker compose up --build -d --wait paper storefront-frontend
docker compose --profile catalog run --build --rm catalog
```

The bot `StorefrontItems` creates fixtures near **2048, 65, 2048**, separate from the diamond stress grid. Each chest is registered by clicking its sign, and every slot is verified through the plugin API. Repeating the command replaces that catalog area. Do not run this against a world you want to preserve. Fixtures persist for browsing.

The source of truth is the running Paper item registry, exposed through the operator-only `/storefrontcatalog` command. That command only inspects registries and requires `development.fixtures: true`; the Compose config enables it, while normal plugin installations default to disabled. Air is excluded because it represents an empty slot; items disabled by world feature flags are listed as exclusions.

The catalog covers every enabled base item type at its maximum stack size, plus a bounded variant matrix:

- Every registered potion type in bottles, splash bottles, lingering bottles, and tipped arrows.
- Every registered enchantment at each supported level on an enchanted book.
- Every trim pattern × trim material on a netherite chestplate.
- The sixteen Minecraft dye colors on leather helmets, chestplates, leggings, boots, and horse armor.
- Half-used and nearly broken versions of every durable item.
- Every goat-horn instrument, plus named/lore items, a filled bundle, a loaded crossbow, a firework, and a patterned banner.

This is not every possible component combination. Arbitrary text, player profiles, custom model data, maps, RGB colors, nested container contents, and component combinations make exhaustive enumeration impractical. The trim matrix uses one armor base; the remaining armor types are covered as base items, not crossed with every trim and dye.

The complete manifest, including commands, slot IDs, and observed metadata, is generated at `dev/reports/catalog.json` and excluded from Git. Browse `/storefront/?username=StorefrontItems` for the full catalog.

Run the browser audit:

```sh
npm --prefix dev/browser ci
# If npm has blocked Puppeteer's browser installation:
npm --prefix dev/browser exec -- puppeteer browsers install chrome
npm --prefix dev/browser run catalog
```

Set `STOREFRONT_URL` for another frontend and `BROWSER_EXECUTABLE_PATH` to use an existing Chrome/Chromium installation. The default URL is `http://127.0.0.1:8080/storefront/`.

The audit validates the PNG signature and HTTP status of every base icon, then scrolls every catalog chest in desktop, mobile (including the 600 px breakpoint), and nested-panel layouts. It checks decoded images/fallbacks, deferred initial image requests, item coverage, horizontal overflow, image-induced resizing, JavaScript errors, and cumulative layout shift (limit 0.1). It writes `dev/reports/catalog-coverage.json` and desktop/mobile screenshots. Missing icons fail the audit after writing the report.

Images use native lazy loading and explicit dimensions; inventory slots reserve their space. `content-visibility: auto` lets the browser defer offscreen layout/paint, with an estimated intrinsic height that is replaced by the measured height. This keeps DOM content available to find-in-page and accessibility tools; it is not React DOM virtualization or API pagination. The browser still fetches the complete storefront JSON. The screenshot helper explicitly renders and loads the whole page before exporting it.

Coverage has two separate meanings: the current icon pack supplies a sprite per **base item key**. Metadata variants share those sprites; potion colors, dyed leather, trims, enchantment glint, banner patterns, and filled-container previews do not get distinct images. Stack counts and durability bars are rendered separately. The generated report records this limitation rather than treating the presence of a shared PNG as full variant-rendering support.

References: [Paper registries](https://docs.papermc.io/paper/dev/registries/), [native image loading](https://developer.mozilla.org/en-US/docs/Web/HTML/Reference/Elements/img), and [CSS content visibility](https://developer.mozilla.org/en-US/docs/Web/CSS/Reference/Properties/content-visibility).

## Recorded result

Paper 26.2 / Chrome 153, 2026-09-12: **1,536 enabled base types, 771 variant samples, 86 chests, and no missing icons**. The browser verified all 2,307 slots in every layout.

| Layout | Initial / total distinct icon requests | CLS | Image-induced resizing | Horizontal overflow |
| --- | ---: | ---: | ---: | --- |
| Desktop, 1280 × 800 | 324 / 1,536 | 0 | 0 px | None |
| Mobile, 390 × 844 | 198 / 1,536 | 0 | 0 px | None |
| Mobile breakpoint, 600 × 900 | 198 / 1,536 | 0 | 0 px | None |
| Nested panels, 1280 × 800 | 218 / 1,536 | 0 | 0 px | None |

These are local Chromium measurements, not guarantees for all browsers or networks. CLS here sums unexpected Layout Instability entries over the audit; the raw report includes their sources when present. See [the machine-readable report](../reports/catalog-coverage.json).

The fixture exposed a Java 26 serialization failure for nested Bukkit items (including filled bundles and loaded crossbows). The inventory serializer now delegates `ConfigurationSerializable` objects to their supported `serialize()` method instead of reflecting through server and JDK internals. A regression test covers nested serialization, and the real catalog verifies those inventory samples reach the API. The browser audit also checks the preserved bundle and crossbow contents.
