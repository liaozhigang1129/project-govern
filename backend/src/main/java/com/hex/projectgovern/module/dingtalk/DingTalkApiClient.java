package com.hex.projectgovern.module.dingtalk;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 钉钉 OpenAPI 客户端 (V2.13 Phase 1)
 *
 * 调用流程:
 *   1) getAccessToken()  → /v1.0/oauth2/accessToken  (7200s 有效, 缓存提前 300s 过期)
 *   2) listSubDeptIds(rootDeptId)  → /v2.0/contact/departments/{deptId}/sub-department-ids
 *   3) listDeptUsers(deptId)  → /v2.0/contact/users/list  (cursor 分页)
 *
 * 错误处理:
 *   - 4xx/5xx → 抛 DingTalkApiException (含 HTTP 状态 + 钉钉 errmsg)
 *   - access_token 失效 (errcode=88) → 清缓存重试 1 次
 *
 * URL 可被 env 覆盖 (pmo.dingtalk.api-base) 便于 mock 测试
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DingTalkApiClient {

    private final DingTalkProperties props;
    // P1: 钉钉服务端用 IPv6 (2401:b180::/32); 老 macOS JDK HttpURLConnection 偶发拒绝
    //   - 设置 connectTimeout/readTimeout + 禁用 IPv6 (优先 IPv4) 解决 "Connection refused" 误报
    private final RestTemplate rest = createRestTemplate();
    private final ObjectMapper mapper = new ObjectMapper();
    private final DingTalkUserLookupRepository userLookup;

    private static RestTemplate createRestTemplate() {
        var factory = new org.springframework.http.client.SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(10000);
        factory.setReadTimeout(30000);
        // 强制 IPv4 (钉钉 api.dingtalk.com 同时有 A 和 AAAA, IPv6 偶发 Connection refused)
        System.setProperty("java.net.preferIPv4Stack", "true");
        return new RestTemplate(factory);
    }

    /** 访问令牌缓存: key=appKey, value=(token, expiresAt) */
    private final ConcurrentHashMap<String, TokenEntry> tokenCache = new ConcurrentHashMap<>();
    private static final long TOKEN_SAFE_TTL_SECONDS = 300L;  // 提前 5 分钟失效

    /**
     * 最近一次"详情 API 失败"的第一条错误消息. 
     * 让 sync service 能消费并写到 sync_log.errorMessage, 不必翻后端日志.
     * 设计: 一次 listLeaves 调用会填充, service 读后清空 (consume 语义).
     */
    private volatile String lastDetailError = null;

    public String consumeLastDetailError() {
        String s = lastDetailError;
        lastDetailError = null;
        return s;
    }

    private void setLastDetailError(String s) {
        lastDetailError = s;
    }

    private record TokenEntry(String token, Instant expiresAt) {}

    private static final String DEFAULT_API_BASE = "https://oapi.dingtalk.com";
    private static final String OAUTH_API_BASE = "https://api.dingtalk.com";
    // V2.14 请休假 / OA 审批: 新版 v1.0 workflow API 也在 api.dingtalk.com
    //   - 老 oapi topapi/* 路径已下线("不合法 ApiName")
    //   - 留 OAPI_BASE 仅给老部门/用户接口(simplelist 等)用
    private static final String WORKFLOW_API_BASE = "https://api.dingtalk.com";
    private String apiBase = System.getenv().getOrDefault("PMO_DINGTALK_API_BASE", DEFAULT_API_BASE);

    // ============================================================
    // 诊断: 不污染 sync_log, 也不入缓存
    //   1) 配置空 → 返回 "EMPTY_CONFIG"
    //   2) 网络错 → 返回 "NETWORK_ERROR"
    //   3) 钉钉 200 → 返回 "OK" + token 前 6 位
    //   4) 钉钉 400/4xx → 返回 errcode/errmsg
    //   5) 5xx → 返回 "REMOTE_5xx"
    // ============================================================
    public TestResult testConnection() {
        String key = props.getAppKey();
        String sec = props.getAppSecret();
        if (key.isEmpty() || sec.isEmpty()) {
            return new TestResult("EMPTY_CONFIG", "appKey 或 appSecret 未配置", null, null);
        }
        ObjectNode body = mapper.createObjectNode();
        body.put("appKey", key);
        body.put("appSecret", sec);
        try {
            // accessToken 单独用 api.dingtalk.com
            String url = OAUTH_API_BASE + "/v1.0/oauth2/accessToken";
            HttpHeaders h = new HttpHeaders();
            h.setContentType(MediaType.APPLICATION_JSON);
            ResponseEntity<String> resp;
            try {
                resp = rest.postForEntity(url, new HttpEntity<>(body, h), String.class);
            } catch (Exception e) {
                throw new DingTalkApiException("accessToken failed: " + e.getMessage(), e);
            }
            if (!resp.getStatusCode().is2xxSuccessful()) {
                throw new DingTalkApiException("accessToken HTTP " + resp.getStatusCode() + ": " + resp.getBody());
            }
            JsonNode root;
            try {
                root = mapper.readTree(resp.getBody());
            } catch (Exception ex) {
                throw new DingTalkApiException("accessToken parse failed: " + ex.getMessage(), ex);
            }
            String token = root.path("accessToken").asText("");
            long expire = root.path("expireIn").asLong(0L);
            if (token.isEmpty()) {
                return new TestResult("BAD_RESPONSE", "accessToken 字段缺失: " + root, null, null);
            }
            return new TestResult("OK", "连接成功,token 有效期 " + expire + "s", maskToken(token), String.valueOf(expire));
        } catch (DingTalkApiException e) {
            String msg = e.getMessage() == null ? "" : e.getMessage();
            if (msg.contains("invalidClientIdOrSecret")) {
                return new TestResult("BAD_CREDENTIAL", "appKey 或 appSecret 无效 (invalidClientIdOrSecret)", null, null);
            }
            if (msg.contains("400")) {
                // 解析 body 找 code 字段 (e.g. {"code":"invalidClientIdOrSecret"})
                java.util.regex.Matcher mc = java.util.regex.Pattern.compile("\"code\"\s*:\s*\"([^\"]{1,64})\"").matcher(msg);
                String code = mc.find() ? mc.group(1) : "";
                return new TestResult("HTTP_400", "钉钉拒绝: " + (code.isEmpty() ? msg : code), null, code);
            }
            if (msg.contains("HTTP 5")) {
                return new TestResult("REMOTE_5xx", "钉钉服务端错误: " + msg, null, null);
            }
            return new TestResult("UNKNOWN", msg, null, null);
        } catch (Exception e) {
            return new TestResult("NETWORK_ERROR", "无法连接钉钉: " + e.getMessage(), null, null);
        }
    }

    private static String maskToken(String t) {
        if (t == null || t.length() < 8) return "****";
        return t.substring(0, 6) + "..." + t.substring(t.length() - 4);
    }

    public record TestResult(String status, String message, String tokenMasked, String expireIn) {}

    // ============================================================
    // access_token
    // ============================================================
    public synchronized String getAccessToken() {
        String key = props.getAppKey();
        TokenEntry cached = tokenCache.get(key);
        if (cached != null && cached.expiresAt.isAfter(Instant.now())) {
            return cached.token;
        }
        return refreshAccessToken(key);
    }

    private String refreshAccessToken(String appKey) {
        ObjectNode body = mapper.createObjectNode();
        body.put("appKey", appKey);
        body.put("appSecret", props.getAppSecret());
        try {
            // accessToken 单独用 api.dingtalk.com
            String url = OAUTH_API_BASE + "/v1.0/oauth2/accessToken";
            HttpHeaders h = new HttpHeaders();
            h.setContentType(MediaType.APPLICATION_JSON);
            ResponseEntity<String> resp = rest.postForEntity(url, new HttpEntity<>(body, h), String.class);
            if (!resp.getStatusCode().is2xxSuccessful()) {
                throw new DingTalkApiException("accessToken HTTP " + resp.getStatusCode() + ": " + resp.getBody());
            }
            JsonNode root;
            try {
                root = mapper.readTree(resp.getBody());
            } catch (Exception ex) {
                throw new DingTalkApiException("accessToken parse failed: " + ex.getMessage(), ex);
            }
            String token = root.path("accessToken").asText();
            long expireIn = root.path("expireIn").asLong(7200L);
            if (token.isEmpty()) {
                throw new DingTalkApiException("accessToken missing in response: " + root);
            }
            long safeExpire = Math.max(60L, expireIn - TOKEN_SAFE_TTL_SECONDS);
            tokenCache.put(appKey, new TokenEntry(token, Instant.now().plusSeconds(safeExpire)));
            log.info("[DingTalk] accessToken refreshed, expireIn={}s", expireIn);
            return token;
        } catch (HttpStatusCodeException e) {
            throw new DingTalkApiException("accessToken HTTP " + e.getStatusCode() + ": " + e.getResponseBodyAsString());
        }
    }

    private void clearToken(String appKey) {
        tokenCache.remove(appKey);
    }

    // ============================================================
    // 部门
    // ============================================================
    /**
     * 递归拉取所有子部门 ID (BFS, rootDeptId 通常 = 1 即根)
     * @return 部门 ID 列表, 含 rootDeptId
     */


    public List<Long> listAllDeptIds(Long rootDeptId) {
        // 改用 /department/list 一次拉全企业, 不依赖 list_ids 递归
        List<DeptInfo> all = listAllDepts();
        if (all.isEmpty()) {
            return listAllDeptIdsBfs(rootDeptId);
        }
        return all.stream().map(DeptInfo::deptId).collect(Collectors.toList());
    }

    /**
     * 一次性拉取全企业所有部门
     */
    public List<DeptInfo> listAllDepts() {
        List<DeptInfo> result = new ArrayList<>();
        JsonNode root = getJson("/department/list?lang=zh_CN", mapper.createObjectNode());
        if (root == null) return result;
        JsonNode arr = root.path("department");
        if (!arr.isArray()) return result;
        for (JsonNode n : arr) {
            long id = n.path("id").asLong(0);
            if (id == 0) continue;
            long parentId = n.path("parentid").asLong(0L);
            String name = n.path("name").asText("");
            result.add(new DeptInfo(id, name, parentId));
        }
        log.info("[DingTalk] listAllDepts: {} departments", result.size());
        return result;
    }

    /**
     * 旧 BFS 实现 (list 失败时兜底)
     */
    private List<Long> listAllDeptIdsBfs(Long rootDeptId) {
        List<Long> all = new ArrayList<>();
        all.add(rootDeptId);
        java.util.ArrayDeque<Long> queue = new java.util.ArrayDeque<>();
        queue.add(rootDeptId);
        while (!queue.isEmpty()) {
            Long cur = queue.poll();
            JsonNode resp = getJson("/department/list_ids?id=" + cur, mapper.createObjectNode());
            if (resp == null) continue;
            JsonNode arr = resp.path("sub_dept_id_list");
            if (arr != null && arr.isArray()) {
                for (JsonNode n : arr) {
                    Long child = n.asLong();
                    if (!all.contains(child)) {
                        all.add(child);
                        queue.add(child);
                    }
                }
            }
        }
        return all;
    }

    /**
     * 获取单个部门详情 (返回 dept_id, name, parent_id)
     */
    public DeptInfo getDept(Long deptId) {
        JsonNode root = getJson("/department/get?id=" + deptId, mapper.createObjectNode());
        if (root == null) return null;
        return new DeptInfo(
            root.path("dept_id").asLong(deptId),
            root.path("name").asText(""),
            root.path("parent_id").asLong(0L)
        );
    }

    public record DeptInfo(long deptId, String name, long parentId) {}

    // ============================================================
    // 用户
    // ============================================================
    /**
     * 按部门分页拉用户详情, 合并到一个 list 返回
     *
     * 使用新版钉钉接口 /topapi/v2/user/list, 能拿到完整字段:
     *   userid / name / mobile / email / title / active / department
     *   与手机号/邮箱/职位等 source-of-truth 信息
     *
     * 注意: 此接口需要应用开通 [qyapi_get_department_member] 权限
     *   旧接口 /user/simplelist 只返 userid/name, 无法满足同步需求
     */
    public List<JsonNode> listDeptUsers(Long deptId) {
        List<JsonNode> users = new ArrayList<>();
        long cursor = 0L;
        int safety = 0;
        while (safety++ < 1000) {  // 防止 cursor 死循环
            ObjectNode body = mapper.createObjectNode();
            body.put("dept_id", deptId);
            body.put("cursor", cursor);
            body.put("size", 100);
            body.put("language", "zh_CN");
            JsonNode root;
            try {
                root = postJson("/topapi/v2/user/list", body, true);
            } catch (DingTalkApiException e) {
                String msg = e.getMessage() == null ? "" : e.getMessage();
                // 权限不足时降级到 simplelist (只返 userid/name, 但能保证 sync 不卡死)
                if (msg.contains("60011") || msg.contains("权限") || msg.contains("permission")) {
                    log.warn("[DingTalk] topapi/v2/user/list 权限不足, 降级到 simplelist (无 mobile/email/title)");
                    return listDeptUsersSimplelist(deptId);
                }
                throw e;
            }
            if (root == null) {
                log.warn("[/topapi/v2/user/list] 返回 null dept_id={}, 跳过 (可能权限不足)", deptId);
                break;
            }
            // /topapi/v2/user/list 响应结构: { errcode, errmsg, result: { list: [...], has_more, next_cursor } }
            JsonNode result = root.path("result");
            JsonNode list = result.path("list");
            if (list.isArray()) {
                for (JsonNode u : list) {
                    // 新接口返回 department (数组), 不是 dept_id_list; 注入统一字段
                    if (u.isObject() && (u.path("dept_id_list").isMissingNode() || !u.path("dept_id_list").isArray())) {
                        ObjectNode uObj = (ObjectNode) u;
                        com.fasterxml.jackson.databind.node.ArrayNode arr = uObj.putArray("dept_id_list");
                        JsonNode deptArr = u.path("department");
                        if (deptArr.isArray()) {
                            for (JsonNode d : deptArr) arr.add(d.asLong());
                        } else {
                            arr.add(deptId);
                        }
                    }
                    users.add(u);
                }
            }
            boolean hasMore = result.path("has_more").asBoolean(false);
            if (!hasMore) break;
            cursor = result.path("next_cursor").asLong(0L);
        }
        log.info("[DingTalk] listDeptUsers dept_id={} -> {} users", deptId, users.size());
        return users;
    }

    /**
     * 降级: 老接口 /user/simplelist (只返 userid/name, 不需要详细权限)
     * 钉钉此接口对内部应用默认开放, 用作 topapi/v2/user/list 权限不足时的 fallback
     */
    private List<JsonNode> listDeptUsersSimplelist(Long deptId) {
        List<JsonNode> users = new ArrayList<>();
        long offset = 0L;
        int safety = 0;
        while (safety++ < 1000) {
            JsonNode root = getJson("/user/simplelist?department_id=" + deptId + "&size=100&offset=" + offset, mapper.createObjectNode());
            if (root == null) break;
            JsonNode list = root.path("userlist");
            if (list.isArray()) {
                for (JsonNode u : list) {
                    // 注入统一字段 dept_id_list
                    if (u.isObject() && (u.path("dept_id_list").isMissingNode() || !u.path("dept_id_list").isArray())) {
                        ((com.fasterxml.jackson.databind.node.ObjectNode) u).putArray("dept_id_list").add(deptId);
                    }
                    // simplelist 不返回 email/mobile/title, 补上空值避免 sync 端误覆盖
                    if (u.isObject() && u.path("active").isMissingNode()) {
                        ((com.fasterxml.jackson.databind.node.ObjectNode) u).put("active", true);
                    }
                    users.add(u);
                }
            }
            boolean hasMore = root.path("hasMore").asBoolean(false);
            if (!hasMore) break;
            offset += list.size();
        }
        log.info("[DingTalk] listDeptUsersSimplelist dept_id={} -> {} users (降级, 仅 userid/name)", deptId, users.size());
        return users;
    }

    // ============================================================
    // 请休假 (V2.14: 改用新版 OA 审批流程 API)
    //   - /v1.0/workflow/processes/instanceIds/query   (POST, 分页)
    //   - /v1.0/workflow/processInstances             (GET, 单条详情)
    //   旧版 /topapi/attendance/getLeaveStatus 已下线
    // ============================================================

    /**
     * 拉取某段时间内某审批模板下的实例 IDs (新版 OA 审批 API).
     * 一次最多 maxResults=20, 通过 nextToken 翻页.
     *
     * 新版 API 必须用 api.dingtalk.com + x-acs-dingtalk-access-token header
     * (query 传 access_token 会 400); 老 oapi.dingtalk.com topapi 路径已全部下线.
     */
    private List<String> listProcessInstanceIds(String processCode, long startTimeMs,
                                                long endTimeMs, List<String> statuses) {
        List<String> ids = new ArrayList<>();
        Object nextToken = 0;
        int safety = 50; // 最大 50 页 = 1000 个
        while (safety-- > 0) {
            ObjectNode body = mapper.createObjectNode();
            body.put("processCode", processCode);
            body.put("startTime", startTimeMs);
            body.put("endTime", endTimeMs);
            if (nextToken instanceof String) body.put("nextToken", (String) nextToken);
            else body.put("nextToken", 0);
            body.put("maxResults", 20);
            if (statuses != null && !statuses.isEmpty()) {
                com.fasterxml.jackson.databind.node.ArrayNode arr = mapper.createArrayNode();
                for (String s : statuses) arr.add(s);
                body.set("statuses", arr);
            }
            try {
                JsonNode root = postJsonWorkflow("/v1.0/workflow/processes/instanceIds/query", body);
                if (root == null || !root.path("success").asBoolean(false)) {
                    log.warn("[DingTalk] listProcessInstanceIds processCode={} 返回 success=false", processCode);
                    break;
                }
                JsonNode list = root.path("result").path("list");
                if (list.isArray()) {
                    for (JsonNode n : list) ids.add(n.asText());
                }
                String nt = root.path("result").path("nextToken").asText("");
                if (nt == null || nt.isEmpty()) break;
                nextToken = nt;
            } catch (Exception e) {
                log.warn("[DingTalk] listProcessInstanceIds processCode={} 失败: {}", processCode, e.getMessage());
                break;
            }
        }
        return ids;
    }

    /**
     * 拉取单条审批实例详情 (GET).
     * 路径: /v1.0/workflow/processInstances?processInstanceId=...
     *
     * 不再吞异常: 权限不足/QPS 限流等都让上层 listLeaves 决定如何处理.
     * 历史: 这里以前吞掉异常 return null, 导致 service 误判为"缺 start_time", 看不出根因.
     */
    private JsonNode getProcessInstance(String instanceId) {
        ObjectNode body = mapper.createObjectNode();
        // 新版 API: token 走 header, 不能放 query (会 400)
        JsonNode root = getJsonWorkflow("/v1.0/workflow/processInstances?processInstanceId=" + instanceId, body);
        if (root == null) return null;
        JsonNode result = root.path("result");
        return result.isObject() ? result : null;
    }

    /**
     * 拉取钉钉请休假记录 (新版 OA 审批流程 API).
     * 该方法处理了所有模板 + 时间分窗口 (避免单次 >120 天报错)。
     *
     * 响应每条记录字段:
     *   - processInstanceId:  唯一 ID
     *   - title / status / result
     *   - createTime / finishTime (毫秒时间戳)
     *   - originatorUserId:  发起人 userid
     *   - formComponentValues: 表单详情 (含请假/出差/外出/加班的"开始/结束"组件)
     *
     * @param processCodes 需同步的审批模板 processCode 列表
     * @param startTimeMs  开始时间戳 (毫秒)
     * @param endTimeMs    结束时间戳 (毫秒)
     * @param windowDays   单窗口上限 (默认 60 <= 120)
     */
    public List<JsonNode> listLeaves(List<String> processCodes, long startTimeMs,
                                     long endTimeMs, int windowDays) {
        List<JsonNode> result = new ArrayList<>();
        if (processCodes == null || processCodes.isEmpty()) return result;

        // 拆窗口: 钉钉单次 <= 120 天; 默认 60. 避免边缘区间请求失败.
        long windowMs = (long) windowDays * 86400_000L;
        List<long[]> windows = new ArrayList<>();
        long cursor = endTimeMs;
        while (cursor - windowMs > startTimeMs) {
            windows.add(new long[]{cursor - windowMs, cursor});
            cursor -= windowMs;
        }
        windows.add(new long[]{startTimeMs, cursor});
        Collections.reverse(windows);

        for (long[] w : windows) {
            for (String pc : processCodes) {
                log.info("[DingTalk] listLeaves window=[{}..{}] processCode={}",
                        java.time.Instant.ofEpochMilli(w[0]), java.time.Instant.ofEpochMilli(w[1]), pc);
                List<String> ids = listProcessInstanceIds(pc, w[0], w[1],
                        List.of("RUNNING", "COMPLETED", "TERMINATED"));
                log.info("[DingTalk] processCode={} window=[{}..{}] ids={}",
                        pc, w[0], w[1], ids.size());
                int success = 0, qpsBlocked = 0, otherFail = 0;
                String firstOtherFailMsg = null;  // 记第一个非 QPS 错误消息, 暴露到 sync_log
                for (String id : ids) {
                    JsonNode pi;
                    try {
                        pi = getProcessInstance(id);
                    } catch (DingTalkApiException ex) {
                        // 钉钉 QPS 限流: 该 instance 跳过, 但不阻塞其它 instance
                        String msg = ex.getMessage() == null ? "" : ex.getMessage();
                        if (msg.contains("QpsLimit") || msg.contains("QPS")) {
                            qpsBlocked++;
                            // QPS 限流: 等 1s 后继续, 避免雪崩
                            try { Thread.sleep(1000L); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); break; }
                        } else {
                            otherFail++;
                            if (firstOtherFailMsg == null) firstOtherFailMsg = msg;
                            log.warn("[DingTalk] getProcessInstance {} 失败: {}", id, msg);
                        }
                        continue;
                    }
                    if (pi == null) {
                        otherFail++;
                        if (firstOtherFailMsg == null) firstOtherFailMsg = "返回 null (可能权限/网络/参数问题)";
                        continue;
                    }
                    pi = normalizeLeave(pi, pc);
                    if (pi.path("start_time").asLong(0) == 0) {
                        diagForm(pi, pc, id);
                    }
                    result.add(pi);
                    success++;
                    // 钉钉 QPS 较紧, 串行详情每 200ms 一次 = 5/s, 留足余量
                    try { Thread.sleep(200L); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); break; }
                }
                // 暴露"非 QPS 限流"的真正错误到 sync_log (e.g. 403 权限不足)
                if (firstOtherFailMsg != null) {
                    setLastDetailError(firstOtherFailMsg);
                }
                if (qpsBlocked > 0 || otherFail > 0) {
                    log.warn("[DingTalk] processCode={} window 详情拉取: success={} qpsBlocked={} otherFail={} totalIds={} firstErr={}",
                            pc, success, qpsBlocked, otherFail, ids.size(), firstOtherFailMsg);
                }
            }
        }
        log.info("[DingTalk] listLeaves total: {}", result.size());
        return result;
    }

    /**
     * 把钉钉 OA 审批原始结构归一化为"请休假记录"标准结构:
     *   leaveId, userid, leaveType, startTime, endTime, duration, durationUnit, reason, status, approverUserid
     * 不在原 schema 的字段(如 duration), 直接从 formComponentValues 中提取.
     */
    private JsonNode normalizeLeave(JsonNode pi, String processCode) {
        ObjectNode n = mapper.createObjectNode();
        // 钉钉 v1.0 workflow 详情 API 不返回 processInstanceId 字段, 唯一 ID 用 businessId
        // 兼容老版本: 如果 pi 是旧版(带 processInstanceId), 优先用它
        String leaveId = pi.path("businessId").asText("");
        if (leaveId.isEmpty()) leaveId = pi.path("processInstanceId").asText("");
        n.put("leave_id", leaveId);
        n.put("userid", pi.path("originatorUserId").asText(""));
        String status = pi.path("status").asText("RUNNING");
        String result = pi.path("result").asText("");
        String mappedStatus;
        if ("COMPLETED".equals(status) && "refuse".equalsIgnoreCase(result)) mappedStatus = "REJECT";
        else if ("TERMINATED".equals(status)) mappedStatus = "REVOKE";
        else mappedStatus = "NORMAL";
        n.put("status", mappedStatus);
        // createTime / finishTime 可能是 ISO 字符串(新版) 也可能是 millis(老版)
        n.put("create_time", parseTimeField(pi, "createTime", "createTimeInMills"));
        n.put("update_time", parseTimeField(pi, "finishTime", "finishTimeInMills"));

        // leave_type / duration / start / end / reason 从表单里取
        String leaveType = "";
        String reason = "";
        long startMs = 0, endMs = 0;
        long durationVal = 0;
        String durationUnit = "HOUR";
        for (JsonNode fc : pi.path("formComponentValues")) {
            String name = fc.path("name").asText("");
            String ctype = fc.path("componentType").asText("");
            String value = fc.path("value").asText("");
            if (ctype.startsWith("DDHolidayField")) {
                // 请假/出差/外出: 包含时间区间 + 类型
                // Bugfix: 钉钉新版 v1.0 API 的 ext 是一个 JSON 字符串 (字段名 extValue), 不是 object node.
                //   fc.path("ext") 永远拿到 missing node → 解析不出 leaveType / start_time / duration
                //   改用 extValue 字符串 + 解析为 JsonNode
                JsonNode ext = parseExtValue(fc);
                // extension 是另一个嵌套 JSON 字符串, 内含 {"tag":"年假"} 这种
                if (ext != null && ext.hasNonNull("extension")) {
                    String extTag = ext.path("extension").asText("{}");
                    JsonNode inner = tryParse(extTag);
                    if (inner != null && inner.hasNonNull("tag")) {
                        String tag = inner.path("tag").asText("");
                        if (!tag.isEmpty() && leaveType.isEmpty()) leaveType = tag;
                    } else if (extTag.contains("\"tag\"")) {
                        // 兜底: 字符串正则
                        String tag = extTag.replaceAll(".*\"tag\"\\s*:\\s*\"([^\"]+)\".*", "$1");
                        if (!tag.isEmpty() && !"{".equals(tag) && leaveType.isEmpty()) leaveType = tag;
                    }
                }
                long day = ext == null ? 0 : ext.path("durationInDay").asLong(0);
                long hour = ext == null ? 0 : ext.path("durationInHour").asLong(0);
                if (day > 0) { durationVal = day; durationUnit = "DAY"; }
                else if (hour > 0) { durationVal = hour; durationUnit = "HOUR"; }
                // detailList[].workDate 区间: 最早~最晚
                if (ext != null) {
                    for (JsonNode det : ext.path("detailList")) {
                        long wd = det.path("workDate").asLong(0);
                        if (wd == 0) continue;
                        if (startMs == 0 || wd < startMs) startMs = wd;
                        if (wd > endMs) endMs = wd + 86400_000L - 1;
                    }
                    // ext 顶层也可能有 fromTime/toTime (加班/补卡)
                    if (startMs == 0) {
                        long ft = ext.path("fromTime").asLong(0);
                        if (ft > 0) startMs = ft;
                    }
                    if (endMs == 0) {
                        long tt = ext.path("toTime").asLong(0);
                        if (tt > 0) endMs = tt;
                    }
                }
            } else if ("DateField".equals(ctype) || "TimeField".equals(ctype)) {
                // 部分表单用独立 DateField 表示开始/结束 (加班、调休常见)
                if (value != null && !value.isEmpty()) {
                    long ts = 0;
                    try {
                        // 形如 "2026-07-08 09:00:00" 或 ISO-8601
                        if (value.length() == 19) {
                            ts = java.time.LocalDateTime.parse(value, java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                                    .atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
                        } else {
                            ts = java.time.Instant.parse(value).toEpochMilli();
                        }
                    } catch (Exception ex) { /* ignore */ }
                    if (ts > 0) {
                        if (name.contains("开始") || name.contains("起始") || name.toLowerCase().contains("start") || name.toLowerCase().contains("begin")) {
                            if (startMs == 0 || ts < startMs) startMs = ts;
                        } else if (name.contains("结束") || name.contains("截止") || name.toLowerCase().contains("end") || name.toLowerCase().contains("finish")) {
                            if (ts > endMs) endMs = ts;
                        } else if (startMs == 0) {
                            startMs = ts;
                        } else if (endMs == 0) {
                            endMs = ts;
                        }
                    }
                }
            } else if ("TextareaField".equals(ctype) || "TextField".equals(ctype)) {
                // 一般是事由 (排除一些"审批说明"的常驻文本框)
                if (value != null && !value.isBlank() && !looksLikeBoilerplate(name)) {
                    if (reason.isEmpty()) reason = value;
                }
            } else if ("DDSelectField".equals(ctype)) {
                // 一些表单用单选表示类型
                if (leaveType.isEmpty()) leaveType = value;
            }
        }
        if (leaveType.isEmpty()) leaveType = mapProcessCodeToLeaveType(processCode);
        // 兜底: 没有 endMs 但有 startMs + duration → 推算
        if (endMs == 0 && startMs > 0 && durationVal > 0) {
            if ("DAY".equals(durationUnit)) {
                endMs = startMs + durationVal * 86400_000L - 1;
            } else {
                endMs = startMs + durationVal * 3600_000L - 1;
            }
        }
        n.put("leave_type", leaveType);
        n.put("start_time", startMs);
        n.put("end_time", endMs);  // 0 表示 null (数据库已允许 end_time 为空)
        n.put("duration", durationVal);
        n.put("duration_unit", durationUnit);
        n.put("reason", reason);

        // 审批人: 取 operationRecords 中 type=EXECUTE_TASK_NORMAL / AGREE 的 userId
        String approver = "";
        for (JsonNode op : pi.path("operationRecords")) {
            String type = op.path("type").asText("");
            String r2 = op.path("result").asText("");
            if ("EXECUTE_TASK_NORMAL".equals(type) && "AGREE".equalsIgnoreCase(r2)) {
                approver = op.path("userId").asText("");
                if (!approver.isEmpty()) break;
            }
        }
        n.put("approver_userid", approver);
        return n;
    }

    /**
     * 诊断辅助: 当 normalizeLeave 后 start_time=0, 把 pi 里 formComponentValues 内容写到日志,
     * 方便调试"为什么 form 解析不出开始时间".
     * 仅在 listLeaves 路径内诊断, 不会持久化.
     */
    private void diagForm(JsonNode pi, String processCode, String leaveId) {
        try {
            JsonNode fc = pi.path("formComponentValues");
            if (!fc.isArray() || fc.isEmpty()) {
                StringBuilder keys = new StringBuilder();
                java.util.Iterator<String> it = pi.fieldNames();
                if (it != null) while (it.hasNext()) { keys.append(it.next()).append(","); }
                log.warn("[DingTalkDiag] leaveId={} formComponentValues 缺失/空. piKeys=[{}]", leaveId, keys);
                return;
            }
            int n = Math.min(fc.size(), 3);
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < n; i++) {
                JsonNode c = fc.get(i);
                sb.append(String.format("{name=%s, componentType=%s, value=%s}",
                    c.path("name").asText(""), c.path("componentType").asText(""), c.path("value").asText("")));
                if (i < n-1) sb.append(" | ");
            }
            log.warn("[DingTalkDiag] leaveId={} form[0..{}]={}", leaveId, n-1, sb);
        } catch (Exception e) {
            log.warn("[DingTalkDiag] leaveId={} diag 自身异常: {}", leaveId, e.getMessage());
        }
    }

    private boolean looksLikeBoilerplate(String name) {
        if (name == null) return true;
        String n = name.toLowerCase();
        return n.contains("温馨") || n.contains("提醒") || n.contains("说明") || n.contains("附件");
    }

    /**
     * 解析钉钉新版 v1.0 API 中 DDHolidayField 的 extValue 字段.
     * 钉钉把它存成 JSON 字符串 (不是嵌套对象), 需自己 parse.
     * 兼容: ext 是对象 (老版) / extValue 是字符串 (新版) / extValue 是对象 (防御).
     */
    private JsonNode parseExtValue(JsonNode fc) {
        if (fc == null) return null;
        // 1) 老版: 字段名 ext, 直接是对象
        JsonNode ext = fc.path("ext");
        if (ext != null && ext.isObject() && ext.size() > 0) return ext;
        // 2) 新版: 字段名 extValue, 是 JSON 字符串
        JsonNode ev = fc.path("extValue");
        if (ev == null || ev.isMissingNode() || ev.isNull()) return null;
        if (ev.isObject()) return ev;  // 防御: 钉钉哪天改成对象
        String s = ev.asText("");
        if (s == null || s.isEmpty()) return null;
        return tryParse(s);
    }

    /** 安全 parse JSON 字符串 → JsonNode; 失败返 null */
    private JsonNode tryParse(String s) {
        if (s == null || s.isEmpty()) return null;
        try {
            return mapper.readTree(s);
        } catch (Exception e) {
            log.debug("[DingTalk] extValue/extension 解析失败: {}", e.getMessage());
            return null;
        }
    }

    private String mapProcessCodeToLeaveType(String processCode) {
        if (processCode == null) return "请假";
        if (processCode.equalsIgnoreCase("PROC-325BD729-5E99-4E99-9534-A3CB99617938") || processCode.contains("325BD729")) return "请假";
        if (processCode.equalsIgnoreCase("PROC-87E7F27D-E672-48BB-9B0A-C893CF30B40E") || processCode.contains("87E7F27D")) return "出差";
        if (processCode.equalsIgnoreCase("PROC-EF72FC5E-96CF-4B62-BA09-E5E404B85531") || processCode.contains("EF72FC5E")) return "外出";
        if (processCode.equalsIgnoreCase("PROC-B0EEAB01-2C29-4583-ACDD-28A5451358C2") || processCode.contains("B0EEAB01")) return "加班";
        return "其他审批";
    }

    /**
     * 解析钉钉返回的时间字段: 优先 ISO-8601 字符串(新版 v1.0), 退回 millis 数值(老版)
     * 返回 epoch millis; 解析失败返 0
     */
    private long parseTimeField(JsonNode pi, String isoField, String millisField) {
        JsonNode n = pi.path(millisField);
        if (n.canConvertToLong() && n.asLong() > 0) {
            return n.asLong();
        }
        String iso = pi.path(isoField).asText("");
        if (iso == null || iso.isEmpty()) return 0;
        try {
            return java.time.Instant.parse(iso).toEpochMilli();
        } catch (Exception ex) {
            try {
                return java.time.OffsetDateTime.parse(iso).toInstant().toEpochMilli();
            } catch (Exception ex2) {
                log.debug("[DingTalk] 时间字段 {} 解析失败: {}", isoField, iso);
                return 0;
            }
        }
    }

    // ============================================================
    // HTTP helpers
    // ============================================================
    private JsonNode postJson(String path, ObjectNode body, boolean needToken) {
        // 钉钉 oapi.topapi/v2 接口要求 access_token 放 query 参数 (Bearer 鉴权会返 "access_token is blank")
        String tokenQuery = "";
        if (needToken) tokenQuery = "?access_token=" + getAccessToken();
        String url = apiBase + path + tokenQuery;
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        try {
            ResponseEntity<String> resp = rest.postForEntity(url, new HttpEntity<>(body, h), String.class);
            if (!resp.getStatusCode().is2xxSuccessful()) {
                throw new DingTalkApiException("POST " + path + " HTTP " + resp.getStatusCode() + ": " + resp.getBody());
            }
            JsonNode root = mapper.readTree(resp.getBody());
            // 业务码 0/200 算成功, 其它都抛 (兼容老版本 errcode)
            if (root.has("errcode") && root.path("errcode").asInt(0) != 0) {
                int code = root.path("errcode").asInt();
                String msg = root.path("errmsg").asText("");
                String subCode = root.path("sub_code").asText("");
                // 真正的权限错误(60011 qyapi_get_xxx 等)不会因换 token 而消失
                // 不要走 token 重试分支, 直接抛错
                boolean isPermissionError = subCode.startsWith("60011") || subCode.startsWith("60020")
                        || msg.contains("权限") || msg.contains("permission");
                // 88 = access_token 失效 (sub_code 为空时) — 清缓存重试
                boolean isTokenExpired = (code == 88 || code == 40014) && !isPermissionError;
                if (needToken && isTokenExpired) {
                    log.warn("[DingTalk] access_token 失效 errcode={}, 重试", code);
                    clearToken(props.getAppKey());
                    // 重试时用最新 token 重新拼 query
                    String retryUrl = apiBase + path + "?access_token=" + getAccessToken();
                    ResponseEntity<String> retry = rest.postForEntity(retryUrl, new HttpEntity<>(body, h), String.class);
                    JsonNode retryRoot = mapper.readTree(retry.getBody());
                    // 重试响应再次校验 errcode (可能仍为 60011 等权限错误, 或 token 仍失效)
                    if (retryRoot.has("errcode") && retryRoot.path("errcode").asInt(0) != 0) {
                        int rCode = retryRoot.path("errcode").asInt();
                        String rMsg = retryRoot.path("errmsg").asText("");
                        throw new DingTalkApiException("POST " + path + " (retry) errcode=" + rCode + " errmsg=" + rMsg);
                    }
                    return retryRoot;
                }
                throw new DingTalkApiException("POST " + path + " errcode=" + code + " errmsg=" + msg);
            }
            return root;
        } catch (HttpStatusCodeException e) {
            throw new DingTalkApiException("POST " + path + " HTTP " + e.getStatusCode() + ": " + e.getResponseBodyAsString());
        } catch (DingTalkApiException e) {
            throw e;
        } catch (Exception e) {
            throw new DingTalkApiException("POST " + path + " failed: " + e.getMessage(), e);
        }
    }

    private JsonNode getJson(String path, ObjectNode body) {
        // oapi.dingtalk.com 走 query string 传 access_token
        String sep = path.contains("?") ? "&" : "?";
        String url = apiBase + path + sep + "access_token=" + getAccessToken();
        try {
            ResponseEntity<String> resp = rest.getForEntity(url, String.class);
            if (!resp.getStatusCode().is2xxSuccessful()) {
                log.warn("[DingTalk] GET {} -> HTTP {}", path, resp.getStatusCode());
                return null;
            }
            String body2 = resp.getBody();
            if (body2 == null || body2.isEmpty()) return null;
            JsonNode root = mapper.readTree(body2);
            // oapi 老接口业务码 errcode != 0 报错
            if (root.has("errcode") && root.path("errcode").asInt(0) != 0) {
                int code = root.path("errcode").asInt();
                String msg = root.path("errmsg").asText("");
                log.warn("[DingTalk] GET {} errcode={} errmsg={}", path, code, msg);
                return null;
            }
            return root;
        } catch (Exception e) {
            log.warn("[DingTalk] GET {} failed: {}", path, e.getMessage());
            return null;
        }
    }

    // ============================================================
    // 钉钉 oapi.dingtalk.com 老接口专用 POST helper
    //   - 走 DEFAULT_API_BASE (oapi.dingtalk.com),不是 api.dingtalk.com
    //   - access_token 放 query (?access_token=...)
    //   - 业务码 errcode != 0 抛异常(让上层同步失败重试)
    //   - 用于考勤 /attendance/list (钉钉已下线 v1.0 attendance,真实路径是 oapi 这个)
    // ============================================================
    private JsonNode postJsonOapi(String path, ObjectNode body) {
        String url = DEFAULT_API_BASE + path + "?access_token=" + getAccessToken();
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        try {
            ResponseEntity<String> resp = rest.postForEntity(url, new HttpEntity<>(body, h), String.class);
            if (!resp.getStatusCode().is2xxSuccessful()) {
                throw new DingTalkApiException("POST " + path + " HTTP " + resp.getStatusCode() + ": " + resp.getBody());
            }
            JsonNode root = mapper.readTree(resp.getBody());
            if (root.has("errcode") && root.path("errcode").asInt(0) != 0) {
                int code = root.path("errcode").asInt();
                String msg = root.path("errmsg").asText("");
                throw new DingTalkApiException("POST " + path + " errcode=" + code + " errmsg=" + msg);
            }
            return root;
        } catch (HttpStatusCodeException e) {
            throw new DingTalkApiException("POST " + path + " HTTP " + e.getStatusCode() + ": " + e.getResponseBodyAsString());
        } catch (DingTalkApiException e) {
            throw e;
        } catch (Exception e) {
            throw new DingTalkApiException("POST " + path + " failed: " + e.getMessage(), e);
        }
    }

    // ============================================================
    // 考勤记录 (V4.30 + V4.32 修复)
    //   旧路径 (错误): /v1.0/attendance/records/query  →  永远 404 InvalidAction.NotFound
    //   中间路径 (实测 41100 时间不合法): oapi.dingtalk.com/attendance/list → fromDate+toDate 字段被拒
    //   新路径 (实测 errcode=0): oapi.dingtalk.com/attendance/listRecord
    //     字段: { userIds: [...], checkDateFrom: "yyyy-MM-dd HH:mm:ss", checkDateTo: "yyyy-MM-dd HH:mm:ss" }
    //   userIds 字段名 (不带 'L'), 时间必须带 "HH:mm:ss" 否则 41100
    //   响应: { recordresult: [ { bizId, userId, workDate, baseCheckTime, timeResult, source, ... } ] }
    // ============================================================
    public List<JsonNode> listAttendances(List<String> userIds, String checkDateFrom, String checkDateTo) {
        List<JsonNode> result = new ArrayList<>();
        if (userIds == null || userIds.isEmpty()) return result;
        // 钉钉单次 userIds 上限约 50, 超出就分批
        int batchSize = 50;
        // oapi listRecord 要求 "yyyy-MM-dd HH:mm:ss" 格式时间
        String fromDt = checkDateFrom + " 00:00:00";
        String toDt   = checkDateTo   + " 23:59:59";
        for (int i = 0; i < userIds.size(); i += batchSize) {
            List<String> sub = userIds.subList(i, Math.min(i + batchSize, userIds.size()));
            ObjectNode body = mapper.createObjectNode();
            com.fasterxml.jackson.databind.node.ArrayNode arr = mapper.createArrayNode();
            for (String u : sub) arr.add(u);
            body.set("userIds", arr);   // 字段名是 userIds (不带 L), listRecord 专有
            body.put("checkDateFrom", fromDt);
            body.put("checkDateTo",   toDt);
            try {
                JsonNode root = postJsonOapi("/attendance/listRecord", body);
                if (root == null) continue;
                // oapi 旧考勤响应字段: recordresult (全小写) — 同时兼容 records / recordResult
                JsonNode records = root.path("recordresult");
                if (!records.isArray()) records = root.path("recordResult");
                if (!records.isArray()) records = root.path("records");
                if (records.isArray()) {
                    for (JsonNode r : records) result.add(r);
                } else {
                    log.warn("[DingTalk] listAttendances batch 响应无 recordresult/records, root keys={}",
                            root.fieldNames().hasNext() ? "non-empty" : "empty");
                }
            } catch (DingTalkApiException e) {
                String msg = e.getMessage() == null ? "" : e.getMessage();
                // QPS 限流: 退避 1s 后重试当前 batch
                if (msg.contains("QpsLimit") || msg.contains("60020") || msg.contains("88")) {
                    log.warn("[DingTalk] listAttendances batch 限流, 1s 后重试: {}", msg);
                    try { Thread.sleep(1000L); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); break; }
                    i -= batchSize; // 重试同一批
                    continue;
                }
                // 权限不足 / 其它业务错: 暴露到 sync_log.errorMessage, 不再静默吞
                log.warn("[DingTalk] listAttendances batch {}..{} 失败: {}", i, i + sub.size(), msg);
                setLastDetailError(msg);  // 写给上层 service 消费
                throw e;  // 终止整个 sync, 让 service 标 FAILED + 写 errorMessage
            } catch (Exception e) {
                log.warn("[DingTalk] listAttendances batch {}..{} 异常: {}", i, i + sub.size(), e.getMessage());
                setLastDetailError(e.getMessage());
                throw new DingTalkApiException("attendance/list 异常: " + e.getMessage(), e);
            }
        }
        log.info("[DingTalk] listAttendances userIds={} from={} to={} -> {} records",
                userIds.size(), fromDt, toDt, result.size());
        return result;
    }

    // ============================================================
    // 考勤单条详细 (V4.34 补卡判定)
    //   路径: oapi.dingtalk.com/attendance/getCheckRecord
    //   字段: { userid, check_id_list: [bizId] }
    //   响应: { recordresult: [ { sourceType, ... } ] }
    //   sourceType: UN_APPROVED=补卡, APPROVED=审批通过, COMPLETE=正常, USER=手动
    // ============================================================
    public String getSourceType(String userid, String bizId) {
        if (userid == null || bizId == null) return "COMPLETE";
        try {
            ObjectNode body = mapper.createObjectNode();
            body.put("userid", userid);
            com.fasterxml.jackson.databind.node.ArrayNode arr = mapper.createArrayNode();
            arr.add(bizId);
            body.set("check_id_list", arr);
            JsonNode root = postJsonOapi("/attendance/getCheckRecord", body);
            JsonNode records = root.path("recordresult");
            if (!records.isArray() || records.size() == 0) return "COMPLETE";
            return records.get(0).path("sourceType").asText("COMPLETE");
        } catch (Exception e) {
            return "COMPLETE";  // 失败兜底: 当作非补卡
        }
    }

    /**
     * 拉取全公司所有 userid 列表(从本地 AppUser 读, 只同步 PMO 系统中已存在的用户)
     * 避免钉钉未授权的 user 拉到一堆脏数据
     */
    public List<String> listAllUserIdsFromPmo() {
        return userLookup == null ? List.of() : userLookup.findAllDingtalkUserIds();
    }

    // ============================================================
    // 新版 v1.0 workflow API 专用 helper
    //   - base = api.dingtalk.com (不是 oapi)
    //   - 鉴权用 x-acs-dingtalk-access-token header (query 传 access_token 会 400)
    //   - 错误码在 "code" 字段(不是 "errcode"), 形如
    //       "Forbidden.AccessDenied.AccessTokenPermissionDenied"
    //   - 业务成功字段在 "success" (boolean)
    // ============================================================
    private JsonNode postJsonWorkflow(String path, ObjectNode body) {
        String url = WORKFLOW_API_BASE + path;
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        h.set("x-acs-dingtalk-access-token", getAccessToken());
        try {
            ResponseEntity<String> resp = rest.postForEntity(url, new HttpEntity<>(body, h), String.class);
            if (!resp.getStatusCode().is2xxSuccessful()) {
                throw new DingTalkApiException("POST " + path + " HTTP " + resp.getStatusCode() + ": " + resp.getBody());
            }
            JsonNode root = mapper.readTree(resp.getBody());
            // 新版 API 错误码在 "code" 字段(类似 "Forbidden.AccessDenied.AccessTokenPermissionDenied")
            // success=false 也算业务失败
            if (root.has("code") && !root.path("code").asText("").isEmpty()
                    && !"OK".equalsIgnoreCase(root.path("code").asText())) {
                String code = root.path("code").asText();
                String msg = root.path("message").asText("");
                throw new DingTalkApiException("POST " + path + " code=" + code + " message=" + msg);
            }
            return root;
        } catch (HttpStatusCodeException e) {
            throw new DingTalkApiException("POST " + path + " HTTP " + e.getStatusCode() + ": " + e.getResponseBodyAsString());
        } catch (DingTalkApiException e) {
            throw e;
        } catch (Exception e) {
            throw new DingTalkApiException("POST " + path + " failed: " + e.getMessage(), e);
        }
    }

    private JsonNode getJsonWorkflow(String path, ObjectNode body) {
        String url = WORKFLOW_API_BASE + path;
        HttpHeaders h = new HttpHeaders();
        // 钉钉新版 v1.0 API: 必须同时设 Content-Type + access-token header
        // (缺 Content-Type 会被网关拒绝; 缺 access-token 报 AuthenticationFailed.MissingParameter)
        h.setContentType(MediaType.APPLICATION_JSON);
        h.set("x-acs-dingtalk-access-token", getAccessToken());
        try {
            // 用 exchange() 才能带上 headers, getForEntity() 不接受 headers
            ResponseEntity<String> resp = rest.exchange(url, org.springframework.http.HttpMethod.GET,
                    new HttpEntity<>(h), String.class);
            if (!resp.getStatusCode().is2xxSuccessful()) {
                // 4xx/5xx 都抛出去, 让上层决定是限流退避还是终止
                throw new DingTalkApiException("GET " + path + " HTTP " + resp.getStatusCode() + ": " + resp.getBody());
            }
            String body2 = resp.getBody();
            if (body2 == null || body2.isEmpty()) {
                throw new DingTalkApiException("GET " + path + " 响应体为空");
            }
            JsonNode root = mapper.readTree(body2);
            // 钉钉业务码: code != OK 视为失败, 抛出包含 code+message 的异常
            // 修复历史 bug: 之前 return null 被吞, sync_log 看不到根因(权限/参数等)
            if (root.has("code") && !root.path("code").asText("").isEmpty()
                    && !"OK".equalsIgnoreCase(root.path("code").asText())) {
                String code = root.path("code").asText();
                String msg = root.path("message").asText("");
                log.warn("[DingTalk] GET {} code={} message={}", path, code, msg);
                throw new DingTalkApiException("GET " + path + " 业务错: code=" + code + " message=" + msg);
            }
            return root;
        } catch (DingTalkApiException e) {
            throw e;  // 不吞, 让上层 listLeaves 处理
        } catch (Exception e) {
            throw new DingTalkApiException("GET " + path + " failed: " + e.getMessage(), e);
        }
    }
}
