# AttributeSwapFolia

AttributeSwapFolia is a small Paper plugin that enables Paper's attribute-swapping behavior on Minecraft 1.21.11. It supports Paper, Folia, and Canvas without platform-specific APIs.

Paper normally starts with this setting enabled:

```yaml
unsupported-settings:
  update-equipment-on-player-actions: true
```

On startup, this plugin safely changes that one value to `false` in `config/paper-global.yml`. It creates `paper-global.yml.attributeswap-folia.bak` before its first modification and uses a temporary file plus replacement move to avoid truncating the live configuration.

## Installation

1. Download `AttributeSwapFolia-1.0.0.jar` from the release page.
2. Place it in the server's `plugins` directory.
3. Start the server once. The plugin updates `config/paper-global.yml` and logs that one more restart is required.
4. Restart the server. Paper, Folia, or Canvas now reads the changed setting during startup.

If the setting is already `false`, the plugin does not rewrite the file or notify operators.

## Uninstalling

Remove the plugin JAR from `plugins` and restart the server. Uninstalling does **not** restore the Paper setting automatically.

To restore vanilla/Paper behavior manually, edit `config/paper-global.yml` and set:

```yaml
unsupported-settings:
  update-equipment-on-player-actions: true
```

Restart the server after changing the Paper configuration.

## Development

Requirements: JDK 21 and Maven.

```bash
mvn -B clean verify
```

The build produces `target/AttributeSwapFolia-1.0.0.jar`.

## Clean-room implementation

This is an independent implementation based on externally observable behavior and configuration semantics. It does not contain source code from the original Attribute Swap Fixer JAR.
