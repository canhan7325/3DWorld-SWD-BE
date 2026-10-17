package com.project.w_3d_world.common.service;

import jakarta.mail.MessagingException;

import lombok.RequiredArgsConstructor;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

@Service
@RequiredArgsConstructor
public class EmailService {
    private final JavaMailSender mailSender;

    @Value("${app.mail.from}")
    private String sender;

    public void sendLink(String recipient, String subject, String description, String link) {
        try {
            var message = mailSender.createMimeMessage();
            var helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(sender);
            helper.setTo(recipient);
            helper.setSubject("3DWorld - " + subject);
            String text =
                    description
                            + "\n\n"
                            + link
                            + "\n\nIf you did not request this action, you can ignore this email.";
            String html =
                    "<h2>3DWorld</h2><p>"
                            + HtmlUtils.htmlEscape(description)
                            + "</p><p><a href=\""
                            + HtmlUtils.htmlEscape(link)
                            + "\" style=\"display:inline-block;padding:12px"
                            + " 20px;background:#2563eb;color:white;border-radius:8px;text-decoration:none\">"
                            + HtmlUtils.htmlEscape(subject)
                            + "</a></p><p>If you did not request this action, you can ignore this email.</p>";
            helper.setText(text, html);
            mailSender.send(message);
        } catch (MessagingException exception) {
            throw new MailSendException("Cannot prepare authentication email", exception);
        }
    }
}
