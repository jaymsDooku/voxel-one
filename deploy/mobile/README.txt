Native iPhone HTTPS gateway path

Status: prepared and tested on isolated Linux fixtures. Not installed on the VPS.
This configuration does not qualify the native app, live game servers or iPhone.

Topology
The phone uses ordinary system-trusted HTTPS to a dedicated mobile DNS hostname.
Nginx forwards only /mobile/v1/ to the existing loopback Java gateway. The gateway
uses its existing pinned-TLS client for Sandbox and City; account storage and
authoritative movement, edits, inventory and city commands remain on those servers.
Do not expose port25567 or either test fixture server publicly. Do not change ATS,
use an untrusted certificate override, or substitute a proxy for server pinning.

Controller setup (review before any production installation)
Use the existing approved service/certificate provisioning path. Root coordinates
the public DNS name, system-trusted certificate/renewal and the Java gateway's
protected launch configuration with actual game-server certificate fingerprints.
No certificate, account, key, profile or deployed configuration is supplied here.
Do not place private inputs in the repository, command output or evidence.

Render a new http-context include without touching system configuration:

  python3 deploy/mobile/render_proxy.py \
    --host mobile.example.invalid \
    --certificate /protected/mobile/fullchain.pem \
    --private-key /protected/mobile/key.pem \
    --output target/mobile-nginx.conf

Replace the example hostname and protected paths through the controller's approved
configuration flow. The renderer validates literal values and creates output
exclusively; it never reads certificate/key contents, installs or reloads services.
Default listen is443 and upstream is127.0.0.1:25567. --loopback-only and explicit
ports support isolated checks. Include exactly once inside nginx's http block;
the two connection-limit zones have fixed names. Preserve unrelated virtual hosts.
Use a dedicated mobile hostname; this server deliberately rejects every other
route. The controller must validate nginx -t on the actual assembled configuration
and check protected file permissions, renewal, firewall and gateway lifecycle
before any authorized reload. No production install/reload was executed here.

Transport and resource bounds
Only TLS1.2/1.3, with normal trust. Only POST login/state/move/action/logout and GET
health are routed. Queries, encoded path aliases, extra routes, wrong Host and
browser Origin are rejected. Java retains its independent Origin rejection,
including an empty Origin. Authorization passes unchanged; client-supplied
Forwarded/X-Forwarded-* and X-Real-IP are cleared. The gateway continues to count
login attempts by actual loopback peer:10/minute across this proxy, not10 per
public address. Do not trust spoofed client headers to bypass that existing limit.

The proxy caps body size at8192bytes, peer requests at16 concurrent and total
requests at64 concurrent. Header/body/send timeouts are10/15/15seconds; upstream
connect/send/read timeouts are3/15/95seconds. Read/send limits are idle intervals,
not whole-request deadlines; the native app retains its own finite timeouts.
Connection keepalive is15seconds. Game snapshots stream without proxy response
buffering/cache/temp files; request buffering and proxy retries are off. An
ambiguous action is never replayed automatically; reconnect and resynchronize
through the app's normal route. Game action receipt/deduplication rules remain.
No access logs, request bodies or Authorization headers are recorded by this
virtual host. Its error log is disabled too: health and fixed controller receipts
provide diagnostics without request data. Do not enable debug logging to diagnose
a real user's sign-in. Backend/client privacy rules remain required independently.

Health and live acceptance
GET /mobile/v1/health returns only application=voxel-one,
component=mobile-gateway,status=alive,protocol=1 with no-store/nosniff headers.
This is process liveness, not game-server readiness or authenticated playability.
It creates no account, session or game connection. An unreachable gateway returns
502 through the proxy. The endpoint rejects POST, query/body and browser Origin.

After authorized deployment, the controller can use ordinary TLS trust:

  curl --fail --max-time 5 https://MOBILE_HOST/mobile/v1/health

Check the fixed fields above; a200 by itself proves neither upstream readiness
nor player access. Then use a fresh synthetic game account through the native app
to test sign-in, world/model load, movement, edit/inventory authority, disconnect,
reconnect/resync and logout. Preserve the live app capture and exact source. Root
must coordinate any service publication. Owner account data must not enter logs.

Reproduce isolated qualification
Requires existing Java/Maven dependencies, openssl and an available Nginx binary.
Do not install or change a system service just to run this fixture. A distribution
binary extracted into ignored worktree scratch can run unprivileged, with every
path/port/process owned by the test. Do not copy the binary into source/evidence.

  mvn -q -DskipTests compile
  python3 deploy/mobile/test_proxy.py --nginx /PATH/TO/NGINX \
    --report target/mobile-proxy/https-playtest.json

The test generates a localhost-only certificate and private test key in a fresh
0700 ignored directory. Only its explicit test context trusts that certificate;
the normal client context must reject it. It starts actual isolated Java TLS game
servers, the loopback gateway and Nginx, exercises normal/edge/regression requests,
then stops only its processes and deletes private fixture files. The sanitized
receipt contains labels/results/source hashes, never tokens, passwords or keys.
Tests include TLS1.2/1.3, health, routing/method/Origin/body limits, authenticated
Sandbox movement/edit/reconnect, City road/treasury and gateway loss. This is a
CLI integration playtest; browser UI does not apply to these native-only routes.

Nginx primary references used for the configuration:
https://nginx.org/en/docs/http/ngx_http_proxy_module.html
https://nginx.org/en/docs/http/ngx_http_core_module.html
https://nginx.org/en/docs/http/ngx_http_ssl_module.html
