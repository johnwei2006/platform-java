package com.iot.platform.bootstrap;

import com.iot.platform.model.User;
import com.iot.platform.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.Date;

/**
 * 启动种子数据：保证至少存在一个管理员账号，便于前端联调登录。
 * 生产环境请删除或改为从配置/环境变量注入初始凭据，并哈希存储密码。
 */
@Component
public class DataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    @Autowired
    private UserRepository userRepository;

    @Override
    public void run(String... args) {
        if (!userRepository.findByUsername("admin").isPresent()) {
            User u = new User();
            u.setUsername("admin");
            u.setPassword("admin123");
            u.setRole("ADMIN");
            u.setCreatedAt(new Date());
            userRepository.save(u);
            log.info("seeded default admin user: admin / admin123");
        }
    }
}
