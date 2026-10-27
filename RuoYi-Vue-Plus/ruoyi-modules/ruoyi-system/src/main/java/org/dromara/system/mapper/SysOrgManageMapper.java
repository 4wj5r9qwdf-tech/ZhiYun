package org.dromara.system.mapper;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * 部门业务管辖关联 Mapper（管理部门↔被管部门，多对多；与组织树层级无关）。
 * 表 sys_org_manage(manage_org_id, managed_org_id)，无独立实体，纯注解 SQL。
 *
 * @author织云
 */
public interface SysOrgManageMapper {

    /** 查某部门管辖的被管部门ID列表 */
    @Select("SELECT managed_org_id FROM sys_org_manage WHERE manage_org_id = #{manageOrgId} ORDER BY managed_org_id")
    List<Long> selectManagedIds(@Param("manageOrgId") Long manageOrgId);

    /** 清空某部门的管辖关系 */
    @Delete("DELETE FROM sys_org_manage WHERE manage_org_id = #{manageOrgId}")
    int deleteByManageOrgId(@Param("manageOrgId") Long manageOrgId);

    /** 新增一条管辖关系（ignore 重复） */
    @Insert("INSERT INTO sys_org_manage (manage_org_id, managed_org_id, create_time) " +
        "VALUES (#{manageOrgId}, #{managedOrgId}, now()) ON CONFLICT DO NOTHING")
    int insertManage(@Param("manageOrgId") Long manageOrgId, @Param("managedOrgId") Long managedOrgId);

    /** 全量统计：每个管辖方管了多少被管部门 */
    @Select("SELECT manage_org_id AS manageOrgId, COUNT(*) AS cnt FROM sys_org_manage GROUP BY manage_org_id")
    List<Map<String, Object>> selectManagedCounts();

    /** 被管部门是否被引用（删除部门前校验） */
    @Select("SELECT COUNT(*) FROM sys_org_manage WHERE managed_org_id = #{orgId}")
    int countManaged(@Param("orgId") Long orgId);
}
