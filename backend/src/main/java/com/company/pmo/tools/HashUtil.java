package com.company.pmo.tools;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

public class HashUtil {
    public static void main(String[] args) {
        BCryptPasswordEncoder enc = new BCryptPasswordEncoder();
        // 验证 V1.4 种子里的 hash
        String stored = "$2a$10$j/Vd3KOxoOA0NgbdMa23r.ZN5Ka7sIDJQRsMQCQEwLBkfxdnsLU4G";
        for (String p : new String[]{"admin", "admin123", "password", "P@ssw0rd", "pmo2024", "Admin@123", "123456", "pmo123", "test"}) {
            System.out.println(p + " => " + enc.matches(p, stored));
        }
    }
}
