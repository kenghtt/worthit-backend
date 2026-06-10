## Backend Starter Porting Task

I have an existing Spring Boot backend from another project called snap-vault-backend that has a solid foundation. I want to use it as a reference/template for this worthit-backend app.

Goal:
Create a new barebones Spring Boot backend for Worthit-backend by reusing only the generic infrastructure pieces from the existing project.

Do not copy over product-specific business logic, domain models, controllers, services, database tables, or feature code from the old app.

Port over only reusable backend foundation pieces such as:

- Spring Boot project structure
- Gradle/Maven configuration
- application.yml / application.properties structure
- environment/profile setup
- JWT authentication configuration
- security configuration
- CORS configuration
- exception handling
- logging setup
- validation setup
- common response/error models if generic
- health check or hello-world endpoint
- Docker/dev setup if applicable
- basic test setup
- README startup instructions

For Worthit-backend, create only a minimal starter backend with:

- app starts successfully
- `/api/hello` or `/hello` endpoint returns a simple response
- JWT/security config is wired but only minimal/auth-ready
- no old app-specific entities or business logic
- clean package names for worthIt
- clear TODOs where worthIt-specific features will be added later

After porting, verify:

- app compiles
- app runs locally
- hello endpoint works
- security/JWT config does not block local startup
- no references to the old project name remain unless intentionally documented