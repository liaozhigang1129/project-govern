package com.company.pmo.module.member.dto;

import com.company.pmo.module.member.MemberRole;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 成员角色字典响应 — 前端下拉用
 */
public class MemberRoleResponse {
    public Long id;
    public String code;
    public String name;
    public String description;
    public int sortOrder;

    public static MemberRoleResponse from(MemberRole r) {
        MemberRoleResponse d = new MemberRoleResponse();
        d.id = r.getId();
        d.code = r.getCode();
        d.name = r.getName();
        d.description = r.getDescription();
        d.sortOrder = r.getSortOrder();
        return d;
    }

    public static List<MemberRoleResponse> fromList(List<MemberRole> rs) {
        return rs.stream().map(MemberRoleResponse::from).collect(Collectors.toList());
    }
}
