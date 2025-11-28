# AezaMobile

## Configuration

Create a `local.properties` file (not checked into VCS) to override API settings if needed:

```
api.baseUrl=https://check-host.net/
api.username=
api.password=
```

- Check-Host API calls are anonymous; leave all values blank unless you intentionally use another service behind the same client.
- `api.username` and `api.password` enable HTTP Basic authentication when both are non-empty.
