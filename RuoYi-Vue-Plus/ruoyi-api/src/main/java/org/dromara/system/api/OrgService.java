package org.dromara.system.api;

import org.dromara.system.api.domain.OrgDTO;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * 通用 部门服务
 *
 * @author Lion Li
 */
public interface OrgService {

    /**
     * 通过部门ID查询部门名称
     *
     * @param orgIds 部门ID串逗号分隔
     * @return 部门名称串逗号分隔
     */
    String selectOrgNameByIds(String orgIds);

    /**
     * 根据部门ID查询部门负责人
     *
     * @param orgId 部门ID，用于指定需要查询的部门
     * @return 返回该部门的负责人ID
     */
    Long selectOrgLeaderById(Long orgId);

    /**
     * 查询部门
     *
     * @return 部门列表
     */
    List<OrgDTO> selectOrgsByList();

    /**
     * 根据部门 ID 列表查询部门名称映射关系
     *
     * @param orgIds 部门 ID 列表
     * @return Map，其中 key 为部门 ID，value 为对应的部门名称
     */
    Map<Long, String> selectOrgNamesByIds(Collection<Long> orgIds);

}
