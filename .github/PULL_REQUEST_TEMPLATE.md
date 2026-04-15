## Description

<!-- A clear and concise description of what this PR does. -->

Closes #<!-- issue number -->

---

## Type of Change

- [ ] 🐛 Bug fix
- [ ] 🚀 New feature
- [ ] 🔨 Refactor (no functional change)
- [ ] 📝 Documentation update
- [ ] ⚡ Performance improvement
- [ ] 🔒 Security fix

---

## How Has This Been Tested?

<!-- Describe the tests you ran and how to reproduce them. -->

- [ ] Unit tests added / updated
- [ ] Integration tests pass locally
- [ ] Manually tested via Swagger UI

---

## Checklist

- [ ] My code follows the project's layered architecture (Controller → Service → Repository)
- [ ] I have not exposed secrets or hardcoded credentials
- [ ] DTOs are used at the API boundary — entities are not returned directly
- [ ] New endpoints are documented in Swagger via `@Operation` / `@Tag`
- [ ] All new `@Service` methods are covered by the interface contract
- [ ] I have run `mvn clean verify` locally and it passes

---

## Screenshots / Logs (if applicable)

<!-- Add any relevant Swagger screenshots, curl output, or log snippets -->