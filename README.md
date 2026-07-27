# Moka Agent

AI-powered post-meal voice callback system.  
Analyzes restaurant receipts, generates personalized conversation context, and pushes to a voice engine for natural post-dining follow-up calls.

## Tech Stack

| Component | Technology |
|-----------|-----------|
| Language  | Java 21 |
| Framework | Spring Boot 3.4.3 |
| AI SDK    | LangChain4j 1.0.0-beta2 |
| Text LLM  | DeepSeek (`deepseek-v4-flash`) |
| Vision LLM | OpenRouter (`qwen3.6-plus`) |
| Database  | PostgreSQL 16 |
| Cache     | Redis 7 |
| Weather   | Amap (Gaode) API + wttr.in fallback |

## Prerequisites

- JDK 21+
- Docker (for PostgreSQL & Redis — see `docker-compose.yml`)
- API keys for DeepSeek, OpenRouter, and Amap

## Configuration

Set these environment variables before starting:

| Variable | Required | Description |
|----------|----------|-------------|
| `DEEPSEEK_API_KEY` | Yes | DeepSeek API key for text inference |
| `OPENROUTER_API_KEY` | Yes | OpenRouter API key for vision (receipt OCR) |
| `GAODE_API_KEY` | Yes | Amap API key for weather queries |
| `DB_PASSWORD` | No | PostgreSQL password (default: `moka123` for local dev) |

Add a `spring.profiles.active=dev` for local development with mock LLM responses.

## Quick Start

```bash
# 1. Start infrastructure
docker compose up -d

# 2. Start the application
java -jar moka-agent.jar

# Production (explicit, though default already sets mock=false):
MOKA_LLM_MOCK=false java -jar moka-agent.jar

# Local development with mock mode:
java -jar moka-agent.jar --spring.profiles.active=dev
```

## Core Pipeline

The system runs a 6-step orchestrated pipeline (sequence configurable via `moka-workflow.yml`):

```
Receipt Photo → OCR parsing → Dish matching → Weather/Time context
→ Experience reasoning → Conversation planning → Runtime prompt
```

## API Endpoints

| Method | Path | Description |
|--------|------|-------------|
| POST | `/api/v1/orders/upload-photo` | Parse receipt photo → order data |
| POST | `/api/v1/calls/preview` | Generate prompt without photo (preview mode) |
| POST | `/api/v1/calls/prepare` | Full pipeline: photo → runtime prompt |
| POST | `/api/v1/calls/demo` | Full pipeline with all intermediate outputs |

> `POST /api/v1/proxy/sessions` is only available with `spring.profiles.active=demo` — not loaded in production.

## Project Structure

```
com.moka
├── ai/                  Core AI layer
│   ├── agent/           LLM agent interfaces & implementations
│   ├── context/         Data models (WorkflowContext, OrderData, etc.)
│   ├── workflow/        6-node orchestration pipeline
│   ├── retrieval/       Knowledge base (dish & restaurant data)
│   └── tools/           Weather tool
├── biz/controller/      REST API endpoints
├── demo/                Demo controllers (behind @Profile("demo"))
└── common/              Shared config (LLM clients, mock mode)
```
