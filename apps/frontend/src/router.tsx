import type { QueryClient } from "@tanstack/react-query";
import type { RouteObject } from "react-router-dom";
import App from "./App.tsx";
import { storefrontQuery } from "./storefront.ts";
import { LoadingPage, RouteError } from "./route-status.tsx";

export function storefrontRoutes(queryClient: QueryClient): RouteObject[] {
  return [
    {
      path: "/",
      loader: () => queryClient.ensureQueryData({ ...storefrontQuery, revalidateIfStale: true }),
      Component: App,
      HydrateFallback: LoadingPage,
      ErrorBoundary: RouteError,
    },
  ];
}
