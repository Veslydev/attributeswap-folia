package dev.vesly.attributeswapfolia;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PaperGlobalConfigPatcherTest {
    @Test
    void changesTrueToFalse() {
        String input = "unsupported-settings:\n  update-equipment-on-player-actions: true\n";

        PaperGlobalConfigPatcher.TransformationResult result = PaperGlobalConfigPatcher.transform(input);

        assertEquals(PaperGlobalConfigPatcher.PatchStatus.MODIFIED, result.status());
        assertEquals("unsupported-settings:\n  update-equipment-on-player-actions: false\n", result.contents());
    }

    @Test
    void leavesAlreadyFalseUnchanged() {
        String input = "unsupported-settings:\n  update-equipment-on-player-actions: false\n";

        PaperGlobalConfigPatcher.TransformationResult result = PaperGlobalConfigPatcher.transform(input);

        assertEquals(PaperGlobalConfigPatcher.PatchStatus.ALREADY_ENABLED, result.status());
        assertEquals(input, result.contents());
    }

    @Test
    void leavesUnrelatedTrueValuesUnchanged() {
        String input = "feature-enabled: true\nunsupported-settings:\n  update-equipment-on-player-actions: true\n";

        PaperGlobalConfigPatcher.TransformationResult result = PaperGlobalConfigPatcher.transform(input);

        assertEquals(PaperGlobalConfigPatcher.PatchStatus.MODIFIED, result.status());
        assertEquals(
                "feature-enabled: true\nunsupported-settings:\n  update-equipment-on-player-actions: false\n",
                result.contents());
    }

    @Test
    void preservesIndentationAndWindowsLineEndings() {
        String input = "unsupported-settings:\r\n    update-equipment-on-player-actions: true\r\n";

        PaperGlobalConfigPatcher.TransformationResult result = PaperGlobalConfigPatcher.transform(input);

        assertEquals(PaperGlobalConfigPatcher.PatchStatus.MODIFIED, result.status());
        assertEquals("unsupported-settings:\r\n    update-equipment-on-player-actions: false\r\n", result.contents());
    }

    @Test
    void preservesTrailingComment() {
        String input = "unsupported-settings:\n  update-equipment-on-player-actions: true # preserve this\n";

        PaperGlobalConfigPatcher.TransformationResult result = PaperGlobalConfigPatcher.transform(input);

        assertEquals(PaperGlobalConfigPatcher.PatchStatus.MODIFIED, result.status());
        assertEquals(
                "unsupported-settings:\n  update-equipment-on-player-actions: false # preserve this\n", result.contents());
    }

    @Test
    void doesNotModifyWhenTargetSettingIsMissing() {
        String input = "unsupported-settings:\n  other-setting: true\n";

        PaperGlobalConfigPatcher.TransformationResult result = PaperGlobalConfigPatcher.transform(input);

        assertEquals(PaperGlobalConfigPatcher.PatchStatus.MISSING_SETTING, result.status());
        assertEquals(input, result.contents());
    }

    @Test
    void doesNotModifyDuplicateTargetSettings() {
        String input = "unsupported-settings:\n"
                + "  update-equipment-on-player-actions: true\n"
                + "  update-equipment-on-player-actions: false\n";

        PaperGlobalConfigPatcher.TransformationResult result = PaperGlobalConfigPatcher.transform(input);

        assertEquals(PaperGlobalConfigPatcher.PatchStatus.DUPLICATE_SETTING, result.status());
        assertEquals(input, result.contents());
    }

    @Test
    void doesNotModifySimilarlyNamedSetting() {
        String input = "unsupported-settings:\n  update-equipment-on-player-actions-extra: true\n";

        PaperGlobalConfigPatcher.TransformationResult result = PaperGlobalConfigPatcher.transform(input);

        assertEquals(PaperGlobalConfigPatcher.PatchStatus.MISSING_SETTING, result.status());
        assertEquals(input, result.contents());
    }

    @Test
    void transformationIsIdempotent() {
        String input = "unsupported-settings:\n  update-equipment-on-player-actions: true\n";

        PaperGlobalConfigPatcher.TransformationResult first = PaperGlobalConfigPatcher.transform(input);
        PaperGlobalConfigPatcher.TransformationResult second = PaperGlobalConfigPatcher.transform(first.contents());

        assertEquals(PaperGlobalConfigPatcher.PatchStatus.MODIFIED, first.status());
        assertEquals(PaperGlobalConfigPatcher.PatchStatus.ALREADY_ENABLED, second.status());
        assertEquals(first.contents(), second.contents());
    }

    @Test
    void createsBackupBeforeSafelyReplacingConfiguration(@TempDir Path serverRoot) throws IOException {
        Path configDirectory = Files.createDirectories(serverRoot.resolve("config"));
        Path configFile = configDirectory.resolve("paper-global.yml");
        String original = "unsupported-settings:\n  update-equipment-on-player-actions: true\n";
        Files.writeString(configFile, original);

        PaperGlobalConfigPatcher.PatchResult result = new PaperGlobalConfigPatcher().patch(serverRoot);

        assertEquals(PaperGlobalConfigPatcher.PatchStatus.MODIFIED, result.status());
        assertEquals(
                "unsupported-settings:\n  update-equipment-on-player-actions: false\n", Files.readString(configFile));
        assertEquals(original, Files.readString(configDirectory.resolve("paper-global.yml.attributeswap-folia.bak")));
    }

    @Test
    void preservesExistingBackup(@TempDir Path serverRoot) throws IOException {
        Path configDirectory = Files.createDirectories(serverRoot.resolve("config"));
        Path configFile = configDirectory.resolve("paper-global.yml");
        Path backupFile = configDirectory.resolve("paper-global.yml.attributeswap-folia.bak");
        Files.writeString(configFile, "unsupported-settings:\n  update-equipment-on-player-actions: true\n");
        Files.writeString(backupFile, "known-good backup\n");

        PaperGlobalConfigPatcher.PatchResult result = new PaperGlobalConfigPatcher().patch(serverRoot);

        assertEquals(PaperGlobalConfigPatcher.PatchStatus.MODIFIED, result.status());
        assertEquals("known-good backup\n", Files.readString(backupFile));
    }
}
