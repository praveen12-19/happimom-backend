# HappiMoM — Backend

Spring Boot REST API powering **HappiMoM**, a pregnancy and postpartum companion app.
It handles user authentication, mother/child/parent profile management, medical file
storage, and an AI assistant for casual conversation, health guidance, and
prescription/document reading.

## Tech Stack

- **Java 17+ / Spring Boot 3.4.3**
- **MySQL** (via Spring Data JPA / Hibernate)
- **Cloudinary** — medical file/prescription storage
- **Google Gemini API** — conversational AI, symptom guidance, file/image reading
- **Groq API** — fast conversational AI (fallback / primary for text chat)
- **Lombok**, **dotenv-java** for local environment config

## Project Structure

```
Backend/
├── src/main/java/com/healthcare/happimom/
│   ├── controller/     # REST endpoints
│   ├── service/        # Business logic (AI, file storage, auth)
│   ├── entity/          # JPA entities
│   ├── repository/      # Spring Data repositories
│   ├── dto/              # Request/response DTOs
│   ├── config/           # App configuration (CORS, security, etc.)
│   └── exception/        # Custom exception handling
├── src/main/resources/
│   └── application.properties
├── .env.example
└── pom.xml
```

## Getting Started

### Prerequisites
- Java 17+
- Maven
- MySQL running locally (or a remote instance)
- Free API keys for:
  - [Google Gemini](https://aistudio.google.com/apikey)
  - [Groq](https://console.groq.com/keys)
  - [Cloudinary](https://cloudinary.com/) (free tier)

### Setup

1. Clone the repo and navigate to `Backend/`.
2. Copy `.env.example` to `.env`:
   ```bash
   cp .env.example .env
   ```
3. Fill in your own values in `.env`:
   ```
   SERVER_PORT=8080

   DB_URL=jdbc:mysql://localhost:3306/happimom_db
   DB_USERNAME=your_mysql_username
   DB_PASSWORD=your_mysql_password

   GEMINI_API_KEY=your_gemini_key
   GEMINI_MODEL_URL=https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent

   GROQ_API_KEY=your_groq_key
   GROQ_MODEL_URL=https://api.groq.com/openai/v1/chat/completions

   CLOUDINARY_CLOUD_NAME=your_cloud_name
   CLOUDINARY_API_KEY=your_api_key
   CLOUDINARY_API_SECRET=your_api_secret
   ```
4. Create the MySQL database:
   ```sql
   CREATE DATABASE happimom_db;
   ```
5. Run the app:
   ```bash
   ./mvnw spring-boot:run
   ```
   The API will start on `http://localhost:8080`.


## API Overview

| Endpoint | Method | Description |
|---|---|---|
| `/api/auth/register` | POST | Register a new user |
| `/api/auth/login` | POST | Authenticate a user |
| `/api/users/{id}` | GET | Fetch a user's profile |
| `/api/users/{id}/profile` | PUT | Update a user's profile |
| `/api/upload/medical-file` | POST | Upload a medical file/prescription to Cloudinary |
| `/api/upload/files/{filename}` | GET | Retrieve an uploaded file |
| `/api/ai/chat` | POST | Chat with the AI assistant (casual talk, advice, health guidance, file reading) |
| `/api/ai/chat/stream` | POST | Same as above, streamed response |

## AI Assistant

The AI assistant is powered by **Gemini** (primary for file/image reading) and
**Groq** (primary for fast plain-text chat), with automatic fallback between the two
if one is unavailable. It handles:
- Casual conversation and greetings
- Pregnancy/postpartum advice and suggestions
- Non-diagnostic health condition guidance
- Reading and explaining uploaded prescriptions, lab reports, and medical documents

All AI responses include a disclaimer that they are informational only and not a
substitute for professional medical advice.

## Environment Variables Reference

| Variable | Description |
|---|---|
| `SERVER_PORT` | Port the backend runs on |
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | MySQL connection details |
| `GEMINI_API_KEY`, `GEMINI_MODEL_URL` | Google Gemini API config |
| `GROQ_API_KEY`, `GROQ_MODEL_URL` | Groq API config |
| `CLOUDINARY_CLOUD_NAME`, `CLOUDINARY_API_KEY`, `CLOUDINARY_API_SECRET` | Cloudinary config for file uploads |

## Contributing

1. Create a feature branch: `git checkout -b feature/your-feature`
2. Commit your changes
3. Push and open a pull request against `main`
4. Each contributor should use their **own** free Gemini/Groq/Cloudinary keys locally

## License

Add your license here (e.g., MIT).
