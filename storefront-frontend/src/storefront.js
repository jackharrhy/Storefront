import { useCallback, useEffect, useRef, useState } from "react";

export function groupStorefronts(storefronts, username) {
  const players = {};
  for (const storefront of storefronts) {
    if (username != null && storefront.owner.name !== username) continue;
    const contents = storefront.contents.map((item) =>
      item === null
        ? null
        : {
            ...item,
            image: item.key.replace(/^minecraft:/, ""),
          },
    );
    (players[storefront.owner.uuid] ??= []).push({ ...storefront, contents });
  }
  return players;
}

export function useStorefront(username) {
  const [state, setState] = useState({
    players: {},
    loading: true,
    error: null,
    currentItem: null,
  });
  const request = useRef(null);

  const loadData = useCallback(async () => {
    request.current?.abort();
    const controller = new AbortController();
    request.current = controller;
    setState((previous) => ({
      ...previous,
      loading: true,
      error: null,
      currentItem: null,
    }));
    try {
      const response = await fetch(
        `${import.meta.env.BASE_URL}api/storefronts/`,
        { signal: controller.signal },
      );
      if (!response.ok)
        throw new Error(`Unable to load storefronts (${response.status}).`);
      const storefronts = await response.json();
      if (controller.signal.aborted) return;
      setState({
        players: groupStorefronts(storefronts, username),
        loading: false,
        error: null,
        currentItem: null,
      });
    } catch (error) {
      if (controller.signal.aborted) return;
      setState((previous) => ({
        ...previous,
        loading: false,
        error: error.message,
      }));
    }
  }, [username]);

  useEffect(() => {
    loadData();
    return () => request.current?.abort();
  }, [loadData]);

  return [
    state,
    {
      loadData,
      setCurrentItem: (item) =>
        setState((previous) => ({ ...previous, currentItem: item })),
      clearCurrentItem: () =>
        setState((previous) => ({ ...previous, currentItem: null })),
    },
  ];
}
