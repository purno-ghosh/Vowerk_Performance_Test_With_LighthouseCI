import java.io.*;
import java.util.Properties;
import javax.activation.DataSource;
import javax.mail.*;
import javax.mail.internet.*;

public class LighthouseRunner {

    public static void main(String[] args) {
        try {
            // Load Configuration
            Properties properties = new Properties();
            try (InputStream input = LighthouseRunner.class.getClassLoader().getResourceAsStream("application.properties")) {
                if (input == null) {
                    System.out.println("Sorry, unable to find application.properties");
                    return;
                }
                properties.load(input);
            }

            String websiteUrl = properties.getProperty("website.url");
            String reportDir = properties.getProperty("report.dir");
            String reportFile = reportDir + "/report.html";
            String emailRecipient = properties.getProperty("email.recipient");

            // Run Lighthouse CI
            runLighthouse(websiteUrl, reportDir, reportFile);

            // Send Email
            sendEmail(properties, reportFile, emailRecipient);

        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    private static void runLighthouse(String websiteUrl, String reportDir, String reportFile) throws IOException, InterruptedException {
        ProcessBuilder processBuilder = new ProcessBuilder(
                "lhci", "collect", "--url=" + websiteUrl,
                "--preset=desktop",
                "--numberOfRuns=1",
                "--output=html",
                "--output-path=" + reportFile
        );
        processBuilder.directory(new File("."));
        processBuilder.redirectErrorStream(true);

        Process process = processBuilder.start();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println(line);
            }
        }

        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new IOException("Lighthouse CI failed with exit code: " + exitCode);
        }

        System.out.println("Lighthouse CI completed successfully.");
    }

    private static void sendEmail(Properties properties, String reportFile, String emailRecipient) throws MessagingException, IOException {
        String smtpHost = properties.getProperty("mail.smtp.host");
        String smtpPort = properties.getProperty("mail.smtp.port");
        String smtpUsername = properties.getProperty("mail.smtp.username");
        String smtpPassword = properties.getProperty("mail.smtp.password");
        String smtpFrom = properties.getProperty("mail.smtp.from");

        Properties mailProps = new Properties();
        mailProps.put("mail.smtp.host", smtpHost);
        mailProps.put("mail.smtp.port", smtpPort);
        mailProps.put("mail.smtp.auth", "true");
        mailProps.put("mail.smtp.starttls.enable", "true");

        Session session = Session.getInstance(mailProps, new Authenticator() {
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(smtpUsername, smtpPassword);
            }
        });

        Message message = new MimeMessage(session);
        message.setFrom(new InternetAddress(smtpFrom));
        message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(emailRecipient));
        message.setSubject("Lighthouse Performance Report");

        Multipart multipart = new MimeMultipart();

        MimeBodyPart textPart = new MimeBodyPart();
        textPart.setText("Please find the attached Lighthouse performance report.", "utf-8");
        multipart.addBodyPart(textPart);

        MimeBodyPart attachmentPart = new MimeBodyPart();
        File report = new File(reportFile);
        DataSource source = new javax.activation.FileDataSource(report);
        attachmentPart.setDataHandler(new javax.activation.DataHandler(source));
        attachmentPart.setFileName(report.getName());
        multipart.addBodyPart(attachmentPart);

        message.setContent(multipart);
        Transport.send(message);

        System.out.println("Email sent successfully to: " + emailRecipient);
    }
}
