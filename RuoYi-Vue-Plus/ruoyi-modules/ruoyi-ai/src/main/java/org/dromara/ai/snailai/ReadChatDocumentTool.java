/*
 * 文件用途：向单次智能体对话提供读取用户本轮附件和提交知识候选草稿的工具定义。
 * 文件版本：1.0.0
 * 功能目的：向单次智能体对话提供读取用户本轮附件和提交知识候选草稿的工具定义。
 * 设计思路：工具不暴露服务密钥或员工 ID；通过服务端签发的短期 grant 签名访问本机文件接口。
 * 实现方法：读取工具校验文件 ID 后请求解析服务；草稿工具只提交候选内容并返回人工确认状态。
 * 关键约束：未授权的文件 ID 拒绝读取；草稿不会直接进入正式知识库。
 * 依赖与引用：Docs/AI/织云AI集成总体规划.md；sql/SnailAI聊天文件归属_20261118.sql；sql/SnailAI知识草稿_20261118.sql
 * 初始开发人员：JY（织云项目自研内容）
 * 初始开发时间：待追溯（不以补录时间代替原始开发时间）
 * 当前责任人：JY（项目维护责任）
 * 最近修改人员：JY
 * AI辅助工具：Codex（智能体）
 * 最近修改时间：2026-11-20
 * 修改标记：ZY-ERP-20261120-注释整理；补齐文件说明和业务逻辑注释，不改变运行行为
 * 最近测试时间：本次整理未执行功能测试
 * 测试人员：本次整理未执行
 * 测试状态：待功能回归；历史测试记录待追溯
 * 测试范围与记录位置：本次为注释/元信息整理；见 Docs/功能任务/ZY-ERP-20261120-既有功能注释整理.md
 */
package org.dromara.ai.snailai;

import org.springframework.ai.tool.annotation.Tool;
import com.aizuda.snail.ai.common.util.JsonUtil;
import java.util.Map;
import org.springframework.ai.tool.annotation.ToolParam;
import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.nio.file.Path;

/** 面向单次原生对话的文件解析工具。授权只存在于服务端，不暴露为模型参数。 */
public class ReadChatDocumentTool {
    private final String grant;
    private final String host;
    private final int port;
    private final String prefix;
    private final Path keyFile;

    public ReadChatDocumentTool(String grant, String host, int port, String prefix, String keyFile) {
        this.grant = grant;
        this.keyFile = Path.of(keyFile);
        this.host = host;
        this.port = port;
        this.prefix = prefix == null ? "" : prefix.replaceAll("^/+|/+$", "");
    }

    /** 读取工具仅接收本轮文件清单 ID；服务端 grant 将该 ID 与上传者/智能体/会话绑定。 */
    @Tool(name = "read_chat_document", description = "读取本次会话中用户上传的文件。PDF、Office 和 Markdown 会按需调用 Docling 解析，文本和表格以数据形式返回。")
    public String read(@ToolParam(description = "本次会话文件清单中的文件 ID") String fileId) {
        try {
            if (fileId == null || !fileId.matches("[0-9a-fA-F-]{36}")) return "文件编号无效";
            // 当前仅在同一机器的回环接口传送短期授权；跨主机部署需要另配安全传输。
            if (!InetAddress.getByName(host).isLoopbackAddress()) return "文件服务未配置为本机安全连接";
            URI uri = new URI("http", null, host, port, "/" + (prefix.isEmpty() ? "" : prefix + "/")
                + "zhiyun/chat-document-tools/" + fileId + "/analysis", null, null);
            HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
            HttpRequest request = HttpRequest.newBuilder(uri).timeout(Duration.ofMinutes(5))
                .header("X-Chat-Document-Session", grant)
                .header("X-Chat-Document-Signature", ChatDocumentSignature.sign(keyFile, grant)).POST(HttpRequest.BodyPublishers.noBody()).build();
            // 非成功响应只返回可操作的通用提示，不把本机路径、认证头或内部异常带进模型上下文。
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            return response.statusCode() == 200 ? response.body() : "文件读取失败或授权已过期，请告知用户重试。";
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return "文件读取已中断";
        } catch (Exception ex) {
            return "文件读取失败，请检查文件服务及 Docling 是否可用。";
        }
    }
    /** 创建待人工确认的知识候选；工具结果始终说明尚未正式入库。 */
    @Tool(name = "draft_knowledge_entry", description = "仅在用户明确要求写入知识库时，提交待确认知识草稿。此操作不会直接入库。")
    public String draft(@ToolParam(description = "智能体已绑定的知识库 ID") Long ragId,
                        @ToolParam(description = "知识条目标题") String title,
                        @ToolParam(description = "待确认的知识正文") String content,
                        @ToolParam(description = "内容来源说明") String source) {
        try {
            if (ragId == null || title == null || content == null || source == null) return "草稿参数缺失";
            if (!InetAddress.getByName(host).isLoopbackAddress()) return "知识服务未配置为本机安全连接";
            URI uri = new URI("http", null, host, port, "/" + (prefix.isEmpty() ? "" : prefix + "/")
                + "zhiyun/chat-document-tools/knowledge-draft", null, null);
            // 用 JSON 序列化请求正文，避免标题或正文中的特殊字符改变签名桥接请求结构。
            String body = JsonUtil.toJsonString(Map.of("ragId", ragId, "title", title, "content", content, "source", source));
            HttpRequest request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(30)).header("Content-Type", "application/json")
                .header("X-Chat-Document-Session", grant).header("X-Chat-Document-Signature", ChatDocumentSignature.sign(keyFile, grant))
                .POST(HttpRequest.BodyPublishers.ofString(body)).build();
            HttpResponse<String> response = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()
                .send(request, HttpResponse.BodyHandlers.ofString());
            return response.statusCode() == 200 ? response.body() : "草稿创建失败，请检查权限和知识库绑定；未写入知识库。";
        } catch (InterruptedException ex) { Thread.currentThread().interrupt(); return "草稿创建已中断"; }
        catch (Exception ex) { return "草稿创建失败，未写入知识库。"; }
    }

}
