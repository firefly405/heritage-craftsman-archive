package com.qizixuan.heritage;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.qizixuan.heritage.entity.Announcement;
import com.qizixuan.heritage.entity.CraftCategory;
import com.qizixuan.heritage.entity.SysUser;
import com.qizixuan.heritage.mapper.AnnouncementMapper;
import com.qizixuan.heritage.mapper.CraftCategoryMapper;
import com.qizixuan.heritage.mapper.SysUserMapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.time.LocalDateTime;
import java.util.List;

@SpringBootApplication
@MapperScan("com.qizixuan.heritage.mapper")
@EnableScheduling
public class HeritageArchiveApplication {
    public static void main(String[] args) {
        SpringApplication.run(HeritageArchiveApplication.class, args);
    }

    @Bean
    BCryptPasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    CommandLineRunner seedData(SysUserMapper users, CraftCategoryMapper categories,
                               AnnouncementMapper announcements, BCryptPasswordEncoder encoder,
                               @Value("${app.seed-admin.username:}") String initialAdminUsername,
                               @Value("${app.seed-admin.password:}") String initialAdminPassword) {
        return args -> {
            boolean hasUsername = initialAdminUsername != null && !initialAdminUsername.isBlank();
            boolean hasPassword = initialAdminPassword != null && !initialAdminPassword.isBlank();
            if (hasUsername != hasPassword) throw new IllegalStateException("初始管理员用户名和密码必须同时配置");
            if (hasPassword && initialAdminPassword.length() < 6) throw new IllegalStateException("初始管理员密码不能少于6位");
            long adminCount = users.selectCount(new LambdaQueryWrapper<SysUser>().eq(SysUser::getRole, "ADMIN"));
            if (adminCount == 0 && !hasUsername) {
                throw new IllegalStateException("系统尚无管理员，请设置 INITIAL_ADMIN_USERNAME 和 INITIAL_ADMIN_PASSWORD 后重新启动");
            }
            SysUser sameName = hasUsername ? users.selectOne(new LambdaQueryWrapper<SysUser>()
                    .eq(SysUser::getUsername, initialAdminUsername.trim())) : null;
            if (adminCount == 0 && sameName != null) {
                throw new IllegalStateException("初始管理员用户名已被非管理员账号占用，请更换 INITIAL_ADMIN_USERNAME");
            }
            if (adminCount == 0) {
                SysUser admin = new SysUser();
                admin.setUsername(initialAdminUsername.trim());
                admin.setPassword(encoder.encode(initialAdminPassword));
                admin.setRealName("系统管理员");
                admin.setRole("ADMIN");
                admin.setEnabled(true);
                admin.setCreatedAt(LocalDateTime.now());
                users.insert(admin);
            }
            if (categories.selectCount(null) == 0) {
                List.of(
                    new CraftCategory(null, "传统剪纸", "以剪刀或刻刀在纸上创作纹样", LocalDateTime.now()),
                    new CraftCategory(null, "陶瓷烧制", "传统制陶、绘制与烧制技艺", LocalDateTime.now()),
                    new CraftCategory(null, "木雕技艺", "传统木雕与建筑雕饰技艺", LocalDateTime.now()),
                    new CraftCategory(null, "刺绣织造", "刺绣、织锦和染织类传统技艺", LocalDateTime.now())
                ).forEach(categories::insert);
            }
            if (announcements.selectCount(null) == 0) {
                Announcement item = new Announcement();
                item.setTitle("欢迎使用民间非遗手工艺人档案管理系统");
                item.setContent("请完善艺人档案并提交审核，审核通过后将进入正式档案库。");
                item.setPublisher("系统管理员");
                item.setPublished(true);
                item.setCreatedAt(LocalDateTime.now());
                announcements.insert(item);
            }
        };
    }
}
