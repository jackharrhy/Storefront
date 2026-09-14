# Storefront

Place a chest in the world, put a sign on it, slap `[storefront]` on the first line, discuss your wares, and it's on a website!

![Front page of Storefront, showing a few players' current trades](docs/images/storefront.png)

The frontend Docker build accepts `--build-arg BASE_PATH=/storefront/` (default `/`).
When serving under a prefix, strip it at the reverse proxy before forwarding to
the frontend container. Set `API_PROXY_TARGET` to the plugin's HTTP address.
