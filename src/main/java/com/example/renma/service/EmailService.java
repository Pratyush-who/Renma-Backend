package com.example.renma.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    @Autowired
    private JavaMailSender mailSender;

    public void sendOtp(String to, String otp) {
        log.info("DEV OTP for {}: {}", to, otp);

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setTo(to);
            helper.setSubject("🔐 Verify your Email - Renma");

            String htmlContent = buildOtpEmail(otp);

            helper.setText(htmlContent, true); // true = HTML enabled

            mailSender.send(message);

        } catch (MessagingException e) {
            throw new RuntimeException("Failed to send OTP email", e);
        }
    }

    private String buildOtpEmail(String otp) {

        return """
            <div style="font-family: Arial, sans-serif; background:#f6f6f6; padding:30px;">
                <div style="max-width:500px;margin:auto;background:white;padding:25px;border-radius:10px;
                            box-shadow:0 2px 10px rgba(0,0,0,0.1);">

                    <h2 style="color:#333;">Welcome to Renma 🚀</h2>

                    <p style="font-size:15px;color:#555;">
                        Use the following OTP to verify your email address:
                    </p>

                    <div style="text-align:center;margin:25px 0;">
                        <span style="font-size:28px;letter-spacing:5px;
                                     font-weight:bold;color:#4CAF50;">
                            %s
                        </span>
                    </div>

                    <p style="font-size:13px;color:#888;">
                        This OTP is valid for a limited time. Do not share it with anyone.
                    </p>

                    <hr style="border:none;border-top:1px solid #eee;">

                    <p style="font-size:12px;color:#aaa;text-align:center;">
                        © Renma • Secure Authentication System
                    </p>
                </div>
            </div>
        """.formatted(otp);
    }
}
