package org.dromara.web.controller;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.crypto.digest.BCrypt;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.common.satoken.utils.LoginHelper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 个人中心/工作台/待办/搜索 公共接口。
 * 当前登录人：从 token 解析 userId（超管也走 sys_user admin 行做身份载体）。
 * 待办红点：模块级=该模块下所有功能待办累加；功能级=自己的数；无待办不显示。
 * 用户配置：拖拽排序/图标像素大小/置顶/加入工作台（sys_user_menu_config）。
 *
 * @author 织云ERP
 */
@RequiredArgsConstructor
@RestController
@RequestMapping("/system/profile")
public class ProfileController {

    private final JdbcTemplate jdbcTemplate;

    /**
     * 当前登录人信息（右上角显示真实登录人，不写死）。
     */
    @GetMapping("/info")
    public R<Map<String, Object>> info() {
        Long userId = LoginHelper.getUserId();
        Map<String, Object> row = jdbcTemplate.queryForMap(
            "SELECT user_id, user_name, nick_name, phone_number FROM sys_user WHERE user_id=?", userId);
        return R.ok(row);
    }

    /**
     * 当前用户权限码集合（前端据此过滤功能卡片/模块/路由可见性）。
     * 超管返回通配 "*"（全部可见）；普通用户返回其启用角色的菜单权限码并集。
     */
    @GetMapping("/permissions")
    public R<java.util.Set<String>> permissions() {
        Long userId = LoginHelper.getUserId();
        if (userId == null) {
            return R.fail("未登录");
        }
        if (LoginHelper.isSuperAdmin(userId)) {
            return R.ok(java.util.Set.of("*"));
        }
        return R.ok(new java.util.LinkedHashSet<>(cn.dev33.satoken.stp.StpUtil.getPermissionList()));
    }

    /** 通知按完整接收人编号匹配；公共消息由每个人分别确认已读。 */
    @GetMapping("/messages")
    public R<List<Map<String, Object>>> messages() {
        Long userId = LoginHelper.getUserId();
        // 仅向当前收件人返回公告类别的正文快照，其他消息正文不扩大暴露范围。
        // Return notice snapshots only to the current recipient; do not expose other message bodies.
        return R.ok(jdbcTemplate.queryForList(
            "SELECT m.message_id, m.title, m.message, m.type, m.category, m.path, m.create_time, " +
            "CASE WHEN m.category='notice' THEN m.content ELSE NULL END AS notice_content, " +
            "CASE WHEN r.user_id IS NULL THEN '0' ELSE '1' END AS read_flag " +
            "FROM sys_message m LEFT JOIN sys_message_receipt r ON r.message_id=m.message_id AND r.user_id=? " +
            "WHERE (m.send_user_ids='0' OR ?=ANY(string_to_array(replace(m.send_user_ids,' ',''),','))) " +
            "AND COALESCE(m.message,'') NOT LIKE '%欢迎登录 RuoYi-Vue-Plus 后台管理系统' " +
            "ORDER BY m.create_time DESC,m.message_id DESC LIMIT 50", userId, userId.toString()));
    }

    /** 未读统计使用与消息列表相同的人员范围，不能按发送人或编号子串匹配。 */
    @GetMapping("/messages/unread")
    public R<Long> unread() {
        Long userId = LoginHelper.getUserId();
        Long count = jdbcTemplate.queryForObject(
            "SELECT count(*) FROM sys_message m WHERE " +
            "(m.send_user_ids='0' OR ?=ANY(string_to_array(replace(m.send_user_ids,' ',''),','))) " +
            "AND COALESCE(m.message,'') NOT LIKE '%欢迎登录 RuoYi-Vue-Plus 后台管理系统' " +
            "AND NOT EXISTS (SELECT 1 FROM sys_message_receipt r WHERE r.message_id=m.message_id AND r.user_id=?)",
            Long.class, userId.toString(), userId);
        return R.ok(count == null ? 0L : count);
    }

    /** 只能标记本人收到的消息；其他人的消息不存在于本人的可操作范围。 */
    @PostMapping("/messages/{id}/read")
    public R<Void> readMessage(@PathVariable Long id) {
        Long userId = LoginHelper.getUserId();
        int count = jdbcTemplate.update(
            "INSERT INTO sys_message_receipt(message_id,user_id) " +
            "SELECT m.message_id,? FROM sys_message m WHERE m.message_id=? " +
            "AND (m.send_user_ids='0' OR ?=ANY(string_to_array(replace(m.send_user_ids,' ',''),','))) " +
            "ON CONFLICT (message_id,user_id) DO NOTHING", userId, id, userId.toString());
        if (count == 0) {
            Integer visible = jdbcTemplate.queryForObject("SELECT count(*) FROM sys_message m WHERE m.message_id=? " +
                "AND (m.send_user_ids='0' OR ?=ANY(string_to_array(replace(m.send_user_ids,' ',''),',')))", Integer.class, id, userId.toString());
            if (visible == null || visible == 0) return R.fail("通知不存在或无权查看");
        }
        return R.ok();
    }

    /** 一键已读仅影响当前人员可接收的通知，不改变审批待办。 */
    @PostMapping("/messages/read-all")
    public R<Void> readAllMessages() {
        Long userId = LoginHelper.getUserId();
        jdbcTemplate.update("INSERT INTO sys_message_receipt(message_id,user_id) " +
            "SELECT m.message_id,? FROM sys_message m WHERE " +
            "(m.send_user_ids='0' OR ?=ANY(string_to_array(replace(m.send_user_ids,' ',''),','))) " +
            "ON CONFLICT (message_id,user_id) DO NOTHING", userId, userId.toString());
        return R.ok();
    }

    /**
     * 全局搜索（功能/人员/单据，前端规范 A 节）。
     * keyword 命中：功能名（modules 列表）、人员姓名/工号/手机号（sys_user）、单据号（后期）。
     */
    @GetMapping("/search")
    public R<List<Map<String, Object>>> search(@RequestParam String keyword) {
        List<Map<String, Object>> result = new java.util.ArrayList<>();
        if (keyword == null || keyword.isBlank()) {
            return R.ok(result);
        }
        String kw = "%" + keyword.trim() + "%";
        // 搜人员：姓名/工号/手机号
        List<Map<String, Object>> users = jdbcTemplate.queryForList(
            "SELECT user_id, user_name, nick_name, phone_number AS phone, 'person' AS type " +
            "FROM sys_user WHERE (nick_name LIKE ? OR user_name LIKE ? OR phone_number LIKE ?) " +
            "AND del_flag='0' LIMIT 10", kw, kw, kw);
        result.addAll(users);
        return R.ok(result);
    }

    /**
     * 待办数（模块级累加 + 功能级，无待办=0 前端不显示红点）。
     * 返回：[{module_key, menu_key, count}] count=0 的行前端忽略。
     */
    @GetMapping("/todo/count")
    public R<List<Map<String, Object>>> todoCount() {
        Long userId = LoginHelper.getUserId();
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            "SELECT module_key, menu_key, count(*) AS count " +
            "FROM sys_todo WHERE status='0' AND (user_id IS NULL OR user_id=?) " +
            "GROUP BY module_key, menu_key", userId);
        return R.ok(rows);
    }

    /**
     * 用户功能配置列表（拖拽排序/图标大小/置顶/工作台）。
     */
    @GetMapping("/menu-config")
    public R<List<Map<String, Object>>> menuConfig() {
        Long userId = LoginHelper.getUserId();
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            "SELECT menu_key, sort, card_w, card_h, icon_size, is_top, in_workbench, locked " +
            "FROM sys_user_menu_config WHERE user_id=? ORDER BY is_top DESC, sort", userId);
        return R.ok(rows);
    }

    /**
     * 保存用户功能配置（拖拽排序/卡片宽高自由拉伸/置顶/加入工作台，单个功能）。
     */
    @PutMapping("/menu-config")
    public R<Void> saveMenuConfig(@RequestBody Map<String, Object> body) {
        Long userId = LoginHelper.getUserId();
        String menuKey = (String) body.get("menuKey");
        Integer sort = body.get("sort") == null ? 0 : Integer.valueOf(body.get("sort").toString());
        // 卡片宽高独立（自由拉伸，非等比）；兼容旧 iconSize 字段（沿用为宽）
        Integer cardW = body.get("cardW") != null ? Integer.valueOf(body.get("cardW").toString())
                       : (body.get("iconSize") != null ? Integer.valueOf(body.get("iconSize").toString()) : 160);
        Integer cardH = body.get("cardH") != null ? Integer.valueOf(body.get("cardH").toString()) : 110;
        if (cardW < 120) cardW = 120;
        if (cardW > 480) cardW = 480;
        if (cardH < 100) cardH = 100;
        if (cardH > 320) cardH = 320;
        Integer iconSize = cardW; // 兼容旧列，保持同步
        String isTop = (String) body.getOrDefault("isTop", "0");
        String inWorkbench = (String) body.getOrDefault("inWorkbench", "0");
        String locked = (String) body.getOrDefault("locked", "1");   // 卡片等比锁定

        jdbcTemplate.update(
            "INSERT INTO sys_user_menu_config (user_id, menu_key, sort, card_w, card_h, icon_size, is_top, in_workbench, locked, update_time) " +
            "VALUES (?,?,?,?,?,?,?,?,?,now()) " +
            "ON CONFLICT (user_id, menu_key) DO UPDATE SET " +
            "  sort=EXCLUDED.sort, card_w=EXCLUDED.card_w, card_h=EXCLUDED.card_h, icon_size=EXCLUDED.icon_size, " +
            "  is_top=EXCLUDED.is_top, in_workbench=EXCLUDED.in_workbench, locked=EXCLUDED.locked, update_time=now()",
            userId, menuKey, sort, cardW, cardH, iconSize, isTop, inWorkbench, locked);

        // 同步工作台表
        if ("1".equals(inWorkbench)) {
            jdbcTemplate.update(
                "INSERT INTO sys_user_workbench (user_id, menu_key, sort) VALUES (?,?,?) " +
                "ON CONFLICT (user_id, menu_key) DO UPDATE SET sort=EXCLUDED.sort",
                userId, menuKey, sort);
        } else {
            jdbcTemplate.update("DELETE FROM sys_user_workbench WHERE user_id=? AND menu_key=?", userId, menuKey);
        }
        return R.ok();
    }

    /**
     * 用户工作台功能列表（登录默认首页）。
     */
    @GetMapping("/workbench")
    public R<List<Map<String, Object>>> workbench() {
        Long userId = LoginHelper.getUserId();
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            "SELECT menu_key, sort FROM sys_user_workbench WHERE user_id=? ORDER BY sort", userId);
        return R.ok(rows);
    }

    /**
     * 本人修改密码（右上角个人中心）：校验原密码 → 改为新密码（BCrypt）。
     * body: { oldPassword, newPassword }
     */
    @PutMapping("/password")
    public R<Void> changePassword(@RequestBody Map<String, Object> body) {
        Long userId = LoginHelper.getUserId();
        String oldPwd = str(body.get("oldPassword"));
        String newPwd = str(body.get("newPassword"));
        if (oldPwd == null || oldPwd.isBlank()) return R.fail("请输入原密码");
        if (newPwd == null || newPwd.length() < 6) return R.fail("新密码至少 6 位");
        // 超管密码由 .env 的 SUPER_ADMIN_PASSWORD 控制（登录走配置旁路、不查库），不在此改
        if (LoginHelper.isSuperAdmin()) {
            return R.fail("超级管理员密码由服务器配置文件(.env)控制，请修改 SUPER_ADMIN_PASSWORD 后重启");
        }
        Map<String, Object> row = jdbcTemplate.queryForMap(
            "SELECT password FROM sys_user WHERE user_id=?", userId);
        String stored = str(row.get("password"));
        if (stored == null || !BCrypt.checkpw(oldPwd, stored)) {
            return R.fail("原密码错误");
        }
        jdbcTemplate.update("UPDATE sys_user SET password=?, update_time=now() WHERE user_id=?",
            BCrypt.hashpw(newPwd), userId);
        return R.ok();
    }

    /**
     * 本人修改资料（昵称/手机号/邮箱，右上角个人中心）。
     * body: { nickName, phoneNumber, email }
     */
    @PutMapping
    public R<Void> updateProfile(@RequestBody Map<String, Object> body) {
        Long userId = LoginHelper.getUserId();
        String nickName = str(body.get("nickName"));
        String phone = str(body.get("phoneNumber"));
        String email = str(body.get("email"));
        if (nickName == null || nickName.isBlank()) return R.fail("昵称不能为空");
        if (phone != null && !phone.isBlank() && !phone.matches("\\d{11}")) return R.fail("手机号需为 11 位");
        jdbcTemplate.update(
            "UPDATE sys_user SET nick_name=?, phone_number=?, email=?, update_time=now() WHERE user_id=?",
            nickName, phone == null ? "" : phone, email == null ? "" : email, userId);
        return R.ok();
    }

    /** null 安全取字符串 */
    private String str(Object o) { return o == null ? null : String.valueOf(o); }
}
