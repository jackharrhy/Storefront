import { useRevalidator, useRouteError } from "react-router-dom";

export function LoadingPage() {
  return (
    <main id="app-root" data-loading="true" aria-busy="true">
      <output className="status">Loading storefronts…</output>
    </main>
  );
}

export function RouteError() {
  const error = useRouteError();
  const revalidator = useRevalidator();
  return (
    <main id="app-root">
      <header>
        <h1>Storefront</h1>
      </header>
      <p className="status" role="alert">
        {error instanceof Error ? error.message : "Unable to load this page."}
      </p>
      <button disabled={revalidator.state !== "idle"} onClick={() => void revalidator.revalidate()}>
        {revalidator.state === "idle" ? "Retry" : "Retrying…"}
      </button>
    </main>
  );
}
