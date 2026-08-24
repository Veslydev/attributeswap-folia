package dev.vesly.attributeswapfolia;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermission;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Safely applies the one Paper setting needed to enable attribute swapping. */
public final class PaperGlobalConfigPatcher {
    private static final String CONFIG_DIRECTORY = "config";
    private static final String CONFIG_FILE_NAME = "paper-global.yml";
    private static final String BACKUP_SUFFIX = ".attributeswap-folia.bak";

    private static final Pattern TARGET_KEY = Pattern.compile(
            "^[\\t ]*update-equipment-on-player-actions[\\t ]*:.*$");
    private static final Pattern TARGET_SETTING = Pattern.compile(
            "^(?<indent>[\\t ]*)update-equipment-on-player-actions(?<separator>[\\t ]*:[\\t ]*)"
                    + "(?<value>true|false)(?<suffix>[\\t ]*(?:#.*)?)$");
    private static final Pattern UNSUPPORTED_SETTINGS = Pattern.compile(
            "^(?<indent>[\\t ]*)unsupported-settings[\\t ]*:[\\t ]*(?:#.*)?$");

    public PatchResult patch(Path serverRoot) {
        Path configPath = serverRoot.resolve(CONFIG_DIRECTORY).resolve(CONFIG_FILE_NAME).normalize();
        if (!Files.isRegularFile(configPath)) {
            return new PatchResult(PatchStatus.MISSING_FILE, configPath, "Configuration file does not exist.");
        }

        final String original;
        try {
            original = decodeUtf8(Files.readAllBytes(configPath));
        } catch (IOException exception) {
            return new PatchResult(PatchStatus.WRITE_FAILED, configPath, exception.getMessage());
        }

        TransformationResult transformation = transform(original);
        if (transformation.status() != PatchStatus.MODIFIED) {
            return new PatchResult(transformation.status(), configPath, transformation.detail());
        }

        Path backupPath = configPath.resolveSibling(configPath.getFileName() + BACKUP_SUFFIX);
        try {
            createBackupIfAbsent(configPath, backupPath);
        } catch (IOException exception) {
            return new PatchResult(PatchStatus.BACKUP_FAILED, configPath, exception.getMessage());
        }

        try {
            safelyReplace(configPath, transformation.contents());
            return new PatchResult(PatchStatus.MODIFIED, configPath, "Configuration updated.");
        } catch (IOException exception) {
            return new PatchResult(PatchStatus.WRITE_FAILED, configPath, exception.getMessage());
        }
    }

    static TransformationResult transform(String contents) {
        List<String> lines = splitLines(contents);
        List<Integer> targetLines = new ArrayList<>();

        for (int index = 0; index < lines.size(); index++) {
            if (TARGET_KEY.matcher(withoutLineEnding(lines.get(index))).matches()) {
                targetLines.add(index);
            }
        }

        if (targetLines.isEmpty()) {
            return new TransformationResult(PatchStatus.MISSING_SETTING, contents, "Target setting was not found.");
        }
        if (targetLines.size() > 1) {
            return new TransformationResult(PatchStatus.DUPLICATE_SETTING, contents, "Multiple active target settings were found.");
        }

        int targetIndex = targetLines.getFirst();
        String targetLine = withoutLineEnding(lines.get(targetIndex));
        Matcher targetSetting = TARGET_SETTING.matcher(targetLine);
        if (!targetSetting.matches()) {
            return new TransformationResult(
                    PatchStatus.INVALID_SETTING_VALUE, contents, "Target setting must be the boolean true or false.");
        }
        if (!isInsideUnsupportedSettings(lines, targetIndex)) {
            return new TransformationResult(
                    PatchStatus.MISSING_SETTING, contents, "Target setting is not under unsupported-settings.");
        }
        if (targetSetting.group("value").equals("false")) {
            return new TransformationResult(PatchStatus.ALREADY_ENABLED, contents, "Target setting is already false.");
        }

        String replacement = targetLine.substring(0, targetSetting.start("value"))
                + "false"
                + targetLine.substring(targetSetting.end("value"));
        lines.set(targetIndex, replacement + lineEnding(lines.get(targetIndex)));
        return new TransformationResult(PatchStatus.MODIFIED, String.join("", lines), "Target setting changed to false.");
    }

    private static boolean isInsideUnsupportedSettings(List<String> lines, int targetIndex) {
        int sectionIndentation = -1;

        for (int index = 0; index <= targetIndex; index++) {
            String line = withoutLineEnding(lines.get(index));
            if (isBlankOrComment(line)) {
                continue;
            }

            int indentation = indentationOf(line);
            if (sectionIndentation >= 0 && indentation <= sectionIndentation) {
                sectionIndentation = -1;
            }

            Matcher section = UNSUPPORTED_SETTINGS.matcher(line);
            if (section.matches()) {
                sectionIndentation = indentation;
                continue;
            }

            if (index == targetIndex) {
                return sectionIndentation >= 0 && indentation > sectionIndentation;
            }
        }

        return false;
    }

    private static void createBackupIfAbsent(Path configPath, Path backupPath) throws IOException {
        if (Files.exists(backupPath)) {
            if (!Files.isRegularFile(backupPath)) {
                throw new IOException("Backup path exists but is not a regular file: " + backupPath);
            }
            return;
        }

        Files.copy(configPath, backupPath);
    }

    private static void safelyReplace(Path configPath, String contents) throws IOException {
        Path tempPath = Files.createTempFile(configPath.getParent(), configPath.getFileName().toString(), ".tmp");
        try {
            preservePosixPermissions(configPath, tempPath);
            byte[] bytes = contents.getBytes(StandardCharsets.UTF_8);
            try (FileChannel channel = FileChannel.open(tempPath, StandardOpenOption.WRITE)) {
                ByteBuffer buffer = ByteBuffer.wrap(bytes);
                while (buffer.hasRemaining()) {
                    channel.write(buffer);
                }
                channel.force(true);
            }

            try {
                Files.move(tempPath, configPath, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(tempPath, configPath, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(tempPath);
        }
    }

    private static void preservePosixPermissions(Path source, Path target) throws IOException {
        try {
            Set<PosixFilePermission> permissions = Files.getPosixFilePermissions(source);
            Files.setPosixFilePermissions(target, permissions);
        } catch (UnsupportedOperationException ignored) {
            // Not every server filesystem exposes POSIX permissions.
        }
    }

    private static String decodeUtf8(byte[] bytes) throws CharacterCodingException {
        return StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString();
    }

    private static List<String> splitLines(String contents) {
        List<String> lines = new ArrayList<>();
        int start = 0;
        for (int index = 0; index < contents.length(); index++) {
            if (contents.charAt(index) == '\n') {
                lines.add(contents.substring(start, index + 1));
                start = index + 1;
            }
        }
        if (start < contents.length()) {
            lines.add(contents.substring(start));
        }
        return lines;
    }

    private static String withoutLineEnding(String line) {
        if (line.endsWith("\r\n")) {
            return line.substring(0, line.length() - 2);
        }
        if (line.endsWith("\n")) {
            return line.substring(0, line.length() - 1);
        }
        return line;
    }

    private static String lineEnding(String line) {
        if (line.endsWith("\r\n")) {
            return "\r\n";
        }
        if (line.endsWith("\n")) {
            return "\n";
        }
        return "";
    }

    private static boolean isBlankOrComment(String line) {
        String trimmed = line.trim();
        return trimmed.isEmpty() || trimmed.startsWith("#");
    }

    private static int indentationOf(String line) {
        int indentation = 0;
        while (indentation < line.length()) {
            char character = line.charAt(indentation);
            if (character != ' ' && character != '\t') {
                break;
            }
            indentation++;
        }
        return indentation;
    }

    public enum PatchStatus {
        MODIFIED,
        ALREADY_ENABLED,
        MISSING_FILE,
        MISSING_SETTING,
        DUPLICATE_SETTING,
        INVALID_SETTING_VALUE,
        BACKUP_FAILED,
        WRITE_FAILED
    }

    public record PatchResult(PatchStatus status, Path configPath, String detail) {}

    record TransformationResult(PatchStatus status, String contents, String detail) {}
}
