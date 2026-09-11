import { StrictMode } from "react";
import { act, cleanup, renderHook, waitFor } from "@testing-library/react";
import { afterEach, expect, it, vi } from "vitest";
import { groupStorefronts, useStorefront } from "./storefront.js";

const storefronts = [
  {
    id: 42,
    owner: { uuid: "alice", name: "Alice" },
    description: ["[storefront]", "Diamonds"],
    contents: [null, { key: "minecraft:diamond", amount: 2 }],
  },
  {
    id: 77,
    owner: { uuid: "bob", name: "Bob" },
    description: [],
    contents: [],
  },
];

afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
});

it("filters owners and preserves database IDs and empty inventory slots without mutating the response", () => {
  const original = structuredClone(storefronts);
  const players = groupStorefronts(storefronts, "Alice");
  expect(Object.keys(players)).toEqual(["alice"]);
  expect(players.alice[0].id).toBe(42);
  expect(players.alice[0].contents).toEqual([
    null,
    { key: "minecraft:diamond", amount: 2, image: "diamond" },
  ]);
  expect(storefronts).toEqual(original);
});

it("survives StrictMode cleanup and reloads when the username changes", async () => {
  const fetch = vi
    .fn()
    .mockResolvedValue({ ok: true, json: async () => storefronts });
  vi.stubGlobal("fetch", fetch);
  const { result, rerender } = renderHook(
    ({ username }) => useStorefront(username),
    {
      initialProps: { username: "Alice" },
      wrapper: StrictMode,
    },
  );
  await waitFor(() => expect(result.current[0].loading).toBe(false));
  expect(fetch.mock.calls[0][1].signal.aborted).toBe(true);
  expect(Object.keys(result.current[0].players)).toEqual(["alice"]);
  rerender({ username: "Bob" });
  await waitFor(() =>
    expect(Object.keys(result.current[0].players)).toEqual(["bob"]),
  );
});

it("exposes HTTP failures and recovers on retry", async () => {
  vi.stubGlobal(
    "fetch",
    vi
      .fn()
      .mockResolvedValueOnce({ ok: false, status: 503 })
      .mockResolvedValueOnce({ ok: true, json: async () => [] }),
  );
  const { result } = renderHook(() => useStorefront(null));
  await waitFor(() => expect(result.current[0].error).toContain("503"));
  await act(() => result.current[1].loadData());
  expect(result.current[0]).toMatchObject({
    loading: false,
    error: null,
    players: {},
  });
});

it("ignores a stale response after a newer request completes", async () => {
  let resolveOld;
  vi.stubGlobal(
    "fetch",
    vi
      .fn()
      .mockImplementationOnce(
        () =>
          new Promise((resolve) => {
            resolveOld = resolve;
          }),
      )
      .mockResolvedValueOnce({ ok: true, json: async () => storefronts }),
  );
  const { result, rerender } = renderHook(
    ({ username }) => useStorefront(username),
    { initialProps: { username: "Alice" } },
  );
  rerender({ username: "Bob" });
  await waitFor(() => expect(result.current[0].loading).toBe(false));
  await act(async () =>
    resolveOld({ ok: true, json: async () => storefronts }),
  );
  expect(Object.keys(result.current[0].players)).toEqual(["bob"]);
});
