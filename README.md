# Teleguard

A simple Telegram bot for protecting from spam Clojurians ;)
Inspired by [Teleward](https://github.com/igrishaev/teleward).
Not tested in production, please don't use it.

## Building

```bash
./deploy/build.sh
```

## Running

```bash
java -jar target/teleguard.jar --config deploy/config-example.edn
```