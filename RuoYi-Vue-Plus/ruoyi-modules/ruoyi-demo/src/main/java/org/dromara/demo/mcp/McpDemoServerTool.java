/*
 * 文件用途：承载织云 ERP 对上游框架“McpDemoServerTool”能力的项目扩展或适配。
 * 文件版本：1.0.0
 * 功能目的：承载织云 ERP 对上游框架“McpDemoServerTool”能力的项目扩展或适配。
 * 设计思路：保留上游框架结构和原版权，仅在必要扩展点接入织云组织、权限、审批、审计或 AI 契约。
 * 实现方法：沿用既有框架接口和服务，在本项目改动处完成适配；具体影响以关联任务和代码为准。
 * 关键约束：必须保留原作者和许可证声明；JY 仅登记实际项目改造责任。
 * 依赖与引用：Docs/规范/开发规范.md；Docs/规范/多人协作与任务交接.md；Docs/项目记录/功能实现状态.md
 * 初始开发人员：上游原作者/许可证：保留源文件既有声明；本项目改造责任人：JY
 * 初始开发时间：待追溯（不以补录时间代替原始开发时间）
 * 当前责任人：JY（项目维护责任；框架文件仅限项目改造范围）
 * 最近修改人员：JY
 * AI辅助工具：Codex（智能体）
 * 最近修改时间：2026-11-20
 * 修改标记：ZY-ERP-20261120-注释整理；补齐文件说明和业务逻辑注释，不改变运行行为
 * 最近测试时间：本次整理未执行功能测试
 * 测试人员：本次整理未执行
 * 测试状态：待功能回归；历史测试记录待追溯
 * 测试范围与记录位置：本次为注释/元信息整理；见 Docs/功能任务/ZY-ERP-20261120-既有功能注释整理.md
 */
package org.dromara.demo.mcp;

import org.springframework.context.annotation.Profile;
import org.springframework.ai.mcp.annotation.McpResource;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * MCP Server 演示工具，对外提供业务数据。
 * <p>
 * 仅激活 `mcp-demo` Spring Profile 时，本类中的 `@McpTool` 与 `@McpResource` 才会被
 * Spring AI MCP 注解扫描器注册到当前应用的 MCP Server，默认通过同端口 `/mcp` 暴露。
 * <p>
 * 可被外部 MCP Client 调用的示例能力：
 * <ul>
 *     <li>`demo_get_data`：根据 id 查询一条演示数据</li>
 *     <li>`demo_list_data`：按 limit 查询演示数据列表</li>
 *     <li>`demo://summary`：读取演示模块摘要资源</li>
 * </ul>
 * 真实业务中不要在 MCP 工具里直接访问 Mapper，应继续调用业务 Service，
 * 并保留权限、租户、脱敏、审计和幂等规则。
 *
 * @author Lion Li
 */
@Component
@Profile("mcp-demo")
public class McpDemoServerTool {

    /**
     * 查询一条演示数据。
     *
     * @param id 业务主键
     * @return 演示数据
     */
    @McpTool(name = "demo_get_data", description = "根据业务主键查询演示数据")
    public McpDemoData getData(@McpToolParam(description = "业务主键") String id) {
        return new McpDemoData(id, "演示数据-" + id, "ruoyi-demo", 100, LocalDateTime.now());
    }

    /**
     * 查询演示数据列表。
     *
     * @param limit 返回条数
     * @return 演示数据列表
     */
    @McpTool(name = "demo_list_data", description = "查询演示数据列表")
    public List<McpDemoData> listData(@McpToolParam(description = "可选返回条数；省略或小于1时默认3，最大20", required = false) Integer limit) {
        int size = limit == null || limit < 1 ? 3 : Math.min(limit, 20);
        return java.util.stream.IntStream.rangeClosed(1, size)
            .mapToObj(index -> new McpDemoData(String.valueOf(index), "演示数据-" + index, "ruoyi-demo", index * 10, LocalDateTime.now()))
            .toList();
    }

    /**
     * 读取演示资源。
     *
     * @return 演示资源内容
     */
    @McpResource(
        name = "demo_summary",
        uri = "demo://summary",
        description = "演示模块摘要资源",
        mimeType = "text/plain"
    )
    public String summary() {
        return "ruoyi-demo MCP resource summary";
    }

    /**
     * MCP 演示数据。
     *
     * @author Lion Li
     */
    public record McpDemoData(
        String id,
        String name,
        String source,
        Integer amount,
        LocalDateTime syncTime
    ) {
    }

}
