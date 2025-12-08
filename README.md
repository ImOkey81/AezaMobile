# AezaMobile

## Configuration

Create a `local.properties` file (not checked into VCS) to override API settings if needed. By default the app targets the local backend reachable from the Android emulator at `http://10.0.2.2:8080/`:

```
api.baseUrl=http://10.0.2.2:8080/
api.username=
api.password=
```

- Leave `api.baseUrl` empty to use the default local backend URL. Any legacy `https://check-host.net/` value is ignored and will be replaced with the local backend.
- `api.username` and `api.password` enable HTTP Basic authentication when both are non-empty.
