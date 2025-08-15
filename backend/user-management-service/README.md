This package contains the *new*/updated classes to add:
  - DB-backed refresh token rotation and logout
  - Password reset token generation, email sending, and reset endpoint

How to use:
1. Copy the Java files under `src/main/java/com/company/user/...` into your existing project (package root `com.company.user`).
2. Ensure you have the corresponding repositories registered (Spring Data JPA).
3. Set the following properties in application.properties or environment:
   - app.reset-token.expiry-seconds (defaults to 3600)
   - APP_BASE_URL environment variable for password reset links (e.g. https://yourapp.com)
4. Make sure MailSender is configured for `EmailService`.
5. Wire the new services into your existing AuthController or replace it with the provided updated controller.

Files included:
- src/main/java/com/company/user/model/RefreshToken.java
- src/main/java/com/company/user/model/PasswordResetToken.java
- src/main/java/com/company/user/repository/RefreshTokenRepository.java
- src/main/java/com/company/user/repository/PasswordResetTokenRepository.java
- src/main/java/com/company/user/service/RefreshTokenService.java
- src/main/java/com/company/user/service/EmailService.java (updated)
- src/main/java/com/company/user/service/AuthService.java (updated - drop-in replacement)
- src/main/java/com/company/user/controller/AuthController.java (updated - drop-in replacement)

Notes:
- The provided AuthService expects the RecruiterRepository, PasswordResetTokenRepository, RefreshTokenService, EmailService etc. to be present.
- The reset link format is: $APP_BASE_URL/reset-password?token=<token>
- For security, the forgot-password endpoint is silent (doesn't reveal account existence).
