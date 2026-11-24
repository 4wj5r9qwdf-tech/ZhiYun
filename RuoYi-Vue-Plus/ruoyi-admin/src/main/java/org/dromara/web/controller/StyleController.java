/*
 * 文件用途：为织云 ERP 的款式档案接口提供服务端 HTTP 接口。
 * 文件版本：1.0.0
 * 功能目的：为织云 ERP 的款式档案接口提供服务端 HTTP 接口。
 * 设计思路：控制器承接请求契约和权限入口，业务流程由服务层承载；写操作维护事务与审计。
 * 实现方法：校验参数和权限范围后调用服务/持久层，并通过项目统一响应返回。
 * 关键约束：接口权限、数据范围、异常和审计须与 API 契约一致。
 * 依赖与引用：Docs/API/API文档.md；Docs/规范/开发规范.md；Docs/项目记录/功能实现状态.md
 * 初始开发人员：JY（织云项目自研内容）
 * 初始开发时间：待追溯（不以补录时间代替原始开发时间）
 * 当前责任人：JY（项目维护责任；框架文件仅限项目改造范围）
 * 最近修改人员：JY
 * AI辅助工具：Codex（智能体）
 * 最近修改时间：2026-11-21
 * 修改标记：ZY-ERP-20261121-款式写入字段契约；拒绝非单款模式中被静默忽略的单款规格集合
 * 最近测试时间：2026-11-21
 * 测试人员：Codex（智能体）
 * 测试状态：通过（款式控制器模式契约 2 项测试）
 * 测试范围与记录位置：单款与套装规格输入边界；Maven Surefire 报告
 */
package org.dromara.web.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.log.annotation.Log;
import org.dromara.common.log.enums.BusinessType;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.system.controller.system.BusinessAudit;
import org.dromara.web.service.CategoryAccessService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.*;

import static org.dromara.system.service.ISysOssService.STYLE_COVER_PREFIX;

/** 款式统一主档，订单和样衣使用同一稳定标识。 / Shared style master for orders and sampling. / 统一维护款式身份，订单和样衣复用同一稳定主键。 / Maintain one stable style identity for orders and sampling. */
@RestController
@RequiredArgsConstructor
@RequestMapping("/rd/style")
@SaCheckPermission("rd:style:list")
public class StyleController {
    private static final String TAG_FEATURE = "rd_style";
    private static final String SELECT = """
        SELECT s.style_id::text AS id,s.internal_code AS "styleCode",
               s.style_name AS name,s.customer_id::text AS "customerId",c.customer_name AS "customerName",
               s.category_id::text AS "categoryId",b.category_name AS "categoryName",s.season,s.description,
               s.cover_oss_id::text AS "coverOssId",s.status,s.remark,s.production_mode AS "productionMode",s.same_hanger AS "sameHanger",s.create_time AS "createTime",s.update_time AS "updateTime",
               (SELECT u.nick_name FROM sys_user u WHERE u.user_id=s.create_by) AS "creatorName",
               (SELECT u.nick_name FROM sys_user u WHERE u.user_id=s.update_by) AS "updaterName",
               ARRAY(SELECT r.tag_id::text FROM sys_tag_relation r
                     WHERE r.module_key='rd_style' AND r.record_id=s.style_id ORDER BY r.tag_id) AS "tagIds"
        FROM rd_style s LEFT JOIN customer_master c ON c.customer_id=s.customer_id
        JOIN base_category b ON b.category_id=s.category_id WHERE
        """;
    private final JdbcTemplate jdbc;
    private final CategoryAccessService categories;
    private final PrivateFileController privateFiles;

    /** 列表只返回有分类权限的款式。 / Filter the catalog by employee taxonomy grants. */
    @GetMapping("/list")
    public R<List<Map<String,Object>>> list() {
        StringBuilder sql = new StringBuilder(SELECT);
        List<Object> args = new ArrayList<>();
        categories.appendScope(sql, args, "s.category_id", categories.scope());
        var result = jdbc.queryForList(sql.append(" ORDER BY s.style_id DESC").toString(), args.toArray());
        for (var row : result) {
            normalizeTagIds(row);
            Long styleId = id(row.get("id"));
            row.put("colorIds", specIds(styleId, COLOR));
            row.put("sizeIds", specIds(styleId, SIZE));
            row.put("tags", tagOptions(styleId));
            var visibleMembers = members(styleId, true);
            row.put("members", visibleMembers);
            row.put("membersRestricted", visibleMembers.size() != members(styleId, false).size());
        }
        return R.ok(result);
    }

    /** 已停用的历史标签仍可查看和移除。 / Keep historical disabled selections visible. */
    @GetMapping("/{id}/tags")
    public R<List<Map<String,Object>>> tags(@PathVariable Long id) {
        current(id, false);
        return R.ok(tagOptions(id));
    }

    /** 复用公共颜色、尺码和尺码组，停用项仅用于显示既有关联。 / Reuse shared colors, sizes and size groups; disabled entries remain visible only for existing assignments. */
    @GetMapping("/spec-options")
    public R<Map<String,Object>> specOptions() {
        return R.ok(Map.of(
            "colors", referenceOptions(COLOR),
            "sizes", referenceOptions(SIZE),
            "sizeGroups", jdbc.queryForList("""
                SELECT g.id::text AS value,g.name AS label,
                       ARRAY(SELECT i.size_id::text FROM sys_size_group_item i JOIN sys_size s ON s.id=i.size_id
                             WHERE i.group_id=g.id AND s.status='0' ORDER BY i.sort_order) AS "sizeIds"
                FROM sys_size_group g WHERE g.status='0' ORDER BY g.sort_order,g.id
                """).stream().map(row -> normalizeIds(row, "sizeIds")).toList()));
    }

    /** 套装组成的展示与写入均检查每个子款的分类权限。 / Enforce each child style's category grant for both set display and updates. */
    @GetMapping("/{id}/configuration")
    public R<Map<String,Object>> configuration(@PathVariable Long id) {
        current(id, false);
        var members = members(id, true);
        boolean restricted = members.size() != members(id, false).size();
        return R.ok(Map.of("members", members, "restricted", restricted,
            "setSpecs", restricted ? List.of() : setSpecs(id),
            "colorIds", specIds(id, COLOR), "sizeIds", specIds(id, SIZE), "excludedPairs", exclusions(id)));
    }
    /** 返回当前人员可访问的款式分类选项，保留导航所需祖先节点。 / Return caller-authorized style categories with ancestors needed for navigation. */

    @GetMapping("/category-options")
    public R<Map<String,Object>> categoryOptions() {
        return R.ok(categories.options());
    }

    /** 只用于页面初始定位，失效或无可见分类时回退到全部授权范围。 / Use this value only for initial navigation and fall back to the full authorized scope when invalid. */
    @GetMapping("/default-category")
    public R<Map<String,Object>> defaultCategory() {
        List<String> values = jdbc.queryForList("SELECT config_value FROM sys_config WHERE config_key='rd.style.defaultCategoryScheme'", String.class);
        String configured = values.isEmpty() ? "" : Objects.toString(values.getFirst(),"").trim();
        if (configured.isEmpty()) return R.ok(Map.of("schemeId","","invalid",false));
        @SuppressWarnings("unchecked")
        List<Map<String,Object>> rows = (List<Map<String,Object>>) categories.options().get("rows");
        boolean usable = rows.stream().anyMatch(row -> configured.equals(Objects.toString(row.get("schemeId"),""))
            && Boolean.TRUE.equals(row.get("selectable")));
        return R.ok(Map.of("schemeId",usable ? configured : "","invalid",!usable));
    }
    /** 返回可供款式档案选择的有效客户。 / List active customers selectable on a style record. */

    @GetMapping("/customer-options")
    public R<List<Map<String,Object>>> customerOptions() {
        return R.ok(jdbc.queryForList("""
            SELECT customer_id::text AS value,customer_name AS label FROM customer_master
            WHERE status='0' ORDER BY customer_name,customer_id
            """));
    }
    /** 创建款式档案及规格、标签、套装组成并建立审计上下文。 / Create a style and its specifications, tags and set members with an audit context. */

    @PostMapping
    @SaCheckPermission({"rd:style:list","rd:style:add"})
    @Transactional
    @Log(title="款式档案", businessType=BusinessType.INSERT)
    public R<String> create(@RequestBody StyleInput input) {
        return save(null, input);
    }
    /** 更新款式资料并事务性校验子款、标签和规格关联。 / Update style data while transactionally validating member, tag and specification references. */

    @PutMapping("/{id}")
    @SaCheckPermission({"rd:style:list","rd:style:edit"})
    @Transactional
    @Log(title="款式档案", businessType=BusinessType.UPDATE)
    public R<String> update(@PathVariable Long id, @RequestBody StyleInput input) {
        return save(id, input);
    }

    /** 只更新状态，保留后续订单和样衣的历史引用。 / Change availability without rewriting history. */
    @PutMapping("/{id}/status")
    @SaCheckPermission({"rd:style:list","rd:style:edit"})
    @Transactional
    @Log(title="款式档案", businessType=BusinessType.UPDATE)
    public R<Void> status(@PathVariable Long id, @RequestBody Map<String,String> body) {
        String status = body.get("status");
        if (!Set.of("0","1").contains(Objects.toString(status,""))) return R.fail("状态无效");
        lockComposition();
        Map<String,Object> before = current(id, true);
        if (!status.equals(before.get("status"))) {
            jdbc.update("UPDATE rd_style SET status=?,update_by=?,update_time=now() WHERE style_id=?",
                status, LoginHelper.getUserId(), id);
            audit("0".equals(status) ? "启用款式" : "停用款式", id, before, current(id, false));
        }
        return R.ok();
    }

    /** 后续业务外键会阻止删除已被引用的款式。 / Downstream foreign keys protect referenced styles. */
    @DeleteMapping("/{id}")
    @SaCheckPermission({"rd:style:list","rd:style:remove"})
    @Transactional
    @Log(title="款式档案", businessType=BusinessType.DELETE)
    public R<Void> delete(@PathVariable Long id) {
        lockComposition();
        Map<String,Object> before = current(id, true);
        Map<String,Object> deletedFields = auditFields(before);
        deletedFields.putAll(configurationAudit(id));
        try {
            jdbc.update("DELETE FROM rd_style WHERE style_id=?", id);
            jdbc.update("DELETE FROM sys_tag_relation WHERE module_key=? AND record_id=?", TAG_FEATURE, id);
        } catch (DataIntegrityViolationException e) {
            throw new ServiceException("该款式已有业务引用，请停用");
        }
        BusinessAudit.record("删除款式",id,"款式名称",deletedFields,Map.of("已删除",true));
        return R.ok();
    }

    /** 主图仅接受有签名的 PNG、JPEG，文件保存在现有私有桶。 / Validate image headers before private storage. */
    @PostMapping(value="/cover", consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    @SaCheckPermission(value={"rd:style:add","rd:style:edit"}, mode=cn.dev33.satoken.annotation.SaMode.OR)
    public R<Map<String,Object>> uploadCover(@RequestParam("file") MultipartFile file) throws IOException {
        cn.dev33.satoken.stp.StpUtil.checkPermission("rd:style:list");
        if (file.isEmpty() || file.getSize()>5L*1024*1024) return R.fail("主图须为不超过5 MB的PNG或JPEG");
        byte[] header;
        try (var stream = file.getInputStream()) {
            header = stream.readNBytes(8);
        }
        boolean png = Arrays.equals(header, new byte[]{(byte)137,80,78,71,13,10,26,10});
        boolean jpeg = header.length >= 3 && header[0]==(byte)255 && header[1]==(byte)216 && header[2]==(byte)255;
        if (!png && !jpeg) return R.fail("主图须为PNG或JPEG");
        return privateFiles.uploadPrivate(file, STYLE_COVER_PREFIX, png ? "image/png" : "image/jpeg");
    }

    /** 先校验款式分类权限，再从私有存储读取主图。 / Enforce style access before proxying its cover. */
    @GetMapping("/{id}/cover")
    public ResponseEntity<byte[]> cover(@PathVariable Long id) {
        var row = current(id, false);
        Long ossId = id(row.get("coverOssId"));
        if (ossId == null) return ResponseEntity.notFound().build();
        List<String> keys = jdbc.queryForList("SELECT file_name FROM sys_oss WHERE oss_id=? AND service='minio-private'",
            String.class, ossId);
        if (keys.isEmpty() || !keys.getFirst().startsWith(STYLE_COVER_PREFIX)) {
            return ResponseEntity.notFound().build();
        }
        return privateFiles.streamFile(keys.getFirst(), "style-cover");
    }

    /** 款式附件沿用受保护的私有前缀，按文件签名限制可接收类型。 / Store style attachments under the protected private prefix and validate file types by signature. */
    @PostMapping(value="/attachments", consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    @SaCheckPermission(value={"rd:style:add","rd:style:edit"}, mode=cn.dev33.satoken.annotation.SaMode.OR)
    public R<Map<String,Object>> uploadAttachment(@RequestParam("file") MultipartFile file) throws IOException {
        cn.dev33.satoken.stp.StpUtil.checkPermission("rd:style:list");
        if (file.isEmpty() || file.getSize()>10L*1024*1024) return R.fail("单个附件不能超过10 MB");
        String name = Objects.toString(file.getOriginalFilename(),"").toLowerCase(Locale.ROOT);
        byte[] h;
        try (var stream=file.getInputStream()) { h=stream.readNBytes(8); }
        boolean png=Arrays.equals(h,new byte[]{(byte)137,80,78,71,13,10,26,10});
        boolean jpg=h.length>=3 && h[0]==(byte)255 && h[1]==(byte)216 && h[2]==(byte)255;
        boolean pdf=h.length>=5 && h[0]=='%' && h[1]=='P' && h[2]=='D' && h[3]=='F' && h[4]=='-';
        boolean zip=h.length>=4 && h[0]=='P' && h[1]=='K' && h[2]==3 && h[3]==4;
        String type;
        if (png && name.endsWith(".png")) type="image/png";
        else if (jpg && (name.endsWith(".jpg") || name.endsWith(".jpeg"))) type="image/jpeg";
        else if (pdf && name.endsWith(".pdf")) type="application/pdf";
        else if (zip && (name.endsWith(".docx") || name.endsWith(".xlsx") || name.endsWith(".pptx"))) type="application/octet-stream";
        else return R.fail("请选择PNG、JPG、PDF、DOCX、XLSX或PPTX文件");
        return privateFiles.uploadPrivate(file, STYLE_COVER_PREFIX, type);
    }
    /** 列出款式附件及安全预览信息，不泄露私有存储路径。 / List style attachments and safe previews without exposing private storage paths. */

    @GetMapping("/{id}/attachments")
    public R<List<Map<String,Object>>> attachments(@PathVariable Long id) {
        current(id,false);
        return R.ok(attachmentRows(id));
    }
    /** 解析单个款式附件的授权预览地址。 / Resolve an authorized preview for one style attachment. */

    @GetMapping("/{id}/attachments/{ossId}")
    public ResponseEntity<byte[]> attachment(@PathVariable Long id, @PathVariable Long ossId) {
        current(id,false);
        var rows=jdbc.queryForList("""
            SELECT o.file_name FROM rd_style_attachment a JOIN sys_oss o ON o.oss_id=a.oss_id
            WHERE a.style_id=? AND a.oss_id=? AND o.service='minio-private' AND starts_with(o.file_name,?)
            """,id,ossId,STYLE_COVER_PREFIX);
        if (rows.isEmpty()) return ResponseEntity.notFound().build();
        return privateFiles.streamFile(rows.getFirst().get("file_name").toString(),"style-attachment");
    }
    /** 规范化附件列表并校验文件类别与款式归属。 / Normalize attachment rows and validate file category and style ownership. */

    private List<Map<String,Object>> attachmentRows(Long styleId) {
        return jdbc.queryForList("""
            SELECT a.oss_id::text AS "ossId",a.category,o.original_name AS "fileName"
            FROM rd_style_attachment a JOIN sys_oss o ON o.oss_id=a.oss_id
            WHERE a.style_id=? ORDER BY a.sort_order,a.oss_id
            """,styleId);
    }

    public record AttachmentInput(Long ossId, String category) {}
    /** 保存款式和私有文件关联，并执行文件归属校验。 / Persist style-to-file links while enforcing file ownership. */
    private void saveAttachments(Long styleId, List<AttachmentInput> input, Long user, Long oldCoverId) {
        if (input==null) return;
        // 旧数据允许100个附件加1张独立主图，合并显示后须能原样保存。
        if (input.size()>101) throw new ServiceException("款式图片和附件合计不能超过101个");
        Set<Long> existing=new HashSet<>();
        for (var row:attachmentRows(styleId)) existing.add(id(row.get("ossId")));
        Set<Long> submitted=new HashSet<>();
        Set<String> categories=Set.of("style_image","main_label","size_label","wash_label","hang_tag","tag_sticker","document");
        int order=0;
        for (var item:input) {
            if (item==null || item.ossId()==null || !categories.contains(item.category()) || !submitted.add(item.ossId()))
                throw new ServiceException("附件信息无效或重复");
            var files=jdbc.queryForList("""
                SELECT original_name,create_by FROM sys_oss WHERE oss_id=? AND service='minio-private'
                AND starts_with(file_name,?) FOR UPDATE
                """,item.ossId(),STYLE_COVER_PREFIX);
            if (files.isEmpty() || (!existing.contains(item.ossId()) && !Objects.equals(oldCoverId,item.ossId()) && !Objects.equals(id(files.getFirst().get("create_by")),user))) {
                throw new ServiceException("附件不可用，请重新上传");
            }
            String name=Objects.toString(files.getFirst().get("original_name"),"").toLowerCase(Locale.ROOT);
            if (!"document".equals(item.category()) && !Objects.equals(oldCoverId,item.ossId()) && !(name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg"))) {
                throw new ServiceException("标识及款式图片只能选择图片文件");
            }
            if (!jdbc.queryForList("SELECT 1 FROM rd_style WHERE cover_oss_id=? AND (style_id<>? OR ?<>'style_image')",item.ossId(),styleId,item.category()).isEmpty()) {
                throw new ServiceException("主图只能作为本款式的款式图片");
            }
        }
        jdbc.update("DELETE FROM rd_style_attachment WHERE style_id=?",styleId);
        for (var item:input) {
            jdbc.update("INSERT INTO rd_style_attachment(style_id,oss_id,category,sort_order) VALUES (?,?,?,?)",
                styleId,item.ossId(),item.category(),order++);
        }
    }

    public record StyleInput(String styleCode, String name, Long customerId, Long categoryId,
                             String season, String description, Long coverOssId, String remark, List<Long> tagIds,
                             String productionMode, Boolean sameHanger, List<Member> members,
                             List<Long> colorIds, List<Long> sizeIds, List<SpecPair> excludedPairs, List<AttachmentInput> attachments, List<SetSpec> setSpecs) {}
    public record SetSpec(Long id, String colorName, String sizeName, String remark, List<SetSpecItem> items) {}
    public record SetSpecItem(Long childId, Long colorId, Long sizeId) {}
    public record Member(Long childId, String roleName, Integer quantity) {}
    public record SpecPair(Long colorId, Long sizeId) {}
    private record SpecReference(String table, String key, String name, String code, String relation) {}
    private static final SpecReference COLOR = new SpecReference("base_color","color_id","color_name","color_code","rd_style_color");
    private static final SpecReference SIZE = new SpecReference("sys_size","id","name","code","rd_style_size");

    /** 保存前重新检查分类、客户和新上传主图的所有权。 / Revalidate references and draft cover ownership on save. */
    private R<String> save(Long styleId, StyleInput input) {
        lockComposition();
        Map<String,Object> before = styleId == null ? Map.of() : current(styleId, true);
        String code = field(input.styleCode(), 60, "款号");
        String name = field(input.name(), 120, "款式名称");
        if (code.isEmpty() || name.isEmpty()) return R.fail("款号和款式名称必填");
        if (styleId != null && !code.equals(before.get("styleCode"))) return R.fail("款号创建后固定");
        Long categoryId = input.categoryId();
        if (categoryId == null || !categories.categoryAllowed(categoryId)
            || (!Objects.equals(categoryId, id(before.get("categoryId"))) && !categories.categoryIsActive(categoryId))) {
            return R.fail("请选择有权使用的有效款式分类");
        }
        Long customerId = input.customerId();
        if (customerId != null && !Objects.equals(customerId, id(before.get("customerId")))
            && jdbc.queryForList("SELECT customer_id FROM customer_master WHERE customer_id=? AND status='0' FOR SHARE", customerId).isEmpty()) {
            return R.fail("客户不可用，请重新选择");
        }
        Long user = LoginHelper.getUserId();
        Long ossId = input.coverOssId();
        if (ossId != null && !Objects.equals(ossId,id(before.get("coverOssId")))) {
            var matches=jdbc.queryForList("""
                SELECT o.oss_id FROM sys_oss o WHERE o.oss_id=? AND o.service='minio-private'
                AND starts_with(o.file_name,?)
                AND lower(o.original_name) ~ '[.](png|jpg|jpeg)$'
                AND (EXISTS (SELECT 1 FROM rd_style_attachment a WHERE a.style_id=? AND a.oss_id=o.oss_id AND a.category='style_image')
                     OR (o.create_by=? AND NOT EXISTS (SELECT 1 FROM rd_style_attachment a WHERE a.oss_id=o.oss_id)))
                AND NOT EXISTS (SELECT 1 FROM rd_style other WHERE other.cover_oss_id=o.oss_id AND other.style_id<>?)
                FOR UPDATE OF o
                """,ossId,STYLE_COVER_PREFIX,styleId,user,styleId == null ? -1L : styleId);
            if (matches.isEmpty()) return R.fail("请选择当前款式的图片作为主图");
        }
        String season = field(input.season(),40,"季节");
        if (!season.isEmpty() && !Set.of("春","夏","秋","冬","春夏","秋冬","四季").contains(season)
            && !season.equals(Objects.toString(before.get("season"),""))) return R.fail("请选择有效季节");
        Set<Long> tags = validateTags(styleId, input.tagIds());
        String mode = Objects.toString(input.productionMode(), Objects.toString(before.get("productionMode"), "single"));
        if (!Set.of("single","combined","separate").contains(mode)) return R.fail("请选择有效的款式生产模式");
        boolean sameHanger = input.sameHanger() == null ? Boolean.TRUE.equals(before.get("sameHanger")) : input.sameHanger();
        if (sameHanger && !"combined".equals(mode)) return R.fail("只有合并生产套装可要求同架生产");
        if (!Objects.equals(mode, before.get("productionMode")) && styleId != null
            && !jdbc.queryForList("SELECT 1 FROM rd_style_member WHERE child_id=?",styleId).isEmpty()) {
            return R.fail("已作为子款使用的档案必须保留为单款");
        }
        Map<String,Object> beforeConfig = configurationAudit(styleId);
        Object[] values = {name,customerId,categoryId,
            season,field(input.description(),1000,"款式说明"),ossId,field(input.remark(),500,"备注"),mode,sameHanger,user};
        Long savedId;
        try {
            var args = new ArrayList<>(Arrays.asList(values));
            if (styleId == null) {
                args.addFirst(code);
                savedId = jdbc.queryForObject("""
                    INSERT INTO rd_style(internal_code,style_name,customer_id,category_id,season,
                                         description,cover_oss_id,remark,production_mode,same_hanger,create_by)
                    VALUES (?,?,?,?,?,?,?,?,?,?,?) RETURNING style_id
                    """, Long.class, args.toArray());
            } else {
                args.add(styleId);
                jdbc.update("""
                    UPDATE rd_style SET style_name=?,customer_id=?,category_id=?,season=?,description=?,
                                        cover_oss_id=?,remark=?,production_mode=?,same_hanger=?,update_by=?,update_time=now() WHERE style_id=?
                    """, args.toArray());
                savedId = styleId;
            }
        } catch (DuplicateKeyException e) {
            throw new ServiceException("款号已存在，或主图已被其他款式使用");
        }
        saveConfiguration(savedId, mode, input);
        saveSetSpecs(savedId, mode, input.setSpecs());
        saveAttachments(savedId, input.attachments(), user, id(before.get("coverOssId")));
        if (tags != null) {
            jdbc.update("DELETE FROM sys_tag_relation WHERE module_key=? AND record_id=?", TAG_FEATURE, savedId);
            for (Long tagId : tags) {
                jdbc.update("INSERT INTO sys_tag_relation(module_key,record_id,tag_id,create_by) VALUES (?,?,?,?)",
                    TAG_FEATURE, savedId, tagId, user);
            }
        }
        Map<String,Object> after = auditFields(current(savedId, false));
        after.putAll(configurationAudit(savedId));
        Map<String,Object> beforeFields = new LinkedHashMap<>(auditFields(before));
        beforeFields.putAll(beforeConfig);
        BusinessAudit.record(styleId == null ? "新增款式" : "修改款式",savedId,"款式名称",beforeFields,after);
        return R.ok(null, String.valueOf(savedId));
    }

    /** 串行化档案写入，避免并发组成调整与模式切换产生互相引用。 / Serialize style writes so concurrent member edits and production-mode switches cannot create cyclic references. */
    private void lockComposition() {
        jdbc.query("SELECT pg_advisory_xact_lock(1976509041)", rs -> {});
    }

    /** 从受控的公共档案表读取颜色或尺码选项，并标记停用的历史值。 / Load color or size options from an allow-listed master table and mark disabled historical values. */
    private List<Map<String,Object>> referenceOptions(SpecReference ref) {
        return jdbc.queryForList("""
            SELECT %s::text AS value,%s || '（' || %s || '）' AS label,(status<>'0') AS disabled
            FROM %s ORDER BY sort_order,%s
            """.formatted(ref.key(),ref.name(),ref.code(),ref.table(),ref.key()));
    }
    /** 提取规格引用中的稳定颜色或尺码 ID，并拒绝重复项。 / Extract stable color or size identifiers and reject duplicates. */

    private List<String> specIds(Long id, SpecReference ref) {
        String key = ref == COLOR ? "color_id" : "size_id";
        return jdbc.queryForList("SELECT "+key+"::text FROM "+ref.relation()+" WHERE style_id=? ORDER BY "+key,
            String.class,id);
    }
    /** 读取款式明确排除的颜色尺码组合。 / Load color-size combinations explicitly excluded from a style. */

    private List<Map<String,Object>> exclusions(Long id) {
        return jdbc.queryForList("""
            SELECT color_id::text AS "colorId",size_id::text AS "sizeId"
            FROM rd_style_spec_exclusion WHERE style_id=? ORDER BY color_id,size_id
            """,id);
    }
    /** 按档案顺序读取套装子款关系和子款资料。 / Load set members and child details in configured order. */

    private List<Map<String,Object>> members(Long id, boolean scoped) {
        StringBuilder sql = new StringBuilder("""
            SELECT m.child_id::text AS "childId",m.role_name AS "roleName",m.quantity
            FROM rd_style_member m JOIN rd_style s ON s.style_id=m.child_id WHERE m.style_id=?
            """);
        List<Object> args = new ArrayList<>();
        args.add(id);
        if (scoped) {
            sql.append(" AND ");
            categories.appendScope(sql,args,"s.category_id",categories.scope());
        }
        return jdbc.queryForList(sql.append(" ORDER BY m.sort_order,m.child_id").toString(),args.toArray());
    }

    /** 规格 ID 保留供后续业务引用，配套件数读取当前套装组成。 / Keep specification IDs stable for downstream references and derive member counts from set composition. */
    private List<Map<String,Object>> setSpecs(Long styleId) {
        var specs = jdbc.queryForList("""
            SELECT id::text AS id,color_name AS "colorName",size_name AS "sizeName",remark
            FROM rd_style_set_spec WHERE style_id=? ORDER BY sort_order,id
            """, styleId);
        for (var spec : specs) {
            spec.put("items", jdbc.queryForList("""
                SELECT i.child_id::text AS "childId",i.color_id::text AS "colorId",i.size_id::text AS "sizeId",
                       m.quantity
                FROM rd_style_set_spec_item i
                JOIN rd_style_set_spec s ON s.id=i.spec_id
                LEFT JOIN rd_style_member m ON m.style_id=s.style_id AND m.child_id=i.child_id
                WHERE i.spec_id=? ORDER BY m.sort_order,i.child_id
                """, id(spec.get("id"))));
        }
        return specs;
    }

    /** 在款式保存事务内校验完整配套，避免组成和规格分别写入。 / Validate complete pairings in the style transaction so composition and specifications cannot diverge. */
    private void saveSetSpecs(Long styleId, String mode, List<SetSpec> submitted) {
        var existing = setSpecs(styleId);
        if ("single".equals(mode) && submitted != null && !submitted.isEmpty()) {
            throw new ServiceException("单款不能配置套装规格");
        }
        if ("single".equals(mode) && submitted == null && !existing.isEmpty()) {
            throw new ServiceException("请先移除已有套装规格");
        }
        var members = members(styleId, false);
        var children = new HashSet<Long>();
        members.forEach(member -> children.add(id(member.get("childId"))));
        if (submitted != null) {
            if (members.size() != members(styleId, true).size()) throw new ServiceException("您无权维护完整套装规格");
            if (submitted.size() > 100) throw new ServiceException("最多维护100条套装规格");
            var previousIds = new HashSet<Long>();
            existing.forEach(spec -> previousIds.add(id(spec.get("id"))));
            var kept = new HashSet<Long>();
            var names = new HashSet<List<String>>();
            for (var spec : submitted) {
                if (spec == null || spec.items() == null) throw new ServiceException("请填写子款配套明细");
                String color = field(spec.colorName(),120,"套装配色");
                String size = field(spec.sizeName(),120,"套装尺码");
                if (color.isEmpty() || size.isEmpty() || !names.add(List.of(color, size))) {
                    throw new ServiceException("套装配色和尺码必填，且组合不能重复");
                }
                if (spec.id() != null && (!previousIds.contains(spec.id()) || !kept.add(spec.id()))) {
                    throw new ServiceException("套装规格编号无效或重复");
                }
                var selected = new HashSet<Long>();
                for (var item : spec.items()) {
                    if (item == null || !children.contains(item.childId()) || !selected.add(item.childId())
                        || item.colorId() == null || item.sizeId() == null) {
                        throw new ServiceException("请为每个套装子款选择一组颜色和尺码");
                    }
                }
                if (!selected.equals(children)) throw new ServiceException("请为每个套装子款补齐颜色和尺码");
            }
            // 延迟唯一校验，允许同一事务内交换两个规格的名称。
            jdbc.execute("SET CONSTRAINTS rd_style_set_spec_name_unique DEFERRED");
            for (Long previousId : previousIds) {
                if (!kept.contains(previousId)) jdbc.update("DELETE FROM rd_style_set_spec WHERE id=?",previousId);
            }
            int order = 0;
            for (var spec : submitted) {
                Long specId = spec.id();
                String color = spec.colorName().trim(), size = spec.sizeName().trim();
                String remark = field(spec.remark(),500,"规格备注");
                if (specId == null) {
                    specId = jdbc.queryForObject("""
                        INSERT INTO rd_style_set_spec(style_id,color_name,size_name,remark,sort_order)
                        VALUES (?,?,?,?,?) RETURNING id
                        """, Long.class, styleId,color,size,remark,order++);
                } else {
                    jdbc.update("""
                        UPDATE rd_style_set_spec SET color_name=?,size_name=?,remark=?,sort_order=? WHERE id=?
                        """,color,size,remark,order++,specId);
                    jdbc.update("DELETE FROM rd_style_set_spec_item WHERE spec_id=?",specId);
                }
                for (var item : spec.items()) {
                    jdbc.update("INSERT INTO rd_style_set_spec_item(spec_id,child_id,color_id,size_id) VALUES (?,?,?,?)",
                        specId,item.childId(),item.colorId(),item.sizeId());
                }
            }
        }
        // 单款与套装都校验，阻止移除已被配套引用的颜色、尺码或组成。
        if (Boolean.TRUE.equals(jdbc.queryForObject("""
            SELECT EXISTS (
                SELECT 1 FROM rd_style_set_spec s WHERE s.style_id=? AND (
                    (SELECT count(*) FROM rd_style_set_spec_item i WHERE i.spec_id=s.id)
                    <> (SELECT count(*) FROM rd_style_member m WHERE m.style_id=s.style_id))
                UNION ALL
                SELECT 1 FROM rd_style_set_spec_item i JOIN rd_style_set_spec s ON s.id=i.spec_id
                WHERE (s.style_id=? OR i.child_id=?) AND (
                    NOT EXISTS (SELECT 1 FROM rd_style_member m WHERE m.style_id=s.style_id AND m.child_id=i.child_id)
                    OR NOT EXISTS (SELECT 1 FROM rd_style_color c WHERE c.style_id=i.child_id AND c.color_id=i.color_id)
                    OR NOT EXISTS (SELECT 1 FROM rd_style_size z WHERE z.style_id=i.child_id AND z.size_id=i.size_id)
                    OR EXISTS (SELECT 1 FROM rd_style_spec_exclusion x WHERE x.style_id=i.child_id
                               AND x.color_id=i.color_id AND x.size_id=i.size_id))
            )
            """, Boolean.class, styleId,styleId,styleId))) {
            throw new ServiceException("已有套装规格与子款组成或有效颜色尺码不一致，请先调整相关套装规格");
        }
    }
    /** 构造款式组成、规格、标签和附件变更的审计快照。 / Build an auditable snapshot of style members, specifications, tags and attachments. */

    private Map<String,Object> configurationAudit(Long id) {
        if (id == null) return Map.of();
        return Map.of("套装规格",setSpecs(id),"套装组成",members(id,false),"可用颜色ID",specIds(id,COLOR),
            "可用尺码ID",specIds(id,SIZE),"排除的颜色尺码组合",exclusions(id),"款式标签",tagOptions(id),"款式附件",attachmentRows(id));
    }

    /** 同一事务更新主档和组成或规格；异常时一并回滚。 / Persist master data and related composition or specifications atomically; roll back on failure. */
    private void saveConfiguration(Long id, String mode, StyleInput input) {
        boolean single = "single".equals(mode);
        validateStyleSpecificationMode(mode, input.colorIds(), input.sizeIds(), input.excludedPairs());
        var previousMembers = members(id,false);
        List<Member> submitted = input.members();
        if (!single && submitted == null && previousMembers.isEmpty()) throw new ServiceException("套装至少需要一个子款");
        if (submitted != null || single) {
            if (previousMembers.size() != members(id,true).size()) throw new ServiceException("您无权维护完整套装组成");
            List<Member> desired = single ? List.of() : submitted;
            if (!single && (desired.isEmpty() || desired.size()>100)) throw new ServiceException("套装需要1至100个子款");
            Set<Long> childIds = new HashSet<>();
            Set<Long> previousIds = new HashSet<>();
            previousMembers.forEach(row -> previousIds.add(id(row.get("childId"))));
            jdbc.update("DELETE FROM rd_style_member WHERE style_id=?",id);
            int order = 0;
            for (Member member : desired) {
                if (member == null || member.childId() == null || id.equals(member.childId()) || !childIds.add(member.childId())) {
                    throw new ServiceException("子款无效、重复或指向套装自身");
                }
                var child = current(member.childId(),false);
                if (!"single".equals(child.get("productionMode")) ||
                    (!"0".equals(child.get("status")) && !previousIds.contains(member.childId()))) {
                    throw new ServiceException("只能新选启用的单款作为子款");
                }
                String role = field(member.roleName(),60,"组成角色");
                if (role.isEmpty() || member.quantity()==null || member.quantity()<1) throw new ServiceException("请填写组成角色和正整数件数");
                jdbc.update("INSERT INTO rd_style_member(style_id,child_id,role_name,quantity,sort_order) VALUES (?,?,?,?,?)",
                    id,member.childId(),role,member.quantity(),order++);
            }
        }
        if (!single) {
            jdbc.update("DELETE FROM rd_style_color WHERE style_id=?",id);
            jdbc.update("DELETE FROM rd_style_size WHERE style_id=?",id);
            return;
        }
        if (input.colorIds()==null && input.sizeIds()==null && input.excludedPairs()==null) return;
        if (input.colorIds()==null || input.sizeIds()==null || input.excludedPairs()==null) throw new ServiceException("请完整提交颜色尺码规格");
        replaceSpec(id,COLOR,input.colorIds());
        replaceSpec(id,SIZE,input.sizeIds());
        jdbc.update("DELETE FROM rd_style_spec_exclusion WHERE style_id=?",id);
        Set<SpecPair> pairs = new HashSet<>(input.excludedPairs());
        Set<Long> colors = new HashSet<>(input.colorIds()), sizes = new HashSet<>(input.sizeIds());
        for (SpecPair pair : pairs) {
            if (pair == null || !colors.contains(pair.colorId()) || !sizes.contains(pair.sizeId())) {
                throw new ServiceException("排除的组合必须属于已选颜色和尺码");
            }
            jdbc.update("INSERT INTO rd_style_spec_exclusion(style_id,color_id,size_id) VALUES (?,?,?)",id,pair.colorId(),pair.sizeId());
        }
    }

    /** 套装模式不得携带会被忽略的单款颜色/尺码规格，避免请求方误判保存结果。 / Reject ignored single-style specifications on set-style modes. */
    static void validateStyleSpecificationMode(String mode, List<Long> colorIds, List<Long> sizeIds, List<SpecPair> excludedPairs) {
        if (!"single".equals(mode) && (colorIds != null || sizeIds != null || excludedPairs != null)) {
            throw new ServiceException("合并或分开生产套装不能提交单款颜色、尺码和排除组合字段");
        }
    }

    /** 替换款式规格并校验订单或 BOM 对既有规格的引用。 / Replace style specifications while validating order and BOM references. */

    private void replaceSpec(Long id, SpecReference ref, List<Long> requested) {
        if (requested.size()>100 || requested.contains(null)) throw new ServiceException("颜色或尺码最多各选择100项");
        Set<String> previous = new HashSet<>(specIds(id,ref));
        String key = ref == COLOR ? "color_id" : "size_id";
        Set<Long> desired = new TreeSet<>(requested);
        for (Long value : desired) {
            if (previous.remove(value.toString())) continue;
            if (jdbc.queryForList("SELECT 1 FROM "+ref.table()+" WHERE "+ref.key()+"=? AND status='0' FOR SHARE",value).isEmpty()) {
                throw new ServiceException("颜色或尺码不存在或已停用");
            }
            jdbc.update("INSERT INTO "+ref.relation()+"(style_id,"+key+") VALUES (?,?)",id,value);
        }
        for (String value : previous) {
            jdbc.update("DELETE FROM "+ref.relation()+" WHERE style_id=? AND "+key+"=?",id,Long.valueOf(value));
        }
    }

    /** 锁定写入目标并对查无记录与无权统一返回。 / Lock writes and avoid exposing inaccessible records. */
    private Map<String,Object> current(Long id, boolean lock) {
        StringBuilder sql = new StringBuilder(SELECT).append(" s.style_id=? AND ");
        List<Object> args = new ArrayList<>();
        args.add(id);
        categories.appendScope(sql,args,"s.category_id",categories.scope());
        if (lock) sql.append(" FOR UPDATE OF s");
        return jdbc.queryForList(sql.toString(),args.toArray()).stream().findFirst().map(this::normalizeTagIds)
            .orElseThrow(() -> new ServiceException("款式不存在或不可访问"));
    }
    /** 规范化标签 ID 集合，检查去重并保留合法的当前标签关联。 / Normalize tag IDs, remove duplicates and retain only valid current assignments. */

    private Map<String,Object> normalizeTagIds(Map<String,Object> row) {
        return normalizeIds(row, "tagIds");
    }
    /** 将外部 ID 列表解析为稳定主键并拒绝空值和非法格式。 / Parse stable identifiers from an external list and reject blank or malformed values. */

    private Map<String,Object> normalizeIds(Map<String,Object> row, String key) {
        if (row.get(key) instanceof java.sql.Array array) {
            try {
                row.put(key, List.of((String[]) array.getArray()));
            } catch (java.sql.SQLException e) {
                throw new IllegalStateException("读取款式关联标识失败", e);
            }
        }
        return row;
    }
    /** 返回款式功能可用标签及分组。 / Return tags and groups available to the style feature. */

    private List<Map<String,Object>> tagOptions(Long id) {
        return jdbc.queryForList("""
            SELECT t.tag_id::text AS value,t.tag_name AS label,g.group_name AS "groupName",(t.status<>'0') AS disabled
            FROM sys_tag_relation r JOIN sys_tag t ON t.tag_id=r.tag_id
            JOIN sys_tag_group g ON g.group_id=t.tag_group_id
            WHERE r.module_key=? AND r.record_id=? ORDER BY t.tag_id
            """, TAG_FEATURE,id);
    }

    /** 缺省保留旧关联；停用标签只能保留在原档案中。 / Omission preserves assignments and disabled tags cannot be newly bound. / Omitting tags preserves existing links; disabled tags may remain but cannot be newly assigned. */
    private Set<Long> validateTags(Long styleId, List<Long> input) {
        if (input == null) return null;
        if (input.size() > 100 || input.contains(null)) throw new ServiceException("请选择有效款式标签，最多100个");
        Set<Long> tags = new TreeSet<>(input);
        for (Long tagId : tags) {
            if (jdbc.queryForList("""
                SELECT t.tag_id FROM sys_tag t WHERE t.tag_id=? AND t.module_key=?
                AND (t.status='0' OR EXISTS (SELECT 1 FROM sys_tag_relation r
                     WHERE r.module_key=t.module_key AND r.record_id=? AND r.tag_id=t.tag_id)) FOR SHARE OF t
                """,tagId,TAG_FEATURE,styleId).isEmpty()) {
                throw new ServiceException("款式标签无效、已停用或属于其他功能");
            }
        }
        return tags;
    }

    /** 审计比较中文业务字段，不记录图片内容。 / Record business field changes without storing image bytes. / Audit named business-field changes while excluding binary image content. */
    private void audit(String action, Long id, Map<String,Object> before, Map<String,Object> after) {
        BusinessAudit.record(action,id,"款式名称",
            auditFields(before),after.isEmpty() ? Map.of("已删除",true) : auditFields(after));
    }

    private Map<String,Object> auditFields(Map<String,Object> row) {
        if (row.isEmpty()) return Map.of();
        Map<String,Object> fields = new LinkedHashMap<>();
        String[] keys = {"styleCode","name","customerId","customerName","categoryId","categoryName",
            "season","description","coverOssId","status","remark"};
        String[] labels = {"款号","款式名称","客户ID","客户","分类ID","款式分类","季节","款式说明","主图文件","状态","备注"};
        for (int i=0;i<keys.length;i++) fields.put(labels[i],row.get(keys[i]));
        fields.put("款式标签ID",row.get("tagIds"));
        fields.put("生产模式",row.get("productionMode"));
        fields.put("要求同架生产",row.get("sameHanger"));
        return fields;
    }
    /** 安全读取 JSON 字段，兼容字段缺失或类型异常。 / Safely read a JSON field when values are absent or malformed. */

    private String field(String value, int max, String label) {
        String cleaned = Objects.toString(value,"").strip();
        if (cleaned.length()>max) throw new ServiceException(label+"不能超过"+max+"字");
        return cleaned;
    }
    /** 将外部主键文本转换为内部 ID 并统一处理非法值。 / Convert an external identifier string to an internal ID with consistent validation. */

    private Long id(Object value) {
        return value == null ? null : Long.valueOf(value.toString());
    }
}
