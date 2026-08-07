package com.company.zhiyu.module.wbs;

import com.company.zhiyu.module.wbs.dto.WbsTaskNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * WbsService.buildTree 边角覆盖 (P0-A.3 Step 2)。
 *
 * buildTree 是 private, 通过 listTreeByProject 间接测试。Step 1 已覆盖
 * 父+子、孤儿。Step 2 补充:
 *  - 3 层嵌套: 根 → 子 → 孙, depth 和 path 全部正确
 *  - 多棵子树: A 有 2 个子, B 没子, 平级关系不串
 *  - wbsCode 字典序输入 → children 也按字典序
 *  - 软删过滤: Repository 只返未删 (这是 Repository 责任, Service 层不重复过滤)
 *  - 同项目多根: 2 个根 → 都在 roots, 互不挂
 *
 * 不依赖 Mockito, 也不连 Spring, 纯逻辑。
 */
class WbsTreeBuilderTest {

    // ============================================================
    // 2.1 3 层嵌套: 根(1) → 子(1.1) → 孙(1.1.1)
    // ============================================================

    @Test
    @DisplayName("buildTree: 3层嵌套 1 → 1.1 → 1.1.1, depth/path 全对")
    void buildTree_threeLevels() {
        // given: 按字典序平铺
        WbsTask root  = mkTask(1L, null,  "1",     "根");
        WbsTask mid   = mkTask(2L, 1L,    "1.1",   "子");
        WbsTask leaf  = mkTask(3L, 2L,    "1.1.1", "孙");
        List<WbsTask> flat = List.of(root, mid, leaf);

        // when
        List<WbsTaskNode> tree = invokeBuildTree(flat);

        // then
        assertThat(tree).hasSize(1);
        WbsTaskNode r = tree.get(0);
        assertThat(r.depth()).isZero();
        assertThat(r.path()).containsExactly("1");
        assertThat(r.children()).hasSize(1);

        WbsTaskNode m = r.children().get(0);
        assertThat(m.depth()).isEqualTo(1);
        assertThat(m.path()).containsExactly("1", "1.1");
        assertThat(m.children()).hasSize(1);

        WbsTaskNode l = m.children().get(0);
        assertThat(l.depth()).isEqualTo(2);
        assertThat(l.path()).containsExactly("1", "1.1", "1.1.1");
        assertThat(l.children()).isEmpty();
    }

    // ============================================================
    // 2.2 多棵子树: A 根有 2 个子, B 根没子 → 平级
    // ============================================================

    @Test
    @DisplayName("buildTree: 2 棵独立子树 (A根2子 + B根0子), children 互不串")
    void buildTree_twoIndependentSubtrees() {
        WbsTask aRoot = mkTask(1L, null, "1",   "项目A");
        WbsTask a1    = mkTask(2L, 1L,   "1.1", "A-子1");
        WbsTask a2    = mkTask(3L, 1L,   "1.2", "A-子2");
        WbsTask bRoot = mkTask(4L, null, "2",   "项目B");
        List<WbsTask> flat = List.of(aRoot, a1, a2, bRoot);

        List<WbsTaskNode> tree = invokeBuildTree(flat);

        assertThat(tree).hasSize(2);
        WbsTaskNode a = tree.get(0);
        WbsTaskNode b = tree.get(1);
        assertThat(a.id()).isEqualTo(1L);
        assertThat(b.id()).isEqualTo(4L);

        // A 有 2 子
        assertThat(a.children()).hasSize(2);
        assertThat(a.children()).extracting(WbsTaskNode::id).containsExactly(2L, 3L);
        assertThat(a.children()).extracting(WbsTaskNode::depth).containsOnly(1);

        // B 无子
        assertThat(b.children()).isEmpty();
    }

    // ============================================================
    // 2.3 字典序: 输入乱序, buildTree 按 wbsCode 拼 children
    //   (但 flat 入参是 wbsCode 升序 — 字典序是 Repository 的 ORDER BY 保证)
    //   这里验证: 即便入参是 [子, 父, 孙], buildTree 仍然能拼对
    // ============================================================

    @Test
    @DisplayName("buildTree: 入参顺序 [子,父,孙] 乱序入, 仍能正确挂父找子")
    void buildTree_inputOrderDoesNotMatter() {
        WbsTask root = mkTask(1L, null, "1",     "根");
        WbsTask mid  = mkTask(2L, 1L,   "1.1",   "子");
        WbsTask leaf = mkTask(3L, 2L,   "1.1.1", "孙");
        // 故意打乱顺序
        List<WbsTask> flat = List.of(mid, leaf, root);

        List<WbsTaskNode> tree = invokeBuildTree(flat);

        assertThat(tree).hasSize(1);
        WbsTaskNode r = tree.get(0);
        assertThat(r.id()).isEqualTo(1L);
        assertThat(r.children()).hasSize(1);
        assertThat(r.children().get(0).id()).isEqualTo(2L);
        assertThat(r.children().get(0).children().get(0).id()).isEqualTo(3L);
    }

    // ============================================================
    // 2.4 多根 + 深嵌套 + 旁系: 1, 1.1, 1.1.1, 1.2, 2
    // ============================================================

    @Test
    @DisplayName("buildTree: 多根 + 旁系 + 深嵌套混合, depth/path 全对")
    void buildTree_mixedDeepAndShallow() {
        WbsTask r1   = mkTask(1L, null, "1",     "根1");
        WbsTask c1   = mkTask(2L, 1L,   "1.1",   "根1-子1");
        WbsTask g1   = mkTask(3L, 2L,   "1.1.1", "根1-孙1");
        WbsTask c2   = mkTask(4L, 1L,   "1.2",   "根1-子2");
        WbsTask r2   = mkTask(5L, null, "2",     "根2");
        List<WbsTask> flat = List.of(r1, c1, g1, c2, r2);

        List<WbsTaskNode> tree = invokeBuildTree(flat);

        assertThat(tree).hasSize(2);
        // 根1: 2 子 (c1, c2), c1 有 1 孙 (g1)
        WbsTaskNode a = tree.get(0);
        assertThat(a.id()).isEqualTo(1L);
        assertThat(a.children()).hasSize(2);
        // c1 children 顺序
        WbsTaskNode c1Node = a.children().stream().filter(n -> n.id() == 2L).findFirst().orElseThrow();
        assertThat(c1Node.children()).hasSize(1);
        assertThat(c1Node.children().get(0).id()).isEqualTo(3L);
        assertThat(c1Node.children().get(0).depth()).isEqualTo(2);
        assertThat(c1Node.children().get(0).path()).containsExactly("1", "1.1", "1.1.1");
        // c2 旁系, depth=1, 无子
        WbsTaskNode c2Node = a.children().stream().filter(n -> n.id() == 4L).findFirst().orElseThrow();
        assertThat(c2Node.children()).isEmpty();
        assertThat(c2Node.depth()).isEqualTo(1);
        // 根2 独立
        WbsTaskNode b = tree.get(1);
        assertThat(b.id()).isEqualTo(5L);
        assertThat(b.children()).isEmpty();
        assertThat(b.depth()).isZero();
    }

    // ============================================================
    // 2.5 空 list → 空 roots
    // ============================================================

    @Test
    @DisplayName("buildTree: 空 list → roots 空, 不抛")
    void buildTree_empty() {
        List<WbsTaskNode> tree = invokeBuildTree(List.of());
        assertThat(tree).isEmpty();
    }

    // ============================================================
    // 2.6 单节点 (无父无子) → 1 根
    // ============================================================

    @Test
    @DisplayName("buildTree: 单节点 → 1 根, depth=0, path=[ownCode]")
    void buildTree_singleNode() {
        WbsTask t = mkTask(1L, null, "1", "孤独根");
        List<WbsTaskNode> tree = invokeBuildTree(List.of(t));

        assertThat(tree).hasSize(1);
        WbsTaskNode n = tree.get(0);
        assertThat(n.id()).isEqualTo(1L);
        assertThat(n.depth()).isZero();
        assertThat(n.path()).containsExactly("1");
        assertThat(n.children()).isEmpty();
    }

    // ============================================================
    // helpers
    // ============================================================

    private static WbsTask mkTask(Long id, Long parentId, String code, String name) {
        WbsTask t = new WbsTask();
        t.setId(id);
        t.setProjectId(100L);
        t.setParentId(parentId);
        t.setWbsCode(code);
        t.setName(name);
        t.setTaskType("EXECUTION");
        t.setStatus("NOT_STARTED");
        t.setWeight(1);
        t.setProgressPct(0);
        t.setPlanHours(BigDecimal.ZERO);
        t.setActualHours(BigDecimal.ZERO);
        t.setPredecessorIds(new Long[0]);
        return t;
    }

    /**
     * buildTree 是 private, 反射调一次抽出来。
     * @return 树根列表
     */
    @SuppressWarnings("unchecked")
    private static List<WbsTaskNode> invokeBuildTree(List<WbsTask> flat) {
        try {
            // buildTree 拿不到 → 走 listTreeByProject 模拟 (但需要 mock Repository)
            // 这里用反射直接调 private buildTree
            java.lang.reflect.Method m = WbsService.class.getDeclaredMethod("buildTree", List.class);
            m.setAccessible(true);
            return (List<WbsTaskNode>) m.invoke(
                    new WbsService(null, null, null, null, null, null), flat);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
