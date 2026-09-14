import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { act, cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { createMemoryRouter, RouterProvider } from "react-router-dom";
import { afterEach, expect, it, vi } from "vitest";
import { storefrontRoutes } from "./router.tsx";
import type { Storefront } from "@storefront/shared";

const fixtures: Storefront[] = [
  {
    id: 42,
    owner: { uuid: "alice", name: "Alice" },
    description: ["[storefront]", "Diamonds"],
    contents: [
      null,
      {
        name: "Diamond",
        key: "minecraft:diamond",
        amount: 2,
        meta: {},
        isBlock: false,
        maxDurability: 0,
      },
    ],
  },
  { id: 77, owner: { uuid: "bob", name: "Bob" }, description: [], contents: [] },
];
const clients: QueryClient[] = [];
const routers: ReturnType<typeof createMemoryRouter>[] = [];
afterEach(() => {
  cleanup();
  routers.splice(0).forEach((router) => router.dispose());
  clients.splice(0).forEach((client) => client.clear());
  vi.unstubAllGlobals();
  vi.unstubAllEnvs();
});
function mount(path = "/") {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  clients.push(client);
  const router = createMemoryRouter(storefrontRoutes(client), {
    initialEntries: [path],
    basename: import.meta.env.BASE_URL,
  });
  routers.push(router);
  render(
    <QueryClientProvider client={client}>
      <RouterProvider router={router} />
    </QueryClientProvider>,
  );
  return { client, router };
}
function response(data: unknown = fixtures) {
  return new Response(JSON.stringify(data), { status: 200 });
}

it("loads shops and item icons under the configured base path", async () => {
  vi.stubEnv("BASE_URL", "/storefront/");
  const fetch = vi.fn<typeof globalThis.fetch>().mockImplementation(async () => response());
  vi.stubGlobal("fetch", fetch);
  mount("/storefront/?username=Alice");
  await screen.findByText("Alice");
  expect(fetch.mock.calls[0]?.[0]).toBe("/storefront/api/storefronts/");
  expect(screen.getByAltText("Diamond (2)").getAttribute("src")).toBe(
    "/storefront/images/diamond.png",
  );
});

it("loads once through the router and filters cached data on navigation", async () => {
  const fetch = vi.fn<typeof globalThis.fetch>().mockImplementation(async () => response());
  vi.stubGlobal("fetch", fetch);
  const { router } = mount("/?username=Alice");
  await screen.findByText("Alice");
  expect(screen.queryByText("Bob")).toBeNull();
  expect(fetch).toHaveBeenCalledTimes(1);
  await act(() => router.navigate("/?username=Bob&simpleUI&timestamp"));
  expect(await screen.findByText("Bob")).toBeTruthy();
  expect(screen.queryByText("Alice")).toBeNull();
  expect(screen.queryByRole("heading")).toBeNull();
  expect(fetch).toHaveBeenCalledTimes(1);
});

it("shows quiet initial loading and keeps shops mounted during refresh", async () => {
  let resolveRequest!: (response: Response) => void;
  const fetch = vi.fn<typeof globalThis.fetch>().mockImplementation(
    () =>
      new Promise<Response>((resolve) => {
        resolveRequest = resolve;
      }),
  );
  vi.stubGlobal("fetch", fetch);
  mount();
  expect(await screen.findByText("Loading storefronts…")).toBeTruthy();
  expect(screen.queryByAltText("Loading storefronts")).toBeNull();
  await act(async () => resolveRequest(response()));
  const shop = await screen.findByText("Alice");
  expect(screen.getByRole("heading").textContent).toBe("Storefront");
  fireEvent.click(screen.getByText("Refresh"));
  expect(screen.getByText("Alice")).toBe(shop);
  expect(await screen.findByText("Updating storefronts…")).toBeTruthy();
  await act(async () => resolveRequest(response([])));
  expect(await screen.findByText("No storefronts found.")).toBeTruthy();
});

it("recovers from a loader failure using the route retry button", async () => {
  vi.stubGlobal(
    "fetch",
    vi
      .fn<typeof globalThis.fetch>()
      .mockResolvedValueOnce(new Response("", { status: 503 }))
      .mockImplementation(async () => response()),
  );
  mount();
  expect(await screen.findByRole("alert")).toHaveProperty(
    "textContent",
    "Unable to load storefronts (503).",
  );
  fireEvent.click(screen.getByText("Retry"));
  expect(await screen.findByText("Alice")).toBeTruthy();
});

it("retains stale shops when background refresh fails", async () => {
  vi.stubGlobal(
    "fetch",
    vi
      .fn<typeof globalThis.fetch>()
      .mockImplementationOnce(async () => response())
      .mockResolvedValue(new Response("", { status: 503 })),
  );
  mount();
  const shop = await screen.findByText("Alice");
  fireEvent.click(screen.getByText("Refresh"));
  await waitFor(() =>
    expect(screen.getByRole("alert").textContent).toContain("Showing the last loaded shops."),
  );
  expect(screen.getByText("Alice")).toBe(shop);
});

it.each([
  { label: "missing fields", data: [{ id: 1 }] },
  {
    label: "invalid item metadata",
    data: [{ ...fixtures[0], contents: [{ ...fixtures[0]!.contents[1], meta: [] }] }],
  },
])("rejects $label at the query boundary", async ({ data }) => {
  vi.stubGlobal(
    "fetch",
    vi.fn<typeof globalThis.fetch>().mockImplementation(async () => response(data)),
  );
  mount();
  expect((await screen.findByRole("alert")).textContent).toContain("invalid storefront data");
});
