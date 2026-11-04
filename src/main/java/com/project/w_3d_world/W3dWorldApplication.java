package com.project.w_3d_world;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class W3dWorldApplication {

    public static void main(String[] args) {
        // Hikari mặc định kiểm tra lại kết nối (thêm 1 vòng mạng tới DB) mỗi khi kết nối nghỉ quá 0,5 giây.
        // Với Supabase ở xa (~260–320 ms/vòng) việc này cộng thêm độ trễ vào hầu hết request.
        // Kết nối được dùng trong 30 giây gần nhất được tin là còn sống; keepalive-time (application.properties)
        // vẫn kiểm tra định kỳ ở nền để phát hiện kết nối hỏng. Có thể ghi đè bằng -Dcom.zaxxer.hikari.aliveBypassWindowMs.
        if (System.getProperty("com.zaxxer.hikari.aliveBypassWindowMs") == null) {
            System.setProperty("com.zaxxer.hikari.aliveBypassWindowMs", "30000");
        }
        SpringApplication.run(W3dWorldApplication.class, args);
    }

}
