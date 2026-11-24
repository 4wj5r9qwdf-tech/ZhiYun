/*
 * 文件用途：验证主数据 MCP Map 适配器的字符串 ID 与完整更新契约。
 * 文件版本：1.0.0
 * 功能目的：阻止 DTO Long 字段发布数值型 MCP ID，并修复建筑 ID 字符串无法到达旧控制器的问题。
 * 设计思路：直接测试 DTO 转换和 Map 校验，不依赖数据库。
 * 实现方法：覆盖组织/岗位字符串 ID、父级 null 根语义、建筑状态及错误输入。
 * 依赖与引用：MasterDataMcpTools.java；McpInputValidator.java；Docs/API/组织架构API.md；Docs/API/岗位管理API.md；Docs/API/建筑档案API.md
 * 当前责任人：JY
 * 最近修改人员：Codex（智能体）
 * AI辅助工具：Codex（智能体）
 * 最近修改时间：2026-11-21
 * 修改标记：ZY-ERP-20261121-主数据MCP对象ID契约修复；验证组织岗位与建筑ID字符串
 * 最近测试时间：2026-11-21
 * 测试人员：Codex（智能体）
 * 测试状态：通过（6 项主数据 MCP 输入契约测试）
 */
package org.dromara.web.mcp;

import org.dromara.common.core.exception.ServiceException;
import org.dromara.system.domain.bo.SysOrgBo;
import org.dromara.system.domain.bo.SysPostBo;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class MasterDataMcpToolsTest {

    private final MasterDataMcpTools tools = new MasterDataMcpTools(null, null, null, null, null);

    @Test
    void organizationUpdateAcceptsStringIdsAndExplicitRootParent() {
        Map<String, Object> body = orgUpdateBody();
        SysOrgBo result = tools.orgBody(body, true, false);
        assertEquals(10001L, result.getOrgId());
        assertNull(result.getParentId());
    }

    @Test
    void organizationRejectsNumericIdsAndMissingFullReplacementFields() {
        Map<String, Object> numericId = orgUpdateBody();
        numericId.put("buildingId", 30001L);
        assertThrows(ServiceException.class, () -> tools.orgBody(numericId, true, false));

        Map<String, Object> incomplete = orgUpdateBody();
        incomplete.remove("parentId");
        assertThrows(ServiceException.class, () -> tools.orgBody(incomplete, true, false));
    }

    @Test
    void positionIdsAreValidatedAsStringsBeforeDtoConversion() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("postId", "20001");
        body.put("orgId", "10001");
        body.put("belongOrgId", null);
        body.put("postCode", "CUTTER");
        body.put("postName", "裁剪员");
        body.put("postCategory", "生产");
        body.put("postSort", 10);
        body.put("status", "0");
        body.put("remark", "");
        SysPostBo result = tools.postBody(body, true, false);
        assertEquals(20001L, result.getPostId());
        assertEquals(10001L, result.getOrgId());

        body.put("orgId", 10001L);
        assertThrows(ServiceException.class, () -> tools.postBody(body, true, false));
    }

    @Test
    void buildingStringIdIsAdaptedToLegacyControllerNumberAndKeepsThreeStatuses() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("plantId", "30001");
        body.put("buildingType", "厂房");
        body.put("plantCode", "BLD001");
        body.put("plantName", "一号厂房");
        body.put("floorInfo", "3层");
        body.put("landArea", 1200);
        body.put("buildingArea", 3600);
        body.put("address", "示例路1号");
        body.put("builtYear", 2018);
        body.put("status", "2");
        body.put("remark", "维修");

        Map<String, Object> normalized = tools.buildingBody(body, true);
        assertInstanceOf(Number.class, normalized.get("plantId"));
        assertEquals(30001L, ((Number) normalized.get("plantId")).longValue());

        body.put("plantId", 30001L);
        assertThrows(ServiceException.class, () -> tools.buildingBody(body, true));
    }

    @Test
    void companyInputRequiresStringFileIdsAndRejectsUnknownFields() {
        Map<String, Object> create = new LinkedHashMap<>();
        create.put("companyName", "织云示例公司");
        create.put("logoOssId", "20001");
        create.put("loginBgOssId", null);
        Map<String, Object> normalized = tools.companyBody(create, false);
        assertEquals(20001L, normalized.get("logoOssId"));
        assertNull(normalized.get("loginBgOssId"));

        create.put("logoOssId", 20001L);
        assertThrows(ServiceException.class, () -> tools.companyBody(create, false));

        create.remove("logoOssId");
        create.put("unexpected", "ignored by old Map handler");
        assertThrows(ServiceException.class, () -> tools.companyBody(create, false));
    }

    @Test
    void companyUpdateRequiresFullReplacementAndExplicitEnumValues() {
        Map<String, Object> update = new LinkedHashMap<>();
        update.put("companyId", "10001");
        update.put("companyName", "织云示例公司");
        update.put("logoOssId", null);
        update.put("loginBgOssId", "20002");
        update.put("slogan", "示例宣传语");
        update.put("loginBgMode", "B");
        update.put("nameColor", "#FFFFFF");
        update.put("theme", "industrial-blue");
        update.put("status", "0");
        Map<String, Object> normalized = tools.companyBody(update, true);
        assertEquals(10001L, normalized.get("companyId"));
        assertNull(normalized.get("logoOssId"));

        update.remove("theme");
        assertThrows(ServiceException.class, () -> tools.companyBody(update, true));
        update.put("theme", "industrial-blue");
        update.put("companyId", 10001L);
        assertThrows(ServiceException.class, () -> tools.companyBody(update, true));
    }

    private Map<String, Object> orgUpdateBody() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("orgId", "10001");
        body.put("parentId", null);
        body.put("orgName", "一车间");
        body.put("orgCategory", "workshop");
        body.put("buildingId", "30001");
        body.put("location", "一号厂房/1层");
        body.put("orderNum", 10);
        body.put("leader", "20001");
        body.put("phone", "021-55550000");
        body.put("email", "workshop@example.com");
        body.put("status", "0");
        return body;
    }
}
