package org.dromara.ai.snailai;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

/** Reads the ERP-owned Skills directly from Docs/Skills without copying them into SnailAI storage. */
@Slf4j
@Component
public class SnailAiSkillCatalog {

    private final Path root;

    public SnailAiSkillCatalog(@Value("${snail-ai.erp.skills-directory:Docs/Skills}") String directory) {
        this.root = Path.of(directory).toAbsolutePath().normalize();
    }

    public List<SkillEntry> list() {
        if (!Files.isDirectory(root)) {
            return List.of();
        }
        try (var paths = Files.walk(root)) {
            return paths.filter(path -> path.getFileName() != null
                    && "SKILL.md".equals(path.getFileName().toString())
                    && Files.isRegularFile(path))
                .sorted(Comparator.comparing(Path::toString))
                .map(this::readEntry)
                .toList();
        } catch (IOException e) {
            log.warn("Unable to scan ERP Skills directory: {}", root);
            return List.of();
        }
    }

    public String read(String skillName) {
        if (skillName == null || skillName.isBlank()) {
            return "请提供 Skill 名称。";
        }
        return list().stream()
            .filter(skill -> skill.name().equals(skillName.trim()))
            .findFirst()
            .map(SkillEntry::content)
            .orElse("未找到该 Skill。请先从可用 Skill 列表中选择名称。");
    }

    public String prompt() {
        List<SkillEntry> entries = list();
        if (entries.isEmpty()) {
            return "\n\n## 织云 ERP Skills\n当前没有可读取的 ERP Skill 文件。";
        }
        StringBuilder prompt = new StringBuilder();
        prompt.append("\n\n## 织云 ERP Skills\n")
            .append("下面是本次 ERP 会话可用的业务技能。遇到匹配的业务问题时，先调用 read_zhiyun_skill 读取对应技能。\n")
            .append("只通过当前 ERP MCP 工具开展业务；工具目录已按当前员工权限过滤。写操作会暂停并等待员工在页面确认。\n")
            .append("不要把未执行的操作说成已完成，也不要调用普通 HTTP、SQL、命令行或其他未授权工具。\n\n");
        for (SkillEntry entry : entries) {
            prompt.append("- **").append(entry.name()).append("**：")
                .append(entry.description()).append('\n');
        }
        return prompt.toString();
    }

    private SkillEntry readEntry(Path file) {
        try {
            String content = Files.readString(file, StandardCharsets.UTF_8);
            Metadata metadata = parseMetadata(content, file.getParent().getFileName().toString());
            return new SkillEntry(metadata.name(), metadata.description(), content);
        } catch (IOException e) {
            log.warn("Unable to read ERP Skill file: {}", file);
            return new SkillEntry(file.getParent().getFileName().toString(), "技能文件当前不可读取", "");
        }
    }

    private Metadata parseMetadata(String content, String fallbackName) {
        String name = fallbackName;
        String description = "";
        String[] lines = content.split("\\R", -1);
        if (lines.length > 0 && "---".equals(lines[0].trim())) {
            for (int i = 1; i < lines.length && !"---".equals(lines[i].trim()); i++) {
                String line = lines[i].trim();
                if (line.startsWith("name:")) {
                    name = unquote(line.substring("name:".length()).trim());
                } else if (line.startsWith("description:")) {
                    description = unquote(line.substring("description:".length()).trim());
                }
            }
        }
        if (description.isBlank()) {
            for (String line : lines) {
                String text = line.trim();
                if (text.isBlank() || text.startsWith("#") || text.startsWith("---")
                    || text.startsWith(">") || text.startsWith("|")) {
                    continue;
                }
                description = text.length() > 240 ? text.substring(0, 240) : text;
                break;
            }
        }
        return new Metadata(name, description);
    }

    private String unquote(String value) {
        if (value.length() >= 2 && ((value.startsWith("\"") && value.endsWith("\""))
            || (value.startsWith("'") && value.endsWith("'")))) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }

    public record SkillEntry(String name, String description, String content) {
    }

    private record Metadata(String name, String description) {
    }
}
