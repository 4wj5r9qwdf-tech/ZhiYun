package org.dromara.system.mapper;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * 部门业务管辖关联 Mapper（管理部门↔被管部门，多对多；与组织树层级无关）。
 * 表 sys_dept_manage(manage_dept_id, managed_dept_id)，无独立实体，纯注解 SQL。
 *
 * @author织云
 */
public interface SysDeptManageMapper {

    /** 查某部门管辖的被管部门ID列表 */
    @Select("SELECT managed_dept_id FROM sys_dept_manage WHERE manage_dept_id = #{manageDeptId} ORDER BY managed_dept_id")
    List<Long> selectManagedIds(@Param("manageDeptId") Long manageDeptId);

    /** 清空某部门的管辖关系 */
    @Delete("DELETE FROM sys_dept_manage WHERE manage_dept_id = #{manageDeptId}")
    int deleteByManageDeptId(@Param("manageDeptId") Long manageDeptId);

    /** 新增一条管辖关系（ignore 重复） */
    @Insert("INSERT INTO sys_dept_manage (manage_dept_id, managed_dept_id, create_time) " +
        "VALUES (#{manageDeptId}, #{managedDeptId}, now()) ON CONFLICT DO NOTHING")
    int insertManage(@Param("manageDeptId") Long manageDeptId, @Param("managedDeptId") Long managedDeptId);

    /** 全量统计：每个管辖方管了多少被管部门 */
    @Select("SELECT manage_dept_id AS manageDeptId, COUNT(*) AS cnt FROM sys_dept_manage GROUP BY manage_dept_id")
    List<Map<String, Object>> selectManagedCounts();

    /** 被管部门是否被引用（删除部门前校验） */
    @Select("SELECT COUNT(*) FROM sys_dept_manage WHERE managed_dept_id = #{deptId}")
    int countManaged(@Param("deptId") Long deptId);
}
